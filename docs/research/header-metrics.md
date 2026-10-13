# Header-Maße bei SwiftUI und Compose

Recherche zu [#47](https://github.com/SirCedric/BottomSheet/issues/47) (Map [#46](https://github.com/SirCedric/BottomSheet/issues/46)), Stand **2026-10-13**.

## Rechercherahmen

| Was | Stand |
| --- | --- |
| AndroidX-Quelltext | `androidx/androidx@androidx-main`, HEAD [`64373e74`](https://github.com/androidx/androidx/tree/64373e743667df9cf547e092e9e3fb44a3180927) |
| TalkBack-Quelltext | `google/talkback@master`, HEAD [`229212fd`](https://github.com/google/talkback/tree/229212fdf5842191d0a93fc95d9ca1423b346866) |
| Apple | HIG und Developer Documentation, gelesen über die JSON-Endpunkte von developer.apple.com am Stichtag |

M3 wird nur als **Maßquelle gelesen**, nicht als Dependency.

Markierungen wie in den früheren Recherchen: **[DOK]** steht so in Apple- oder Android-Doku, **[SRC]** steht so im Quelltext, **[UNDOK]** sagt keine Primärquelle.

## TL;DR: Tabelle für den Prototyp

| Größe | Wert für `BottomSheetDefaults.Header` | Herkunft |
| --- | --- | --- |
| Mindesthöhe | **64.dp**, wächst mit dem Titel: `max(64.dp, titleHeight)` | [SRC] `TopAppBarExpandedHeight = AppBarSmallTokens.ContainerHeight = 64.dp`, `maxLayoutHeight = max(height, titlePlaceable.height)` |
| Äußerer Rand links/rechts | **4.dp** um den `navigationAction`- bzw. `actions`-Slot | [SRC] `TopAppBarHorizontalPadding = 4.dp` |
| Touch-Fläche einer Aktion | **48 × 48.dp** (sichtbarer Button 40.dp, Icon 24.dp) | [SRC] `LocalMinimumInteractiveComponentSize = 48.dp`, `SmallIconButtonTokens.ContainerHeight = 40.dp`, `IconSize = 24.dp`; [DOK] developer.android.com „minimum size to 48dp“ |
| Optischer Icon-Rand | ergibt sich: 4.dp + (48 − 24) / 2 = **16.dp** vom Sheet-Rand bis zum Icon | abgeleitet aus den Zeilen darüber |
| Titel-Innenabstand | **4.dp** horizontal um den Titel-Slot | [SRC] `Modifier.layoutId("title").padding(horizontal = TopAppBarHorizontalPadding)` |
| Titel-Startfläche ohne Navigation | **12.dp** (plus 4.dp Innenabstand = 16.dp bis zur Schrift) | [SRC] `TopAppBarTitleInset = 16.dp - TopAppBarHorizontalPadding`, `max(TopAppBarTitleInset, navigationIconPlaceable.width)` |
| Titel-Messung | `maxWidth = width − max(12.dp, navWidth) − actionsWidth`, `minWidth = 0` | [SRC] `TopAppBarMeasurePolicy.measure` |
| Titel-Platzierung | zentriert auf die **ganze** Breite, bei Kollision zur freien Seite geschoben | [SRC] `placeTopAppBar`, Kommentar „centered relative to the entire app bar width“ |
| Vertikal | alle drei Slots mittig in der Höhe | [SRC] `(contentHeight - placeable.height) / 2` |
| Kürzung des Titels | Library setzt **kein** `maxLines`; die App legt `Text(maxLines = 1, overflow = TextOverflow.Ellipsis)` hinein, KDoc empfiehlt das | [SRC] M3 setzt im Layout ebenfalls keins; [DOK] HIG: Titel unter 15 Zeichen |
| Abstand zum Drag Handle | **0.dp** extra; der Handle bringt 12.dp unter sich schon mit | [UNDOK] bei Apple; eigener Handle: `padding(vertical = 12.dp)` um 4.dp Pille |
| Typografie | keine; App-Sache. Referenzen: M3 `TitleLarge` 22.sp/28.sp Regular, iOS `Headline` 17 pt/22 pt Semibold | [SRC] `AppBarSmallTokens.TitleFont`, [DOK] HIG Typography |
| Semantik des Headers | `Modifier.semantics { isTraversalGroup = true }` auf dem Header | [SRC] M3 macht es auf der Bar genauso |
| Traversal links → Titel → rechts | **kein** `traversalIndex` nötig | [SRC] `SemanticsSort.sortByGeometryGroupings`, siehe Abschnitt 3 |
| Titel-Semantik | `Modifier.semantics(mergeDescendants = true) { heading() }` auf dem Titel-Wrapper | [SRC] siehe Abschnitt 3, Falle |
| TalkBack-Ansage | „*Titeltext*, Überschrift“ (en: „Heading“), nur mit „Elementtyp ansagen“ (Default an) | [SRC] TalkBack `heading_template` |
| Slot-Belegung | `navigationAction` = Abbrechen/Schließen/Zurück, `actions` = Fertig als einzige Primäraktion | [DOK] HIG Sheets und Toolbars |

## 1. SwiftUI / iOS

### Was Apple dokumentiert

**[DOK] Platzierungen.** Die drei Placements aus dem Ticket landen auf iOS so:

- [`.principal`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/principal): „In iOS, iPadOS, and tvOS, the system places the principal item in the center of the navigation bar. This item takes precedent over a title specified through `View/navigationTitle`."
- [`.cancellationAction`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/cancellationaction): „In iOS, iPadOS, tvOS, and watchOS, the system places `cancellationAction` items on the leading edge of the navigation bar."
- [`.confirmationAction`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/confirmationaction): „In iOS, iPadOS, and tvOS, the system places `confirmationAction` items in the same location as a `primaryAction` placement." Laut HIG ist das die trailing Seite.
- [`ToolbarItemPlacement`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement): „If not all items fit in the available space, an overflow menu may be created and remaining items placed in that menu." SwiftUI kürzt also nicht nur den Titel, es schiebt notfalls Aktionen in ein Overflow-Menü. Das übernehmen wir nicht. Die App bestimmt, was in `actions` liegt.

**[DOK] Konventionen für Sheets** ([HIG Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)):

- „In iOS and iPadOS, for sheets with a single view, the Cancel button belongs on the leading edge of the top toolbar. When present, the Done button belongs on the trailing edge."
- „If you provide a Done button, always pair it with a Cancel button […] or a Back button"
- „Avoid showing all three buttons — Cancel, Done, and Back — together."
- „if the text in the top-leading corner looks like a page or app title, people won't know how to dismiss the sheet."

**[DOK] Konventionen für Toolbars** ([HIG Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars)):

- „keep the title under 15 characters long so you leave enough room for other controls."
- „Use the standard Back and Close buttons. […] Prefer the standard symbols for each, and don't use a text label that says Back or Close."
- „Use the `.prominent` style for key actions such as Done or Submit. […] Only specify one primary action, and put it on the trailing side of the toolbar."
- „Prefer simple, recognizable symbols for items instead of text, except for actions like edit that aren't well-represented by symbols."

**[DOK] Touch-Fläche** ([HIG Accessibility, Mobility](https://developer.apple.com/design/human-interface-guidelines/accessibility)): iOS „Default control size 44x44 pt", „Minimum control size 28x28 pt". Dazu: „about 12 points of padding around elements that include a bezel. For elements without a bezel, about 24 points of padding works well around the element's visible edges."

**[DOK] Typografie** ([HIG Typography](https://developer.apple.com/design/human-interface-guidelines/typography), Tabelle „Large (default)"): `Headline` Semibold 17 pt, Leading 22 pt; `Body` Regular 17 pt / 22 pt; `Title 3` Regular 20 pt / 25 pt. Bei AX5 wächst `Headline` auf 53 pt / 62 pt. Das spricht dafür, die Header-Höhe mitwachsen zu lassen statt sie fest zu setzen.

**[DOK] Grabber.** [`prefersGrabberVisible`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/prefersgrabbervisible): „the system [draws] a grabber in the standard system-defined location." Wo genau, steht nicht da.

### Was Apple nicht dokumentiert

**[UNDOK]** Folgende Werte stehen in keiner aktuellen Apple-Primärquelle:

- Höhe der Navigation Bar (inline). Die HIG-Seite „Navigation bars" ist in „Toolbars" aufgegangen und nennt keine Höhe mehr.
- Schrift des Inline-Titels. [`UINavigationBarAppearance.titleTextAttributes`](https://developer.apple.com/documentation/uikit/uinavigationbarappearance/titletextattributes) sagt nur: „If you don't specify font or color attributes for the text, UIKit supplies appropriate default values."
- Seitliche Abstände der Bar-Items. Das Nächste sind die [Layout Margins](https://developer.apple.com/documentation/uikit/uiview/layoutmargins): „the default layout margins are normally 8 points on each side". Für Root-Views gelten die [`systemMinimumLayoutMargins`](https://developer.apple.com/documentation/uikit/uiviewcontroller/systemminimumlayoutmargins); das Beispiel dort nennt „20 points", ohne es als Bar-Maß zu belegen.
- Kürzung des Titels bei breiten Aktionen. [`titleView`](https://developer.apple.com/documentation/uikit/uinavigationitem/titleview): „Custom title views are centered on the navigation bar and may be resized to fit." Der Algorithmus dahinter ist nicht beschrieben.
- Abstand zwischen Grabber und Toolbar.

Wer diese Zahlen braucht, muss sie im Xcode View Debugger an einem laufenden Sheet messen. Für die Library ist das unnötig, weil Compose (Abschnitt 2) alles Nötige im Quelltext belegt.

## 2. Compose `CenterAlignedTopAppBar`

Alle Stellen aus `compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt` @ `64373e74`, sofern nicht anders genannt.

### Höhe

**[SRC]** `CenterAlignedTopAppBar(expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight, contentPadding = TopAppBarDefaults.ContentPadding, …)` delegiert an `SingleRowTopAppBar`.

```kotlin
public val TopAppBarExpandedHeight: Dp = AppBarSmallTokens.ContainerHeight   // tokens/AppBarSmallTokens.kt: 64.0.dp
public val ContentPadding: PaddingValues = PaddingValues(all = 0.dp)
```

Die Höhe ist eine **Mindesthöhe**, kein fester Wert:

```kotlin
val maxLayoutHeight =
    max(height.roundToPx(), titlePlaceable.height) + topPaddingPx + bottomPaddingPx
```

Ein mehrzeiliger oder bei großer Schrift hoher Titel macht die Bar höher. Bei 64.dp und `TitleLarge` mit 28.sp Zeilenhöhe passiert das einzeilig erst bei sehr großer Schrift; ab welchem Fontscale genau, hängt an der nichtlinearen Skalierung ab Android 14 und ist nicht nachgerechnet.

### Paddings

```kotlin
private val TopAppBarHorizontalPadding get() = 4.dp
private val TopAppBarTitleInset = 16.dp - TopAppBarHorizontalPadding        // = 12.dp

Box(Modifier.layoutId("navigationIcon").padding(start = TopAppBarHorizontalPadding)) { … }
Box(Modifier.layoutId("title").padding(horizontal = TopAppBarHorizontalPadding)) { … }
Box(Modifier.layoutId("actionIcons").padding(end = TopAppBarHorizontalPadding)) { … }
```

Die Aktionen stecken in `Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically)`. Zwischen den Aktionen gibt es **kein** Spacing; der Abstand entsteht allein aus den 48.dp-Touch-Flächen.

### Touch-Fläche

**[SRC]** `IconButton.kt`: `.minimumInteractiveComponentSize().size(IconButtonDefaults.smallContainerSize())`.

- `InteractiveComponentSize.kt`: `LocalMinimumInteractiveComponentSize` = `48.dp`, „Reserves at least 48.dp in size to disambiguate touch interactions".
- `tokens/SmallIconButtonTokens.kt`: `ContainerHeight = 40.0.dp`, `IconSize = 24.0.dp`.

**[DOK]** [developer.android.com, Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): „make sure to set the minimum size to 48dp". Ohne M3 gilt außerdem: „When the size of a clickable composable is smaller than the minimum touch target size, Compose still increases the touch target size […] outside of the boundaries of the composable." Gegen überlappende Flächen empfiehlt die Seite `sizeIn(minWidth = 48.dp, minHeight = 48.dp)`.

Folge für uns: `minimumInteractiveComponentSize` ist M3 und fällt weg. Die Buttons in den Slots baut die App. Der Baustein kann die 48.dp also nicht erzwingen, nur in der KDoc fordern. Foundation vergrößert die Trefferfläche zwar automatisch, aber nicht das Layout. Zu kleine Buttons rücken dadurch zu eng zusammen, und ihre Trefferflächen überlappen.

### Wie der Titel gemessen und platziert wird

**[SRC]** `TopAppBarMeasurePolicy.measure`:

```kotlin
val navigationIconPlaceable = …measure(constraints.copy(minWidth = 0))
val actionIconsPlaceable    = …measure(constraints.copy(minWidth = 0))
val titleStartSpace = max(TopAppBarTitleInset.roundToPx(), navigationIconPlaceable.width)
val maxTitleWidth =
    (constraints.maxWidth - titleStartSpace - actionIconsPlaceable.width - startPaddingPx - endPaddingPx)
        .coerceAtLeast(0)
val titlePlaceable = …measure(constraints.copy(minWidth = 0, maxWidth = maxTitleWidth))
```

Die Seiten-Slots werden zuerst und **unbeschränkt** gemessen, der Titel bekommt den Rest. Breite Aktionen gewinnen also immer, der Titel wird schmaler, notfalls bis 0.

**[SRC]** `placeTopAppBar`:

```kotlin
// Note that the title is centered relative to the entire app bar width, and not just centered
// between the navigation icon and the actions.
var titleX = titleHorizontalAlignment.align(titlePlaceable.width, constraints.maxWidth, LayoutDirection.Ltr)
if (titleX < start) {
    titleX += startPadding + (start - titleX)
} else if (titleX + titlePlaceable.width > constraints.maxWidth - end) {
    titleX += startPadding + ((constraints.maxWidth - end) - (titleX + titlePlaceable.width))
}
```

Der Titel wird also auf die volle Breite zentriert und nur bei Kollision zur Seite geschoben. Gesetzt wird mit `placeRelative`, RTL spiegelt sich dadurch von selbst.

Rechenbeispiel, 360.dp breit, ein Zurück-Button (52.dp inkl. Rand), drei Aktionen (148.dp inkl. Rand): Der Titel misst mit `maxWidth = 360 − 52 − 148 = 160.dp`. Zentriert läge er bei x = 100 und endete bei 260, die Aktionen beginnen aber bei 212. Er rückt um 48.dp nach links auf x = 52 und klebt am Zurück-Button. Ein kurzer Titel bleibt mittig, ein langer verliert die Mitte, bevor er gekürzt wird. Für den Prototyp ist das das Verhalten zum Übernehmen: optisch mittig, solange es geht, kein Überlappen.

**[SRC]** Kürzung: Das Layout setzt weder `maxLines` noch `overflow`. Es reicht nur `TextStyle` und Farbe per `ProvideContentColorTextStyle` durch. Ob der Titel umbricht oder kürzt, entscheidet der `Text` der App.

### Semantik in M3

**[SRC]** Auf dem äußeren `Box` der Bar:

```kotlin
.semantics { isTraversalGroup = true }
.pointerInput(Unit) {}
```

- `isTraversalGroup = true`: Alle Kinder der Bar werden vor allem anderen besucht. Das übernehmen wir.
- `pointerInput(Unit) {}` schluckt Pointer, damit nichts zur Fläche dahinter durchgeht. Das übernehmen wir **nicht**. Unser Header soll das Ziehen an das `anchoredDraggable` des Panels weitergeben (Map #46, „Ziehen am Header bewegt das Sheet"). Ein leerer `pointerInput` verbraucht zwar nichts. Er wäre aber ohne Zweck und lädt dazu ein, später Gesten hineinzuschreiben.
- M3 setzt **kein** `heading()` auf den Titel. In `AppBar.kt` kommt `heading` nicht vor. Wer bei M3 eine Überschrift will, setzt sie im eigenen `Text`. Unser Baustein macht das laut Map selbst.

## 3. Accessibility

### Was `heading()` bei TalkBack ankommt

**[SRC]** Compose → `AccessibilityNodeInfo`, `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt`:

```kotlin
semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.Heading)?.let {
    info.isHeading = true
}
```

**[SRC]** TalkBack, `talkback/src/main/java/com/google/android/accessibility/talkback/compositor/rule/EventTypeViewAccessibilityFocusedFeedbackRule.java`: Beim Fokus kommt erst die Beschreibung des Knotens, also der Titeltext. Danach, wenn `speakRoles` aktiv ist und der Knoten `isHeading` trägt, `R.string.heading_template`. Der String ist in `values/strings_compositor.xml` „Heading“, in `values-de/strings_compositor.xml` „Überschrift“. `pref_speak_roles_default` ist `true` (`values/donottranslate.xml`), Einstellung „Speak element type“.

Ergebnis: **„Abo bearbeiten, Überschrift“**. Mit abgeschaltetem „Elementtyp ansagen“ hört man nur den Titel. Der Heading-Status bleibt trotzdem nützlich, weil TalkBack per Lesesteuerung von Überschrift zu Überschrift springen kann. [DOK] developer.android.com, [Semantics, Headings](https://developer.android.com/develop/ui/compose/accessibility/semantics): „some accessibility services allow for easier navigation directly between sections or headings."

**Falle: `heading()` auf dem falschen Knoten.** Compose liest `Heading` aus der `unmergedConfig` genau des Knotens, den TalkBack fokussiert. Fokussierbar ist ein Knoten laut `isScreenReaderFocusable` (selbe Datei) nur, wenn er
`isMergingSemanticsOfDescendants` ist oder als Blatt eigenen Text oder eigene Beschreibung trägt:

```kotlin
if (node.unmergedConfig.isMergingSemanticsOfDescendants) return true
…
return isSpeakingNode && node.isUnmergedLeafNode
```

Setzt der Baustein `Modifier.semantics { heading() }` auf den Wrapper um den App-`Text`, ist der Wrapper weder mergend noch Blatt, also nicht fokussierbar. TalkBack landet auf dem `Text`-Kind, und das ist **keine** Überschrift. Richtig ist:

```kotlin
Box(Modifier.semantics(mergeDescendants = true) { heading() }) { title() }
```

Das gehört als Pflicht-Testfall in den Spec-Nachtrag: `onNode(hasText("…")).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))` auf dem gemergten Baum.

### Braucht die Row eine erzwungene Reihenfolge?

**Nein.** **[SRC]** `compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsSort.kt`, `sortByGeometryGroupings`:

> „grouping nodes into distinct, non-overlapping rows based on their top/bottom coordinates, then 2) sorting nodes within each row with the semantics comparator."

Knoten, deren vertikale Ausdehnung sich überschneidet (`max(top) < min(bottom)`), kommen in dieselbe Zeile. Innerhalb der Zeile sortiert `LtrBoundsComparator` nach `left` und `RtlBoundsComparator` nach `right` absteigend, je nach `layoutDirection`. Alle drei Slots sind vertikal zentriert, überschneiden sich also immer. Die Reihenfolge ist damit links → Titel → rechts, unter RTL gespiegelt: Navigation (rechts) → Titel → Aktionen (links). Das ist genau, was wir wollen.

**[DOK]** [developer.android.com, Traversal order](https://developer.android.com/develop/ui/compose/accessibility/traversal): „elements are arranged in expected reading order, which is usually left-to-right, then top-to-bottom." `traversalIndex` ist für Fälle gedacht, in denen „Compose's default sorting algorithm is not sufficient".

Was es trotzdem braucht, ist `isTraversalGroup = true` am Header: „Setting `isTraversalGroup = true` on a node means that all children of that node are visited before moving to other elements." Damit bleiben die drei Slots zusammen und vor dem Content, auch wenn ein Content-Element geometrisch in die Header-Zeile ragt, etwa beim Überscrollen. Die Map verlangt „Header vor Content", und diese Gruppe liefert das ohne `traversalIndex`.

Grenzfall ohne Handlungsbedarf: Ein Titel, der deutlich höher ist als die Buttons (große Schrift, zwei Zeilen), überschneidet sich weiterhin mit ihnen, weil alles mittig sitzt. Erst bei `Alignment.Top` und sehr ungleichen Höhen könnten die Zeilen zerfallen. Der Baustein zentriert, also tritt das nicht auf.

## 4. Offene Punkte

1. **64.dp oder weniger?** Unter einem 28.dp-Handle ergibt das 92.dp Kopf, bevor Content kommt. Das ist der Wert mit Quelle. Apple liefert keinen Gegenwert. Ob es im `Medium`-Detent zu wuchtig wirkt, zeigt der Prototyp. Der Wert sollte als `BottomSheetDefaults.HeaderMinHeight` justierbar sein.
2. **Höhe und Seitenränder der iOS-Bar** sind nur messbar, nicht belegbar (Abschnitt 1). Ohne Grund nicht nachmessen.
3. **Touch-Fläche der App-Buttons** kann der Baustein nicht erzwingen. Die KDoc von `Defaults.Header` nennt 48.dp und `sizeIn`.

## Quellen

Apple

- [HIG Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)
- [HIG Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars)
- [HIG Typography](https://developer.apple.com/design/human-interface-guidelines/typography)
- [HIG Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [`ToolbarItemPlacement`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement), [`.principal`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/principal), [`.confirmationAction`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/confirmationaction), [`.cancellationAction`](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/cancellationaction)
- [`UINavigationBar`](https://developer.apple.com/documentation/uikit/uinavigationbar), [`UINavigationBarAppearance.titleTextAttributes`](https://developer.apple.com/documentation/uikit/uinavigationbarappearance/titletextattributes), [`UINavigationItem.titleView`](https://developer.apple.com/documentation/uikit/uinavigationitem/titleview)
- [`UIView.layoutMargins`](https://developer.apple.com/documentation/uikit/uiview/layoutmargins), [`systemMinimumLayoutMargins`](https://developer.apple.com/documentation/uikit/uiviewcontroller/systemminimumlayoutmargins)
- [`prefersGrabberVisible`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/prefersgrabbervisible)

AndroidX @ `64373e74`

- [`material3/AppBar.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt)
- [`material3/tokens/AppBarSmallTokens.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/AppBarSmallTokens.kt)
- [`material3/tokens/TypeScaleTokens.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/TypeScaleTokens.kt)
- [`material3/IconButton.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/IconButton.kt), [`IconButtonDefaults.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/IconButtonDefaults.kt), [`tokens/SmallIconButtonTokens.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/SmallIconButtonTokens.kt)
- [`material3/InteractiveComponentSize.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/InteractiveComponentSize.kt)
- [`ui/semantics/SemanticsSort.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsSort.kt)
- [`ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt`](https://github.com/androidx/androidx/blob/64373e743667df9cf547e092e9e3fb44a3180927/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt)

developer.android.com

- [Accessibility: API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Accessibility: Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Accessibility: Traversal order](https://developer.android.com/develop/ui/compose/accessibility/traversal)

TalkBack @ `229212fd`

- [`EventTypeViewAccessibilityFocusedFeedbackRule.java`](https://github.com/google/talkback/blob/229212fdf5842191d0a93fc95d9ca1423b346866/talkback/src/main/java/com/google/android/accessibility/talkback/compositor/rule/EventTypeViewAccessibilityFocusedFeedbackRule.java)
- [`values/strings_compositor.xml`](https://github.com/google/talkback/blob/229212fdf5842191d0a93fc95d9ca1423b346866/talkback/src/main/res/values/strings_compositor.xml), [`values-de/strings_compositor.xml`](https://github.com/google/talkback/blob/229212fdf5842191d0a93fc95d9ca1423b346866/talkback/src/main/res/values-de/strings_compositor.xml), [`values/donottranslate.xml`](https://github.com/google/talkback/blob/229212fdf5842191d0a93fc95d9ca1423b346866/talkback/src/main/res/values/donottranslate.xml)
