# SwiftUI `.sheet` + `presentationDetents` — Verhaltensreferenz

Recherche zu [Issue #3](https://github.com/SirCedric/BottomSheet/issues/3), Teil der Map [#1](https://github.com/SirCedric/BottomSheet/issues/1).

**Zweck**: belastbare Referenz dafür, was „verhält sich wie SwiftUI" konkret heißt, als Vorlage für die Gesten- und API-Tickets der Compose-Library.

## Methodik und Vertrauensstufen

Quellen sind ausschließlich Apple-Primärquellen: Apple Developer Documentation (SwiftUI und UIKit), die Human Interface Guidelines sowie die Transkripte der WWDC-Sessions 224 (2019), 10063 (2021) und 10161 (2023). SwiftUIs Sheet-Detents sind auf iOS die SwiftUI-Oberfläche über `UISheetPresentationController`; die UIKit-Doku ist deshalb die genauere Quelle für Mechanik, die die SwiftUI-Doku nur andeutet — sie ist aber formal keine Zusage über SwiftUI.

Hinweis zur Erhebung: developer.apple.com liefert per HTTP nur die SPA-Hülle. Die Zitate stammen aus Apples eigenem DocC-JSON-Backend unter `developer.apple.com/tutorials/data/…`, das denselben Inhalt wie die gerenderte Seite ausliefert, sowie aus den offiziellen WWDC-Transkripten.

Jede Aussage trägt eine Stufe:

| Stufe | Bedeutung |
| --- | --- |
| **[DOK]** | Wörtlich in der SwiftUI-Doku, der UIKit-Doku oder einer WWDC-Session belegt. |
| **[UIKIT]** | In der UIKit-Doku belegt und für SwiftUI plausibel, aber für SwiftUI nicht zugesagt. |
| **[UNDOK]** | Von Apple nirgends dokumentiert. Wird hier **nicht** geraten, sondern als Lücke markiert. |

## 1. `onDismiss` — wann feuert es?

**[DOK]** Die gesamte Doku zum Parameter lautet: „The closure to execute when dismissing the sheet."
Quelle: [`sheet(isPresented:onDismiss:content:)`](https://developer.apple.com/documentation/swiftui/view/sheet(ispresented:ondismiss:content:))

Das ist alles. Apple sagt **nicht**:

- **[UNDOK]** ob `onDismiss` vor oder nach der Exit-Animation läuft,
- **[UNDOK]** ob es bei jedem Dismiss-Pfad (Swipe, programmatisches Binding-Reset, `dismiss()`) feuert oder nur bei einzelnen,
- **[UNDOK]** ob es genau einmal feuert.

Was sich aus UIKit ableiten lässt:

**[UIKIT]** Der Completion-Handler von `dismiss(animated:completion:)` „is called after the `viewDidDisappear(_:)` method is called on the presented view controller" — also **nach** Abschluss der Exit-Animation.
Quelle: [`dismiss(animated:completion:)`](https://developer.apple.com/documentation/uikit/uiviewcontroller/dismiss(animated:completion:))

**[UIKIT]** Achtung, das UIKit-Delegate ist *nicht* das Äquivalent zu `onDismiss`: `presentationControllerDidDismiss(_:)` „is not called if the presentation is dismissed programmatically." Dasselbe gilt für `presentationControllerWillDismiss(_:)`.
Quellen: [`presentationControllerDidDismiss(_:)`](https://developer.apple.com/documentation/uikit/uiadaptivepresentationcontrollerdelegate/presentationcontrollerdiddismiss(_:)), [`presentationControllerWillDismiss(_:)`](https://developer.apple.com/documentation/uikit/uiadaptivepresentationcontrollerdelegate/presentationcontrollerwilldismiss(_:))

**[DOK]** UIKit hat damit **zwei getrennte Kanäle**: das Delegate feuert nur bei User-Dismiss, der `completion:`-Block nur bei programmatischem Dismiss. SwiftUIs einzelnes `onDismiss` muss beide abdecken; wie, ist nicht dokumentiert. Zum Delegate-Kanal ist das Verhalten bei abgebrochenen Gesten präzise beschrieben: „DidDismiss […] is only called once if the user actually pulls the Sheet down and completes the transition." und „if a user were to repeatedly tug on a Sheet without pulling it down, the delegate will receive Should and WillDismiss multiple times before calling DidDismiss, if it receives DidDismiss at all."
Quelle: [WWDC19 Session 224 — Modernizing Your UI for iOS 13](https://developer.apple.com/videos/play/wwdc2019/224/)

**[DOK]** `dismiss()` aus der Environment und das Zurücksetzen des Bindings sind laut Apple beides „programmatic dismissal": „programmatic dismissal, which you can invoke by updating the `Binding` that controls the presentation, or by calling the environment's dismiss action."
Quelle: [`interactiveDismissDisabled(_:)`](https://developer.apple.com/documentation/swiftui/view/interactivedismissdisabled(_:))

**Fazit**: Der genaue Zeitpunkt von `onDismiss` ist eine Doku-Lücke. Wer sich darauf verlässt, verlässt sich auf beobachtetes Verhalten. Für unsere Library ist das eine Freiheit, kein Zwang — wir dürfen den Zeitpunkt selbst festnageln, solange wir ihn dokumentieren.

## 2. Snapping zwischen Detents

**[DOK]** „If you provide more that one detent, people can drag the sheet to resize it." (Tippfehler im Original.)
Quelle: [`presentationDetents(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationdetents(_:))

**[DOK]** Definition eines Detents: „A detent is a height where a sheet naturally rests, and these are defined as a fraction of the fully expanded sheet frame."
Quelle: [WWDC21 Session 10063](https://developer.apple.com/videos/play/wwdc2021/10063/)

**[DOK]** Die verfügbaren Detent-Fälle:

| Fall | Apple-Wortlaut |
| --- | --- |
| `.large` | „The system detent for a sheet at full height." |
| `.medium` | „The system detent for a sheet that's approximately half the height of the screen, and is inactive in compact height." |
| `.fraction(_:)` | „A custom detent with the specified fractional height." |
| `.height(_:)` | „A custom detent with the specified height." |
| `.custom(_:)` | „A custom detent with a calculated height." Berechnet über [`CustomPresentationDetent`](https://developer.apple.com/documentation/swiftui/custompresentationdetent) mit Zugriff auf `Context.maxDetentValue` („The height that the presentation appears in.") |

Quelle: [`PresentationDetent`](https://developer.apple.com/documentation/swiftui/presentationdetent)

**[UIKIT]** Die Detent-Liste ist geordnet: „This array must contain at least one element. When you set this value, specify detents in order from smallest to largest height."
Quelle: [`detents`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/detents)

**[UNDOK]** Es gibt **keine** dokumentierte Snapping-Regel. Weder die SwiftUI-Doku, noch die `UISheetPresentationController`-Doku, noch WWDC21-10063 nennen:

- ob nach Position, nach Velocity oder nach einer Kombination entschieden wird,
- eine Schwelle in Prozent, Punkten oder Punkten pro Sekunde,
- die verwendete Animationskurve für das Snapping.

Apple beschreibt an keiner Stelle mehr als „drag the sheet to resize it". Das ist die größte Lücke dieser Recherche — hier gibt es nichts nachzubauen, nur etwas selbst zu entscheiden.

Dokumentiert ist dagegen der **Startzustand**:

**[UIKIT]** „The default value is `nil`, which means the sheet displays at the smallest detent you specify in `detents`."
Quelle: [`selectedDetentIdentifier`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/selecteddetentidentifier)

**[DOK]** Der Default-Detent-Satz ist `[.large]`: „By default, sheets support the large detent." / „The default value of this property is an array of just the large detent, which is why if you don't set it at all, you get a standard full height sheet."
Quellen: [`presentationDetents(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationdetents(_:)), [WWDC21 Session 10063](https://developer.apple.com/videos/play/wwdc2021/10063/)

**[DOK]** Der Detent lässt sich programmatisch steuern und beobachten, über `presentationDetents(_:selection:)` mit einem `Binding<PresentationDetent>`.
Quelle: [`presentationDetents(_:selection:)`](https://developer.apple.com/documentation/swiftui/view/presentationdetents(_:selection:))

**[DOK]** Ein programmatischer Detent-Wechsel animiert in UIKit **nicht** von selbst; er muss in `animateChanges(_:)` gewrappt werden: „It actually didn't animate at all. I can easily animate this transition by wrapping the setting of the property in a `sheet.animateChanges` block. This will animate the sheet down to the medium detent if needed with **a standard animation curve** and animate other sheets in the stack as well."
Quellen: [WWDC21 Session 10063](https://developer.apple.com/videos/play/wwdc2021/10063/), [`animateChanges(_:)`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/animatechanges(_:))

**[UNDOK]** Welche Kurve „a standard animation curve" ist, sagt Apple nicht.

### Verzahnung mit Scroll-Containern

Das ist der einzige Teil des Gestenmodells, den Apple präzise beschreibt:

**[DOK]** „By default, when a person swipes up on a scroll view in a resizable presentation, the presentation grows to the next detent. A scroll view embedded in the presentation only scrolls after the presentation reaches its largest size." Steuerbar über `presentationContentInteraction(_:)` mit `.automatic`, `.resizes`, `.scrolls`.
Quelle: [`presentationContentInteraction(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationcontentinteraction(_:))

**[UIKIT]** Gleiches in UIKit als `prefersScrollingExpandsWhenScrolledToEdge`, Default `true`: „if the sheet can expand to a larger detent than `selectedDetentIdentifier`, scrolling up in the sheet increases its detent instead of scrolling the sheet's content. After the sheet reaches its largest detent, scrolling begins."
Quelle: [`prefersScrollingExpandsWhenScrolledToEdge`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/prefersscrollingexpandswhenscrolledtoedge)

## 3. `interactiveDismissDisabled`

**[DOK]** Abstract: „Conditionally prevents interactive dismissal of presentations like popovers, sheets, and inspectors."

**[DOK]** Was als „interactive dismissal" gilt: „a user can dismiss a sheet by dragging it down, or a popover by clicking or tapping outside of the presented view."

**[DOK]** Was es nicht tut: „The modifier has no effect on programmatic dismissal, which you can invoke by updating the `Binding` that controls the presentation, or by calling the environment's dismiss action."

**[DOK]** Wirkungsbereich: „You can apply the modifier to any view in the sheet's view hierarchy" — also auch von tief im Sheet-Content aus.

Quelle: [`interactiveDismissDisabled(_:)`](https://developer.apple.com/documentation/swiftui/view/interactivedismissdisabled(_:))

**[UNDOK]** Ob der Modifier den **Wechsel zwischen Detents** blockiert, sagt Apple nirgends. Die Formulierung „prevents interactive dismissal" adressiert ausschließlich das Schließen. Das UIKit-Pendant liest sich enger:

**[UIKIT]** `isModalInPresentation`: „When you set it to `true`, UIKit ignores events outside the view controller's bounds and prevents the interactive dismissal of the view controller while it is onscreen." Auch hier: nur Dismissal, kein Wort zu Detents.
Quelle: [`isModalInPresentation`](https://developer.apple.com/documentation/uikit/uiviewcontroller/ismodalinpresentation)

**[DOK]** Rubber-Band-Feedback steht nicht in der API-Referenz, ist aber in der WWDC-Session wörtlich beschrieben: „when you set this to true on your presented View Controller, it will put the Sheet in a modal state where it cannot be dismissed, and you'll get the rubber-banding effect". Ebenso die Auslösebedingung für das Feedback-Delegate: „DidAttemptToDismiss is only called if isModalInPresentation is true and the user pulls and releases with the intent to dismiss, so with some force or velocity."
Quelle: [WWDC19 Session 224](https://developer.apple.com/videos/play/wwdc2019/224/)

**[UIKIT]** Der zugehörige Hook: `presentationControllerDidAttemptToDismiss(_:)`, „Notifies the delegate that a user-initiated attempt to dismiss a view was prevented. […] Use this method to inform the user why the presentation can't be dismissed." Ein SwiftUI-Äquivalent dazu existiert nicht — das ist eine echte Lücke der SwiftUI-API, gerade weil die HIG eine Bestätigung bei drohendem Datenverlust verlangt (siehe Abschnitt 8).
Quellen: [`presentationControllerDidAttemptToDismiss(_:)`](https://developer.apple.com/documentation/uikit/uiadaptivepresentationcontrollerdelegate/presentationcontrollerdidattempttodismiss(_:)), [Disabling the pull-down gesture for a sheet](https://developer.apple.com/documentation/UIKit/disabling-the-pull-down-gesture-for-a-sheet)

**[DOK]** Wo die Dismiss-Geste hört: „We will place a gesture recognizer on your entire presented view, so pulling down in any noninteractive area will trigger a pulldown on a Sheet."
Quelle: [WWDC19 Session 224](https://developer.apple.com/videos/play/wwdc2019/224/)

**[DOK]** Die Empfehlung, den Drag-Indicator gerade dann zu zeigen, wenn interaktives Dismiss deaktiviert ist (siehe Abschnitt 6), belegt indirekt, dass ein Sheet mit `interactiveDismissDisabled` weiterhin resizable sein soll — sonst wäre der Hinweis „or when the sheet can't dismiss interactively" sinnlos. Das ist eine starke Indizienlage, aber keine wörtliche Zusage.

## 4. Scrim / Dimming

**[UIKIT]** Der Scrim ist als **nicht-interaktiv** dokumentiert: „The default value is `nil`, which means the system adds a **noninteractive** dimming view underneath the sheet at all detents. […] Without a dimming view, the undimmed area around the sheet responds to user interaction, allowing for a nonmodal experience."
Quelle: [`largestUndimmedDetentIdentifier`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/largestundimmeddetentidentifier)

**[UNDOK]** **Scrim-Tap ist nirgends als dismissend dokumentiert.** Apple dokumentiert den Tap-außerhalb ausdrücklich für **Popovers**, nicht für Sheets: „a user can dismiss a sheet by dragging it down, or a popover by clicking or tapping outside of the presented view."
Quelle: [`interactiveDismissDisabled(_:)`](https://developer.apple.com/documentation/swiftui/view/interactivedismissdisabled(_:))

Das ist ein zentrales Ergebnis: Ein iOS-Sheet schließt beim Tippen auf den abgedunkelten Bereich nicht. Genau hier weicht die Android-Erwartungshaltung (und Material) von SwiftUI ab.

**[DOK]** Die Dismiss-Geste sitzt laut Apple auf dem präsentierten View, nicht auf dem Scrim: „We will place a gesture recognizer on your entire presented view, so pulling down in any noninteractive area will trigger a pulldown on a Sheet."
Quelle: [WWDC19 Session 224](https://developer.apple.com/videos/play/wwdc2019/224/)

**[UNDOK]** Dass ein Tap auf den abgedunkelten Bereich bei iPad-Page-/Form-Sheets schließt, ist reale Beobachtung, aber von Apple nicht dokumentiert. Auf dem iPhone existiert bei `.large` ohnehin kaum tappbarer Außenbereich. Wenn unsere Library Scrim-Tap-Dismiss anbietet, ist das eine eigene Design-Entscheidung ohne Apple-Rückendeckung, kein Systemnachbau.

**[DOK]** Zur Kopplung des Dimmings an die Sheet-Position gibt es zwei Aussagen, beide qualitativ und beide auf Detent-Ebene, nicht auf Pixel-Ebene:

> „Notice there's no dimming at the medium detent when I bring up the picker. […] But dimming still **fades in** if I resize to the large detent."
> — [WWDC21 Session 10063](https://developer.apple.com/videos/play/wwdc2021/10063/)

> „As long as the provided argument matches one of the given presentation's detents, SwiftUI will only provide the dimming view at detents greater than the `upThrough` argument. […] When I only enable background interaction up through 200, the dimming view will return for the medium and large detents."
> — [WWDC23 Session 10161 — Inspectors in SwiftUI](https://developer.apple.com/videos/play/wwdc2023/10161/)

Aus der zweiten Stelle folgt nebenbei eine Bedingung, die in der API-Referenz fehlt: Der an `enabled(upThrough:)` übergebene Detent muss im `presentationDetents`-Set enthalten sein.

**[UNDOK]** Eine Formel, ein Max-Alpha, eine Farbe oder eine Interpolationskurve für den Scrim ist nirgends dokumentiert. Belegt ist nur, dass das Dimming beim **Detent-Übergang** ein- und ausblendet — nicht, dass das Alpha kontinuierlich an die momentane Y-Position des Sheets gekoppelt ist.

**[DOK]** Die HIG erwähnt den Scrim nur beschreibend, für iPadOS: „Each style uses a default size for the sheet, centering its content on top of a dimmed background view and providing a consistent experience." Kein Wort zu Tap-Verhalten, kein Wort zu Alpha.
Quelle: [HIG — Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)

## 5. `.large` — Höhe, Corner Radius, Statusbar

**[DOK]** `.large`: „The system detent for a sheet at full height."
Quelle: [`PresentationDetent.large`](https://developer.apple.com/documentation/swiftui/presentationdetent/large)

**[DOK]** `.medium`: „The system detent for a sheet that's approximately half the height of the screen, and is inactive in compact height."
Quelle: [`PresentationDetent.medium`](https://developer.apple.com/documentation/swiftui/presentationdetent/medium)

**[DOK]** Detents sind Anteile des „fully expanded sheet frame"; `.large` ist „the height of a fully expanded sheet". Der „fully expanded frame" ist selbst nicht in Punkten oder relativ zur Statusbar definiert — er wird im Video nur bildlich gezeigt.
Quelle: [WWDC21 Session 10063](https://developer.apple.com/videos/play/wwdc2021/10063/)

**[DOK]** Die HIG formuliert es identisch relativ statt absolut: „The system defines two detents: `large` is the height of a fully expanded sheet and `medium` is about half of the fully expanded height." Und zur Kombinatorik: „Sheets automatically support the `large` detent. Adding the `medium` detent allows the sheet to rest at both heights, whereas specifying only `medium` prevents the sheet from expanding to full height."
Quelle: [HIG — Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)

**[UNDOK]** Der genaue Abstand zwischen Screen-Oberkante und Sheet-Oberkante bei `.large` ist **nicht** dokumentiert — weder als Konstante, noch als „bis unter die Statusbar", noch relativ zur Safe Area. Auch der Begriff „fully expanded sheet frame" wird nirgends numerisch definiert, nur relational: „Detents allow a sheet to resize from one edge of its fully expanded frame while the other three edges remain fixed."
Quelle: [`UISheetPresentationController`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller)

**[UIKIT]** Die einzige explizite Safe-Area-Aussage im ganzen Detent-Umfeld betrifft Custom Detents: „The value you return from this closure is a height within the safe area of the sheet. For example, return `200` for a detent with a height of `200` plus `safeAreaInsets.bottom` when the sheet is edge-attached, or `200` when the sheet is floating." Das heißt: Detent-Höhen rechnen in Safe-Area-Koordinaten, nicht in Screen-Koordinaten — relevant für unser `medium` = Content-Höhe.
Quelle: [`Detent.custom(identifier:resolver:)`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/detent/custom(identifier:resolver:))

**[UNDOK]** Zum **Statusbar-Verhalten** bei `.large` (Umfärben, Verdecken, Style-Wechsel) gibt es in SwiftUI- und UIKit-Doku sowie WWDC21-10063 keine Aussage.

**Corner Radius:**

**[DOK]** `presentationCornerRadius(_:)` „Requests that the presentation have a specific corner radius", `nil` bedeutet Systemdefault. Ab iOS 16.4. „Configuring a corner radius is not supported on watchOS, tvOS, or macOS."
Quelle: [`presentationCornerRadius(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationcornerradius(_:))

**[UIKIT]** `preferredCornerRadius`: „The default value is `nil`. This property only has an effect when the sheet is at the front of its sheet stack."
Quelle: [`preferredCornerRadius`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/preferredcornerradius)

**[UNDOK]** Der numerische System-Default des Corner Radius ist nicht dokumentiert. Ebenso wenig, ob er sich beim Erreichen von `.large` ändert.

**[DOK]** Der Card-Stack-Effekt auf dem präsentierenden View ist dagegen belegt: „the system will keep stacked corners looking consistent, so if this photo picker expands to push back the root sheet, the root sheet will have larger corners to match."
Quelle: [WWDC21 Session 10063](https://developer.apple.com/videos/play/wwdc2021/10063/)

## 6. `presentationDragIndicator`

**[DOK]** „Sets the visibility of the drag indicator on top of a sheet." Parameter ist ein `Visibility` (`.automatic`, `.visible`, `.hidden`). Wann man ihn zeigt: „You can show a drag indicator when it isn't apparent that a sheet can resize or when the sheet can't dismiss interactively."
Quelle: [`presentationDragIndicator(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationdragindicator(_:))

**[UNDOK]** Was `.automatic` konkret auflöst, sagt die SwiftUI-Doku nicht. Der Parameter ist kein sheet-spezifischer Typ, sondern das generische [`Visibility`](https://developer.apple.com/documentation/swiftui/visibility), dessen `.automatic` lautet: „The element may be visible or hidden depending on the policies of the component accepting the visibility configuration." Die Doku delegiert die Antwort also ausdrücklich weiter. Der nächstliegende Anker ist der UIKit-Default `false`, also kein Grabber.

**[UIKIT]** UIKit ist konkreter. `prefersGrabberVisible`, Default `false`: „A grabber is a visual affordance that indicates that a sheet is resizable. Showing a grabber may be useful when it isn't apparent that a sheet can resize or when the sheet can't dismiss interactively. […] The system automatically hides the grabber at appropriate times, like when the sheet is full screen in a compact-height size class or when another sheet presents on top of it."
Quelle: [`prefersGrabberVisible`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/prefersgrabbervisible)

**Rolle im Gestenmodell** — der Grabber ist nicht nur Dekoration:

**[DOK]** „People can always resize your presentation using the drag indicator."
Quelle: [`presentationContentInteraction(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationcontentinteraction(_:))

Das heißt: Selbst wenn Swipes im Content-Bereich per `.scrolls` an den Scroll-Container gehen, bleibt der Drag-Indicator ein Griff, der immer das Sheet bewegt. Er ist ein eigener, priorisierter Gesten-Hitbereich.

**[DOK]** Die HIG ergänzt zwei Punkte, die in der API-Doku fehlen — **Tap** auf den Grabber und Accessibility: „Include a grabber in a resizable sheet. A grabber shows people that they can drag the sheet to resize it; **they can also tap it to cycle through the detents.** In addition to providing a visual indicator of resizability, a grabber also works with VoiceOver so people can resize the sheet without seeing the screen."
Quelle: [HIG — Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)

## 7. `presentationBackgroundInteraction`

**[DOK]** „Controls whether people can interact with the view behind a presentation. […] On many platforms, SwiftUI automatically disables the view behind a sheet that you present, so that people can't interact with the backing view until they dismiss the sheet. Use this modifier if you want to enable interaction." Ab iOS 16.4.
Quelle: [`presentationBackgroundInteraction(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationbackgroundinteraction(_:))

Werte des Typs `PresentationBackgroundInteraction`:

| Wert | Doku |
| --- | --- |
| `.automatic` | „The default background interaction for the presentation." **[UNDOK]**, worauf das auflöst. |
| `.enabled` | „People can interact with the view behind a presentation." |
| `.enabled(upThrough:)` | „People can interact with the view behind a presentation up through a specified detent." Zusatz: „At detents larger than the one you specify, SwiftUI disables interaction." |
| `.disabled` | Doku-Seite war beim Abruf nicht erreichbar (HTTP 504); Semantik ergibt sich aus dem Modifier-Text. |

Quelle: [`PresentationBackgroundInteraction`](https://developer.apple.com/documentation/swiftui/presentationbackgroundinteraction)

**[UIKIT]** Das UIKit-Pendant ist `largestUndimmedDetentIdentifier` — und dort ist Scrim und Hintergrund-Interaktion **dieselbe Sache**: Kein Dimming-View bedeutet automatisch, dass der Bereich außen auf Eingaben reagiert.
Quelle: [`largestUndimmedDetentIdentifier`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/largestundimmeddetentidentifier)

**Bezug zu unserem `draggable = false`**: keiner. `presentationBackgroundInteraction` regelt, ob der Bereich *hinter* dem Sheet Eingaben bekommt; unser `draggable = false` regelt, ob das *Sheet selbst* per Geste bewegt oder geschlossen werden kann. Das sind orthogonale Achsen. Das SwiftUI-Gegenstück zu `draggable = false` ist `interactiveDismissDisabled`, nicht `presentationBackgroundInteraction`. Relevant bleibt aber die dokumentierte Kopplung Scrim ⟷ Hintergrund-Interaktion: Wer den Scrim wegnimmt, gibt auf iOS zwangsläufig die Modalität auf.

## 8. Human Interface Guidelines

Die HIG ist die einzige Quelle, die Erwartungshaltungen statt API-Semantik beschreibt. Relevante Stellen, alle **[DOK]**:

**Swipe-Dismiss ist die erwartete Geste, und Datenverlust braucht eine Bestätigung:**

> „Support swiping to dismiss a sheet. People expect to swipe vertically to dismiss a sheet instead of tapping a dismiss button. If people have unsaved changes in the sheet when they begin swiping to dismiss it, use an action sheet to let them confirm their action."
> — [HIG — Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)

> „When necessary, help people avoid data loss by getting confirmation before closing a modal view. Regardless of whether people use a dismiss gesture or a button, if closing the view could result in the loss of user-generated content, be sure to explain the situation and give people ways to resolve it."
> — [HIG — Modality](https://developer.apple.com/design/human-interface-guidelines/modality)

Bemerkenswert: Die HIG verlangt ein Verhalten, für das SwiftUI keinen Hook anbietet (siehe Abschnitt 3). In UIKit gibt es dafür `presentationControllerDidAttemptToDismiss(_:)`.

**Modal vs. nonmodal ist auf iOS eine bewusste Wahl:**

> „In iOS and iPadOS, a sheet can be either modal or nonmodal. When a nonmodal sheet is onscreen, people use its functionality to affect the parent view without dismissing the sheet."

**Genau ein Sheet gleichzeitig — deckt sich mit unserer Host-Entscheidung:**

> „Display only one sheet at a time from the main interface. When people close a sheet, they expect to return to the parent view or window. If closing a sheet takes people back to another sheet, they can lose track of where they are in your app."

**Wann `medium` sinnvoll ist:**

> „In an iPhone app, consider supporting the `medium` detent to allow progressive disclosure of the sheet's content. For example, a share sheet displays the most relevant items within the `medium` detent […] In contrast, you might not want to support the `medium` detent if a sheet's content is more useful when it displays at full height. For example, the compose sheets in Messages and Mail display only at full height."

Die HIG rahmt Detents ausdrücklich als iPhone-Feature („Designed for iPhone") und empfiehlt für iPad stattdessen Page- oder Form-Sheet.

**Immer einen sichtbaren Ausweg anbieten:**

> „Provide an alternative to the Done button. If you provide a Done button, always pair it with a Cancel button to give people a clear way to dismiss the sheet without confirming or saving their changes […] Relying solely on the Done button implies that completing the task is the only way to exit the sheet, which can feel restrictive or misleading."

Für unser `draggable = false` heißt das: Wer alle Gesten sperrt, muss im Content einen expliziten Schließen-Weg anbieten. Das ist keine API-Anforderung, aber die dokumentierte Erwartung.

## Referenztabelle: SwiftUI-Verhalten → unsere Compose-Library

Die rechte Spalte ist ein Vorschlag auf Basis der in [#1](https://github.com/SirCedric/BottomSheet/issues/1) festgezurrten Entscheidungen; die API- und Gesten-Tickets zurren sie fest.

| # | Thema | SwiftUI (belegt) | Stufe | Unsere Library |
| --- | --- | --- | --- | --- |
| 1 | Default-Detents | `[.large]` | [DOK] | **Anders**: Default `[medium, large]`, `skipPartial = true` ⇒ nur `large`. |
| 2 | Start-Detent | kleinster angegebener Detent | [UIKIT] | **Übernehmen**: Start bei `medium`, falls vorhanden, sonst `large`. |
| 3 | `.medium`-Höhe | „approximately half the height of the screen" | [DOK] | **Bewusst anders**: `medium` = Content-Höhe statt 50 % Screen. Passt besser zu Compose-Content ohne feste Rasterhöhe. |
| 4 | `.large`-Höhe | „full height", exakter Wert offen | [DOK] / [UNDOK] | **Bewusst anders und schärfer**: bis unter die Statusbar (`WindowInsets.statusBars`). Wir legen fest, was Apple offenlässt. |
| 5 | Statusbar bei `.large` | keine Aussage | [UNDOK] | **Eigene Entscheidung**: Statusbar bleibt sichtbar und unangetastet, Sheet endet darunter. |
| 6 | Corner Radius | konfigurierbar, Default nicht dokumentiert; Card-Stack passt Radien an | [DOK] / [UNDOK] | **Teilweise anders**: Shape konfigurierbar, Radius bleibt bei `large` erhalten. **Kein** Card-Stack-Effekt auf dem Hintergrund-Content (v1, kein Sheet-Stacking). |
| 7 | Snapping-Regel | nicht dokumentiert | [UNDOK] | **Eigene Entscheidung**: `AnchoredDraggable` mit `positionalThreshold` + `velocityThreshold`. Konkrete Werte sind ein offener Punkt fürs Gesten-Ticket, keine Apple-Vorgabe. |
| 8 | Scrim-Tap schließt | nicht dokumentiert; Scrim ist „noninteractive"; Tap-außen gilt nur für Popovers | [UIKIT] / [UNDOK] | **Bewusst anders**: Scrim-Tap schließt (Android-Erwartung), außer `draggable = false`. |
| 9 | Scrim-Sichtbarkeit | immer, an allen Detents, außer man hebt Dimming per `largestUndimmedDetentIdentifier` / `presentationBackgroundInteraction` auf | [UIKIT] / [DOK] | **Übernehmen**: Scrim immer sichtbar, auch bei `large`. Kein Undimmed-Modus in v1. |
| 10 | Scrim-Alpha-Kopplung | „dimming fades in", keine Formel | [DOK] / [UNDOK] | **Eigene Entscheidung**: Alpha linear an den Sheet-Offset gekoppelt, Max-Alpha konfigurierbar. |
| 11 | Hintergrund-Interaktion | per Default aus, optional per Detent freigebbar | [DOK] | **Reduziert**: immer aus. `enabled(upThrough:)` hat in v1 kein Äquivalent. |
| 12 | Interaktives Dismiss abschaltbar | `interactiveDismissDisabled(_:)`, wirkt nur auf Gesten | [DOK] | **Übernehmen und erweitern**: `draggable = false` blockiert Drag, Scrim-Tap **und** System-Back. |
| 13 | Programmatisches Dismiss trotz Sperre | „no effect on programmatic dismissal" | [DOK] | **Übernehmen**: `isPresented = false` schließt immer, unabhängig von `draggable`. |
| 14 | Detent-Wechsel bei gesperrtem Dismiss | nicht dokumentiert | [UNDOK] | **Bewusst anders und explizit**: `draggable = false` sperrt *jede* Nutzergeste, also auch den Detent-Wechsel. Ein Wert, nicht zwei. |
| 15 | Feedback bei abgewiesenem Dismiss | Rubber-Band bei `isModalInPresentation`, UIKit-Hook `didAttemptToDismiss`; SwiftUI-Äquivalent fehlt | [DOK] / [UIKIT] | **Nicht übernehmen** in v1: kein Callback, kein Bounce-Zwang. Notiz fürs API-Ticket: Die HIG verlangt Datenverlust-Bestätigung, SwiftUI liefert dafür keinen Hook — hier könnte unsere API besser sein als das Vorbild. |
| 16 | `onDismiss`-Zeitpunkt | „when dismissing the sheet", sonst nichts | [UNDOK] | **Eigene Zusage**: feuert genau einmal, **nach** der Exit-Animation, für alle Dismiss-Pfade (Drag, Scrim-Tap, Back, Binding-Reset). Muss dokumentiert werden, weil Apple es nicht tut. |
| 17 | Drag-Indicator sichtbar | `Visibility` mit `.automatic` / `.visible` / `.hidden`, UIKit-Default `false` | [DOK] / [UIKIT] | **Vereinfacht**: Boolean plus optionaler Composable-Slot. Kein `automatic`. |
| 18 | Drag-Indicator als Griff | „People can always resize your presentation using the drag indicator." | [DOK] | **Übernehmen**: Handle ist immer ein Sheet-Griff, unabhängig vom Nested-Scroll-Verhalten des Contents. |
| 18b | Tap auf den Grabber | „they can also tap it to cycle through the detents"; Grabber ist zudem VoiceOver-bedienbar | [DOK] | **Prüfen fürs Gesten-Ticket**: Tap-to-cycle ist billig zu implementieren und der dokumentierte Accessibility-Einstieg. Kandidat zum Übernehmen. |
| 19 | Scroll vs. Resize | Default: erst Sheet vergrößern, dann scrollen; per `presentationContentInteraction(.scrolls)` umkehrbar | [DOK] | **Default übernehmen**, Umschalter in v1 offen. Gehört ins Nested-Scroll-Ticket. |
| 20 | Detent programmatisch setzen | `presentationDetents(_:selection:)` mit `Binding` | [DOK] | **Übernehmen**: Detent als beobachtbarer, setzbarer State. |
| 21 | Tastatur / IME | „medium height sheets support automatic keyboard avoidance", Sheet wächst und schrumpft wieder | [DOK] | **Übernehmen als Zielverhalten**; Umsetzung im Insets-Ticket. |
| 22 | Compact-Height-Adaption | Sheet wird im vertikal kompakten Kontext zum Full-Screen-Cover; `.medium` ist dort inaktiv | [DOK] | **Offen**: Landscape/Tablet ist in [#1](https://github.com/SirCedric/BottomSheet/issues/1) explizit „not yet specified". SwiftUI liefert hier eine brauchbare Default-Regel. |
| 23 | Sheet-Stacking | technisch möglich, aber HIG: „Display only one sheet at a time from the main interface." | [DOK] | **Nicht übernehmen** in v1: genau ein Sheet gleichzeitig. Deckt sich mit der HIG-Empfehlung. |
| 24 | Höhenbezug | Detent-Höhen rechnen in Safe-Area-Koordinaten, ohne Bottom-Inset | [UIKIT] | **Übernehmen**: `medium` = Content-Höhe zzgl. Bottom-Inset, nicht in Screen-Koordinaten gerechnet. |
| 25 | Detent-Reihenfolge | „specify detents in order from smallest to largest height" | [UIKIT] | **Übernehmen**: `medium` < `large`, Anchors sortiert. |

## Offene Punkte, die Apple nicht beantwortet

Für diese Punkte gibt es keine Vorlage, sie sind eigene Design-Entscheidungen:

1. Snapping-Schwellen (Position, Velocity) und die Snap-Animation.
2. Zeitpunkt und Häufigkeit des `onDismiss`-Callbacks relativ zur Exit-Animation.
3. Scrim-Farbe, Max-Alpha und Alpha-Kurve über den Drag-Verlauf.
4. Exakte Pixel-Höhe von `.large` und das Statusbar-Verhalten dort.
5. Numerischer Default-Corner-Radius; ob er sich detent-abhängig ändert. Der einzige dokumentierte Kontextfaktor ist die Stack-Position, nicht der Detent.
6. Ob interaktives Dismiss-Sperren auch das Resizing sperrt.
7. Was `.automatic` bei `presentationDragIndicator` und `presentationBackgroundInteraction` auflöst.
8. Geometrie des Grabber-Hit-Bereichs. Dass er interaktiv ist, belegt die HIG; wie groß der Trefferbereich ist, sagt niemand.
9. Skalierungsfaktor und Radius des Card-Stack-Effekts auf dem präsentierenden View.

Für die Compose-Seite gibt es zu Punkt 1 immerhin eine dokumentierte Mechanik als Ausgangsbasis: `AnchoredDraggableState` nimmt `positionalThreshold` und `velocityThreshold` als Lambdas; die Migrationsdoku nennt als historische `Modifier.swipeable`-Defaults `FixedThreshold(56.dp)` bzw. `125.dp`.
Quelle: [Migrate from Swipeable to AnchoredDraggable](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/migrate-swipeable)

## Quellen

SwiftUI:

- [`sheet(isPresented:onDismiss:content:)`](https://developer.apple.com/documentation/swiftui/view/sheet(ispresented:ondismiss:content:))
- [`presentationDetents(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationdetents(_:))
- [`presentationDetents(_:selection:)`](https://developer.apple.com/documentation/swiftui/view/presentationdetents(_:selection:))
- [`PresentationDetent`](https://developer.apple.com/documentation/swiftui/presentationdetent) inkl. [`medium`](https://developer.apple.com/documentation/swiftui/presentationdetent/medium), [`large`](https://developer.apple.com/documentation/swiftui/presentationdetent/large), [`fraction(_:)`](https://developer.apple.com/documentation/swiftui/presentationdetent/fraction(_:)), [`height(_:)`](https://developer.apple.com/documentation/swiftui/presentationdetent/height(_:))
- [`CustomPresentationDetent`](https://developer.apple.com/documentation/swiftui/custompresentationdetent) und [`PresentationDetent.Context.maxDetentValue`](https://developer.apple.com/documentation/swiftui/presentationdetent/context/maxdetentvalue)
- [`Visibility`](https://developer.apple.com/documentation/swiftui/visibility)
- [`interactiveDismissDisabled(_:)`](https://developer.apple.com/documentation/swiftui/view/interactivedismissdisabled(_:))
- [`presentationDragIndicator(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationdragindicator(_:))
- [`presentationCornerRadius(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationcornerradius(_:))
- [`presentationBackgroundInteraction(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationbackgroundinteraction(_:)) und [`PresentationBackgroundInteraction`](https://developer.apple.com/documentation/swiftui/presentationbackgroundinteraction)
- [`presentationContentInteraction(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationcontentinteraction(_:))
- [`presentationCompactAdaptation(_:)`](https://developer.apple.com/documentation/swiftui/view/presentationcompactadaptation(_:))
- [`EnvironmentValues.dismiss`](https://developer.apple.com/documentation/swiftui/environmentvalues/dismiss) und [`DismissAction`](https://developer.apple.com/documentation/swiftui/dismissaction)

UIKit:

- [`UISheetPresentationController`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller)
- [`detents`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/detents), [`Detent`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/detent), [`large()`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/detent/large()), [`medium()`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/detent/medium()), [`custom(identifier:resolver:)`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/detent/custom(identifier:resolver:))
- [`selectedDetentIdentifier`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/selecteddetentidentifier), [`animateChanges(_:)`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/animatechanges(_:))
- [`largestUndimmedDetentIdentifier`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/largestundimmeddetentidentifier)
- [`prefersGrabberVisible`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/prefersgrabbervisible)
- [`prefersScrollingExpandsWhenScrolledToEdge`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/prefersscrollingexpandswhenscrolledtoedge)
- [`preferredCornerRadius`](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller/preferredcornerradius)
- [`isModalInPresentation`](https://developer.apple.com/documentation/uikit/uiviewcontroller/ismodalinpresentation)
- [`dismiss(animated:completion:)`](https://developer.apple.com/documentation/uikit/uiviewcontroller/dismiss(animated:completion:))
- [`presentationControllerDidDismiss(_:)`](https://developer.apple.com/documentation/uikit/uiadaptivepresentationcontrollerdelegate/presentationcontrollerdiddismiss(_:)), [`presentationControllerWillDismiss(_:)`](https://developer.apple.com/documentation/uikit/uiadaptivepresentationcontrollerdelegate/presentationcontrollerwilldismiss(_:)), [`presentationControllerShouldDismiss(_:)`](https://developer.apple.com/documentation/uikit/uiadaptivepresentationcontrollerdelegate/presentationcontrollershoulddismiss(_:)), [`presentationControllerDidAttemptToDismiss(_:)`](https://developer.apple.com/documentation/uikit/uiadaptivepresentationcontrollerdelegate/presentationcontrollerdidattempttodismiss(_:))
- [Disabling the pull-down gesture for a sheet](https://developer.apple.com/documentation/UIKit/disabling-the-pull-down-gesture-for-a-sheet)

Human Interface Guidelines:

- [Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)
- [Modality](https://developer.apple.com/design/human-interface-guidelines/modality)

WWDC:

- [WWDC19 Session 224 — Modernizing Your UI for iOS 13](https://developer.apple.com/videos/play/wwdc2019/224/)
- [WWDC21 Session 10063 — Customize and resize sheets in UIKit](https://developer.apple.com/videos/play/wwdc2021/10063/)
- [WWDC22 Session 10068 — What's new in UIKit](https://developer.apple.com/videos/play/wwdc2022/10068/)
- [WWDC23 Session 10161 — Inspectors in SwiftUI: Discover the details](https://developer.apple.com/videos/play/wwdc2023/10161/)

Android, zum Vergleich:

- [Migrate from Swipeable to AnchoredDraggable](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/migrate-swipeable)
