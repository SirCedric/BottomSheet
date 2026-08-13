# BottomSheet

Eine Jetpack-Compose-Library, die ein Bottom Sheet per Modifier an eine beliebige View hängt und sich wie SwiftUIs `.sheet` mit `presentationDetents` verhält. Dieses Glossar hält die Begriffe fest, in denen über die Library gesprochen und geschrieben wird — Prosa auf Deutsch, Begriffe auf Englisch, wie im Code.

## Language

### Sheet und Zustände

**Sheet**:
Die Fläche, die von unten über den App-Content fährt und den Content der App zeigt. Genau eine davon ist gleichzeitig sichtbar.
_Avoid_: Dialog, Modal, Panel

**Presented**:
Der von der App gewünschte Zustand „dieses Sheet soll sichtbar sein". Er sagt nichts darüber, ob das Sheet gerade schon oder noch gezeichnet wird — während der Exit-Animation ist ein Sheet nicht mehr presented, aber weiterhin sichtbar.
_Avoid_: Offen, Visible, Shown

**Detent**:
Eine benannte Rastposition, auf der ein Sheet zur Ruhe kommt. Es gibt genau drei: `Hidden`, `Medium`, `Large`.
_Avoid_: Rastpunkt, Snap-Punkt, State, Stop

**Medium**:
Der Detent, auf dem das Sheet genau so hoch ist wie sein Content. Entfällt, wenn der Content mindestens so hoch wie `Large` ist.
_Avoid_: Partial, Half, Peek

**Large**:
Der Detent, auf dem das Sheet bis unter die obere Kante des sicheren Zeichenbereichs reicht. Der Scrim bleibt darüber sichtbar; es ist kein Vollbild.
_Avoid_: Full, Expanded, Fullscreen

**Hidden**:
Der Detent unterhalb des Bildschirms. Ein Sheet auf `Hidden` ist geschlossen.
_Avoid_: Closed, Collapsed, Gone

**Anchor**:
Die konkrete Pixel-Position, die zu einem Detent gehört. Detents sind benannt, Anchors sind gemessen.
_Avoid_: Offset, Position, Stop

### Bestandteile

**Host**:
Der einmalige Wrapper um den App-Content, der alle Sheets zeichnet. Er liegt über der gesamten App, nicht nur über dem Owner.
_Avoid_: Root, Container, Overlay, Provider

**Owner**:
Das Composable, das den Modifier trägt und damit ein Sheet besitzt. Der Owner bestimmt, *was* im Sheet steht — nicht, *wo* es gezeichnet wird.
_Avoid_: Call Site, Anchor View, Parent

**Scrim**:
Die abdunkelnde Fläche zwischen App-Content und Sheet. Ihre Deckkraft hängt an der Position des Sheets.
_Avoid_: Overlay, Dimmer, Backdrop, Abdunklung

**Drag Handle**:
Der waagerechte Griff am oberen Rand des Sheets. Er signalisiert Beweglichkeit und ist zugleich eine Bedienfläche.
_Avoid_: Grabber, Griff, Indicator, Pill

### Interaktion

**Dismiss**:
Der Übergang eines Sheets nach `Hidden`, gleich ob durch Nutzergeste oder durch die App ausgelöst. Ein Detent-Wechsel zwischen `Medium` und `Large` ist kein Dismiss.
_Avoid_: Cancel, Close, Abbrechen

**Interaktives Dismiss**:
Ein Dismiss, der von einer Nutzergeste ausgeht — Wischen, Scrim-Tap oder Back. Abgrenzung zum programmatischen Dismiss, den die App durch Zurücksetzen ihres Zustands auslöst und der nie gesperrt ist.
_Avoid_: User Dismiss, Manual Dismiss, Swipe-to-dismiss

**Dismiss-Versuch**:
Eine Nutzergeste, die ein Dismiss ausgelöst hätte, aber gesperrt war. Er ist ein eigenes Ereignis, weil die App darauf antworten können muss, etwa mit einer Rückfrage vor Datenverlust.
_Avoid_: Failed Dismiss, Blocked Dismiss, Abgelehnter Dismiss

**Commit**:
Der Moment, in dem eine Geste ihr Ergebnis festlegt — der Finger hebt ab und das Ziel steht fest. Er liegt vor der Animation dorthin, nicht danach.
_Avoid_: Confirm, Settle, Bestätigen
