# BottomSheet — Spec v1

Eine Jetpack-Compose-Library, die ein Bottom Sheet per Modifier an eine beliebige View hängt und sich wie SwiftUIs `.sheet` mit `presentationDetents` verhält. Android-only, Material3-frei.

Dieses Dokument fasst die Entscheidungen aus [Weg zur Spec](https://github.com/SirCedric/BottomSheet/issues/1) zusammen — elf Tickets und ihre Nachträge. Es ist die Vorlage für die Implementierung; die Begründungen bleiben in den Tickets, hier steht nur, was gilt. Jeder Abschnitt nennt seine Quelle.

Die Begriffe (Sheet, Detent, Anchor, Host, Owner, Scrim, Dismiss-Versuch, …) definiert [`CONTEXT.md`](../CONTEXT.md) und werden hier ohne Wiederholung benutzt.

---

## 1. Aufbau

Die Library besteht aus zwei Teilen, die zusammengehören:

**`BottomSheetHost`** — ein einmaliger Wrapper um den App-Content, ganz oben in `setContent`. Er rendert alle Sheets, liegt über der **gesamten** App und hält die App-weiten Defaults.

**`Modifier.bottomSheet(…)`** — hängt ein Sheet an ein beliebiges Composable. Der Owner bestimmt, *was* im Sheet steht; gezeichnet wird es im Host.

```kotlin
setContent {
    BottomSheetHost {
        App()
    }
}

Box(
    modifier = Modifier.bottomSheet(
        isPresented = state.isFilterSheetPresented,
        onDismissRequest = { onAction(OnFilterSheetDismiss) },
    ) {
        FilterSheetContent(currentDetent, state, onAction)
    },
)
```

Der Host rendert **in-composition** — ein Overlay-`Box` am Root, kein `Popup` und kein eigenes Window. Die Popup-Variante wurde gebaut und verworfen: sie liefert im Sheet-Content `WindowInsets.safeDrawing` als 0/0 px und lässt die Statusbar vom Scrim frei ([#6](https://github.com/SirCedric/BottomSheet/issues/6)).

Der Preis dieser Entscheidung ist Abschnitt 9: Fokus-Trapping und Abschirmung des Accessibility-Baums bringt ein eigenes Window gratis mit, in-composition müssen sie gebaut werden.

**Genau ein Sheet ist gleichzeitig sichtbar.** Registrieren sich zwei offene Sheets, gewinnt das zuletzt registrierte und die Library warnt mit beiden Owner-Stellen. Die Zusage gilt dem Bild, nicht der Registry.

**Ohne Host kracht es laut** — sofort beim ersten Rendern, nicht erst beim Öffnen:

```
java.lang.IllegalStateException: Modifier.bottomSheet ohne BottomSheetHost verwendet.
Umschließe den Root deiner App mit BottomSheetHost { ... }.
```

---

## 2. Public API

Quelle: [#10](https://github.com/SirCedric/BottomSheet/issues/10) samt drei Nachträgen. `explicitApi()` ist strict, **nichts** trägt `@Experimental`.

```kotlin
package dev.sircedric.bottomsheet

@Immutable
public sealed interface PresentationDetent {
    public data object Medium : PresentationDetent
    public data object Large : PresentationDetent
}

@Immutable
public class SheetDetents private constructor(
    internal val values: Set<PresentationDetent>,
) {
    public companion object {
        public val Medium: SheetDetents = SheetDetents(setOf(PresentationDetent.Medium))
        public val Large: SheetDetents = SheetDetents(setOf(PresentationDetent.Large))
        public val MediumAndLarge: SheetDetents =
            SheetDetents(setOf(PresentationDetent.Medium, PresentationDetent.Large))

        public fun of(
            first: PresentationDetent,
            vararg rest: PresentationDetent,
        ): SheetDetents = SheetDetents(setOf(first, *rest))
    }
}

@Stable
public interface BottomSheetScope {
    public val currentDetent: PresentationDetent
    public fun animateTo(detent: PresentationDetent)
}

@Immutable
public class BottomSheetColors internal constructor(
    public val sheet: Color,
    public val handle: Color,
    public val scrim: Color,
    public val scrimMaxAlpha: Float,
)

@Immutable
public class BottomSheetMotion internal constructor(
    public val animationSpec: AnimationSpec<Float>,
)

@Immutable
public class BottomSheetDetentNames internal constructor(
    public val medium: String?,
    public val large: String?,
)

public object BottomSheetDefaults {

    public val Shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    public const val AppContentMinScale: Float = 0.92f

    /** `0.16f` ab API 31 (dort trägt der Blur mit), sonst `0.32f`. */
    public fun scrimMaxAlpha(): Float

    public fun colors(
        sheet: Color = Color.White,
        handle: Color = Color.Black.copy(alpha = 0.4f),
        scrim: Color = Color.Black,
        scrimMaxAlpha: Float = scrimMaxAlpha(),
    ): BottomSheetColors

    public fun motion(
        animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 380f),
    ): BottomSheetMotion

    /** Ohne Werte sagt die Library keinen Zustand an — sie bringt keine Strings mit. */
    public fun detentNames(
        medium: String? = null,
        large: String? = null,
    ): BottomSheetDetentNames

    @Composable
    public fun DragHandle(modifier: Modifier = Modifier)
}

@Composable
public fun BottomSheetHost(
    modifier: Modifier = Modifier,
    colors: BottomSheetColors = BottomSheetDefaults.colors(),
    motion: BottomSheetMotion = BottomSheetDefaults.motion(),
    shape: Shape = BottomSheetDefaults.Shape,
    appContentMinScale: Float = BottomSheetDefaults.AppContentMinScale,
    detentNames: BottomSheetDetentNames = BottomSheetDefaults.detentNames(),
    content: @Composable () -> Unit,
)

public fun Modifier.bottomSheet(
    isPresented: Boolean,
    onDismissRequest: () -> Unit,
    presentationDetents: SheetDetents = SheetDetents.MediumAndLarge,
    initialDetent: PresentationDetent = PresentationDetent.Medium,
    gesturesEnabled: Boolean = true,
    interactiveDismissEnabled: Boolean = true,
    onDismissAttempt: (() -> Unit)? = null,
    onExpandAttempt: (() -> Unit)? = null,
    paneTitle: String? = null,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    colors: BottomSheetColors? = null,
    motion: BottomSheetMotion? = null,
    shape: Shape? = null,
    appContentMinScale: Float? = null,
    content: @Composable BottomSheetScope.() -> Unit,
): Modifier
```

### Regeln zur Signatur

**`isPresented` + `onDismissRequest`, kein `MutableState`.** State lebt im ViewModel als `StateFlow`; ein `MutableState`-Parameter wäre von dort nicht befüllbar.

**`onDismissRequest` feuert nur bei library-ausgelösten Dismissals** — interaktives Dismiss und der `onDetach`-Fall. Setzt die App selbst `isPresented = false`, feuert er nicht. Daher *Request*, passend zu *Attempt*.

**`SheetDetents` hat einen privaten Konstruktor**, `of()` erzwingt ein erstes Argument. Die leere Menge ist zur Compile-Zeit ausgeschlossen — kein `require`, kein Test dafür.

**`PresentationDetent` ist ein Sealed Interface, kein Enum.** Freie Höhen (`.height(x)`, `.fraction(x)`) können später ohne Bruch dazukommen.

**`Hidden` ist nicht öffentlich.** In einer Menge erlaubter Ruhepositionen wäre es ein illegaler Zustand; „geschlossen" drückt `isPresented` aus. Presented und Detent bleiben orthogonal.

**`initialDetent` gilt bei jedem Übergang `false → true`** — eine neue Präsentation ist neu, nicht die Fortsetzung der letzten. Fehlt der Wert in `presentationDetents`, greift der kleinste enthaltene Detent.

**Kein State-Holder.** Was die App braucht, liefert `BottomSheetScope` im Content ohne `remember`. `currentDetent` ist der **geruhte** Wert, nicht der laufende Offset. `animateTo` ist **nicht** `suspend`. Kein `dismiss()` — ein zweiter Schließweg am App-State vorbei ist Desync.

**Host = App-Default, Modifier = nullbarer Override.** `null` heißt „nimm die des Hosts". Kein `CompositionLocal`, weil `Modifier.bottomSheet` eine gewöhnliche, nicht-composable Extension ist. **Ausnahme:** `detentNames` gibt es nur am Host — die Namen sind app-weit identisch.

**Scrim-Farbe und Max-Alpha bleiben getrennt.** Das Alpha wird animiert, die Farbe nicht; und nur ein eigener Parameter kann als Default eine Funktion tragen, die das API-Level liest.

**`@Immutable class` + Factory, keine `data class`.** `copy()` und `componentN()` würden Teil der Binär-API und bräche jedes später hinzugefügte Member.

**Scharfe Kante, die bleibt:** `sheet = Color.White` ist im Dark Mode falsch. Die Library ist M3-frei und kennt kein Theme. Eine Dark-Mode-App setzt `BottomSheetHost(colors = …)` einmal — dafür ist der App-weite Default da.

---

## 3. Layout und Detents

Quelle: [#7](https://github.com/SirCedric/BottomSheet/issues/7) mit Revision aus [#9](https://github.com/SirCedric/BottomSheet/issues/9).

Der Content wird in einem `Modifier.layout {}` mit `maxHeight = largeHeight` gemessen, und die Anchors werden **im selben Pass** gesetzt. Das ist der dokumentierte Weg für größenabhängige Anchors — kein `SubcomposeLayout`, kein `onGloballyPositioned`, keine Vorab-Messung.

| Anchor | Position |
|---|---|
| `Hidden` | `containerHeight` |
| `Medium` | `containerHeight − mediumHeight` |
| `Large` | `topInset` |

```kotlin
val largeHeight = (containerHeight - topInset).coerceAtLeast(0)
val mediumHeight = if (contentHeight >= largeHeight) {
    (largeHeight * 0.5f).roundToInt()
} else {
    contentHeight
}
```

**Das Panel ist immer `largeHeight` hoch**, der Content sitzt oben darin. Bei `medium` ragt es unten aus dem Bildschirm — dadurch bleibt der Hintergrund lückenlos und der Corner-Radius sitzt oben richtig.

**`large` misst an `safeDrawing`, nicht an `statusBars`** — sonst bleibt das Sheet auf Geräten, deren Cutout die Statusbar überragt, unter der Kamera hängen.

**Content ≥ `largeHeight`**: `medium` fällt **nicht** weg, sondern bekommt 50 % von `large`. Die ursprüngliche Kappungs-Regel aus #7 ist revidiert — sie nahm dem Nutzer die Zwischenstufe genau dann, wenn am meisten Inhalt da war.

**Scrollen ist Sache der App.** Ohne eigenen Scroll-Container wird zu hoher Content abgeschnitten. Ein von der Library aufgezwungener Container würde mit `LazyColumn`s kollidieren.

**Ändert sich die Content-Höhe zur Laufzeit**, wird auf den neuen Anchor **animiert**, nicht gesprungen (`updateAnchors` würde `trySnapTo` aufrufen). Läuft gerade ein Drag oder eine Animation, zieht der Anchor still nach.

**Der Content wird erst beim Öffnen komponiert** — und bleibt bis zum **Ende der Exit-Animation** am Leben (siehe Abschnitt 5). Vorab-Komposition bringt keinen Positionsvorteil und legt einen Scrim mit `alpha = 0` über die App, der jeden Tap schluckt.

**Z-Ordnung**: App-Content, darüber der Scrim über die volle Fläche, darüber das Panel. Der Corner-Radius wird per `clip` **vor** dem `background` gesetzt, damit er auch den Content beschneidet.

---

## 4. Animation, Scrim und App-Content

Quelle: [#9](https://github.com/SirCedric/BottomSheet/issues/9). Alle Werte am Gerät beurteilt; Apple gibt dazu nichts her, es ist kein Nachbau.

| | Wert |
|---|---|
| Enter-/Exit-Kurve | `spring(dampingRatio = 0.9f, stiffness = 380f)` |
| Scrim-Kopplung | linear am Offset, dunkelt bis `large` weiter nach |
| Max-Alpha ohne Blur | `0.32f` |
| Max-Alpha mit Blur | `0.16f` |
| Blur-Radius bei `large` | `24.dp`, skaliert mit dem Fortschritt |
| App-Content | skaliert auf `1f − 0.08f × fortschritt` |

```kotlin
alpha = ((hidden - offset) / (hidden - topMostAnchor)).coerceIn(0f, 1f) * maxAlpha
```

Linear und **nicht** zusätzlich gekurvt — die Bewegung ist durch den Spring schon geformt; eine zweite Kurve darüber entkoppelt beim Ziehen Abdunklung und Fingerposition. Gelesen wird in `graphicsLayer`, also in der Draw-Phase, ohne Recomposition pro Frame. Dasselbe gilt für den Blur: `graphicsLayer { renderEffect = BlurEffect(…) }`, nicht `Modifier.blur`.

**Blur ab API 31.** Darunter passiert still gar nichts — das ist eine Plattformgrenze, kein Fallback. Deshalb zwei Max-Alphas: unter API 31 trägt der Scrim mit `0.32f` allein, ab API 31 tritt er auf `0.16f` zurück.

**Der Scrim wird erst tappbar, wenn das Sheet ruht** (`settledValue != Hidden && !isAnimationRunning`). Sonst schließt ein hektischer Doppeltipp auf den Öffnen-Knopf das Sheet sofort wieder.

**Reduzierte Systemanimationen sind gratis erledigt** — Compose' Suspend-Animationen lesen `MotionDurationScale` aus dem Coroutine-Kontext. Zu tun ist nichts, zu dokumentieren schon.

**Die Library malt hinter dem App-Content nichts.** Wer den Skalierungs-Effekt nutzt, braucht ein Window-Theme, dessen Hintergrund zum App-Theme passt — sonst blitzt am Rand der Default durch.

---

## 5. Gesten und Dismiss

Quelle: [#8](https://github.com/SirCedric/BottomSheet/issues/8) mit Nachtrag aus [#16](https://github.com/SirCedric/BottomSheet/issues/16).

Zwei Schalter, beide positiv formuliert. **`gesturesEnabled` gattert `interactiveDismissEnabled`**: ist Ersteres `false`, ist Letzteres bedeutungslos.

Spalten: **A** = beide `true` (Default) · **B** = `gesturesEnabled` ✓, `interactiveDismissEnabled` ✗ · **C** = `gesturesEnabled` ✗

| Geste | A | B | C |
|---|---|---|---|
| Drag zwischen den Detents | snappt nach Schwelle | snappt nach Schwelle | keine Reaktion |
| Drag unter den untersten Detent | schließt | Rubber-Band, federt zurück, `onDismissAttempt` | keine Reaktion |
| Drag über den obersten Detent | Rubber-Band, federt zurück; `onExpandAttempt` **nur**, wenn `large` nicht in `presentationDetents` steht | dito | keine Reaktion |
| Schneller Wisch nach unten | Decay-Projektion entscheidet, `medium` darf übersprungen werden | rastet auf unterstem erlaubten Anchor, `onDismissAttempt` | keine Reaktion |
| Scrim-Tap | schließt | konsumiert, `onDismissAttempt` | konsumiert, **kein** Callback |
| System-Back (< API 34) | schließt | geschluckt, `onDismissAttempt` | geschluckt, **kein** Callback |
| Predictive Back (API 34+) | Sheet folgt der Geste Richtung `Hidden`; Loslassen committet, Abbruch federt zurück | **keine** Vorschau, geschluckt, `onDismissAttempt` | **keine** Vorschau, geschluckt, kein Callback |
| Handle-Tap | `medium` ↔ `large` | `medium` ↔ `large` | keine Reaktion |
| Drag-Handle sichtbar (wenn angefordert) | ja | ja | **nein** |
| Drag während der Enter-Animation | greifbar | greifbar | keine Reaktion |
| Drag während der Exit-Animation | nicht greifbar | nicht greifbar | keine Reaktion |
| Scrim-Tap während eines laufenden Drags | ignoriert | ignoriert | keine Reaktion |

### Schwellenwerte

| | Wert |
|---|---|
| Positionsschwelle | halbe Distanz zum nächsten Anchor (`AnchoredDraggableDefaults.PositionalThreshold`) |
| Mindest-Fling-Geschwindigkeit | 125 dp/s |
| Überspringen von `medium` | **keine** eigene Schwelle — die Decay-Projektion entscheidet |

Das Überspringen läuft über `animateToWithDecay`: projiziert wird, wo der Wisch auslaufen *würde*, der dazu nächste Anchor gewinnt. Ein harter Wurf landet damit von selbst jenseits von `medium`, ohne eine erfundene zweite Magic Number.

### Zeitliches Verhalten

**Back wird in jeder Konfiguration geschluckt.** Wird der Handler bei gesperrtem Dismiss nicht registriert, navigiert die App **hinter** dem offenen Sheet weg.

**Predictive Back zeigt bei gesperrtem Dismiss keine Vorschau** — eine Geste mitlaufen zu lassen, die nie committen kann, verspricht ein Ergebnis, das nicht eintritt.

**`gesturesEnabled = false` feuert kein `onDismissAttempt`.** Wo keine Interaktion vorgesehen ist, gibt es keinen Versuch.

**Der Drag-Handle verschwindet bei `gesturesEnabled = false`**, auch wenn die App ihn anfordert — ein Griff, an dem nichts passiert, ist eine Falschaussage. Bei `interactiveDismissEnabled = false` bleibt er sichtbar: dort signalisiert er korrekt „verschiebbar, aber nicht wegwischbar".

**`onDismissRequest` feuert sofort beim Commit der Geste**, nicht nach der Exit-Animation. Konsequenz für den Host: er hält den Registry-Eintrag über das Ende von `isPresented` hinaus am Leben, bis die Exit-Animation durch ist.

**Wiederöffnen während der Exit-Animation** fährt aus der aktuellen Position zurück, kein Neustart von unten.

**Owner verlässt die Composition bei offenem Sheet** ⇒ `onDismissRequest` feuert. Das Sheet ist sofort weg — eine Exit-Animation ist ohne lebende Composition-Stelle nicht zu haben —, aber State und Bild laufen nicht auseinander. Der gefährliche Fall ist das Wegscrollen aus einer `LazyColumn`, das ohne Zutun des Entwicklers passiert.

---

## 6. Rubber-Band an gesperrten Kanten

Quelle: [#16](https://github.com/SirCedric/BottomSheet/issues/16).

**Eine gesperrte Kante ist ein fehlender Anchor**, kein abgewürgtes Gesten-Handling:

- Dismiss gesperrt ⇒ kein `Hidden`-Anchor, unterste Rastposition ist `medium`.
- `large` nicht in `presentationDetents` ⇒ kein `Large`-Anchor.

`Modifier.anchoredDraggable` nimmt einen `overscrollEffect` und reicht **genau** den Delta- und Velocity-Anteil dorthin durch, den die Anchors nicht mehr aufnehmen. Das Rubber-Band ist damit ein eigener `OverscrollEffect`, dessen Überzug additiv auf den Sheet-Offset gelegt wird. `Modifier.overscroll()` wird nicht gebraucht — wir zeichnen den Überzug selbst.

> **Der `Hidden`-Anchor muss zurückkommen, sobald die App programmatisch schließt**, sonst hat die Exit-Animation kein Ziel. Die Sperre gilt der Geste, nicht dem Zustand.

| | Wert |
|---|---|
| Widerstandsfunktion | asymptotisch: `über = max × (1 − e^(−roh × faktor / max))` |
| Faktor | `0.35` |
| Maximum | `96.dp` |
| Rückfahrt | `tween(180, FastOutSlowInEasing)` |
| Schwelle für den Versuch | `48.dp` Überzug |

**An beiden Kanten dieselben Werte.** Beide sagen dasselbe; zwei Kurven für dieselbe Aussage wären eine Zahl, die niemand begründen kann.

**Asymptotisch statt linear**: die Kante soll „hier ist Schluss" sagen, und das tut nur eine Kurve, die gegen eine Wand läuft.

**Die Rückfahrt weicht bewusst von der Detent-Kurve ab** — die Strecke ist kurz und der Endpunkt bekannt, die Feder wirkt darauf träge.

**Ein Wurf gegen die Kante federt nicht weiter aus als ein langsamer Zug.** Die Restvelocity verfällt. An einer gesperrten Kante ist das Ergebnis ohnehin festgelegt; ein weit ausschlagender Überzug verspricht ein Nachgeben, das nicht kommt.

**Die Versuchs-Callbacks feuern beim Schwellenübertritt im Zug**, nicht beim Loslassen, und einmal pro Geste. Abweichung von Apples Hinweis, bewusst: der Callback existiert, damit die App vor Datenverlust rückfragen kann — diese Rückfrage will man sehen, solange der Finger noch zieht.

`onDismissAttempt` gilt der unteren Kante, `onExpandAttempt` der oberen. Getrennt, weil ein Zug gegen ein gesperrtes `large` nichts schließt. Die 48 dp sind eine Library-Konstante, kein Parameter — wie die 125 dp/s.

---

## 7. Nested Scroll

Quelle: [#12](https://github.com/SirCedric/BottomSheet/issues/12).

Aufwärts `onPreScroll`, abwärts `onPostScroll` — die M3/iOS-Konvention. Die Asymmetrie ist gewollt: bei einer Liste mit 200 Einträgen wäre „erst ans Scroll-Ende, dann `large`" nicht vorhersagbar, sondern taub.

| Sheet | Scroll-Position | Richtung | Konsument | Phase |
|---|---|---|---|---|
| `medium` | beliebig | hoch | **Sheet**, bis `large` erreicht ist | `onPreScroll` |
| `large` | beliebig | hoch | Content | `onPreScroll` klemmt bei `minPosition()`, konsumiert 0 |
| `large` | mittendrin | runter | Content | `onPreScroll` ignoriert positive Deltas |
| `large` | am Anfang | runter | **Sheet**, bis `medium` bzw. `Hidden` | `onPostScroll` |
| `medium` | am Anfang | runter | **Sheet**, Richtung `Hidden` | `onPostScroll` |
| beliebig | beliebig | horizontal | Content | wir lesen nur `y` |

Kein Sonderfall fragt `firstVisibleItemIndex` ab — die Regeln sind rein deltabasiert und damit automatisch korrekt für `reverseLayout` und verschachtelte Scroller.

**`onPreFling` wird nicht implementiert.** Restvelocity geht in `onPostFling` ans Sheet; der Rückgabewert ist `available − remaining` (M3 verletzt dort den `performFling`-Kontrakt und ist keine Vorlage).

**`VerticalPager` wird unterstützt** — gemessen, der Pager snappt sauber. Zu dokumentieren ist kein Defekt, sondern eine Folge der Verzahnung: aus `medium` blättert die erste Aufwärtsgeste nicht, sondern expandiert das Sheet.

**Eine laufende Animation wird beim ersten Scroll-Delta übernommen**, nicht ausgesessen: ein neuer `anchoredDrag` reißt die `MutatorMutex` an sich und bricht die Animation ab, dieser eine Frame fällt aus. Sonst widerspräche es „die Enter-Animation ist greifbar".

**Kein eigener Overscroll-Effekt am Sheet** — solange das Sheet den Rest konsumiert, entsteht am inneren Container ohnehin kein Stretch. Das Federn an gesperrten Kanten ist Abschnitt 6.

**Nicht konfigurierbar in v1** (SwiftUIs `presentationContentInteraction`). Ein Schalter hieße, den verworfenen Pfad dauerhaft zu supporten.

### Umsetzungs-Randbedingungen

- Nur `source == NestedScrollSource.UserInput` bewegt das Sheet. Die Fling-Übergabe ist dadurch ein Sprung, kein fließender Übergang — der Preis dafür, dass der Listen-Fling das Sheet nicht mitzieht.
- `state.offset` vor jedem Zugriff auf `isNaN()` prüfen.
- Die Connection **muss** `remember`t werden.
- Achsen-Extraktion orientierungsgebunden (`available.y`), nicht `x + y` — sonst brechen `LazyRow` und `HorizontalPager` im Sheet.
- Ein `FlingBehavior` läuft nicht direkt gegen den `AnchoredDraggableState`; es braucht einen Adapter von `ScrollScope` auf `AnchoredDragScope`.

---

## 8. Insets und IME

Quelle: [#5](https://github.com/SirCedric/BottomSheet/issues/5), verankert in [#7](https://github.com/SirCedric/BottomSheet/issues/7).

Die Library konsumiert **nur `safeDrawing.only(Bottom)`** im Content-Slot des Sheets (Navbar, Gesture-Bar, IME, unterer Cutout), plus einen offsetabhängigen Top-Konsum. Statusbar, seitliche Insets und Gesture-Insets werden durchgereicht.

**Kein separates `imePadding()`** — `safeDrawing` enthält das IME bereits; M3 hat sein unbedingtes `imePadding()` als Bug entfernt.

`WindowInsets.ime` liefert den laufenden Wert und animiert auch auf API 29 (dort als simulierter 160-ms-Backport). Kein eigener Fallback nötig. Inset-Konsum ist subtree-lokal, der App-Content bleibt unberührt; die einzige Leck-Möglichkeit ist ein Inset-Modifier am gemeinsamen Host-`Box`.

Häufigste Ursache für ein springendes Sheet ist ein Vorfahr-View mit `DISPATCH_MODE_STOP` — gehört in die Doku, nicht in den Code.

---

## 9. Accessibility

Quelle: [#14](https://github.com/SirCedric/BottomSheet/issues/14), korrigiert durch [#17](https://github.com/SirCedric/BottomSheet/issues/17).

### Die Regel, die alles andere trägt

Ein Foundation-Modifier, der wegen seiner **Geste** gewählt wird, bringt ungefragt **Semantics** mit. Für jede dekorative oder rein bauliche Fläche gilt deshalb:

- Wird nur die Geste gebraucht → `pointerInput`, nicht `clickable`.
- Wird nur der Fokus gebraucht → `focusTarget()`, nicht `focusable()`.
- Was in den Accessibility-Baum gehört, wird ausschließlich im `semantics { }`-Block entschieden, nirgends nebenbei.

Beide Korrekturen an #14 waren Verstöße gegen genau diese Regel und am Papier unsichtbar.

### Abschirmung

Der Host legt `clearAndSetSemantics { }` auf den App-Content-Slot, solange ein Sheet presented ist. **Nicht** `hideFromAccessibility()` — das markiert nur den Knoten selbst als unwichtig und schneidet den Teilbaum nicht ab. **Nicht** die Occlusion-Logik des Delegates — die würde zwar greifen, ist aber ein Implementierungsdetail und kein Vertrag.

Gekoppelt an `isPresented`: beim Dismiss-Commit ist der App-Content sofort wieder bedienbar, noch während die Exit-Animation läuft.

Das Panel trägt `isTraversalGroup = true`. Keine `Role` (es gibt keine passende), kein `dialog()` — die Property hat im Delegate keinen Konsumenten und bringt nichts.

### Fokus

`focusGroup()` + `focusTarget()` am Panel, `focusProperties { onExit = { cancelFocusChange() } }` als Trap, beim Öffnen `requestFocus()`. `onEnter`/`onExit`, nicht die deprecateten `enter`/`exit`.

Das Panel muss den Fokus auch dann halten, wenn der Sheet-Content **kein** fokussierbares Element enthält — ein Sheet mit reinem Text ist nicht exotisch. `focusProperties { canFocus = false }` auf dem App-Content wäre **kein** Trap: ein deaktivierter Knoten wird übersprungen, seine Kinder bleiben suchbar.

Keine Fokus-Rückgabe an den Owner in v1 — der Owner kann beim Schließen weg sein.

### Ansagen: die Library bleibt ressourcenfrei

Kein `res/values/strings.xml`, keine Übersetzungen, kein Default-Text. Die Library sagt von sich aus **nichts** an. Zwei nullable Hebel:

- `paneTitle` am Modifier — löst `PANE_APPEARED` aus und ist der einzige Hebel, den die Plattform für „hier ist jetzt ein Sheet" hat (Compose kann TalkBack-Fokus nicht aktiv setzen). Ohne Wert passiert nichts.
- `detentNames` an den Host-Defaults, für `stateDescription`.

Die **Labels der Aktionen bleiben `null`** — das Framework setzt seine eigenen, lokalisierten Namen ein. Am Gerät bestätigt: **Maximieren**, **Minimieren**, **Schließen**.

### Aktionen

| `presentationDetents` | aktueller Detent | Aktion | `stateDescription` |
|---|---|---|---|
| zwei Detents | `medium` | nur `expand` | ja |
| zwei Detents | `large` | nur `collapse` | ja |
| ein Detent | — | keine | nein |

Immer nur die mögliche Richtung anbieten. Bei einem einzigen Detent gibt es keinen unterscheidbaren Zustand.

`dismiss()` ist strikt an Abschnitt 5 gekoppelt: **kein erlaubtes Nutzer-Dismiss ⇒ keine Aktion.** Damit haben TalkBack-Nutzer bei gesperrtem Sheet keinen Weg heraus, den die Library stellt. Das ist die bewusste Härte — eine Accessibility-Hintertür, die die Sperre aushebelt, wäre schlimmer. **In die KDoc gehört die ausdrückliche Pflicht:** wer `interactiveDismissEnabled = false` setzt, muss selbst einen Schließen-Weg in den Sheet-Content legen.

**Der Scrim trägt keine Semantics** — auch dann nicht, wenn sein Tap schließt. Sein Tap läuft über `pointerInput`; mit `clickable` entstünde ein ganzflächiger, unbeschrifteter Knoten über dem Sheet.

**Der Drag-Handle trägt keine eigenen Semantics** — sein Tap-Zyklus läuft ebenfalls über `pointerInput`. Er ist Teil des Sheet-Knotens.

**TalkBacks „Aktivieren" bleibt bewusst tot.** Es ist der Standardeintrag, der auf jedem Knoten im Aktions-Zyklus steht (`ACTION_CLICK`). Es zu belegen wurde gebaut und verworfen: es dupliziert Maximieren/Minimieren und kostet auf jedem Fokuswechsel die Ansage „zum Aktivieren doppeltippen".

---

## 10. Zustand über Rotation und Prozess-Tod

Quelle: [#10](https://github.com/SirCedric/BottomSheet/issues/10).

`isPresented` gehört der App. Die Library hält den **Detent** in einem `rememberSaveable` im Host; nach der Rotation registriert sich derselbe Owner neu und das Sheet steht wieder auf `large`, statt auf `initialDetent` zurückzufallen. Wiederhergestellt wird nur, wenn der Detent in den `presentationDetents` des wiederhergestellten Sheets vorkommt.

**Beim Wiederherstellen wird gesnappt, nicht animiert.** Ein Sheet, das nach jeder Drehung erneut hereinfährt, behauptet eine Präsentation, die längst passiert ist.

---

## 11. Testbarkeit

Quelle: [#11](https://github.com/SirCedric/BottomSheet/issues/11) mit zwei Nachträgen.

Die Implementierung bietet zwei `internal`, **Compose-freie** Nähte an. Sie sind eine Vorgabe an die Implementierung, keine Testfrage:

```kotlin
internal fun computeAnchors(
    containerHeight: Int,
    topInset: Int,
    contentHeight: Int,
    detents: SheetDetents,
): SheetAnchors

internal fun resolveGesture(
    gesture: Gesture,
    gesturesEnabled: Boolean,
    interactiveDismissEnabled: Boolean,
    currentDetent: Detent,
): GestureOutcome
```

`SheetAnchors` ist ein eigener Wert-Typ — **kein `DraggableAnchors` im Rückgabetyp**, sonst bräuchte der Test eine Android-Laufzeit. Die Umwandlung bleibt ein Einzeiler im `layout`-Block. Die Regeltabelle aus Abschnitt 7 wird auf dieselbe Weise herausgezogen.

| Ebene | Ort | Engine | Läuft |
|---|---|---|---|
| Anchor-Mathematik, Gesten-Policy, Nested-Scroll-Regeln | `src/test` | JUnit5 + AssertK | JVM, in CI |
| API-Dump | `bottomsheet/api/` | `apiCheck` an `check` | JVM, in CI |
| Compose-Verdrahtung | `src/androidTest` | JUnit4 + `createComposeRule()` | Gerät, **nicht** in CI |
| Blur, echte Insets, Predictive Back, TalkBack | — | — | Hand-Checkliste vor Release |

**Kein Robolectric** (zu teuer), **keine Screenshot-Tests** (sie nageln fest, was absichtlich konfigurierbar ist; Layout-Regressionen fängt `getBoundsInRoot()`).

**Animationen per `mainClock.autoAdvance = false`**, nicht per `MotionDurationScale` — nur so ist *mitten in* der Animation assertierbar, und zwei Entscheidungen existieren ausschließlich dort.

**Gating**: `./gradlew check` inkl. `apiCheck` auf einem committeten API-Dump. `connectedAndroidTest` ist Release-Schritt plus Pre-Push-Hook, nie PR-blockierend.

### Pflichtfälle

JVM (`src/test`):

1. `computeAnchors` über alle Detent-Kombinationen, Content kleiner als `large`, Content ≥ `large` (50-%-Regel), Content 0, `topInset` 0 und groß.
2. `resolveGesture` als Parameter-Matrix über die vollständige Tabelle aus Abschnitt 5.
3. `initialDetent`-Fallback, wenn `Medium` nicht in `presentationDetents` steht.
4. Die Nested-Scroll-Regeltabelle als zweite Matrix.

Gerät (`src/androidTest`):

5. Modifier ohne Host ⇒ Crash mit der Nachricht aus Abschnitt 1.
6. Owner verlässt die Composition bei offenem Sheet ⇒ `onDismissRequest`.
7. Scrim-Tap: schließt / feuert `onDismissAttempt` / ist während der Enter-Animation taub.
8. Back in allen drei Konfigurationsspalten.
9. Swipe unter den untersten Detent ⇒ `onDismissRequest`.
10. Drag-Handle horizontal zentriert und über dem Content-Slot.
11. Handle-Slot wird bei `gesturesEnabled = false` verworfen.
12. Rotation: Detent überlebt, ohne Enter-Animation.
13. Content wird erst beim Öffnen komponiert.
14. Sheet presented ⇒ App-Content-Knoten sind im Semantics-Baum nicht auffindbar.
15. Dismiss-Commit ⇒ App-Content sofort wieder auffindbar, noch während der Exit-Animation.
16. Zwei Detents auf `medium` ⇒ `Expand`, nicht `Collapse`; auf `large` umgekehrt.
17. Ein Detent ⇒ weder `Expand` noch `Collapse` noch `StateDescription`.
18. `interactiveDismissEnabled = false` ⇒ `Dismiss` fehlt.
19. `paneTitle` gesetzt ⇒ Property am Sheet-Knoten; nicht gesetzt ⇒ Property fehlt.
20. Fokus-Trap: Panel bekommt den Fokus, auch ohne fokussierbaren Sheet-Content.
21. Fokus-Trap hält: Fokusbewegung aus dem Sheet landet nicht im App-Content.
22. Scrim erzeugt **keinen** klickbaren Knoten, auch wenn sein Tap schließt.
23. Panel erzeugt **keinen** fokussierbaren Knoten, obwohl es den Eingabefokus hält.
24. Pager-Snap, Animationsübernahme, Fling-Übergabe (drei Fälle aus Abschnitt 7).

Zusätzlich in CI zu verankern: der M3-Freiheits-Check über `releaseCompileClasspath`.

---

## 12. Modul und Toolchain

Quelle: [#2](https://github.com/SirCedric/BottomSheet/issues/2).

| Modul | Plugin | Zweck |
|---|---|---|
| `:bottomsheet` | `com.android.library` | die Library, einziges publizierbares Artefakt |
| `:playground` | `com.android.application` | internes Werkzeug zum Anfassen der Prototypen |

| | |
|---|---|
| Gradle (Wrapper) | 9.7.0 |
| AGP | 9.3.1 |
| Kotlin | 2.4.10 |
| Compose BOM | 2026.08.00 → Compose 1.12.0 |
| compileSdk / targetSdk | 37 |
| minSdk | 29 |
| JVM-Target + Toolchain | 17 |

Namespace und Root-Package `dev.sircedric.bottomsheet`, Gradle-Group `dev.sircedric`. Alle Versionen zentral in `gradle/libs.versions.toml`, auch die SDK-Level.

**AGP 9 bringt Kotlin eingebaut mit** — `org.jetbrains.kotlin.android` darf **nicht** appliziert werden, sonst bricht die Konfiguration ab. Angewendet werden nur `com.android.library`/`com.android.application` plus `org.jetbrains.kotlin.plugin.compose`.

```kotlin
api(platform(libs.compose.bom))
api(libs.compose.foundation)
api(libs.compose.ui)
```

`api` statt `implementation`, weil die Public API Compose-Typen exponiert. M3-Freiheit ist am Compile-Classpath nachgewiesen.

**`Modifier.draggableAnchors` ist Material3-intern** und muss als eigener `LayoutModifierNode` nachgebaut werden ([#4](https://github.com/SirCedric/BottomSheet/issues/4)). `AnchoredDraggable` selbst ist seit Foundation 1.8.0 stabil.

---

## 13. Nicht in v1

**Bewusst offen gelassen** — in Scope einer späteren Version, aber nicht spezifiziert:

- **Sheet-Stacking** (Sheet öffnet Sheet). Die Host-Mechanik entscheidet, wie teuer ein Nachrüsten wird.
- **Freie Detent-Höhen** (`.height(x)`, `.fraction(x)`). Der Typ ist vorbereitet, die Layout- und Snapping-Regeln sind es nicht.
- **Tablet/Landscape**: maximale Breite, zentriertes Sheet, Verhalten bei sehr niedriger Höhe.
- **Optionales Material3-Theming-Artefakt**, das die M3-freie Kern-Library mit M3-Defaults verheiratet.

**Außerhalb dieser Library**: Maven-Central-Publishing, eine Demo-/Sample-App, Compose Multiplatform.

**Bewusst nicht konfigurierbar**: Blur (hängt am API-Level), Nested-Scroll-Verzahnung, Rubber-Band-Werte, Gestenschwellen. Nachrüsten per defaultetem Parameter ist jederzeit möglich und nicht breaking — das ist der Grund, warum sie heute fehlen dürfen.

---

## Quellen

| Ticket | Was dort steht |
|---|---|
| [Gradle-Library-Modul aufsetzen](https://github.com/SirCedric/BottomSheet/issues/2) | Module, Versionen, AGP-9-Falle |
| [Recherche: SwiftUI .sheet](https://github.com/SirCedric/BottomSheet/issues/3) | was Apple dokumentiert — und was nicht |
| [Recherche: Nested Scroll](https://github.com/SirCedric/BottomSheet/issues/4) | AnchoredDraggable-Status, M3-Fallen |
| [Recherche: WindowInsets und IME](https://github.com/SirCedric/BottomSheet/issues/5) | Inset-Konsum, IME-Animation auf API 29 |
| [Prototyp: Host-Mechanik](https://github.com/SirCedric/BottomSheet/issues/6) | in-composition vs. Popup, Node-Registry, Detach |
| [Layout-Modell und Detent-Berechnung](https://github.com/SirCedric/BottomSheet/issues/7) | Mess-Strategie, Anchor-Modell |
| [Gesten- und Dismiss-Semantik](https://github.com/SirCedric/BottomSheet/issues/8) | Entscheidungstabelle, Schwellen, Zeitpunkte |
| [Animations- und Scrim-Spec](https://github.com/SirCedric/BottomSheet/issues/9) | Kurven, Alpha, Blur, fester Handle |
| [Public-API-Surface](https://github.com/SirCedric/BottomSheet/issues/10) | vollständige Signatur samt Begründungen |
| [Test-Strategie](https://github.com/SirCedric/BottomSheet/issues/11) | Nähte, Ebenen, Gating, Pflichtfälle |
| [Nested-Scroll-Verzahnung](https://github.com/SirCedric/BottomSheet/issues/12) | Regeltabelle, Fling-Übergabe |
| [Accessibility des in-composition Hosts](https://github.com/SirCedric/BottomSheet/issues/14) | Abschirmung, Fokus, Aktionen |
| [Rubber-Band an gesperrten Kanten](https://github.com/SirCedric/BottomSheet/issues/16) | Widerstandsfunktion, Zahlen, Versuchs-Callbacks |
| [TalkBack-Durchlauf am Gerät](https://github.com/SirCedric/BottomSheet/issues/17) | Korrekturen an #14, Semantics-Regel |

Prototypen liegen auf den Branches `prototype/6-host-mechanics`, `prototype/7-layout-detents`, `prototype/9-animation-scrim`, `prototype/12-nested-scroll`, `prototype/16-rubber-band` und `prototype/17-talkback`; Recherche-Findings auf `research/3-swiftui-sheet-detents`, `research/4-nested-scroll` und `research/5-insets-ime`.
