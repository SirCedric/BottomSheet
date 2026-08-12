# WindowInsets und IME für ein in-composition BottomSheet

Recherche zu [Issue #5](https://github.com/SirCedric/BottomSheet/issues/5), Teil der Map [#1](https://github.com/SirCedric/BottomSheet/issues/1).

Ziel: festlegen, welche Insets die Library konsumiert und welche sie durchreicht — als Vorlage für die Layout-Entscheidung in [#7](https://github.com/SirCedric/BottomSheet/issues/7).

## Kontext

- Android-only, `minSdk 29`, Dependencies nur `compose.foundation` + `compose.ui`, kein Material3.
- Das Sheet rendert **in der App-Composition** über einen Root-Host, der über der gesamten App liegt — nicht in einem eigenen `Window`/`Dialog`.
- Detents: `medium` = Content-Höhe, `large` = bis unter die Statusbar.

## Recherchierter Stand

Stand 2026-08-12. Alle Aussagen sind gegen Primärquellen geprüft: `developer.android.com`, AOSP-Plattformquellen, androidx-Quelltext (`androidx-main` via gitiles/cs.android.com), veröffentlichte `-sources.jar`-Artefakte von Google Maven und die offiziellen Release Notes.

| Artefakt | Version | Quelle |
| --- | --- | --- |
| `androidx.compose.foundation` / `compose.ui` | **1.12.0** stabil (1.13.0-alpha01 aktuell) | [foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation), [ui](https://developer.android.com/jetpack/androidx/releases/compose) |
| Compose BOM | **2026.06.01** → foundation/ui 1.11.4 | [BOM-Mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping) |
| `androidx.activity` | 1.13.0 | [Release Notes](https://developer.android.com/jetpack/androidx/releases/activity) |
| `androidx.core` | 1.19.0 | [Release Notes](https://developer.android.com/jetpack/androidx/releases/core) |
| `androidx.compose.material3` (nur als Referenzimplementierung gelesen) | 1.4.0 stabil, 1.5.0-alpha am Head | [Release Notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) |

Die BOM-Mapping-Seite listet 1.12.0 noch nicht; eine BOM mit 1.12.0 ist vermutlich unmittelbar bevorstehend, aber **nicht verifiziert**.

Verifikationshinweis: `developer.android.com/reference/*` rendert clientseitig und liefert beim Abruf nur Navigations-Chrome. Alle API-Referenz-Aussagen wurden stattdessen gegen den ausgelieferten Quelltext geprüft (AOSP-SDK-Sources, `-sources.jar` aus Google Maven, gitiles-Rohtext) — das ist dieselbe Quelle, aus der die Referenzseiten generiert werden.

---

## Empfehlung in einem Absatz

Die Library konsumiert **ausschließlich innerhalb ihres eigenen Subtrees** und **nur den unteren Rand** (`safeDrawing.only(Bottom)`: Navigationsleiste, Gesture-Bar, IME, unterer Display-Cutout) — angewandt auf den Content-Slot des Sheets, nicht auf die Sheet-Surface. Zusätzlich konsumiert sie den oberen Rand **positionsabhängig** in Höhe des aktuellen Sheet-Offsets. Alles andere — Statusbar, oberer Cutout, seitliche Insets, Gesture-Insets — reicht sie unverändert an den App-Content unter dem Sheet durch. Der Scrim und der Sheet-Hintergrund zeichnen edge-to-edge, ohne Padding. Die Library ruft **kein** `enableEdgeToEdge()` auf und verändert keine Fenster-Flags; sie dokumentiert die zwei Voraussetzungen (`adjustResize` + `decorFitsSystemWindows = false`).

Die Detailtabelle steht in [Empfehlung](#empfehlung).

---

## 1. Die Insets-APIs

Alle Typen liegen in `androidx.compose.foundation.layout` und sind dünne Wrapper über die passende `WindowInsetsCompat.Type` ([`WindowInsets.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/androidMain/kotlin/androidx/compose/foundation/layout/WindowInsets.android.kt), verifiziert in `WindowInsetsHolder`).

### Einzeltypen

| API | Bedeutung (KDoc, sinngemäß) |
| --- | --- |
| `WindowInsets.statusBars` | Obere System-UI-Leiste mit Notification-Icons |
| `WindowInsets.navigationBars` | Navigationsleiste links/rechts/unten. **Ändert sich zur Laufzeit**, wenn der Nutzer die Navigationsmethode wechselt |
| `WindowInsets.systemBars` | Union aus Status-, Navigations- und Caption-Bar. **Enthält kein IME** |
| `WindowInsets.ime` | Platz, den die Software-Tastatur unten belegt |
| `WindowInsets.displayCutout` | Notch/Punch-Hole |
| `WindowInsets.systemGestures` | Bereich, in dem das System Gesten für Navigation abfängt |
| `WindowInsets.mandatorySystemGestures` | Teilmenge davon, die **nicht** per `Modifier.systemGestureExclusion` abwählbar ist |
| `WindowInsets.tappableElement` | Bereich, in dem Taps vom System behandelt werden |
| `WindowInsets.waterfall` | Gekrümmte Displaykanten |
| `*IgnoringVisibility` | Die Werte, die gälten, wenn der Typ sichtbar wäre — nützlich für stabile Layouts im Immersive Mode |

### Zusammengesetzte Typen

Verifiziert im Quelltext (`WindowInsets.android.kt`, Zeilen 378-381):

```kotlin
val safeDrawing  = systemBars.union(ime).union(displayCutout)
val safeGestures = tappableElement.union(mandatorySystemGestures).union(systemGestures).union(waterfall)
val safeContent  = safeDrawing.union(safeGestures)
```

Wichtig für uns: **`safeDrawing` enthält `ime`.** Wer `safeDrawing.only(Bottom)` paddet, paddet damit automatisch auch für die Tastatur — ohne separates `imePadding()`.

Und die Doku nennt Bottom Sheets ausdrücklich beim Gesture-Typ: `safeGestures` dient dazu, *"content with gestures"* zu schützen, *"such as those for bottom sheets"* ([Insets-Guide](https://developer.android.com/develop/ui/compose/layouts/insets)). Das ist relevant für den Drag-Handle-Bereich am unteren Sheet-Rand, wenn er in die Gesture-Zone der Navigationsleiste ragt.

### Modifier

- `Modifier.windowInsetsPadding(insets)` — paddet, damit Content nicht in `insets` hineinragt. Zieht ab, was Vorfahren bereits konsumiert haben, und **konsumiert die Insets für Kinder mit**.
- `Modifier.imePadding()`, `safeDrawingPadding()`, `navigationBarsPadding()` usw. — Spezialisierungen mit identischer Semantik. KDoc jeweils: *"When used, the `WindowInsets` will be consumed."*
- `Modifier.consumeWindowInsets(insets)` — konsumiert, **ohne** zu padden.
- `Modifier.onConsumedWindowInsetsChanged { }` — reine Lesesonde, konsumiert selbst nichts (siehe [§4](#4-konsum-semantik)).
- **Inset-Size-Modifier** (`windowInsetsBottomHeight` etc.) berücksichtigen bereits konsumierte Insets, **konsumieren aber selbst nicht**, weil sie direkt ihre Größe ändern ([Insets-Guide](https://developer.android.com/develop/ui/compose/system/insets-ui)).
- Escape-Hatch für Rohwerte ohne Konsum-Verrechnung: `WindowInsets.asPaddingValues()`.

---

## 2. Edge-to-edge und die API-Level-Grenzen

### Was `enableEdgeToEdge()` tut

Die offizielle Empfehlung ist inzwischen `WindowCompat.enableEdgeToEdge(window)` aus `androidx.core` ([edge-to-edge-Guide](https://developer.android.com/develop/ui/views/layout/edge-to-edge)). Verifiziert in `core-1.19.0-sources.jar`, [`WindowCompat.java`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/view/WindowCompat.java):

1. `setDecorFitsSystemWindows(window, false)`
2. `statusBarColor = TRANSPARENT`, `navigationBarColor = TRANSPARENT`
3. API ≥ 28: `layoutInDisplayCutoutMode` = `ALWAYS` (API ≥ 30) bzw. `SHORT_EDGES`
4. API ≥ 29: `setStatusBarContrastEnforced(false)`, `setNavigationBarContrastEnforced(false)`

`ComponentActivity.enableEdgeToEdge()` aus `androidx.activity` 1.13.0 ist **nicht deprecated**, beide Docs-Seiten lenken aber auf die `WindowCompat`-Variante.

### `setDecorFitsSystemWindows(false)` pro API-Level

Aus derselben Quelle, relevant für minSdk 29:

| API | Umsetzung |
| --- | --- |
| **< 30 (also 29)** | Nur Legacy-Flags: `SYSTEM_UI_FLAG_LAYOUT_STABLE \| LAYOUT_HIDE_NAVIGATION \| LAYOUT_FULLSCREEN`. Eine Plattform-Methode `Window.setDecorFitsSystemWindows` existiert dort **gar nicht** |
| 30–34 | `SYSTEM_UI_FLAG_LAYOUT_STABLE` **plus** `window.setDecorFitsSystemWindows(...)` |
| 35+ | Nur `window.setDecorFitsSystemWindows(...)` |

Auf API 29 gilt außerdem: `SystemBarStyle.auto()` ignoriert die übergebenen Scrim-Farben, weil Status- und Navigationsleiste dort bereits transparent sind und der Scrim für 3-Button-Navigation **von der Plattform** kommt und nicht anpassbar ist.

### API 35 (Android 15): edge-to-edge erzwungen

[Behavior changes, Android 15](https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge), wörtlich:

> Apps are edge-to-edge by default on devices running Android 15 if the app is targeting Android 15 (API level 35). This is a breaking change that might negatively impact your app's UI.

Für uns entscheidend:

- **Gesten-Navigation**: transparent, Bottom-Offset deaktiviert. `setNavigationBarColor` deprecated **und wirkungslos**.
- **3-Button-Navigation**: 80 % Deckkraft per Default, `setNavigationBarContrastEnforced` ist dort `true` per Default. `setNavigationBarColor` deprecated, wirkt aber weiter.
- **Statusbar**: transparent, `setStatusBarColor` deprecated und **ohne Wirkung**.
- **`Window#setDecorFitsSystemWindows(boolean)` ist deprecated *und deaktiviert*.**
- `layoutInDisplayCutoutMode` muss `ALWAYS` sein; `SHORT_EDGES`, `NEVER` und `DEFAULT` werden als `ALWAYS` interpretiert.
- `Configuration.screenWidthDp`/`screenHeightDp` **enthalten jetzt die System Bars** (vorher nicht).

Android 16 (API 36) zieht nach: `windowOptOutEdgeToEdgeEnforcement` ist deprecated und deaktiviert, ein Opt-out gibt es nicht mehr ([Behavior changes, Android 16](https://developer.android.com/about/versions/16/behavior-changes-16)).

**Konsequenz für die Library:** Auf modernen Geräten ist `decorFitsSystemWindows == false` der unabänderliche Zustand. Unterhalb API 35 ist es das nur, wenn die App es selbst einschaltet. Die Library muss also **beide Welten aushalten** und darf keine Fenster-Flags im Rücken der App setzen.

### Was auf API 29 fehlt

Verifiziert per Diff der AOSP-Plattformquellen android-29 gegen android-30:

| API | Auf Android 10 (API 29) |
| --- | --- |
| `android.view.WindowInsets.Type` inkl. `Type.ime()` | im Quelltext vorhanden, aber `@hide pending unhide` — **kein Public API** |
| `android.view.WindowInsetsController` | `@hide pending unhide` — **kein Public API** |
| `Window.setDecorFitsSystemWindows(boolean)` | **existiert überhaupt nicht** |
| `View.setWindowInsetsAnimationCallback` / `WindowInsetsAnimation` | Android-11-Feature, nicht vorhanden |

Was `androidx.core` auf API 29 nachbaut, steht in [`WindowInsetsCompat.java`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/view/WindowInsetsCompat.java). `Impl30` reicht direkt an die Plattform durch; unterhalb 30 synthetisiert `Impl20.getInsetsForType()` alles aus den Legacy-Werten. Der IME-Zweig wörtlich:

```java
final Insets systemWindow = getSystemWindowInsets();
final Insets rootStable   = getRootStableInsets();
if (systemWindow.bottom > rootStable.bottom) {
    // This handles the adjustResize case on < API 30, since
    // systemWindow.bottom is probably going to be the IME
    return Insets.of(0, 0, 0, systemWindow.bottom);
} else if (mRootViewVisibleInsets != null && !mRootViewVisibleInsets.equals(Insets.NONE)) {
    // This handles the adjustPan case on < API 30. We look at the root view's
    // visible rect and check it's bottom against the root stable insets
    if (mRootViewVisibleInsets.bottom > rootStable.bottom) {
        return Insets.of(0, 0, 0, mRootViewVisibleInsets.bottom);
    }
}
return Insets.NONE;
```

`mRootViewVisibleInsets` wird per **Reflection** befüllt (`View.getViewRootImpl()` → `ViewRootImpl.mAttachInfo` → `View$AttachInfo.mVisibleInsets`). Schlägt die Reflection fehl, gibt es `Insets.NONE`. Das offizielle KDoc von `WindowInsetsCompat.getInsets()` sagt dazu:

> When running on devices with API Level 29 and before, the returned insets are an approximation based on the information available. This is especially true for the IME type.

**Merksatz für minSdk 29:** IME-Insets sind dort eine Heuristik über Legacy-Geometrie plus einen reflektiven Zugriff auf ein verstecktes Feld — nicht die echten typisierten Insets.

---

## 3. `adjustResize` vs. `adjustPan`

### Was die Plattform sagt

Verifiziert in AOSP `frameworks/base/core/java/android/view/WindowManager.java`:

- `SOFT_INPUT_ADJUST_UNSPECIFIED = 0x00` — das System entscheidet zur Laufzeit: existiert ein sichtbarer Scroll-Container, wird `ADJUST_RESIZE` gewählt, sonst `ADJUST_PAN` (`ViewRootImpl`).
- **`SOFT_INPUT_ADJUST_RESIZE = 0x10` ist seit API 30 `@Deprecated`**, wörtlich:
  > `@deprecated` Call `Window#setDecorFitsSystemWindows(boolean)` with `false` and install an `OnApplyWindowInsetsListener` on your root content view that fits insets of type `Type#ime()`.

  Außerdem: enthält `FLAG_FULLSCREEN`, wird `adjustResize` **ignoriert**.
- `SOFT_INPUT_ADJUST_PAN = 0x20` — **nicht** deprecated (Stand API-34-Quellen). Das Fenster wird verschoben statt verkleinert.
- `SOFT_INPUT_ADJUST_NOTHING = 0x30` — keine Anpassung.

### Der entscheidende Zusammenhang mit edge-to-edge

`android.view.Window.setDecorFitsSystemWindows`, wörtlich:

> If set to **true**, the framework will inspect the now deprecated `View#SYSTEM_UI_LAYOUT_FLAGS` **as well the `WindowManager.LayoutParams#SOFT_INPUT_ADJUST_RESIZE` flag and fits content according to these flags**. If set to **false**, the framework will **not fit the content view to the insets** and will just pass through the `WindowInsets` to the content view.

Die Implementierung bestätigt das exakt: `setDecorFitsSystemWindows(false)` installiert einen `null`-`OnContentApplyWindowInsetsListener` in `PhoneWindow`; der Default-Listener ist derjenige, der die `systemWindowInsets` am Content-Root anwendet **und konsumiert**.

**Also: Unter edge-to-edge verschwindet der Decor-Level-Effekt von `adjustResize` — die IME-Höhe kommt stattdessen als `WindowInsets.ime` in der Composition an.** Genau deshalb ist Insets-Padding im Compose-Baum nicht optional, sondern Pflicht.

Ergänzend, API 30+, `android/view/InsetsState.java`:

```java
@InsetsType int compatInsetsTypes = systemBars() | displayCutout();
if (softInputAdjustMode == SOFT_INPUT_ADJUST_RESIZE) compatInsetsTypes |= ime();
```

Der `softInputMode` steuert also nur, ob das IME im **Legacy**-Set `getSystemWindowInsets()` auftaucht — nicht, ob es in der modernen `getInsets(Type.ime())`-Map steht, die Compose liest.

### Trotzdem bleibt `adjustResize` Pflicht im Manifest

Beide Primärquellen sagen es unbedingt:

> Set `android:windowSoftInputMode="adjustResize"` in your Activity's `AndroidManifest.xml` entry. This setting allows your app to receive IME insets. — [Edge-to-edge-Setup](https://developer.android.com/develop/ui/compose/system/setup-e2e)

> To achieve the best backward compatibility with this AndroidX implementation, set `android:windowSoftInputMode="adjustResize"`. — [Software-Keyboard-Guide](https://developer.android.com/develop/ui/views/layout/sw-keyboard)

Und das KDoc von `WindowInsets.Companion.ime` nennt genau die zwei Voraussetzungen:

> Developers should set `android:windowSoftInputMode="adjustResize"` in their `AndroidManifest.xml` file and call `WindowCompat.setDecorFitsSystemWindows(window, false)` in their `Activity.onCreate`.

### Verhalten je Modus

| Modus | API 29 | API 30+ |
| --- | --- | --- |
| `adjustResize` | IME-Insets über den `systemWindow.bottom`-Zweig. Funktioniert | Typisierte Insets, korrekt |
| `adjustPan` | IME-Insets über den Reflection-Zweig (`mRootViewVisibleInsets`). Funktioniert, solange die Reflection greift — **zusätzlich pant das Fenster**, was mit eigenem Padding zu doppeltem Versatz führt | Insets vermutlich vorhanden; ob das Fenster unter `decorFitsSystemWindows=false` noch pant, ist **nicht verifiziert** |
| `adjustNothing` / `adjustUnspecified` ohne Scroll-Container | **`WindowInsets.ime` ist `Insets.NONE`**, `imePadding()` tut still gar nichts. Verifiziert im Quelltext, in keiner Doku erwähnt | — |

**Empfehlung:** Die Library **fordert `adjustResize`** und dokumentiert das als Voraussetzung. `adjustPan` und `adjustNothing` gelten als nicht unterstützt für IME-Verhalten — nicht weil sie garantiert brechen, sondern weil die Pfade undokumentiert bzw. reflektionsabhängig sind und `adjustPan` zusätzlich doppelt verschiebt.

---

## 4. Konsum-Semantik

Das ist die kritische Frage für ein Overlay: kann die Library dem darunterliegenden App-Content Insets wegnehmen?

### Antwort: nein, Konsum wirkt ausschließlich im Subtree

Verifiziert in [`WindowInsetsPadding.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/WindowInsetsPadding.kt). Der Mechanismus ist `TraversableNode`-Traversierung, kein Broadcast:

```kotlin
internal abstract class InsetsConsumingModifierNode : Modifier.Node(), TraversableNode {
    var ancestorConsumedInsets: WindowInsets = WindowInsets(); private set
    var consumedInsets: WindowInsets = WindowInsets(); private set
    abstract fun calculateInsets(ancestorConsumedInsets: WindowInsets): WindowInsets

    override fun onAttach() {
        traverseAncestors(traverseKey) { parent ->
            this.ancestorConsumedInsets = (parent as InsetsConsumingModifierNode).consumedInsets
            false
        }
        insetsInvalidated()
        super.onAttach()
    }

    private fun invalidateChildConsumedInsets() {
        traverseDescendants(traverseKey) { child ->
            (child as InsetsConsumingModifierNode).setAncestorConsumedInsets(consumedInsets)
            SkipSubtreeAndContinueTraversal
        }
    }
}
```

Es gibt genau zwei Richtungen: **beim Attach nach oben lesen** (`traverseAncestors`, erster Treffer gewinnt) und **bei Änderung nach unten schieben** (`traverseDescendants`). Es existiert kein globaler Konsum-Zustand, kein `CompositionLocal`-Broadcast, keine Fenster-Mutation.

Für uns heißt das:

```kotlin
Box {                                                     // gemeinsamer Parent, ohne Inset-Modifier
    AppContent()                                          // Geschwister A
    SheetHost(Modifier.consumeWindowInsets(/* ... */))    // Geschwister B
}
```

`AppContent` ist **völlig unberührt**. Es ist kein Nachfahre von B, `traverseDescendants` erreicht es nie, und seine eigenen Nodes haben ihren `ancestorConsumedInsets` vom `Box` geerbt, der nichts konsumiert hat.

**Die einzige Art, wie das Overlay in den App-Content leckt, ist ein Inset-Modifier auf dem gemeinsamen Parent.** Das ist die harte Regel für die Host-Implementierung: der Host-`Box` bekommt niemals einen Inset- oder Konsum-Modifier; alles hängt am eigenen Sheet-Zweig.

Innerhalb **einer** Modifier-Kette gilt links-nach-rechts als Vorfahre-zu-Nachfahre — `Modifier.consumeWindowInsets(x).windowInsetsPadding(y)` sieht also den vorherigen Konsum. Genau das meint das KDoc mit *"on a parent layout"*.

Historisch war das über `ModifierLocalConsumedWindowInsets` gebaut; das Flag `isWindowInsetsModifierLocalNodeImplementationEnabled` wurde in Foundation **1.10.0-alpha04** entfernt. Beide Mechanismen waren subtree-only.

### Die zwei `consumeWindowInsets`-Überladungen rechnen unterschiedlich

```kotlin
// WindowInsets-Überladung
override fun calculateInsets(ancestorConsumedInsets: WindowInsets) =
    ancestorConsumedInsets.union(insets)          // per-Seite Maximum, idempotent

// PaddingValues-Überladung
override fun calculateInsets(ancestorConsumedInsets: WindowInsets) =
    ancestorConsumedInsets.add(paddingValues.asInsets())   // per-Seite Summe, additiv
```

**Für die Library ausschließlich die `WindowInsets`-Überladung verwenden.** Die `PaddingValues`-Variante ist additiv und über-konsumiert bei doppelter Anwendung.

### `onConsumedWindowInsetsChanged`

Reine Lesesonde — `calculateInsets` gibt `ancestorConsumedInsets` unverändert zurück. KDoc-Warnung:

> `block` can be called before or during measurement and layout. It should not be used to trigger changes to composition because composition will only be applied on the following frame, leading to the UI lagging WindowInsets by a frame.

### Padding-Modifier konsumieren automatisch

`windowInsetsPadding` und alle Spezialisierungen konsumieren, was sie padden. Aus dem Insets-Guide:

> The inset padding modifiers automatically consume the portion of the insets that are applied as padding. […] nested inset padding modifiers and the inset size modifiers know that some portion of the insets have already been consumed by outer inset padding modifiers, and avoid using the same portion of the insets more than once which would result in too much extra space.

### `Modifier.recalculateWindowInsets()`

Der generische Ausweg, wenn ein Parent ein Kind verschiebt, ohne zu konsumieren. Eingeführt in Foundation **1.8.0-alpha03**. KDoc:

> This only works when `Constraints` have fixed width and fixed height. […] In most cases you should not need to use this API, and the parent should instead use `consumeWindowInsets` to provide the correct values.

Für uns ist der explizite Konsum-Weg (siehe [§6](#6-der-positionsabhängige-top-konsum)) sauberer, weil wir den Offset ohnehin kennen.

### Phasen-Warnung

Aus dem Insets-Guide:

> The value of insets are updated after the composition phase, but before the layout phase. This means that reading the value of insets in composition generally uses a value of the insets that is one frame late.

Deshalb: Detent- und Anchor-Berechnung **nicht** über `WindowInsets.safeDrawing.getBottom(density)` im Composable-Body, sondern über ein `WindowInsets`-Objekt, das im Layout-Pass ausgewertet wird — genau das Muster, das Material3 mit `SheetWindowInsets` verwendet.

---

## 5. Animierte, mitlaufende IME-Höhe

### `WindowInsets.ime` liefert den laufenden Wert, nicht den Endwert

Verifiziert in [`WindowInsets.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/androidMain/kotlin/androidx/compose/foundation/layout/WindowInsets.android.kt).

`AndroidWindowInsets` hält die Insets als Snapshot-State:

```kotlin
internal class AndroidWindowInsets(internal val type: Int, private val name: String) : WindowInsets {
    internal var insets by mutableStateOf(AndroidXInsets.NONE)
    var isVisible by mutableStateOf(true)
    override fun getBottom(density: Density): Int = insets.bottom
}
```

`WindowInsetsHolder` ist pro `View` (`WeakHashMap<View, WindowInsetsHolder>`), wird über `LocalView.current` aufgelöst und registriert seine Listener per `DisposableEffect`, solange mindestens ein Zugriff besteht:

```kotlin
fun incrementAccessors(view: View) {
    if (accessCount == 0) {
        insetsListener.resetState()
        ViewCompat.setOnApplyWindowInsetsListener(view, insetsListener)
        if (view.isAttachedToWindow) view.requestApplyInsets()
        view.addOnAttachStateChangeListener(insetsListener)
        ViewCompat.setWindowInsetsAnimationCallback(view, insetsListener)   // ohne SDK_INT-Check
    }
    accessCount++
}
```

`update()` schließt mit `Snapshot.sendApplyNotifications()` ab — die Invalidierung landet also **im selben Frame** wie der Inset-Dispatch.

Der entscheidende Teil ist die Zustandsmaschine in `InsetsListener`:

| Callback | Wirkung |
| --- | --- |
| `onPrepare` | `prepared = true; runningAnimation = true` |
| `onStart` | `prepared = false` |
| **`onProgress`** | **`composeInsets.update(insets)`** — der Per-Frame-Update |
| `onEnd` | `runningAnimation = false`, setzt `ime`, `imeAnimationSource`, `imeAnimationTarget` auf den Endwert |
| `onApplyWindowInsets` | speichert die Insets und setzt `imeAnimationTarget`; schreibt `ime` **nur, wenn gerade keine Animation läuft** |

Wörtlich aus dem Quelltext:

```kotlin
} else if (!runningAnimation) {
    // If an animation is running, rely on onProgress() to update the insets
    // On APIs less than 30 where the IME animation is backported, this avoids reporting
    // the final insets for a frame while the animation is running.
    composeInsets.updateImeAnimationSource(insets)
    composeInsets.update(insets)
}
```

Die Plattform liefert nach `onPrepare` einmal die **Ziel**-Insets über `onApplyWindowInsets`. Compose ignoriert die für `ime` bewusst und leitet sie nach `imeAnimationTarget` um. `ime` wird ausschließlich von `onProgress` getrieben. Das KDoc dazu: *"we want to always report the current size, so we must ignore those calls."*

Für API 30 exakt gibt es einen Sonderfall (`view.post(this)`), weil dort eine abgebrochene Animation keine weiteren Callbacks erzeugt — laut KDoc *"It may have a janky frame, but it is the best we can do."*

### Auf API 29: ja, animiert — aber simuliert

`ViewCompat.setWindowInsetsAnimationCallback` wird ohne SDK-Check registriert. [`WindowInsetsAnimationCompat`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/view/WindowInsetsAnimationCompat.java) verzweigt:

```java
static void setCallback(@NonNull View view, @Nullable Callback callback) {
    if (Build.VERSION.SDK_INT >= 30) {
        Impl30.setCallback(view, callback);
    } else {
        Impl21.setCallback(view, callback);
    }
}
```

`Impl21` ist der Backport. Er startet **nach** `onApplyWindowInsets` — also nachdem das OS den Endwert bereits gemeldet hat — einen gewöhnlichen `ValueAnimator`:

- `COMPAT_ANIMATION_DURATION_IME = 160` ms
- `SHOW_IME_INTERPOLATOR = new PathInterpolator(0, 1.1f, 0f, 1f)`, dessen KDoc offen sagt: *"A fixed interpolator to use when **simulating** the window insets animation for showing the IME. This interpolator was picked via experimentation to subjectively improve the end result."*
- `HIDE_IME_INTERPOLATOR = new FastOutLinearInInterpolator()`

| | API 29 | API 30+ |
| --- | --- | --- |
| `onPrepare`/`onStart`/`onProgress`/`onEnd` feuern | ja (AndroidX-Backport) | ja (Plattform) |
| `WindowInsets.ime` frame-by-frame | **ja** | ja |
| Synchron mit dem echten Tastaturfenster | **nein** — synthetische 160 ms mit geratenem Interpolator, startet leicht verspätet | ja |
| IME-Höhe selbst | Heuristik/Reflection (siehe §2) | echte typisierte Insets |

Der offizielle Guide bestätigt die Grenze:

> The example labeled "Unsynchronized" in figure 2 shows the default behavior in Android 10 (API level 29), in which the text field and content of the app snap into place instead of synchronizing with the keyboard's animation — behavior that can be visually jarring. In Android 11 (API level 30) and higher, you can use `WindowInsetsAnimationCompat` to synchronize the transition. — [sw-keyboard](https://developer.android.com/develop/ui/views/layout/sw-keyboard)

**Für die Library:** kein eigener Fallback-Pfad für API 29 nötig. Derselbe `WindowInsets.ime`-Read liefert dort eine glatte 160-ms-Bewegung. Wir dürfen nur nicht behaupten, sie sei tastatursynchron. Wenn irgendwo verzweigt werden muss, ist `Build.VERSION.SDK_INT >= 30` das korrekte Prädikat.

### Wo man liest, entscheidet über die Kosten

`WindowInsets.ime` als `@Composable`-Getter löst nur den Holder auf und registriert den `DisposableEffect` — er liest die Insets **nicht**. Der eigentliche State-Read ist `getBottom(density)`. Damit bestimmt die Compose-Phase, in der man liest, ob pro Frame recomposed, gemessen oder nur platziert wird ([Compose-Phasen](https://developer.android.com/develop/ui/compose/phases)).

```kotlin
val imeInsets = WindowInsets.ime      // Composition: Holder + Listener-Registrierung
val density = LocalDensity.current

// Placement-Phase: pro Frame, ohne Recomposition, mit eigener Graphics-Layer
Modifier.offset { IntOffset(0, -imeInsets.getBottom(density)) }

// Measure-Phase: pro Frame neu gemessen, ohne Recomposition
Modifier.layout { measurable, constraints ->
    val ime = imeInsets.getBottom(this)
    val p = measurable.measure(constraints.offset(vertical = -ime))
    layout(p.width, p.height + ime) { p.place(0, 0) }
}
```

Ein Read im Composable-Body aktualisiert ebenfalls jeden Frame — er löst nur pro Frame eine Recomposition des umschließenden Restart-Scopes aus. Es ist also nicht "nur der Endwert", sondern schlicht die teure Variante.

Wichtig: **`WindowInsets.ime` muss mindestens einmal in der Composition angefasst werden**, sonst läuft `incrementAccessors` nie und es wird überhaupt kein Listener installiert.

`Modifier.imePadding()` und `Modifier.windowInsetsPadding(WindowInsets.ime)` nutzen beide `InsetsPaddingModifierNode`, das die Insets **in `measure`** liest — sie animieren also von sich aus mit, ohne Recomposition. `imePadding()` ist stabil (nicht experimentell).

### `imeAnimationSource` / `imeAnimationTarget`

Eingeführt in Foundation **1.4.0-alpha01**:

> Added `WindowInsets.imeAnimationSource` and `WindowInsets.imeAnimationTarget` to determine the animation progress and know where the IME will be after animation completes. (I356f1, b/217770337)

Sie liefern das Intervall `[source.bottom, target.bottom]`, also den Fortschritt `(ime - source) / (target - source)` — und, wichtiger für uns, **die finale Tastaturhöhe schon im ersten animierten Frame**. Damit lässt sich ein Detent-Anchor vorab berechnen und clampen, statt dem Wert hinterherzulaufen.

Stabilität: `@ExperimentalLayoutApi` in allen heute relevanten Versionen (≤ 1.12.x), stabil ab 1.13.0-alpha01 (*"Promoted WindowInsets visibility and animation APIs to stable"*, I37441).

### `WindowInsets.isImeVisible`

Existiert in `foundation-layout` (nur Android-Sourceset), eingeführt in Foundation **1.2.0-alpha08**, ebenfalls `@ExperimentalLayoutApi` bis einschließlich 1.12.x. Wert kommt aus `AndroidWindowInsets.isVisible`, wird also auch bei jedem `onProgress` aktualisiert — als `mutableStateOf(Boolean)` kostet ein Read in der Composition aber nur eine Recomposition pro **Änderung**, nicht pro Frame.

### Alternative in `compose.ui`: `WindowInsetsRulers`

`androidx.compose.ui.layout.WindowInsetsRulers` ist reines `compose.ui` und wird aus dem `Placeable.PlacementScope` gelesen — also **per Konstruktion in der Placement-Phase**:

```kotlin
public sealed interface WindowInsetsRulers {
    public val current: RectRulers
    public val maximum: RectRulers
    public fun getAnimation(scope: Placeable.PlacementScope): WindowInsetsAnimation
    public companion object { public val Ime: WindowInsetsRulers = /* ... */ }
}

public sealed interface WindowInsetsAnimation {
    public val source: RectRulers
    public val target: RectRulers
    public val isVisible: Boolean
    public val isAnimating: Boolean
    public val fraction: Float
    public val alpha: Float
}
```

Verfügbarkeit: in `commonMain` als `WindowInsetsRulers` seit ui **1.9.0-beta01**; das Enablement-Flag wurde in **1.11.0-alpha06** durch ein Opt-out `ComposeView.disableWindowInsetsRulers()` ersetzt und in **1.12.0-alpha01** nicht-experimentell. Der Android-Unterbau (`WindowInsetsWatcher.android.kt`) nutzt denselben `WindowInsetsAnimationCompat.Callback`, hat also identische API-29-Eigenschaften.

Das gibt `fraction`, `isAnimating`, `source` und `target` in einem einzigen Placement-Read — mehr als das foundation-Paar. **Bewertung:** attraktiv, aber es bindet die Library an ui ≥ 1.9 und ist nur sinnvoll, wenn wir ohnehin im Placement rechnen. Für v1 ist der foundation-Weg ausreichend und breiter kompatibel; `WindowInsetsRulers` als spätere Option notieren.

### `Modifier.imeNestedScroll()` ist auf API 29 ein stiller No-op

```kotlin
@ExperimentalLayoutApi
public fun Modifier.imeNestedScroll(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return this
    }
    // ...
}
```

Es baut auf dem Plattform-`WindowInsetsAnimationController` (`@RequiresApi(R)`) auf, für den es **keinen** Backport gibt. Relevant für [#4](https://github.com/SirCedric/BottomSheet/issues/4): Drag-to-dismiss-Keyboard existiert auf API 29 schlicht nicht, ohne Fehler.

### Die häufigste Ursache für ein springendes Sheet

`DISPATCH_MODE_STOP` vs. `DISPATCH_MODE_CONTINUE_ON_SUBTREE`. Aus dem KDoc:

> `DISPATCH_MODE_STOP` behaves the same way as returning `WindowInsetsCompat.CONSUMED` during the regular insets dispatch in `View#onApplyWindowInsets`.

Compose wählt:

```kotlin
WindowInsetsAnimationCompat.Callback(
    if (composeInsets.consumes) DISPATCH_MODE_STOP else DISPATCH_MODE_CONTINUE_ON_SUBTREE
)
```

wobei `consumes` aus `AbstractComposeView.consumeWindowInsets` kommt. Dessen Default wurde in Foundation **1.9.0-alpha02** auf `false` gedreht; in **1.10.0-alpha01** wurde das Flag `isWindowInsetsDefaultPassThroughEnabled` entfernt, *"defaulting `WindowInsets` to not consuming so that child Views can receive `WindowInsets` by default"*.

Konsequenz: Installiert irgendein **Vorfahr-View** der Host-App einen Callback mit `DISPATCH_MODE_STOP` oder gibt `CONSUMED` aus `onApplyWindowInsets` zurück, bekommt unser Compose-Subtree **gar kein `onProgress`** — `WindowInsets.ime` fällt auf den nicht-animierten Pfad zurück und das Sheet springt. Genau davor warnt der sw-keyboard-Guide: *"Don't consume WindowInsets in `setWindowInsetsApplyListener` for any parent ViewGroup objects."* Das gehört als Troubleshooting-Hinweis in die Library-Doku.

---

## 6. Der positionsabhängige Top-Konsum

Material3 löst ein Problem, das wir eins zu eins haben: ein Sheet, das bei y = 600 px parkt, soll seinen Content nicht für eine Statusbar padden, die es gar nicht berührt.

Aus [`ModalBottomSheet.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ModalBottomSheet.kt):

```kotlin
internal class SheetWindowInsets(private val state: SheetState) : WindowInsets {
    override fun getTop(density: Density): Int {
        val offset = state.anchoredDraggableState.offset
        return if (offset.isNaN()) 0 else offset.toInt().coerceAtLeast(0)
    }
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getBottom(density: Density) = 0
}

BottomSheet(
    modifier = modifier.align(TopCenter).consumeWindowInsets(sheetWindowInsets),
    // ...
)
```

Das KDoc formuliert die Absicht:

> `ModalBottomSheet` will pre-emptively consume top insets based on it's current offset. This keeps content outside of the expected window insets at any position.

Zwei Eigenschaften machen das für uns wertvoll:

1. Es ist **reines foundation-API** — `consumeWindowInsets` plus ein eigenes `WindowInsets`-Objekt. Kein Material3 nötig.
2. `getTop` liest `state.offset` **lazy im Layout-Pass**, nicht in der Composition. Damit ist es phasenkorrekt und animiert mit dem Sheet mit.

Für unser Detent-Modell heißt das: solange das Sheet unterhalb der Statusbar steht, verschwindet `safeDrawing.top` aus dem Content; sobald es beim `large`-Detent hochfährt, wächst der Top-Anteil wieder ein. Das ist genau die Semantik, die Ticket [#7](https://github.com/SirCedric/BottomSheet/issues/7) für "`large` = bis unter die Statusbar" braucht.

---

## 7. Konvention: wer paddet die Navigationsleiste?

**Antwort: das Sheet.** Und zwar den Content-Slot, nicht die Surface.

Die verbreitete Referenz ist Material3. Aus [`BottomSheet.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/BottomSheet.kt):

```kotlin
Surface(modifier = modifier.widthIn(max = maxWidth).fillMaxWidth() /* ... */) {
    Column(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(contentWindowInsets())   // die einzige Inset-Padding-Stelle
        // ...
    ) { if (dragHandle != null) { /* ... */ }; content() }
}
```

Die Defaults aus [`SheetDefaults.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt):

```kotlin
/** Default insets to be used and consumed by the [BottomSheet]'s content. */
public val standardWindowInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

/** Default insets to be used and consumed by the [ModalBottomSheet]'s content. */
public val modalWindowInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Top)
```

Also: **Hintergrund und Scrim laufen edge-to-edge bis unter die Navigationsleiste, der Content wird gepaddet und die Insets werden dabei konsumiert.** Content, den der Nutzer der Library übergibt, darf deshalb **kein** eigenes `navigationBarsPadding()` mehr setzen — das führte zu doppeltem Abstand.

### Historie des Defaults

| Version | Änderung |
| --- | --- |
| 1.1.1 / 1.2.0-alpha02 | `windowInsets`-Parameter für `ModalBottomSheet` eingeführt; Default hält das Sheet außerhalb der System Bars |
| 1.3.0-alpha06 | Parameter umbenannt `windowInsets` → **`contentWindowInsets`**, Typ zu `@Composable () -> WindowInsets` geändert, *"these are no longer tied to window logic"* |
| 1.3.2 | Default `safeDrawing.only(Bottom)`, angewandt auf die Content-Column |
| 1.4.0-alpha13 | *"Bottomsheet now consumes top insets when smaller than current offset."* Default enthält jetzt zusätzlich `safeDrawing.Top` |
| 1.5.0-alpha19 | *"Fixed an issue in `ModalBottomSheet` where `imePadding` was applied unconditionally, preventing control over IME behavior via `contentWindowInsets`."* |

Bemerkenswert: der Default wurde über die Zeit **größer**, nicht kleiner. Die Vermutung aus dem Ticket, der Default sei irgendwann auf leer gesetzt worden und Consumer padden selbst, trifft **nicht** zu. Was stimmt: der Aufrufer kann per `WindowInsets(0, 0, 0, 0)` **abwählen**.

### IME-Handhabung in der Referenz

- Bis 1.4.x: unbedingtes `Modifier.imePadding()` auf dem Full-Screen-`Box` des Sheet-Fensters. In 1.2.x mit `if (SDK_INT >= 33)` und dem TODO-Kommentar `b/290893168: Figure out a solution for APIs < 30`.
- Am Head: **kein `imePadding()` mehr.** Das IME kommt über `safeDrawing` in `contentWindowInsets` herein, weil `safeDrawing` das IME enthält. Für den fensterübergreifenden Sonderfall verweist das KDoc darauf, `Modifier.imePadding()` selbst auf `modifier` zu legen.
- Es gibt **keinen** `imePadding`-Parameter.

### Der wichtigste Unterschied zu unserer Architektur

`ModalBottomSheet` rendert in einem eigenen Fenster. Verifiziert in [`ModalBottomSheet.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/androidMain/kotlin/androidx/compose/material3/ModalBottomSheet.android.kt): `ModalBottomSheetDialogLayout : AbstractComposeView, DialogWindowProvider`, mit `WindowCompat.setDecorFitsSystemWindows(window, false)` und

```kotlin
window?.setSoftInputMode(
    if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
)
```

| | M3 `ModalBottomSheet` (eigenes Window) | Unser Overlay (gleiche Composition) |
| --- | --- | --- |
| Inset-Quelle | Eigener `WindowInsetsHolder` für die Dialog-View, startet mit **null** konsumierten Insets | Erbt `ancestorConsumedInsets` von den Vorfahren des Hosts |
| Konsum-Leck in den App-Content | Konstruktionsbedingt unmöglich | Unmöglich, **solange** kein Inset-Modifier am gemeinsamen Parent hängt (§4) |
| IME | Fenster setzt `ADJUST_NOTHING` (API ≥ 30), kein Window-Resize, Padding rein in Compose | Vom `softInputMode` der **Activity** bestimmt — wir müssen mit dem leben, was die App deklariert |
| System-Bar-Icons | Setzt Appearance-Flags auf dem **eigenen** Window | Wären die Flags der **App** — daher: nicht anfassen, oder nur mit sauberem Restore |
| Positionsabhängiger Top-Konsum | `consumeWindowInsets(SheetWindowInsets(state))` | **Identisch übernehmbar**, reines foundation-API |

Der Head von material3 enthält inzwischen ein `BottomSheet`-Composable **für genau unsere Architektur**. Sein KDoc:

> Crucially, it renders directly in the composition hierarchy (the main UI tree), unlike `ModalBottomSheet` which launches a separate `Dialog` window. […] It is drawn at the Z-index determined by its placement in the layout. It does not automatically provide a scrim or block interaction with the rest of the screen.

Sein Default für `contentWindowInsets` ist `standardWindowInsets`, also **bottom-only** — nicht bottom+top wie beim modalen. Grund: ein in-composition Sheet erreicht die Statusbar nicht zwangsläufig. Das ist genau unser Fall.

---

## Empfehlung

### Was die Library konsumiert, was sie durchreicht

| Inset | Behandlung | Wo |
| --- | --- | --- |
| `navigationBars` / Gesture-Bar (unten) | **konsumiert** | im Sheet-Content-Slot, als Teil von `safeDrawing.only(Bottom)` |
| `ime` | **konsumiert** | dito — `safeDrawing` enthält `ime`, kein separates `imePadding()` |
| `displayCutout` unten | **konsumiert** | dito |
| `statusBars` / oberer `displayCutout` | **durchgereicht**, plus positionsabhängiger Konsum in Höhe des Sheet-Offsets | `consumeWindowInsets(SheetTopInsets(state))` am Sheet-Zweig |
| seitliche Insets (Landscape-Navbar, Cutout links/rechts) | **durchgereicht** — v1 lässt das Sheet volle Breite; Tablet/Landscape ist ohnehin offen ([#1](https://github.com/SirCedric/BottomSheet/issues/1)) | — |
| `safeGestures` / `systemGestures` | **durchgereicht**, aber relevant fürs Gesten-Ticket [#8](https://github.com/SirCedric/BottomSheet/issues/8): der Drag-Bereich am unteren Sheet-Rand kollidiert sonst mit der System-Zurück-Geste | — |
| Alles, was der App-Content sieht | **unverändert** — die Library legt keinen einzigen Inset-Modifier auf den gemeinsamen Parent | — |

### Struktur des Hosts

```kotlin
Box {                                     // KEIN Inset- oder Konsum-Modifier hier. Harte Regel.
    appContent()                          // Geschwister: sieht seine Insets vollständig

    if (sheetVisible) {
        Scrim(Modifier.fillMaxSize())     // edge-to-edge, kein Padding

        SheetSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, sheetOffset.roundToInt()) }
                .consumeWindowInsets(SheetTopInsets(state))   // positionsabhängiger Top-Konsum
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(contentWindowInsets())  // paddet UND konsumiert
            ) {
                dragHandle?.invoke()
                content()
            }
        }
    }
}
```

mit

```kotlin
// Default für den Public-API-Parameter, analog BottomSheetDefaults.standardWindowInsets
val defaultContentWindowInsets: @Composable () -> WindowInsets =
    { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }

internal class SheetTopInsets(private val state: BottomSheetState) : WindowInsets {
    override fun getTop(density: Density): Int {
        val offset = state.offset
        return if (offset.isNaN()) 0 else offset.toInt().coerceAtLeast(0)
    }
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getBottom(density: Density) = 0
}
```

### Begründungen der Einzelentscheidungen

1. **Kein Inset-Modifier am Host-`Box`.** Konsum propagiert per `traverseDescendants` ausschließlich nach unten. Damit ist die Doppel-Anwendung im App-Content konstruktionsbedingt ausgeschlossen — solange diese eine Regel gilt.
2. **Padding am Content-Slot, nicht an der Surface.** Der Sheet-Hintergrund soll hinter der Navigationsleiste durchlaufen (sonst entsteht ein sichtbarer Streifen zwischen Sheet und Bildschirmkante). Nur der Inhalt rückt hoch. Das ist die Material3-Konvention und die Nutzererwartung.
3. **`safeDrawing.only(Bottom)` statt `navigationBarsPadding() + imePadding()`.** Ein einziger Modifier deckt Navbar, Gesture-Bar, IME und unteren Cutout ab, konsumiert genau einmal und ist idempotent gegenüber Vorfahren. Zwei getrennte Modifier wären additiv riskant.
4. **Kein `imePadding()` auf Host oder Scrim.** Genau das hat Material3 in 1.5.0-alpha19 als Bug behoben: unbedingtes `imePadding()` nimmt dem Aufrufer die Kontrolle über das IME-Verhalten.
5. **`contentWindowInsets` als `@Composable () -> WindowInsets`-Parameter**, nicht als fester Wert. Material3 hat den Typ in 1.3.0-alpha06 genau deshalb umgestellt. Opt-out per `{ WindowInsets(0, 0, 0, 0) }`.
6. **Nur die `WindowInsets`-Überladung von `consumeWindowInsets`**, nie die `PaddingValues`-Überladung — letztere addiert statt zu unionieren.
7. **Detent-Anchors im Layout-Pass rechnen**, nicht in der Composition, sonst hinkt das Sheet den Insets einen Frame hinterher.

### Auswirkung auf die Detents (Vorlage für #7)

- `large` = Fensterhöhe − `safeDrawing.top`. Damit ist "bis unter die Statusbar" automatisch auch "unter dem oberen Cutout", was auf Geräten mit Notch das ist, was man tatsächlich will.
- `medium` = Content-Höhe **plus** der unteren Insets, gedeckelt auf die `large`-Höhe. Ohne den Deckel wächst `medium` bei offener Tastatur über `large` hinaus, weil `safeDrawing.only(Bottom)` das IME enthält.
- Bei offener Tastatur: der Content paddet über `contentWindowInsets` automatisch mit, animiert per `onProgress`. Ob das Sheet zusätzlich als Ganzes hochfährt, ist eine Layout-Entscheidung für [#7](https://github.com/SirCedric/BottomSheet/issues/7) — die Insets-Mechanik trägt beide Varianten. `imeAnimationTarget` liefert die finale Tastaturhöhe bereits im ersten animierten Frame und erlaubt damit, den Anchor vorab zu clampen statt ihn nachzuziehen.

### Was die Library dokumentiert statt erzwingt

Die Library ruft **kein** `enableEdgeToEdge()` und setzt **keine** Fenster-Flags — das wäre ein Eingriff in fremdes Fenster-Setup, und auf API 35+ ist `setDecorFitsSystemWindows` ohnehin deaktiviert. Stattdessen dokumentiert sie als Voraussetzung:

1. `android:windowSoftInputMode="adjustResize"` in der Activity.
2. `WindowCompat.enableEdgeToEdge(window)` bzw. `enableEdgeToEdge()` in `onCreate` — auf API 35+ mit `targetSdk ≥ 35` implizit gegeben.
3. Kein Vorfahr-View darf `WindowInsets` konsumieren oder einen Animation-Callback mit `DISPATCH_MODE_STOP` installieren, sonst springt das Sheet bei Tastaturwechseln.
4. Der übergebene Sheet-Content setzt **kein** eigenes `navigationBarsPadding()` / `imePadding()`.

Optional prüfenswert für die Implementierung: ein Debug-Check, der bei `WindowInsets.ime == Insets.NONE` trotz sichtbarer Tastatur warnt — das ist die stille Failure-Mode von `adjustNothing` bzw. fehlgeschlagener Reflection auf API 29.

---

## Offene Punkte und nicht Verifiziertes

1. Ob eine Compose BOM mit foundation/ui 1.12.0 bereits existiert — die Mapping-Seite endet bei 2026.06.01 → 1.11.4.
2. Ob ein Fenster unter `decorFitsSystemWindows = false` bei `adjustPan` auf API 30+ noch pant und ob dabei zusätzlich IME-Insets gemeldet werden. Betrifft nur den von uns ohnehin nicht unterstützten Modus, wäre aber die Ursache für doppelten Versatz.
3. Deprecation-Status von `SOFT_INPUT_ADJUST_PAN` auf den Plattformquellen ab API 35 — auf API-34-Stand nicht deprecated.
4. Ob `adjustNothing` überhaupt ein legaler Manifest-String ist; in der `<activity>`-Syntax der Doku ist er **nicht** gelistet, die Plattformkonstante existiert aber.
5. Ob `WindowInsets.isImeVisible` während einer Hide-Animation bis `onEnd` `true` bleibt. Das analoge `WindowInsetsAnimation.isVisible` der Rulers-API dokumentiert genau das, der foundation-Pfad reicht aber nur `WindowInsetsCompat.isVisible(type)` durch.
6. Verhalten auf API 29, wenn die backportete Animation zwischen `onPrepare` und `onStart` abgebrochen wird — der Recovery-`Runnable` existiert nur für `SDK_INT == R`.
7. Welche `androidx.core`-Version die aktuellen `Impl21`-Interpolatoren und -Dauern erstmals enthielt.

## Quellen

Compose-Quelltext (`androidx-main`):

- [`WindowInsets.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/androidMain/kotlin/androidx/compose/foundation/layout/WindowInsets.android.kt) · [`WindowInsets.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/WindowInsets.kt)
- [`WindowInsetsPadding.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/WindowInsetsPadding.kt) · [`WindowInsetsPadding.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/androidMain/kotlin/androidx/compose/foundation/layout/WindowInsetsPadding.android.kt)
- [`WindowInsetsConnection.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/androidMain/kotlin/androidx/compose/foundation/layout/WindowInsetsConnection.android.kt) · [`Offset.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/Offset.kt)
- [`WindowInsetsRulers.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/layout/WindowInsetsRulers.kt) · [`WindowInsetsWatcher.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/layout/WindowInsetsWatcher.android.kt) · [`OwnerSnapshotObserver.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/node/OwnerSnapshotObserver.kt)

Material3 als Referenzimplementierung:

- [`ModalBottomSheet.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ModalBottomSheet.kt) · [`ModalBottomSheet.android.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/androidMain/kotlin/androidx/compose/material3/ModalBottomSheet.android.kt) · [`BottomSheet.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/BottomSheet.kt) · [`SheetDefaults.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt) · [`BottomSheetScaffold.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/BottomSheetScaffold.kt)

androidx.core / androidx.activity:

- [`WindowInsetsCompat.java`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/view/WindowInsetsCompat.java) · [`WindowInsetsAnimationCompat.java`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/view/WindowInsetsAnimationCompat.java) · [`ViewCompat.java`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/view/ViewCompat.java) · [`WindowCompat.java`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core/src/main/java/androidx/core/view/WindowCompat.java) · [`EdgeToEdge.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt)

AOSP-Plattform:

- [`WindowManager.java`](https://cs.android.com/android/platform/superproject/+/main:frameworks/base/core/java/android/view/WindowManager.java) · [`Window.java`](https://cs.android.com/android/platform/superproject/+/main:frameworks/base/core/java/android/view/Window.java) · lokal installierte SDK-Sources android-29 / android-30 / android-34 für den API-Level-Diff

Dokumentation:

- [About window insets](https://developer.android.com/develop/ui/compose/system/insets) · [Set up window insets](https://developer.android.com/develop/ui/compose/system/insets-ui) · [Insets in Compose](https://developer.android.com/develop/ui/compose/layouts/insets) · [Use Material 3 insets](https://developer.android.com/develop/ui/compose/system/material-insets)
- [Display content edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge) · [Edge-to-edge in Compose](https://developer.android.com/develop/ui/compose/system/setup-e2e) · [Jetpack Compose phases](https://developer.android.com/develop/ui/compose/phases)
- [Move views as the keyboard shows](https://developer.android.com/develop/ui/views/layout/sw-keyboard) · [Use keyboard IME animations](https://developer.android.com/develop/ui/compose/system/keyboard-animations)
- [Behavior changes, Android 15](https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge) · [Behavior changes, Android 16](https://developer.android.com/about/versions/16/behavior-changes-16) · [Android 11 features](https://developer.android.com/about/versions/11/features) · [`<activity>`-Manifest](https://developer.android.com/guide/topics/manifest/activity-element)
- Release Notes: [Compose Foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation) · [Compose UI](https://developer.android.com/jetpack/androidx/releases/compose) · [Compose Material3](https://developer.android.com/jetpack/androidx/releases/compose-material3) · [Activity](https://developer.android.com/jetpack/androidx/releases/activity) · [Core](https://developer.android.com/jetpack/androidx/releases/core)
