package dev.sircedric.bottomsheet.playground.prototypes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

private val mono = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
private val heading = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

private enum class ContentSize(val label: String, val blocks: Int) {
    Small("klein", 2),
    Medium("mittel", 5),
    Tall("höher als large", 14),
}

@Composable
fun DetentLayoutPrototype() {
    var isPresented by remember { mutableStateOf(false) }
    var skipPartial by remember { mutableStateOf(false) }
    var largeUsesSafeDrawing by remember { mutableStateOf(true) }
    var composeEagerly by remember { mutableStateOf(true) }
    var contentSize by remember { mutableStateOf(ContentSize.Medium) }
    var grown by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("Detent-Layout — Prototyp zu Issue #7", style = heading)

            FlowRow {
                ProtoChip(if (isPresented) "Sheet schließen" else "Sheet öffnen") {
                    isPresented = !isPresented
                }
                ProtoChip("skipPartial", selected = skipPartial) { skipPartial = !skipPartial }
            }

            FlowRow {
                ContentSize.entries.forEach { candidate ->
                    ProtoChip(candidate.label, selected = candidate == contentSize) {
                        contentSize = candidate
                    }
                }
            }

            FlowRow {
                ProtoChip(
                    if (largeUsesSafeDrawing) "large: safeDrawing" else "large: statusBars",
                    selected = largeUsesSafeDrawing,
                ) { largeUsesSafeDrawing = !largeUsesSafeDrawing }
                ProtoChip(
                    if (composeEagerly) "vorab komponiert" else "erst beim Öffnen",
                    selected = composeEagerly,
                ) { composeEagerly = !composeEagerly }
            }

            BasicText("Messung aus der Layout-Phase", style = heading)
            BasicText(DetentMetrics.text, style = mono)
            BasicText("measure passes = ${DetentMetrics.measurePasses}", style = mono)
            ProtoChip("Zähler zurücksetzen") { DetentMetrics.reset() }
        }

        DetentSheet(
            isPresented = isPresented,
            skipPartial = skipPartial,
            largeUsesSafeDrawing = largeUsesSafeDrawing,
            composeEagerly = composeEagerly,
            scrimMaxAlpha = 0.4f,
            onDismiss = { isPresented = false },
        ) {
            SheetContent(
                size = contentSize,
                grown = grown,
                onToggleGrow = { grown = !grown },
                onDismiss = { isPresented = false },
            )
        }
    }
}

@Composable
private fun SheetContent(
    size: ContentSize,
    grown: Boolean,
    onToggleGrow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val blocks = size.blocks + if (grown) 4 else 0
    val scrollModifier = if (size == ContentSize.Tall) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.fillMaxWidth().then(scrollModifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("Sheet-Content — $blocks Blöcke", style = heading)
        FlowRow {
            ProtoChip(if (grown) "schrumpfen" else "wachsen", onClick = onToggleGrow)
            ProtoChip("schließen", onClick = onDismiss)
        }
        repeat(blocks) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        if (index % 2 == 0) Color(0xFFE3ECFF) else Color(0xFFEDEDED),
                        RoundedCornerShape(6.dp),
                    ),
            ) {
                BasicText("Block $index", style = mono, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun ProtoChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .background(
                if (selected) Color(0xFFD6E4FF) else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        BasicText(text, style = TextStyle(fontSize = 13.sp))
    }
}
