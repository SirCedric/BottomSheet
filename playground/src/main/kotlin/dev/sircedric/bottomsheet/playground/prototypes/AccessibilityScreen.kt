package dev.sircedric.bottomsheet.playground.prototypes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val mono = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
private val heading = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)

@Composable
fun AccessibilityPrototype() {
    var isPresented by remember { mutableStateOf(false) }
    var clearSemantics by remember { mutableStateOf(true) }
    var trapFocus by remember { mutableStateOf(true) }
    var withPaneTitle by remember { mutableStateOf(true) }
    var allowLarge by remember { mutableStateOf(true) }
    var dismissLocked by remember { mutableStateOf(false) }
    var emptySheet by remember { mutableStateOf(false) }

    val switches = A11ySwitches(
        clearAppContentSemantics = clearSemantics,
        trapFocus = trapFocus,
        paneTitle = if (withPaneTitle) "Details" else null,
        allowLarge = allowLarge,
        dismissLocked = dismissLocked,
        stateNameMedium = "Halbe Höhe",
        stateNameLarge = "Volle Höhe",
    )

    A11ySheet(
        isPresented = isPresented,
        switches = switches,
        onDismiss = { isPresented = false },
        appContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText("Accessibility — Prototyp zu Issue #17", style = heading)
                BasicText(
                    "Jede Zusage aus #14 einzeln abschaltbar. TalkBack einschalten, dann " +
                        "umschalten und den Unterschied hören.",
                    style = mono,
                )

                Row {
                    Chip(if (isPresented) "Sheet schließen" else "Sheet öffnen") {
                        isPresented = !isPresented
                    }
                }

                BasicText("Zusagen aus #14", style = heading)
                FlowRow {
                    Chip("App-Content abgeschirmt", selected = clearSemantics) {
                        clearSemantics = !clearSemantics
                    }
                    Chip("Fokus-Trap", selected = trapFocus) { trapFocus = !trapFocus }
                    Chip("paneTitle gesetzt", selected = withPaneTitle) {
                        withPaneTitle = !withPaneTitle
                    }
                }

                BasicText("Konfiguration des Sheets", style = heading)
                FlowRow {
                    Chip("large erlaubt (2 Detents)", selected = allowLarge) {
                        allowLarge = !allowLarge
                    }
                    Chip("Dismiss gesperrt", selected = dismissLocked) {
                        dismissLocked = !dismissLocked
                    }
                    Chip("Sheet ohne Bedienelemente", selected = emptySheet) {
                        emptySheet = !emptySheet
                    }
                }

                BasicText("Was jetzt gelten müsste", style = heading)
                BasicText(
                    buildString {
                        appendLine(
                            if (clearSemantics) {
                                "App-Content: für TalkBack unsichtbar, solange presented"
                            } else {
                                "App-Content: bleibt lesbar (Gegenprobe)"
                            },
                        )
                        appendLine(
                            if (allowLarge) {
                                "Aktionen: expand ODER collapse, dazu stateDescription"
                            } else {
                                "Aktionen: keine — ein Detent, kein Zustand"
                            },
                        )
                        appendLine(
                            if (dismissLocked) {
                                "dismiss: fehlt — kein Weg heraus außer dem Button im Sheet"
                            } else {
                                "dismiss: vorhanden"
                            },
                        )
                        append(if (withPaneTitle) "paneTitle: \"Details\"" else "paneTitle: nicht gesetzt")
                    },
                    style = mono,
                )

                BasicText("App-Content zum Anfassen", style = heading)
                FlowRow {
                    repeat(4) { index -> Chip("App-Knopf $index") { } }
                }
            }
        },
        sheetContent = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (emptySheet) {
                    BasicText(
                        "Dieses Sheet hat kein fokussierbares Element — der Trap muss trotzdem halten.",
                        style = mono,
                    )
                } else {
                    BasicText("Sheet-Inhalt", style = heading)
                    Chip("Sheet-Knopf A") { }
                    Chip("Sheet-Knopf B") { }
                    Chip("schließen") { isPresented = false }
                }
            }
        },
    )
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .background(
                if (selected) Color(0x552F6FED) else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        BasicText(text, style = TextStyle(fontSize = 12.sp))
    }
}
