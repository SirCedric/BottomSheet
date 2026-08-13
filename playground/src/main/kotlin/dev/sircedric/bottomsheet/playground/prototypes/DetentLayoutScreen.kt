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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val mono = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
private val heading = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)

private enum class ContentSize(val label: String, val blocks: Int) {
    Small("klein", 2),
    Medium("mittel", 5),
    Tall("höher als large", 14),
    LongList("Liste, 200 Einträge", 200),
}

@Composable
fun DetentLayoutPrototype() {
    var isPresented by remember { mutableStateOf(false) }
    var skipPartial by remember { mutableStateOf(false) }
    var allowLarge by remember { mutableStateOf(true) }
    var contentSize by remember { mutableStateOf(ContentSize.Medium) }
    var motion by remember { mutableStateOf(MotionVariant.SpringIosLike) }
    var scrimMode by remember { mutableStateOf(ScrimMode.LinearToOffset) }
    var scrimAlpha by remember { mutableStateOf(ScrimAlpha.Material) }
    var contentEffect by remember { mutableStateOf(ContentEffect.Scaled) }
    var backdrop by remember { mutableStateOf(BackdropEffect.ScrimAndBlur) }
    var darkBackground by remember { mutableStateOf(false) }
    var interlock by remember { mutableStateOf(ScrollInterlock.ContentFirst) }
    var handover by remember { mutableStateOf(FlingHandover.Handover) }

    val appBackground = if (darkBackground) Color(0xFF121212) else Color.White
    val onApp = if (darkBackground) Color(0xFFEDEDED) else Color.Black

    Box(Modifier.fillMaxSize().background(appBackground))

    DetentSheet(
        isPresented = isPresented,
        skipPartial = skipPartial,
        allowLarge = allowLarge,
        motion = motion,
        scrimMode = scrimMode,
        scrimAlpha = scrimAlpha,
        scrimColor = Color.Black,
        backdrop = backdrop,
        sheetBackground = if (darkBackground) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
        handleColor = if (darkBackground) Color(0x66FFFFFF) else Color(0x33000000),
        contentEffect = contentEffect,
        interlock = interlock,
        handover = handover,
        onDismiss = { isPresented = false },
        appContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appBackground)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText(
                    "Nested Scroll — Prototyp zu Issue #12",
                    style = heading.copy(color = onApp),
                )

                Row {
                    ProtoChip(
                        if (isPresented) "Sheet schließen" else "Sheet öffnen",
                        onApp = onApp,
                    ) { isPresented = !isPresented }
                    ProtoChip("skipPartial", selected = skipPartial, onApp = onApp) {
                        skipPartial = !skipPartial
                    }
                    ProtoChip("large erlaubt", selected = allowLarge, onApp = onApp) {
                        allowLarge = !allowLarge
                    }
                    ProtoChip("dunkel", selected = darkBackground, onApp = onApp) {
                        darkBackground = !darkBackground
                    }
                }

                BasicText("Content", style = heading.copy(color = onApp))
                FlowRow {
                    ContentSize.entries.forEach {
                        ProtoChip(it.label, selected = it == contentSize, onApp = onApp) {
                            contentSize = it
                        }
                    }
                }

                BasicText("Verzahnung Scroll ↔ Sheet", style = heading.copy(color = onApp))
                FlowRow {
                    ScrollInterlock.entries.forEach {
                        ProtoChip(it.label, selected = it == interlock, onApp = onApp) {
                            interlock = it
                        }
                    }
                }
                BasicText(interlock.detail, style = mono.copy(color = onApp))

                BasicText("Fling-Übergabe", style = heading.copy(color = onApp))
                FlowRow {
                    FlingHandover.entries.forEach {
                        ProtoChip(it.label, selected = it == handover, onApp = onApp) {
                            handover = it
                        }
                    }
                }
                BasicText(handover.detail, style = mono.copy(color = onApp))

                BasicText("Enter-/Exit-Kurve", style = heading.copy(color = onApp))
                FlowRow {
                    MotionVariant.entries.forEach {
                        ProtoChip(it.label, selected = it == motion, onApp = onApp) { motion = it }
                    }
                }
                BasicText(motion.detail, style = mono.copy(color = onApp))

                BasicText("Scrim-Kopplung", style = heading.copy(color = onApp))
                FlowRow {
                    ScrimMode.entries.forEach {
                        ProtoChip(it.label, selected = it == scrimMode, onApp = onApp) {
                            scrimMode = it
                        }
                    }
                }
                BasicText(scrimMode.detail, style = mono.copy(color = onApp))

                BasicText("Max-Alpha", style = heading.copy(color = onApp))
                FlowRow {
                    ScrimAlpha.entries.forEach {
                        ProtoChip(it.label, selected = it == scrimAlpha, onApp = onApp) {
                            scrimAlpha = it
                        }
                    }
                }

                BasicText("Hintergrund-Effekt", style = heading.copy(color = onApp))
                FlowRow {
                    BackdropEffect.entries.forEach {
                        ProtoChip(it.label, selected = it == backdrop, onApp = onApp) {
                            backdrop = it
                        }
                    }
                }
                BasicText(backdrop.detail, style = mono.copy(color = onApp))

                BasicText("App-Content unter dem Sheet", style = heading.copy(color = onApp))
                FlowRow {
                    ContentEffect.entries.forEach {
                        ProtoChip(it.label, selected = it == contentEffect, onApp = onApp) {
                            contentEffect = it
                        }
                    }
                }

                BasicText("Messung", style = heading.copy(color = onApp))
                BasicText(DetentMetrics.text, style = mono.copy(color = onApp))
                BasicText(DetentMetrics.stateText, style = mono.copy(color = onApp))
                BasicText(
                    "nested      = ${NestedScrollMetrics.lastPhase}",
                    style = mono.copy(color = onApp),
                )
            }
        },
        sheetContent = {
            SheetContent(
                size = contentSize,
                onSheet = if (darkBackground) Color(0xFFEDEDED) else Color.Black,
                dark = darkBackground,
                interlock = interlock,
                onInterlock = { interlock = it },
                handover = handover,
                onHandover = { handover = it },
                onDismiss = { isPresented = false },
            )
        },
    )
}

@Composable
private fun SheetContent(
    size: ContentSize,
    onSheet: Color,
    dark: Boolean,
    interlock: ScrollInterlock,
    onInterlock: (ScrollInterlock) -> Unit,
    handover: FlingHandover,
    onHandover: (FlingHandover) -> Unit,
    onDismiss: () -> Unit,
) {
    if (size == ContentSize.LongList) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // Die Schalter liegen sonst hinter dem Scrim — im Sheet sind sie umschaltbar,
                // ohne die Scroll-Position und den Detent zu verlieren.
                Column {
                    FlowRow {
                        ScrollInterlock.entries.forEach {
                            ProtoChip(it.label, selected = it == interlock, onApp = onSheet) {
                                onInterlock(it)
                            }
                        }
                    }
                    FlowRow {
                        FlingHandover.entries.forEach {
                            ProtoChip(it.label, selected = it == handover, onApp = onSheet) {
                                onHandover(it)
                            }
                        }
                    }
                    BasicText(
                        NestedScrollMetrics.lastPhase,
                        style = mono.copy(color = onSheet),
                    )
                    Row {
                        ProtoChip("Zähler zurück", onApp = onSheet) {
                            NestedScrollMetrics.reset()
                        }
                        ProtoChip("schließen", onApp = onSheet, onClick = onDismiss)
                    }
                }
            }
            items(size.blocks) { index ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            when {
                                dark && index % 2 == 0 -> Color(0xFF25324A)
                                dark -> Color(0xFF2A2A2A)
                                index % 2 == 0 -> Color(0xFFE3ECFF)
                                else -> Color(0xFFEDEDED)
                            },
                            RoundedCornerShape(6.dp),
                        ),
                ) {
                    BasicText(
                        "Eintrag $index von ${size.blocks}",
                        style = mono.copy(color = onSheet),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        return
    }

    val scrollModifier = if (size == ContentSize.Tall) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.fillMaxWidth().then(scrollModifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("Sheet-Content — ${size.blocks} Blöcke", style = heading.copy(color = onSheet))
        ProtoChip("schließen", onApp = onSheet, onClick = onDismiss)
        repeat(size.blocks) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        when {
                            dark && index % 2 == 0 -> Color(0xFF25324A)
                            dark -> Color(0xFF2A2A2A)
                            index % 2 == 0 -> Color(0xFFE3ECFF)
                            else -> Color(0xFFEDEDED)
                        },
                        RoundedCornerShape(6.dp),
                    ),
            ) {
                BasicText(
                    "Block $index",
                    style = mono.copy(color = onSheet),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProtoChip(
    text: String,
    onApp: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .background(
                if (selected) Color(0x552F6FED) else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .border(1.dp, onApp.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        BasicText(text, style = TextStyle(fontSize = 12.sp, color = onApp))
    }
}
