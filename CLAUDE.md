# BottomSheet

Jetpack-Compose-Library, die ein Bottom Sheet per Modifier an eine beliebige View hängt und sich wie SwiftUIs `.sheet` mit `presentationDetents` verhält. Android-only, Material3-frei.

## Wo die Wahrheit steht

| Frage | Quelle |
| --- | --- |
| Was gilt? | [`docs/SPEC.md`](docs/SPEC.md) — die vollständige Verhaltens- und API-Spec |
| Wie heißt das? | [`CONTEXT.md`](CONTEXT.md) — Glossar, verbindlich für Code und Prosa |
| Warum so? | die geschlossenen GitHub-Issues; jeder Spec-Abschnitt nennt sein Ticket |

**Die Spec ist die Vorgabe, nicht der Code.** Weicht die Implementierung ab, ist das ein Fehler oder eine bewusste Revision — im zweiten Fall gehört sie in die Spec und als Nachtrag ans Ticket, bevor sie eingecheckt wird.

## Aufbau

```
bottomsheet/          die Library, einziges publizierbares Artefakt
  src/main/           Public API flach im Package, Interna in internal/
  src/test/           JUnit5 + AssertK, reine JVM-Logik
  src/androidTest/    JUnit4 + createComposeRule(), nur Verdrahtung
playground/           internes Werkzeug zum Anfassen, keine Sample-App
docs/SPEC.md
```

`BottomSheetHost` hält eine `SheetRegistry` in einem `staticCompositionLocalOf`; `Modifier.bottomSheet` ist eine **gewöhnliche, nicht-composable** Extension, deren `Modifier.Node` sich dort registriert. Gezeichnet wird immer nur der zuletzt registrierte offene Eintrag.

Drei interne Nähte tragen das Verhalten und sind **frei von Compose-Typen**, damit sie auf der JVM prüfbar bleiben: `computeAnchors`, `resolveGesture`, `NestedScrollRules`. Wer Verhalten ändert, ändert es dort — nicht im Composable.

## Harte Regeln

- **Code, KDoc, Kommentare, Testnamen und entwicklergerichtete Strings auf Englisch.** Prosa — Tickets, PRs, `docs/`, `CONTEXT.md`, diese Datei — auf Deutsch.
- **Keine Full-Qualifier im Quelltext.** Typen importieren.
- **Kein Material3**, auch nicht im Playground. Nachgewiesen über `releaseCompileClasspath`.
- **Die Library bringt keine Ressourcen mit** — kein `strings.xml`. Ansagetexte kommen von der App.
- **`explicitApi()` ist strict.** Für Testquellen abgeschaltet, weil Testmethoden interne Typen exponieren.
- **Nichts ist `@Experimental`.** Neue Parameter dürfen nur defaultet dazukommen.
- Wenn nur die **Geste** gebraucht wird: `pointerInput`, nicht `clickable`. Wenn nur der **Fokus** gebraucht wird: `focusTarget()`, nicht `focusable()`. Beides bringt sonst ungefragt Semantics mit — siehe Spec, Abschnitt 9.

## Bauen und Testen

```bash
./gradlew build                                            # kompiliert, lintet, JVM-Tests
ANDROID_SERIAL=<serial> ./gradlew :bottomsheet:connectedDebugAndroidTest
```

Vier Stolpersteine, alle schon einmal Zeit gekostet:

- **`ANDROID_SERIAL` setzen.** Hängt dasselbe Gerät über USB *und* TLS an `adb`, installiert Gradle parallel zweimal darauf und die Instrumentierung schlägt mit `DELETE_FAILED_INTERNAL_ERROR` fehl.
- **AGP 9 bringt Kotlin eingebaut mit.** `org.jetbrains.kotlin.android` darf nicht appliziert werden.
- **Espresso ist auf 3.7 gepinnt.** `ui-test-junit4` zieht 3.5 mit, das auf Android 17 in `InputManager.getInstance` kracht.
- **Gradle 9 braucht `junit-platform-launcher`** explizit auf dem Test-Runtime-Classpath.

## Offen

Der **API-Dump als `check`-Gate** fehlt. Weder die in den Kotlin-Gradle-Plugin eingebaute ABI-Validierung noch der `binary-compatibility-validator` registrieren in diesem Modul Tasks: Kotlin kommt über AGP, es gibt kein konventionelles Kotlin-Target zum Andocken. Damit steht „nichts ist `@Experimental`" ohne seine Absicherung — jede Signaturänderung braucht bis auf Weiteres ein aufmerksames Review.

Predictive Back, Blur, echte Insets und der TalkBack-Durchlauf sind Hand-Checkliste vor einem Release, kein Test.
