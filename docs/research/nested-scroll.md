# Nested Scroll im Bottom Sheet (ohne Material3)

Recherche zu [#4](https://github.com/SirCedric/BottomSheet/issues/4), Stand **2026-08-12**.

## Rechercherahmen

| Was | Version / Pin |
| --- | --- |
| `androidx.compose.foundation` | **1.12.0** (aktuelles Stable, Release Notes vom 12.08.2026) |
| API-Surface geprüft gegen | `compose/foundation/foundation/api/1.12.0-beta01.txt` (= API-Snapshot der 1.12er-Linie) |
| Quelltext gelesen aus | `androidx/androidx@androidx-main`, HEAD `a64f2dff2c14e4fdf2bce99c16dd530bc8da325c` |
| `androidx.compose.material3` | Quelltext entspricht der **1.5.0-alpha**-Linie (`SheetDefaults.kt` zuletzt geändert in `080d2b3e`, 23.07.2026; zuletzt publiziert: 1.5.0-alpha25). Letztes M3-Stable: 1.4.0 |
| Historischer M3-Vergleichsstand | `d1acb6a88510d33e0bec0c4c55767fe0bc5498c8` (28.08.2025) |

M3 wird hier ausschließlich als **Referenzimplementierung gelesen**, nicht als Dependency.

## TL;DR — was wir übernehmen

1. Eine einzige `NestedScrollConnection` am Sheet-Root, oberhalb des Contents. Sie ist nötig, weil der innerste Pointer-Consumer (die `LazyColumn`) den Drag gewinnt und der `Modifier.anchoredDraggable` am Sheet-Root ihn nie zu sehen bekommt.
2. Arbeitsteilung: `onPreScroll` = Drag nach oben, Sheet zuerst; `onPostScroll` = Restdelta, Sheet zuletzt; `onPreFling` = Fling nach oben abfangen wenn Sheet noch nicht oben ist; `onPostFling` = Restvelocity ans Sheet.
3. **Alle** dafür nötigen Foundation-APIs sind in 1.12.0 stabil (seit **1.8.0**). Kein `@ExperimentalFoundationApi` nötig.
4. **Eine Lücke:** `Modifier.draggableAnchors` (Anker aus der Layout-Größe berechnen) ist M3-**intern** und nicht in Foundation public. Das müssen wir als eigenen `LayoutModifierNode` nachbauen — siehe [Abschnitt 4](#4-verdrahtung-mit-anchoreddraggablestate).

## 1. Der Nested-Scroll-Zyklus, wie `scrollable` ihn tatsächlich fährt

Maßgeblich ist `ScrollingLogic.performScroll` in
`compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/Scrollable.kt`:

```kotlin
private fun ScrollScope.performScroll(delta: Offset, source: NestedScrollSource): Offset {
    val consumedByPreScroll = nestedScrollDispatcher.dispatchPreScroll(delta, source)
    val scrollAvailableAfterPreScroll = delta - consumedByPreScroll
    val singleAxisDeltaForSelfScroll =
        scrollAvailableAfterPreScroll.singleAxisOffset().reverseIfNeeded().toFloat()
    val consumedBySelfScroll = scrollBy(singleAxisDeltaForSelfScroll).toOffset().reverseIfNeeded()
    // ...
    val deltaAvailableAfterScroll = scrollAvailableAfterPreScroll - consumedBySelfScroll
    val consumedByPostScroll =
        nestedScrollDispatcher.dispatchPostScroll(consumedBySelfScroll, deltaAvailableAfterScroll, source)
    return consumedByPreScroll + consumedBySelfScroll + consumedByPostScroll
}
```

Daraus folgen vier Dinge, die für unser Design load-bearing sind:

**(a) Overscroll kommt *nach* dem Parent, nicht davor.** Der Overscroll-Effekt umschließt `performScroll`:

```kotlin
override fun scrollByWithOverscroll(offset: Offset, source: NestedScrollSource): Offset {
    latestScrollSource = source
    val overscroll = overscrollEffect
    return if (overscroll != null && shouldDispatchOverscroll) {
        overscroll.applyToScroll(offset, latestScrollSource, performScrollForOverscroll)
    } else { /* performScroll direkt */ }
}
```

Der Effekt bekommt also nur, was *nach* `onPostScroll` übrig ist. Solange unser Sheet das Restdelta konsumiert, entsteht **kein** Stretch-Overscroll an der Liste. Dasselbe gilt für den Fling (`overscroll.applyToFling(availableVelocity, performFling)`, wobei `performFling` intern `dispatchPreFling` → Child-Fling → `dispatchPostFling` macht).

**(b) Nested-Scroll-Deltas sind immer in Gesten-Koordinaten.** `reverseIfNeeded()` wird ausschließlich auf den Self-Scroll-Anteil angewendet, nie auf das, was an die Parents dispatched wird. Im Fling-Pfad wird vor dem Dispatch bewusst zurückgedreht (`pixels.toOffset().reverseIfNeeded()`), damit derselbe Raum gilt. → **`reverseLayout = true` an der `LazyColumn` erzwingt keine Vorzeichenanpassung in unserer Connection.**

**(c) Deltas sind einachsig.** Am Drag-Eingang wird `it.delta.singleAxisOffset()` dispatched, und `singleAxisOffset()` nullt die Gegenachse:

```kotlin
fun Offset.singleAxisOffset(): Offset = if (orientation == Horizontal) copy(y = 0f) else copy(x = 0f)
```

Analog `singleAxisVelocity()` in `onScrollStopped`. → Ein horizontaler Scroll-Container im Sheet liefert `Offset(x, 0f)`; unsere vertikale Connection liest `y` und sieht `0f`.

**(d) Während eines Child-Flings ist `source == SideEffect`.** In `doFlingAnimation`:

```kotlin
return nestedScrollScope.scrollByWithOverscroll(
    offset = pixels.toOffset().reverseIfNeeded(),
    source = SideEffect,
)
```

Da M3 (und wir) in `onPreScroll`/`onPostScroll` auf `source == UserInput` filtern, bewegt sich das Sheet **während** des Listen-Flings nicht. Übergeben wird erst am Ende, über `onPostFling`.

`NestedScrollSource` hat in 1.12.0 nur noch zwei lebende Werte — `UserInput` (1) und `SideEffect` (2); `Drag`, `Fling`, `Wheel`, `Relocate` sind deprecated und mappen auf diese beiden (`NestedScrollModifier.kt`). Insbesondere ist **Mausrad = `UserInput`**.

## 2. M3s `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection`

Vollständig, aus `compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt` (Zeilen 610–669, androidx-main):

```kotlin
internal fun ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection(
    sheetState: SheetState,
    orientation: Orientation,
    flingBehavior: FlingBehavior,
): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.toFloat()
            return if (delta < 0 && source == NestedScrollSource.UserInput) {
                sheetState.anchoredDraggableState.dispatchRawDelta(delta).toOffset()
            } else Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val delta = available.toFloat()
            return if (source == NestedScrollSource.UserInput && delta != 0f) {
                sheetState.anchoredDraggableState.dispatchRawDelta(delta).toOffset()
            } else Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val toFling = available.toFloat()
            val currentOffset = sheetState.requireOffset()
            val minAnchor = sheetState.anchoredDraggableState.anchors.minPosition()
            return if (toFling < 0 && currentOffset > minAnchor) {
                sheetState.anchoredDrag(flingBehavior, toFling)
                // since we go to the anchor with tween settling, consume all for the best UX
                available
            } else Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val toFling = available.toFloat()
            val consumedByAnchoredDraggableFling = sheetState.anchoredDrag(flingBehavior, toFling)
            return Velocity(consumed.x, consumedByAnchoredDraggableFling)
        }
        // ... toFloat()/toOffset()-Helfer, die die Achse gemäß `orientation` auswählen
    }
```

### Aufgabenteilung der vier Phasen

| Phase | Bedingung | Aufgabe |
| --- | --- | --- |
| `onPreScroll` | `delta < 0` (Finger nach oben) **und** `UserInput` | Sheet **vor** der Liste hochziehen. `dispatchRawDelta` klemmt gegen `minPosition()`; ist das Sheet oben, konsumiert es 0 und die Liste scrollt. Reine Vorrangregel „erst aufziehen, dann scrollen". |
| `onPostScroll` | `UserInput` **und** Restdelta ≠ 0 | Sheet **nach** der Liste bewegen. Greift praktisch beim Ziehen nach unten, sobald die Liste am Anfang steht. Kein Vorzeichenfilter — die Richtung ergibt sich daraus, was übrig blieb. |
| `onPreFling` | `toFling < 0` **und** `currentOffset > minPosition()` | Fling nach oben abfangen, solange das Sheet noch nicht am oberen Detent ist: Sheet settelt auf den Zielanker, und es wird **die gesamte** Velocity konsumiert („consume all for the best UX"), damit die Liste nicht zusätzlich losfliegt. |
| `onPostFling` | ungefiltert | Restvelocity nach dem Listen-Fling ans Sheet: Liste hat den Anfang erreicht, der Rest schließt/kollabiert das Sheet. |

### Was sich gegenüber 2025 geändert hat

Snapshot `d1acb6a8` (28.08.2025) vs. heute:

| Damals | Heute |
| --- | --- |
| Parameter `onFling: (velocity: Float) -> Unit` (Callback, der `scope.launch { state.settle(velocity) }` fährt) | Parameter `flingBehavior: FlingBehavior`, `suspend` ausgeführt über `SheetState.anchoredDrag(flingBehavior, velocity)` |
| `onPostScroll` ohne `delta != 0f`-Guard | mit Guard |
| `anchors.minAnchor()` | `anchors.minPosition()` (umbenannt) |
| `onPostFling` gab `available` zurück (konsumiert alles) | gibt den Rückgabewert von `performFling` zurück |

Die zweite Zeile ist die relevante: Der Wechsel auf `FlingBehavior` erlaubt es, Settling und Fling in *einer* `anchoredDrag`-Transaktion zu fahren, statt eine zweite Coroutine gegen die laufende Geste zu starten. Wir sollten direkt die neue Form bauen.

### Der `anchoredDrag`-Brückenkopf

`SheetState.anchoredDrag` (SheetDefaults.kt, Z. 351–366) ist der Trick, mit dem ein `FlingBehavior` (das ein `ScrollScope` erwartet) auf einen `AnchoredDraggableState` (der ein `AnchoredDragScope` anbietet) angewendet wird:

```kotlin
internal suspend fun anchoredDrag(flingBehavior: FlingBehavior, initialVelocity: Float): Float {
    var consumedVelocity = 0f
    anchoredDraggableState.anchoredDrag {
        val scrollScope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                val newOffset = newOffsetForDelta(pixels)
                val consumed = newOffset - offset
                dragTo(newOffset)
                return consumed
            }
        }
        consumedVelocity = with(flingBehavior) { scrollScope.performFling(initialVelocity) }
    }
    return consumedVelocity
}
```

Wichtig: `anchoredDrag` läuft über die `MutatorMutex` des States, `dispatchRawDelta` **nicht** (siehe [Fallstricke](#7-fallstricke)).

## 3. Wo die Connection im Modifier-Chain hängt

`BottomSheet.kt` (androidx-main, Z. 275–334) — die Reihenfolge am Sheet-Root:

```
Modifier
  .widthIn(max = …).fillMaxWidth()
  .nestedScroll(ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection(state, Vertical, flingBehavior))
  .draggableAnchors(state.anchoredDraggableState, Vertical) { sheetSize, constraints -> anchors to newTarget }
  .anchoredDraggable(state = …, orientation = Vertical, enabled = …, flingBehavior = …)
  .semantics { … }
```

`nestedScroll` und `anchoredDraggable` sind komplementär, nicht redundant:

- Startet die Geste über der `LazyColumn`, gewinnt die Liste den Pointer (innerster Consumer) und speist das Sheet über Nested Scroll.
- Startet die Geste über Drag-Handle, Hintergrund oder nicht-scrollbarem Content, greift `anchoredDraggable` direkt.

Die `nestedScroll`-Position im Chain ist unkritisch, solange der Modifier ein Vorfahre des scrollbaren Contents ist. `remember(state) { … }` um die Connection ist Pflicht, sonst wird bei jeder Recomposition eine neue Connection installiert.

## 4. Verdrahtung mit `AnchoredDraggableState`

### API-Stabilität in Foundation 1.12.0

Geprüft gegen `api/1.12.0-beta01.txt` (und quergelesen gegen `api/current.txt`):

| API | Status in 1.12.0 |
| --- | --- |
| `AnchoredDraggableState<T>` (`@Stable`), Ctors `(initialValue)` / `(initialValue, anchors)` | **stabil** |
| `dispatchRawDelta(Float): Float` | **stabil** |
| `anchoredDrag(dragPriority, block)` und `anchoredDrag(targetValue, dragPriority, block)` | **stabil** |
| `updateAnchors(newAnchors, newTarget)` | **stabil** |
| `settle(animationSpec)`, `animateTo`, `animateToWithDecay`, `snapTo` | **stabil** |
| `offset`, `requireOffset()`, `progress(from, to)` | stabil, aber `@FrequentlyChangingValue` (seit 1.10.0-alpha01) — nicht in Composition lesen |
| `currentValue`, `settledValue`, `targetValue`, `lastVelocity`, `isAnimationRunning`, `anchors` | **stabil** |
| `DraggableAnchors<T>`: `minPosition()`, `maxPosition()`, `positionOf`, `hasPositionFor`, `closestAnchor`, `anchorAt`, `positionAt`, `size` | **stabil** |
| `DraggableAnchors { X at 0f }`-Builder / `DraggableAnchorsConfig` | **stabil** |
| `Modifier.anchoredDraggable(state, orientation, enabled, interactionSource, overscrollEffect, flingBehavior)` | **stabil** (die Überladung mit `startDragImmediately` ist `@Deprecated`) |
| `AnchoredDraggableDefaults.flingBehavior(state, positionalThreshold, animationSpec)` (`@Composable` → `TargetedFlingBehavior`), `.PositionalThreshold`, `.SnapAnimationSpec`, `.DecayAnimationSpec` | **stabil** |
| `AnchoredDraggableState.Companion.Saver()` (parameterlos) | **stabil**; die Overloads mit Specs/Thresholds sind `@Deprecated` |
| Ctors/Factories mit `positionalThreshold`/`velocityThreshold`/`snapAnimationSpec`/`decayAnimationSpec`; `settle(velocity)`; `progress` (property) | `@Deprecated` — nicht verwenden |

**Wann wurde stabilisiert:** in `api/1.7.0-beta01.txt` trägt `AnchoredDraggableState` noch `@ExperimentalFoundationApi`, in `api/1.8.0-beta01.txt` nicht mehr. → Promotion in **Foundation 1.8.0**. Achtung: die Guide-Seite *Migrate from Swipeable to AnchoredDraggable* auf developer.android.com behauptet weiterhin „AnchoredDraggable is an experimental API" und zeigt den alten Ctor mit `velocityThreshold` — die Seite ist veraltet, maßgeblich sind die API-Snapshots.

Konsequenz für uns: **keine Opt-ins**, keine `@OptIn`-Verschmutzung der Library-API. Nur `compose.foundation` + `compose.ui` wie geplant.

### Anker aus der Layout-Größe — die eine echte Lücke

`state.updateAnchors(...)` ist stabil, aber der Modifier, der es zur richtigen Zeit ruft, ist es nicht. `Modifier.draggableAnchors` liegt in `androidx.compose.material3.internal` (`compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/internal/DraggableAnchors.kt`) und taucht in `foundation/api/current.txt` nicht auf. Er ist ein `LayoutModifierNode`, der:

1. misst,
2. **in der Measure-Phase** `state.updateAnchors(newAnchors, suggestedTarget)` ruft,
3. im Placement mit `withMotionFrameOfReferencePlacement { placeable.place(0, offset.roundToInt()) }` platziert,
4. in einem Lookahead-Pass die Position des `targetValue` statt `state.offset` verwendet.

Das deckt sich mit dem KDoc von `updateAnchors`:

> **If your anchors depend on the size of the layout, updateAnchors should be called in the layout (placement) phase, e.g. through Modifier.onSizeChanged.** This ensures that the state is set up within the same frame.

Unsere Detents `medium` (= Content-Höhe) und `large` (= unter Statusbar) hängen beide von der gemessenen Sheet-Höhe ab → wir brauchen dieses Verhalten. Zwei Optionen:

- **A (empfohlen):** eigener `LayoutModifierNode` nach dem M3-Vorbild. ~60 Zeilen, nur `compose.ui`-APIs, gibt uns Lookahead-Korrektheit und das `withMotionFrameOfReferencePlacement`-Tagging gratis.
- **B (Fallback):** `Modifier.onSizeChanged { updateAnchors(...) } .offset { IntOffset(0, state.offset.roundToInt()) }`. Einfacher, aber `onSizeChanged` feuert *nach* der Placement-Phase → ein Frame mit `offset == NaN` bzw. ein sichtbarer Sprung beim ersten Layout, und kein Lookahead-Support.

Das ist als Entscheidung fürs Layout-Ticket vorzumerken.

## 5. Sheet auf dem oberen Detent, Liste nicht am Anfang

Koordinatensystem: `offset` ist die y-Verschiebung des Sheet-Roots nach unten. `large` = `minPosition()`, `medium` > `large`, ein evtl. `hidden` = `maxPosition()`. Fingerbewegung nach oben = **negatives** Delta.

| Sheet | Liste | Geste | Ergebnis |
| --- | --- | --- | --- |
| `large` (== `minPosition`) | mittendrin | nach oben (`delta < 0`) | `onPreScroll` ruft `dispatchRawDelta`, das gegen `minPosition()` klemmt → konsumiert **0** → Liste scrollt normal weiter. |
| `large` | mittendrin | nach unten (`delta > 0`) | `onPreScroll` ignoriert positive Deltas → **Liste scrollt zuerst** zurück. Restdelta 0 → Sheet bleibt oben. Genau das gewünschte Verhalten. |
| `large` | am Anfang | nach unten | Liste konsumiert nichts → `onPostScroll` bekommt volles Delta → Sheet fährt Richtung `medium`/`hidden`. |
| `large` | am Anfang | nach oben | `onPreScroll` klemmt (0), Liste kann nicht (0) → Restdelta erreicht den Overscroll-Effekt → Stretch. Korrekt. |
| `medium` | mittendrin | nach oben | `onPreScroll` konsumiert **alles**, bis `large` erreicht ist; erst danach scrollt die Liste. |
| `medium` | mittendrin | Fling nach oben | `onPreFling` greift (`toFling < 0 && offset > minPosition`): Sheet settelt nach `large`, **gesamte** Velocity konsumiert, die Liste flingt nicht. |
| `large` | mittendrin | Fling nach unten | Liste flingt (`source == SideEffect`, Sheet rührt sich nicht). Erreicht die Liste den Anfang, geht die Restvelocity in `onPostFling` → **das Sheet kollabiert im selben Gestenzug**. |

Der letzte Fall ist das bekannte „ein kräftiger Flick schließt das Sheet"-Verhalten von M3. `onPostFling` hat *keinen* Guard. Wenn wir das nicht wollen, ist die Stelle zum Eingreifen genau dort — z.B. Restvelocity nur akzeptieren, wenn sie über einem Schwellwert liegt, oder wenn seit dem Erreichen des Listenanfangs weniger als X ms vergangen sind. Das ist eine Produktentscheidung, keine technische Einschränkung.

## 6. Horizontale Container und Pager im Sheet

**Horizontal (`LazyRow`, `HorizontalPager`, `horizontalScroll`): unkritisch.** Wegen `singleAxisOffset()`/`singleAxisVelocity()` (siehe 1c) dispatched ein horizontaler Scroller `Offset(x, 0f)` und `Velocity(x, 0f)`. Unsere vertikale Connection liest `y` → immer `0f` → `dispatchRawDelta(0f)` konsumiert nichts, `onPreFling`/`onPostFling` bekommen `0f`. Zusätzlich richtet der Orientation-Lock in `draggable`/`scrollable` die Geste einachsig aus, sodass auch der `anchoredDraggable` am Sheet-Root nicht dazwischenfunkt.

Der einzige Fallstrick: die Achsen-Extraktion muss `orientation`-gebunden sein (M3 macht das über `Offset.toFloat()`). Wer stattdessen `available.y + available.x` o.ä. rechnet, zerstört genau diese Trennung.

**`VerticalPager` im Sheet: problematisch.** Der Pager ist ein vertikaler Scroller und dispatched dieselben y-Deltas wie eine `LazyColumn` — die Scroll-Phasen funktionieren also. Der Fling nicht: `onPreFling` konsumiert bei `toFling < 0 && offset > minPosition` die **gesamte** Velocity, damit verliert der Pager seine `SnapFlingBehavior`-Velocity und bleibt zwischen zwei Seiten stehen bzw. snappt zurück. Wenn wir `VerticalPager` im Sheet supporten wollen, brauchen wir dafür eine bewusste Ausnahme (Connection nur bei `state.offset > minPosition` aktivieren, oder ein Opt-out-Flag am Sheet-Content). Empfehlung: **erst mal nicht supporten und dokumentieren.**

**Verschachtelte vertikale Scroller** (zwei `LazyColumn` übereinander) haben dasselbe Problem in verschärfter Form — die innere Liste dispatched an die äußere, die äußere ans Sheet. Funktioniert, solange jede Ebene sauber „was übrig bleibt" weitergibt.

## 7. Fallstricke

**`dispatchRawDelta` umgeht die `MutatorMutex`.** Aus `AnchoredDraggable.kt` (Z. 1247–1252):

```kotlin
public fun dispatchRawDelta(delta: Float): Float {
    val newOffset = newOffsetForDelta(delta)
    val consumedDelta = (newOffset - requireOffset())
    anchoredDragScope.dragTo(newOffset)   // kein dragMutex.mutate!
    return consumedDelta
}
```

`anchoredDrag`/`animateTo`/`settle` gehen über `dragMutex.mutate(...)`, `dispatchRawDelta` schreibt direkt. Läuft also eine `animateTo`-Animation (programmatisches Öffnen, Settling) und der User beginnt gleichzeitig in der Liste zu scrollen, kämpfen beide um `offset`. M3 lebt damit. Wenn wir es sauberer wollen: in `onPreScroll`/`onPostScroll` bei `state.isAnimationRunning` nichts konsumieren, oder die Animation vorher abbrechen.

**`requireOffset()` wirft, solange die Anker nicht gesetzt sind.** `checkPrecondition(!offset.isNaN())` mit der Meldung „The offset was read before being initialized". M3s `onPreFling` ruft `requireOffset()` ungeschützt. Bei uns kann ein Fling theoretisch vor dem ersten Layout eintreffen (Content mit Höhe 0, Sheet gerade erst komponiert) → in der eigenen Implementierung lieber `state.offset` lesen und auf `isNaN()` prüfen.

**`performFling` liefert *remaining*, nicht *consumed*.** `FlingBehavior.performFling` ist dokumentiert mit „@return remaining velocity after fling operation has ended" — `Scrollable.doFlingAnimation` behandelt den Rückgabewert entsprechend als „velocity left". M3s `SheetState.anchoredDrag` nennt denselben Wert `consumedVelocity` und `onPostFling` gibt ihn als konsumiert zurück. Das ist inkonsistent. Für uns: entweder alles konsumieren (`return available`, wie M3 es bis 2025 tat) oder korrekt `available.y - remaining` zurückgeben. Nicht das M3-Muster blind kopieren.

**Fling-Übergabe passiert nur an Fling-Enden.** Weil pre/post-Scroll auf `UserInput` filtern, bewegt sich das Sheet während des Listen-Flings nicht; die Übergabe ist ein diskreter Sprung in `onPostFling`. Das fühlt sich bei langsam auslaufenden Flings nach „Verzögerung" an. Wer stattdessen `SideEffect` mitkonsumiert, bekommt kontinuierliche Übergabe — aber auch, dass ein Listen-Fling das Sheet mitzieht, was M3 bewusst nicht will.

**Overscroll.** Kein Konflikt (siehe 1a): der Effekt sieht nur, was nach `onPostScroll` übrig ist. Aber: `Modifier.anchoredDraggable` nimmt selbst einen `overscrollEffect`-Parameter. Wenn wir dort einen Effekt reinreichen *und* die Liste ihren Default-Effekt behält, bekommen wir zwei sichtbare Stretch-Animationen. Für v1: dem Sheet **keinen** Overscroll-Effekt geben.

**`reverseLayout`.** Vorzeichen bleiben gleich (siehe 1b), unsere Connection braucht keinen Sonderfall. Was sich ändert, ist die Semantik von „Liste am Anfang": bei `reverseLayout = true` ist der Content unten verankert, d.h. der Punkt, an dem das Sheet die Kontrolle übernimmt, liegt am *visuellen* oberen Rand — was aus Nutzersicht identisch bleibt. Das ergibt sich automatisch daraus, dass wir auf Restdelta reagieren und nicht auf `listState.firstVisibleItemIndex`. **Genau deshalb sollten wir den Listenzustand nicht selbst abfragen.**

**Mausrad / externe Eingaben.** `NestedScrollSource.Wheel` ist deprecated und mappt auf `UserInput`. Ein Scrollrad über der Liste bewegt also auch das Sheet. Bei minSdk 29 / Touch-first tolerierbar, aber bei Tablet-/DeX-Nutzung sichtbar.

**Connection nicht `remember`n.** Eine neue Connection-Instanz pro Recomposition reinstalliert den Nested-Scroll-Node und kann laufende Gesten abreißen lassen.

## 8. Code-Skizze für unsere Library

Nur `compose.foundation` + `compose.ui`, keine Opt-ins, Foundation 1.12.0.

```kotlin
package de.example.bottomsheet

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDrag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Verzahnt den vertikalen Drag des Sheets mit einem Scroll-Container im Sheet-Content.
 *
 * Vorrangregel:
 * - nach oben: erst das Sheet aufziehen (onPreScroll), dann die Liste scrollen
 * - nach unten: erst die Liste zurückscrollen, dann das Sheet schliessen (onPostScroll)
 */
internal fun <T> sheetNestedScrollConnection(
    state: AnchoredDraggableState<T>,
    flingBehavior: FlingBehavior,
): NestedScrollConnection = object : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0f && source == NestedScrollSource.UserInput) {
            Offset(0f, state.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = available.y
        return if (delta != 0f && source == NestedScrollSource.UserInput) {
            Offset(0f, state.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.y
        val offset = state.offset
        if (offset.isNaN()) return Velocity.Zero
        return if (toFling < 0f && offset > state.anchors.minPosition()) {
            state.flingToNearestAnchor(flingBehavior, toFling)
            // Settling laeuft als Tween bis zum Anker -> gesamte Velocity schlucken,
            // sonst flingt die Liste zusaetzlich los.
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y == 0f || state.offset.isNaN()) return Velocity.Zero
        val remaining = state.flingToNearestAnchor(flingBehavior, available.y)
        return Velocity(consumed.x, available.y - remaining)
    }
}

/**
 * Fuehrt [flingBehavior] gegen den [AnchoredDraggableState] aus. Adaptiert den
 * [ScrollScope], den FlingBehavior erwartet, auf den AnchoredDragScope des States.
 *
 * @return die nach dem Fling uebrige Velocity (FlingBehavior-Kontrakt)
 */
private suspend fun <T> AnchoredDraggableState<T>.flingToNearestAnchor(
    flingBehavior: FlingBehavior,
    initialVelocity: Float,
): Float {
    var remaining = 0f
    anchoredDrag {
        val scrollScope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                val current = if (offset.isNaN()) 0f else offset
                val newOffset =
                    (current + pixels).coerceIn(anchors.minPosition(), anchors.maxPosition())
                dragTo(newOffset)
                return newOffset - current
            }
        }
        remaining = with(flingBehavior) { scrollScope.performFling(initialVelocity) }
    }
    return remaining
}
```

Verwendung am Sheet-Root:

```kotlin
val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)   // @Composable, stabil
val connection = remember(state, flingBehavior) {
    sheetNestedScrollConnection(state, flingBehavior)
}

Box(
    Modifier
        .fillMaxWidth()
        .nestedScroll(connection)
        .sheetAnchors(state) { size, constraints -> /* medium/large aus size+constraints */ }
        .anchoredDraggable(
            state = state,
            orientation = Orientation.Vertical,
            flingBehavior = flingBehavior,
        )
) { content() }
```

`AnchoredDraggableDefaults.flingBehavior` ist ein `snapFlingBehavior` mit `NoOpDecayAnimationSpec` — es decayt nie, sondern snappt über `SnapLayoutInfoProvider.calculateSnapOffset` direkt auf den Zielanker, berechnet aus `positionalThreshold` (default 50% der Distanz) und `AnchoredDraggableMinFlingVelocity` (**125 dp/s**). Für unsere zwei bis drei Detents ist das genau das richtige Verhalten; ein eigener `FlingBehavior` ist nur nötig, wenn wir Dinge wie M3s Boundary-Dampening nahe dem Hidden-Anker wollen.

`sheetAnchors` ist der in [Abschnitt 4](#4-verdrahtung-mit-anchoreddraggablestate) beschriebene, selbst zu bauende `LayoutModifierNode`.

## 9. Offene Punkte fürs Layout-Ticket

1. `draggableAnchors`-Äquivalent: eigener `LayoutModifierNode` (A) oder `onSizeChanged` + `offset` (B)? Empfehlung: A.
2. Soll ein Fling nach unten, der die Liste bis zum Anfang trägt, im selben Zug das Sheet schließen (M3-Verhalten) — oder guarden wir `onPostFling`?
3. `VerticalPager` im Sheet: bewusst nicht supporten (empfohlen) oder Ausnahme in `onPreFling` bauen?
4. Overscroll: Sheet bekommt keinen `overscrollEffect`. Liste behält ihren Default. Bestätigen.
5. Kollidiert die Sheet-Geste mit dem System-Back-Gesture-Bereich / Predictive Back? Nicht Teil dieser Recherche.

## Quellen

Alle Quelltext-Links auf `androidx-main` @ `a64f2dff2c14e4fdf2bce99c16dd530bc8da325c` (2026-08-12).

- `SheetDefaults.kt` (M3-Referenz, `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection`, `SheetState.anchoredDrag`) — https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt
- Historischer Stand desselben Files @ `d1acb6a88510d33e0bec0c4c55767fe0bc5498c8` (28.08.2025) — https://github.com/androidx/androidx/blob/d1acb6a88510d33e0bec0c4c55767fe0bc5498c8/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt
- `BottomSheet.kt` (Modifier-Chain, Anker-Berechnung, `modalBottomSheetFlingBehavior`) — https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/BottomSheet.kt
- `internal/DraggableAnchors.kt` (M3-interner `Modifier.draggableAnchors`) — https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/internal/DraggableAnchors.kt
- `AnchoredDraggable.kt` (Foundation: `dispatchRawDelta`, `anchoredDrag`, `updateAnchors`, `requireOffset`, `AnchoredDraggableDefaults`) — https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/AnchoredDraggable.kt
- `Scrollable.kt` (`ScrollingLogic.performScroll`, `onScrollStopped`, `doFlingAnimation`, `singleAxisOffset`) — https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/Scrollable.kt
- `FlingBehavior.kt` (Kontrakt „@return remaining velocity") — https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/FlingBehavior.kt
- `NestedScrollModifier.kt` (`NestedScrollConnection`, `NestedScrollSource`) — https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/input/nestedscroll/NestedScrollModifier.kt
- API-Snapshots Foundation: [`1.12.0-beta01.txt`](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/api/1.12.0-beta01.txt), [`1.8.0-beta01.txt`](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/api/1.8.0-beta01.txt), [`1.7.0-beta01.txt`](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/api/1.7.0-beta01.txt), [`current.txt`](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/api/current.txt)
- Compose Foundation Release Notes (Stable 1.12.0; `@FrequentlyChangingValue` in 1.10.0-alpha01) — https://developer.android.com/jetpack/androidx/releases/compose-foundation
- Nested scrolling (Guide) — https://developer.android.com/develop/ui/compose/touch-input/pointer-input/nested-scroll
- Migrate from Swipeable to AnchoredDraggable (Guide, **veraltet**: behauptet experimentell) — https://developer.android.com/develop/ui/compose/touch-input/pointer-input/migrate-swipeable
