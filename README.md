# BottomSheet

Ein Bottom Sheet für Jetpack Compose, das per **Modifier** an eine beliebige View gehängt wird und sich verhält wie SwiftUIs `.sheet` mit `presentationDetents`.

```kotlin
Box(
    modifier = Modifier.bottomSheet(
        isPresented = state.isFilterSheetPresented,
        onDismissRequest = { onAction(OnFilterSheetDismiss) },
    ) {
        FilterSheetContent(currentDetent, state, onAction)
    },
)
```

- **Modifier statt Wrapper.** Das Sheet hängt am Composable, das es besitzt — nicht in einer Verschachtelung um den halben Screen.
- **Material3-frei.** Abhängigkeiten sind ausschließlich `compose.foundation` und `compose.ui`. Wer M3 nutzt, kann; wer nicht, muss nicht.
- **Zwei Detents.** `medium` auf Content-Höhe, `large` bis unter die Statusbar — einzeln oder zusammen erlaubt.
- **Gesten fein steuerbar.** Zwischen „alles erlaubt" und „gar keine Geste" liegt der praktisch wichtigste Fall: verschieben ja, schließen nein — samt Callback, wenn der Nutzer es trotzdem versucht.
- **Accessibility ohne Ressourcen.** Die Library schirmt den App-Content ab, hält den Fokus im Sheet und bietet die passenden Aktionen an, ohne einen einzigen eigenen String mitzubringen.

Ausführliche Begründungen zu jeder Entscheidung stehen in [`docs/SPEC.md`](docs/SPEC.md); die Begriffe definiert [`CONTEXT.md`](CONTEXT.md).

---

## Voraussetzungen

| | |
| --- | --- |
| minSdk | 29 |
| compileSdk | 37 |
| Compose | 1.12.0 (BOM 2026.08.00) |
| Kotlin / JVM-Target | 2.4.10 / 17 |

Der Hintergrund-Blur greift ab API 31; darunter dunkelt der Scrim entsprechend stärker ab. Das ist eine Plattformgrenze, kein Fallback.

## Einbinden

Die Library ist **noch nicht veröffentlicht** — Maven-Central-Publishing ist ein eigener Schritt. Bis dahin als Projekt-Dependency einbinden:

```kotlin
// settings.gradle.kts
include(":bottomsheet")

// app/build.gradle.kts
dependencies {
    implementation(project(":bottomsheet"))
}
```

`compose.foundation` und `compose.ui` kommen als `api`-Dependencies mit, weil die Public API Compose-Typen exponiert.

---

## Get started

### 1. Host setzen

Genau einmal, ganz oben um den Root der App:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BottomSheetHost {
                App()
            }
        }
    }
}
```

Der Host zeichnet **alle** Sheets der App und liegt über dem gesamten Content — auch über der Statusbar. Fehlt er, kracht der erste Modifier beim ersten Rendern mit einer eindeutigen Meldung.

### 2. Sheet anhängen

```kotlin
var isPresented by remember { mutableStateOf(false) }

Button(onClick = { isPresented = true }) { Text("Filter") }

Box(
    modifier = Modifier.bottomSheet(
        isPresented = isPresented,
        onDismissRequest = { isPresented = false },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Filter")
            Button(onClick = { isPresented = false }) { Text("Fertig") }
        }
    },
)
```

Das war es. Der Content wird erst beim Öffnen komponiert und bleibt bis zum Ende der Exit-Animation am Leben.

---

## Präsentieren und Schließen

Der Zustand gehört der App: `isPresented` ist ein `Boolean`, kein Binding und kein State-Holder. Das passt zu einem `StateFlow` im ViewModel, aus dem sich ein `MutableState` nicht befüllen ließe.

`onDismissRequest` feuert **nur**, wenn die Library das Schließen ausgelöst hat:

| Auslöser | `onDismissRequest` |
| --- | --- |
| Wischen, Scrim-Tap, Back | ✓ |
| Owner verlässt die Composition (z. B. Wegscrollen aus einer `LazyColumn`) | ✓ |
| App setzt selbst `isPresented = false` | — |

Der Callback feuert **sofort beim Commit der Geste**, nicht nach der Exit-Animation. Der Zustand der App und das Bild laufen so nie auseinander.

> Der zweite Fall ist der, den man vergisst: Verlässt der Owner die Composition, während sein Sheet offen ist, verschwindet das Sheet — und ohne diese Meldung bliebe `isPresented` auf `true` hängen.

## Detents

```kotlin
Modifier.bottomSheet(
    isPresented = isPresented,
    onDismissRequest = { isPresented = false },
    presentationDetents = SheetDetents.MediumAndLarge,   // Default
    initialDetent = PresentationDetent.Medium,           // Default
) { … }
```

| Wert | Bedeutung |
| --- | --- |
| `SheetDetents.Medium` | nur Content-Höhe |
| `SheetDetents.Large` | nur bis unter die Statusbar |
| `SheetDetents.MediumAndLarge` | beide |
| `SheetDetents.of(…)` | für spätere freie Höhen vorbereitet |

`SheetDetents` hat einen privaten Konstruktor, `of()` verlangt ein erstes Argument — die leere Menge ist zur Compile-Zeit ausgeschlossen.

**`medium`** ist so hoch wie der Content. Ist der Content mindestens so hoch wie `large`, liegt `medium` bei der Hälfte von `large`, statt wegzufallen — die Zwischenstufe bleibt also gerade dann erhalten, wenn am meisten Inhalt da ist.

**`large`** misst an `safeDrawing`, nicht an der Statusbar: auf Geräten, deren Cutout die Statusbar überragt, bliebe das Sheet sonst unter der Kamera hängen.

`initialDetent` gilt bei **jedem** Übergang von `false` nach `true` — eine neue Präsentation ist neu, nicht die Fortsetzung der letzten. Steht der Wert nicht in `presentationDetents`, greift der kleinste enthaltene.

## Gesten sperren

Zwei Schalter, beide positiv formuliert. `gesturesEnabled` gattert `interactiveDismissEnabled`.

```kotlin
Modifier.bottomSheet(
    isPresented = isPresented,
    onDismissRequest = { isPresented = false },
    interactiveDismissEnabled = !hasUnsavedChanges,
    onDismissAttempt = { showDiscardDialog = true },
) { … }
```

| | `gesturesEnabled` | `interactiveDismissEnabled` | Ergebnis |
| --- | --- | --- | --- |
| **A** | `true` | `true` | Default: verschieben und schließen |
| **B** | `true` | `false` | verschieben ja, schließen nein |
| **C** | `false` | egal | das Sheet reagiert auf gar keine Geste |

Spalte **B** ist der Formular-Fall: ungespeicherte Daten, der Nutzer darf zwischen `medium` und `large` wechseln, aber nicht wegwischen. Versucht er es doch, federt die Kante gedämpft zurück und **`onDismissAttempt`** feuert — beim Überschreiten von 48 dp Überzug, noch **während** die Geste läuft. Genau dann will man die Rückfrage sehen, nicht als Nachschlag.

**`onExpandAttempt`** ist das Gegenstück an der oberen Kante: es feuert, wenn `large` nicht erlaubt ist und der Nutzer trotzdem nach oben zieht. Bewusst ein eigener Callback — an der oberen Kante wird nichts geschlossen.

In Spalte **C** feuert **kein** Callback: wo keine Interaktion vorgesehen ist, gibt es keinen Versuch. Der Drag-Handle wird dort ebenfalls verworfen, selbst wenn die App einen anfordert — ein Griff, an dem nichts passiert, wäre eine Falschaussage.

Back wird in **jeder** Konfiguration geschluckt, solange ein Sheet offen ist. Sonst navigiert die App hinter dem Sheet weg.

## Content-Scope

Der Sheet-Content läuft in einem `BottomSheetScope`:

```kotlin
Modifier.bottomSheet(…) {
    Column {
        Text(if (currentDetent == PresentationDetent.Large) "Alle Filter" else "Filter")

        Button(onClick = { animateTo(PresentationDetent.Large) }) {
            Text("Mehr anzeigen")
        }
    }
}
```

`currentDetent` ist der **geruhte** Detent, nicht der laufende Offset — der Content rekomponiert also nicht pro Frame. `animateTo` ist absichtlich nicht `suspend`; die Call Site braucht kein `rememberCoroutineScope()`.

Ein `dismiss()` gibt es nicht: die App hält `isPresented` in der Hand, und ein zweiter Schließweg daran vorbei ließe Zustand und Bild auseinanderlaufen.

## Scrollbarer Content

Für Scrollbarkeit sorgt die App — die Library legt keinen Scroll-Container um den Content, weil der mit `LazyColumn`s der App kollidieren würde.

```kotlin
Modifier.bottomSheet(…) {
    LazyColumn {
        items(200) { ListRow(it) }
    }
}
```

Die Verzahnung folgt der Konvention von Material3 und iOS:

- **Nach oben** gewinnt das Sheet **vor** dem Inhalt: aus `medium` expandiert es zuerst auf `large`, danach scrollt die Liste.
- **Nach unten** scrollt zuerst der Inhalt; an der Listen-Oberkante nimmt der Zug das Sheet mit.

`VerticalPager` funktioniert; nur blättert aus `medium` die erste Aufwärtsgeste nicht, sondern expandiert das Sheet.

Der **Drag-Handle** sitzt als fester Kopf über dem Content und scrollt nicht mit — er ist damit die immer erreichbare Ziehfläche. Wer ihn per `dragHandle = null` abschaltet und scrollbaren Content zeigt, bekommt eine Warnung im Log.

## Aussehen

App-weit einmal am Host, pro Sheet überschreibbar am Modifier (`null` heißt „nimm die des Hosts"):

```kotlin
BottomSheetHost(
    colors = BottomSheetDefaults.colors(
        sheet = MaterialTheme.colorScheme.surface,
        handle = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    motion = BottomSheetDefaults.motion(spring(dampingRatio = 0.9f, stiffness = 380f)),
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    appContentMinScale = 0.92f,
) {
    App()
}
```

| Parameter | Default |
| --- | --- |
| `colors.sheet` | `Color.White` |
| `colors.handle` | `Color.Black` mit Alpha `0.4` |
| `colors.scrim` | `Color.Black` |
| `colors.scrimMaxAlpha` | `0.16` ab API 31, sonst `0.32` |
| `motion.animationSpec` | `spring(0.9, 380)` |
| `shape` | `RoundedCornerShape(28.dp)` oben |
| `appContentMinScale` | `0.92` — `1f` schaltet die Skalierung ab |
| `dragHandle` | `BottomSheetDefaults.DragHandle()`, `null` für keinen |

> **Dark Mode:** Der Default `sheet = Color.White` ist dunkel falsch, und die Library kann das nicht selbst lösen — sie ist M3-frei und kennt kein Theme. Eine Dark-Mode-App setzt `BottomSheetHost(colors = …)` **einmal**; genau dafür ist der App-weite Default da.

Der Scrim folgt linear dem Offset und dunkelt bis `large` weiter nach. Der App-Content skaliert auf `1 − 0.08 × Fortschritt` und wird ab API 31 zusätzlich weichgezeichnet.

> Wer die Skalierung nutzt, braucht ein Window-Theme, dessen Hintergrund zum App-Theme passt — der skalierte Content legt am Rand die App-Wurzel frei, und die Library malt dort bewusst nichts.

## Insets und Tastatur

Die Library konsumiert im Sheet-Content nur `safeDrawing.only(Bottom)` — Navbar, Gesture-Bar, IME und unteren Cutout. Alles andere wird durchgereicht. Ein separates `imePadding()` ist nicht nötig und wäre falsch: `safeDrawing` enthält das IME bereits.

## Accessibility

Die Library erledigt von sich aus:

- **Abschirmung**: Bei offenem Sheet verschwindet der App-Content aus dem Semantics-Baum. Beim Dismiss-Commit ist er sofort wieder da, noch während die Exit-Animation läuft.
- **Fokus-Trap**: Der Fokus wandert beim Öffnen ins Panel und kommt nicht heraus — auch bei Content ohne fokussierbares Element.
- **Aktionen**: `Maximieren` / `Minimieren` in der jeweils möglichen Richtung, `Schließen` nur, wenn Nutzer-Dismiss erlaubt ist. Die Labels liefert das System lokalisiert.
- **Scrim und Drag-Handle** tragen keine Semantics und tauchen im Baum nicht auf.

Zwei Hebel liegen bei der App, weil die Library **keine eigenen Strings** mitbringt:

```kotlin
BottomSheetHost(
    detentNames = BottomSheetDefaults.detentNames(
        medium = stringResource(R.string.sheet_half_height),
        large = stringResource(R.string.sheet_full_height),
    ),
) { … }

Modifier.bottomSheet(
    …,
    paneTitle = stringResource(R.string.filter_sheet),
) { … }
```

Ohne `paneTitle` sagt beim Öffnen **nichts** an — Compose kann den TalkBack-Fokus nicht aktiv setzen, `paneTitle` ist der einzige Hebel dafür.

> **Wichtig bei gesperrtem Dismiss:** Mit `interactiveDismissEnabled = false` bietet die Library TalkBack **keine** Schließen-Aktion an. Das ist Absicht — eine Hintertür, die die Sperre aushebelt, wäre schlimmer. Die App muss dann selbst einen Schließen-Weg in den Sheet-Content legen.

## Rotation und Prozess-Tod

`isPresented` gehört der App und überlebt, wenn sie es richtig hält. Die Library rettet den **Detent** und snappt beim Wiederherstellen dorthin zurück, statt erneut hereinzufahren — ein Sheet, das nach jeder Drehung neu aufblendet, behauptet eine Präsentation, die längst passiert ist.

---

## Nicht in v1

- **Sheet-Stacking** — ein Sheet öffnet ein Sheet.
- **Freie Detent-Höhen** (`.height(x)`, `.fraction(x)`). Der Typ ist dafür vorbereitet, die Layout- und Snapping-Regeln nicht.
- **Tablet/Landscape**: maximale Breite, zentriertes Sheet.
- **Material3-Theming-Artefakt**, das die M3-freie Kern-Library mit M3-Defaults verheiratet.

Bewusst **nicht** konfigurierbar: Blur (hängt am API-Level), Nested-Scroll-Verzahnung, Rubber-Band-Werte, Gestenschwellen. Nachrüsten per defaultetem Parameter ist jederzeit möglich und nicht breaking — das ist der Grund, warum sie heute fehlen dürfen.

## Lizenz

MIT — siehe [`LICENSE`](LICENSE).
