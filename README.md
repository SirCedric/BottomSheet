# BottomSheet

A bottom sheet for Jetpack Compose that attaches to any composable through a **modifier** and behaves like SwiftUI's `.sheet` with `presentationDetents`.

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

- **A modifier, not a wrapper.** The sheet belongs to the composable that owns it, instead of nesting around half your screen.
- **Material3-free.** The only dependencies are `compose.foundation` and `compose.ui`. Use M3 if you want to; you don't have to.
- **Two detents.** `medium` at content height, `large` up to the status bar — allowed individually or together.
- **Fine-grained gesture control.** Between "everything goes" and "no gestures at all" sits the case that actually matters: resize yes, dismiss no — with a callback for when the user tries anyway.
- **Accessibility without resources.** The library shields the app content, keeps focus inside the sheet and exposes the right actions, without shipping a single string of its own.

The reasoning behind every decision lives in [`docs/SPEC.md`](docs/SPEC.md); the vocabulary is defined in [`CONTEXT.md`](CONTEXT.md).

---

## Requirements

| | |
| --- | --- |
| minSdk | 29 |
| compileSdk | 37 |
| Compose | 1.12.0 (BOM 2026.08.00) |
| Kotlin / JVM target | 2.4.10 / 17 |

The background blur applies from API 31 on; below that the scrim darkens more to compensate. That is a platform boundary, not a fallback.

## Installation

The library is **not published yet** — Maven Central publishing is a separate step. Until then, include it as a project dependency:

```kotlin
// settings.gradle.kts
include(":bottomsheet")

// app/build.gradle.kts
dependencies {
    implementation(project(":bottomsheet"))
}
```

`compose.foundation` and `compose.ui` come along as `api` dependencies, because the public API exposes Compose types.

---

## Get started

### 1. Install the host

Exactly once, at the very top, around the root of your app:

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

The host draws **every** sheet in the app and sits above all of your content — including the status bar. Without it, the first modifier fails on the first render with an unambiguous message.

### 2. Attach a sheet

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
            Button(onClick = { isPresented = false }) { Text("Done") }
        }
    },
)
```

That is all. The content is composed only when the sheet opens, and stays alive until the exit animation has finished.

---

## Presenting and dismissing

The state belongs to your app: `isPresented` is a `Boolean`, not a binding and not a state holder. That fits a `StateFlow` in a ViewModel, which could never populate a `MutableState` parameter.

`onDismissRequest` fires **only** when the library caused the dismissal:

| Trigger | `onDismissRequest` |
| --- | --- |
| Swipe, scrim tap, back | ✓ |
| Owner leaves the composition (scrolled out of a `LazyColumn`, say) | ✓ |
| Your app sets `isPresented = false` itself | — |

The callback fires **the moment the gesture commits**, not after the exit animation, so your state and what is on screen never drift apart.

> The second row is the one people forget: if the owner leaves the composition while its sheet is open, the sheet disappears — and without this report `isPresented` would stay stuck at `true`.

## Detents

```kotlin
Modifier.bottomSheet(
    isPresented = isPresented,
    onDismissRequest = { isPresented = false },
    presentationDetents = SheetDetents.MediumAndLarge,   // default
    initialDetent = PresentationDetent.Medium,           // default
) { … }
```

| Value | Meaning |
| --- | --- |
| `SheetDetents.Medium` | content height only |
| `SheetDetents.Large` | up to the status bar only |
| `SheetDetents.MediumAndLarge` | both |
| `SheetDetents.of(…)` | prepared for free heights later |

`SheetDetents` has a private constructor and `of()` requires a first argument, so the empty set is ruled out at compile time.

**`medium`** is as tall as your content. Once the content is at least as tall as `large`, `medium` sits at half of `large` instead of dropping out — the intermediate step survives exactly when there is the most to show.

**`large`** measures against `safeDrawing` rather than the status bar: on devices whose cutout extends past the status bar, the sheet would otherwise get stuck under the camera.

`initialDetent` applies on **every** transition from `false` to `true` — a new presentation is new, not a continuation of the last one. If the value is not part of `presentationDetents`, the smallest contained detent is used.

## Locking gestures

Two switches, both phrased positively. `gesturesEnabled` gates `interactiveDismissEnabled`.

```kotlin
Modifier.bottomSheet(
    isPresented = isPresented,
    onDismissRequest = { isPresented = false },
    interactiveDismissEnabled = !hasUnsavedChanges,
    onDismissAttempt = { showDiscardDialog = true },
) { … }
```

| | `gesturesEnabled` | `interactiveDismissEnabled` | Result |
| --- | --- | --- | --- |
| **A** | `true` | `true` | default: resize and dismiss |
| **B** | `true` | `false` | resize yes, dismiss no |
| **C** | `false` | ignored | the sheet reacts to no gesture at all |

Column **B** is the form case: unsaved changes, the user may move between `medium` and `large` but must not swipe the sheet away. If they try anyway, the edge springs back with resistance and **`onDismissAttempt`** fires — on crossing 48 dp of overdrag, while the gesture is still running. That is exactly when you want to ask, not as an afterthought.

**`onExpandAttempt`** is the counterpart at the upper edge: it fires when `large` is not allowed and the user pulls up regardless. Deliberately a separate callback — nothing is being closed at the top edge.

In column **C** **no** callback fires: where no interaction is intended, there is no attempt. The drag handle is discarded there as well, even if your app supplies one — a handle that does nothing would be a lie.

Back is swallowed in **every** configuration while a sheet is open. Otherwise your app navigates away behind it.

## Content scope

The sheet content runs inside a `BottomSheetScope`:

```kotlin
Modifier.bottomSheet(…) {
    Column {
        Text(if (currentDetent == PresentationDetent.Large) "All filters" else "Filters")

        Button(onClick = { animateTo(PresentationDetent.Large) }) {
            Text("Show more")
        }
    }
}
```

`currentDetent` is the **settled** detent, not the live offset, so your content does not recompose every frame. `animateTo` is deliberately not `suspend`; the call site needs no `rememberCoroutineScope()`.

There is no `dismiss()`: your app owns `isPresented`, and a second way to close that bypasses it would let state and picture drift apart.

## Scrollable content

Scrolling is your app's job — the library wraps no scroll container around your content, because it would clash with your own `LazyColumn`s.

```kotlin
Modifier.bottomSheet(…) {
    LazyColumn {
        items(200) { ListRow(it) }
    }
}
```

The interlocking follows the Material3 and iOS convention:

- **Upwards** the sheet wins **before** the content: from `medium` it expands to `large` first, and only then does the list scroll.
- **Downwards** the content scrolls first; at the top of the list the drag takes the sheet along.

`VerticalPager` works; the only wrinkle is that from `medium`, the first upward gesture expands the sheet instead of paging.

The **drag handle** sits as a fixed header above the content and does not scroll with it, which makes it the always-reachable drag surface. Turn it off with `dragHandle = null` while showing scrollable content and the library logs a warning.

## Appearance

Set once app-wide on the host, overridable per sheet on the modifier (`null` means "use the host's"):

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
| `colors.handle` | `Color.Black` at alpha `0.4` |
| `colors.scrim` | `Color.Black` |
| `colors.scrimMaxAlpha` | `0.16` from API 31, `0.32` below |
| `motion.animationSpec` | `spring(0.9, 380)` |
| `shape` | `RoundedCornerShape(28.dp)` at the top |
| `appContentMinScale` | `0.92` — `1f` turns the scaling off |
| `dragHandle` | `BottomSheetDefaults.DragHandle()`, `null` for none |

> **Dark mode:** the default `sheet = Color.White` is wrong on dark backgrounds, and the library cannot fix that by itself — it is Material3-free and knows no theme. A dark-mode app sets `BottomSheetHost(colors = …)` **once**; that is what the app-wide default is for.

The scrim follows the offset linearly and keeps darkening up to `large`. The app content scales to `1 − 0.08 × progress` and is additionally blurred from API 31 on.

> If you use the scaling, give your window theme a background that matches your app theme — the scaled content exposes the app's root at the edges, and the library deliberately paints nothing there.

## Insets and the keyboard

Inside the sheet content the library consumes only `safeDrawing.only(Bottom)` — navigation bar, gesture bar, IME and the bottom cutout. Everything else is passed through. A separate `imePadding()` is unnecessary and would be wrong: `safeDrawing` already includes the IME.

## Accessibility

The library handles on its own:

- **Shielding**: while a sheet is open, the app content disappears from the semantics tree. On a dismiss commit it is back immediately, while the exit animation is still running.
- **Focus trap**: focus moves into the panel when the sheet opens and does not escape — even for content without a focusable element.
- **Actions**: expand and collapse in whichever direction is possible, dismiss only when a user dismiss is permitted. The system supplies the localized labels.
- **Scrim and drag handle** carry no semantics and never show up in the tree.

Two levers are yours, because the library ships **no strings of its own**:

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

Without a `paneTitle`, **nothing** is announced on open — Compose cannot move screen reader focus programmatically, and `paneTitle` is the only lever the platform offers for it.

> **Important when dismissal is locked:** with `interactiveDismissEnabled = false` the library offers screen readers **no** dismiss action. That is deliberate — a back door that defeats the lock would be worse. Your app has to place a way to close inside the sheet content itself.

## Rotation and process death

`isPresented` belongs to your app and survives if you hold it correctly. The library saves the **detent** and snaps back to it on restore instead of animating in again — a sheet that slides up after every rotation claims a presentation that happened long ago.

---

## Not in v1

- **Sheet stacking** — a sheet opening a sheet.
- **Free detent heights** (`.height(x)`, `.fraction(x)`). The type is prepared for them; the layout and snapping rules are not.
- **Tablet and landscape**: maximum width, centred sheet.
- **A Material3 theming artifact** marrying the M3-free core with M3 defaults.

Deliberately **not** configurable: the blur (it depends on the API level), the nested-scroll interlocking, the rubber-band values and the gesture thresholds. Adding a defaulted parameter later is always possible and never breaking — which is why they can be absent today.

## License

MIT — see [`LICENSE`](LICENSE).
