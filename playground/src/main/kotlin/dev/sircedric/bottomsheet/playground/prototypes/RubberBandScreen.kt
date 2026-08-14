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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val mono = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
private val heading = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)

private val factors = listOf(0.2f, 0.35f, 0.5f, 0.7f)
private val maxima = listOf(64.dp, 96.dp, 128.dp, 160.dp)
private val thresholds = listOf(24.dp, 48.dp, 72.dp)

@Composable
fun RubberBandPrototype() {
    var isPresented by remember { mutableStateOf(false) }
    var allowLarge by remember { mutableStateOf(false) }
    var dismissLocked by remember { mutableStateOf(true) }
    var tallContent by remember { mutableStateOf(false) }

    var resistance by remember { mutableStateOf(Resistance.Asymptotic) }
    var factor by remember { mutableStateOf(0.35f) }
    var maxOver by remember { mutableStateOf(96.dp) }
    var returnCurve by remember { mutableStateOf(ReturnCurve.Snappy) }
    var fling by remember { mutableStateOf(FlingBehaviourAtEdge.Capped) }
    var trigger by remember { mutableStateOf(AttemptTrigger.Threshold) }
    var threshold by remember { mutableStateOf(48.dp) }
    var reportTopEdge by remember { mutableStateOf(true) }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    RubberBandSheet(
        isPresented = isPresented,
        allowLarge = allowLarge,
        dismissLocked = dismissLocked,
        settings = { density ->
            with(density) {
                RubberBandSettings(
                    resistance = resistance,
                    factor = factor,
                    maxOverPx = maxOver.toPx(),
                    dimensionPx = screenHeightDp.toPx(),
                    returnCurve = returnCurve,
                    fling = fling,
                    trigger = trigger,
                    thresholdPx = threshold.toPx(),
                    releaseVelocityPx = 400.dp.toPx(),
                    reportTopEdge = reportTopEdge,
                )
            }
        },
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
                BasicText("Rubber-Band — Prototyp zu Issue #16", style = heading)
                BasicText(
                    "Gesperrte Kanten entstehen durch fehlende Anchors. Unten gesperrt = kein " +
                        "Hidden-Anchor, oben gesperrt = kein Large-Anchor.",
                    style = mono,
                )

                Row {
                    Chip(if (isPresented) "Sheet schließen" else "Sheet öffnen") {
                        isPresented = !isPresented
                    }
                    Chip("Dismiss gesperrt", selected = dismissLocked) {
                        dismissLocked = !dismissLocked
                    }
                    Chip("large erlaubt", selected = allowLarge) { allowLarge = !allowLarge }
                    Chip("hoher Content", selected = tallContent) { tallContent = !tallContent }
                }

                BasicText("Widerstandsfunktion", style = heading)
                FlowRow {
                    Resistance.entries.forEach {
                        Chip(it.label, selected = it == resistance) { resistance = it }
                    }
                }
                BasicText(resistance.detail, style = mono)

                BasicText("Faktor", style = heading)
                FlowRow {
                    factors.forEach { Chip("$it", selected = it == factor) { factor = it } }
                }

                BasicText("Maximum (nur asymptotisch)", style = heading)
                FlowRow {
                    maxima.forEach { Chip("${it.value.roundToInt()} dp", selected = it == maxOver) { maxOver = it } }
                }

                BasicText("Rückfahrt", style = heading)
                FlowRow {
                    ReturnCurve.entries.forEach {
                        Chip(it.label, selected = it == returnCurve) { returnCurve = it }
                    }
                }
                BasicText(returnCurve.detail, style = mono)

                BasicText("Schneller Wurf gegen die Kante", style = heading)
                FlowRow {
                    FlingBehaviourAtEdge.entries.forEach {
                        Chip(it.label, selected = it == fling) { fling = it }
                    }
                }
                BasicText(fling.detail, style = mono)

                BasicText("onDismissAttempt feuert", style = heading)
                FlowRow {
                    AttemptTrigger.entries.forEach {
                        Chip(it.label, selected = it == trigger) { trigger = it }
                    }
                }
                BasicText(trigger.detail, style = mono)

                BasicText("Schwelle", style = heading)
                FlowRow {
                    thresholds.forEach {
                        Chip("${it.value.roundToInt()} dp", selected = it == threshold) { threshold = it }
                    }
                    Chip("obere Kante melden", selected = reportTopEdge) {
                        reportTopEdge = !reportTopEdge
                    }
                }

                BasicText("Messung", style = heading)
                BasicText(RubberBandMetrics.dragText, style = mono)
                BasicText(
                    "größter Überzug seit Reset: ${RubberBandMetrics.peakOverPx.roundToInt()} px",
                    style = mono,
                )
                Row {
                    Chip("Messung zurücksetzen") { RubberBandMetrics.reset() }
                }

                BasicText("onDismissAttempt-Log", style = heading)
                if (RubberBandMetrics.attempts.isEmpty()) {
                    BasicText("noch nichts gefeuert", style = mono)
                } else {
                    RubberBandMetrics.attempts.forEach { BasicText(it, style = mono) }
                }
            }
        },
        sheetContent = {
            val blocks = if (tallContent) 14 else 4
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText("Sheet — $blocks Blöcke", style = heading)
                BasicText(
                    if (dismissLocked) {
                        "Nach unten ziehen: gesperrte Kante, Rubber-Band greift."
                    } else {
                        "Nach unten ziehen schließt — Kante ist offen, kein Rubber-Band."
                    },
                    style = mono,
                )
                BasicText(
                    if (allowLarge) {
                        "Nach oben ziehen: large ist erlaubt, kein Rubber-Band."
                    } else {
                        "Nach oben ziehen: gesperrte Kante, Rubber-Band greift."
                    },
                    style = mono,
                )
                Chip("schließen") { isPresented = false }
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
