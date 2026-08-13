package dev.sircedric.bottomsheet.playground.prototypes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val mono = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
private val label = TextStyle(fontSize = 13.sp)
private val heading = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

@Composable
fun HostMechanicsPrototype() {
    var variant by remember { mutableStateOf(HostVariant.Overlay) }

    SheetHost(variant = variant) {
        Scenarios(variant = variant, onVariantChange = { variant = it })
    }
}

@Composable
private fun Scenarios(
    variant: HostVariant,
    onVariantChange: (HostVariant) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var ownerPresent by remember { mutableStateOf(true) }
    var ownerInList by remember { mutableStateOf(false) }
    var secondOwner by remember { mutableStateOf(false) }
    var payload by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .padding(8.dp),
    ) {
        BasicText("Host-Mechanik — Prototyp zu Issue #6", style = heading)

        Row {
            HostVariant.entries.forEach { candidate ->
                ProtoButton(
                    text = candidate.label,
                    selected = candidate == variant,
                    onClick = { onVariantChange(candidate) },
                )
            }
        }

        Row {
            ProtoButton(if (sheetOpen) "Sheet schließen" else "Sheet öffnen") { sheetOpen = !sheetOpen }
            ProtoButton("payload++ ($payload)") { payload++ }
        }

        Row {
            ProtoButton("Owner: ${if (ownerPresent) "da" else "weg"}", selected = ownerPresent) {
                ownerPresent = !ownerPresent
            }
            ProtoButton("in LazyColumn", selected = ownerInList) { ownerInList = !ownerInList }
            ProtoButton("2. Owner", selected = secondOwner) { secondOwner = !secondOwner }
        }

        val listState = rememberLazyListState()
        val extraActions = listOf<Pair<String, () -> Unit>>(
            "Owner töten" to { ownerPresent = false },
            "aus Liste scrollen" to { listState.requestScrollToItem(25) },
        )

        // Der Call-Site-Marker: Sieht der Sheet-Content diesen Wert oder den des Hosts?
        CompositionLocalProvider(LocalMarker provides "call site") {
            if (ownerPresent) {
                if (ownerInList) {
                    OwnerInList(
                        listState = listState,
                        sheetOpen = sheetOpen,
                        payload = payload,
                        onPayloadIncrement = { payload++ },
                        onDismiss = { sheetOpen = false },
                        extraActions = extraActions,
                    )
                } else {
                    OwnerBox(
                        label = "Owner A",
                        sheetOpen = sheetOpen,
                        payload = payload,
                        onPayloadIncrement = { payload++ },
                        onDismiss = { sheetOpen = false },
                        extraActions = extraActions,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                }
            }
            if (secondOwner) {
                OwnerBox(
                    label = "Owner B",
                    sheetOpen = sheetOpen,
                    payload = payload,
                    onPayloadIncrement = { payload++ },
                    onDismiss = { sheetOpen = false },
                    extraActions = extraActions,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
            }
        }

        BasicText("Log (neueste oben)", style = heading, modifier = Modifier.padding(top = 8.dp))
        ProtoButton("Log leeren") { ProtoLog.clear() }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(ProtoLog.lines.toList()) { line ->
                BasicText(line, style = mono)
            }
        }
    }
}

@Composable
private fun OwnerBox(
    label: String,
    sheetOpen: Boolean,
    payload: Int,
    onPayloadIncrement: () -> Unit,
    onDismiss: () -> Unit,
    extraActions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .border(1.dp, Color.Blue, RoundedCornerShape(4.dp))
            .bottomSheetProto(
                ownerLabel = label,
                isPresented = sheetOpen,
                onDismiss = onDismiss,
            ) {
                SheetBody(
                    ownerLabel = label,
                    payload = payload,
                    onPayloadIncrement = onPayloadIncrement,
                    onDismiss = onDismiss,
                    extraActions = extraActions,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("$label — trägt Modifier.bottomSheetProto", style = mono)
    }
}

@Composable
private fun OwnerInList(
    listState: LazyListState,
    sheetOpen: Boolean,
    payload: Int,
    onPayloadIncrement: () -> Unit,
    onDismiss: () -> Unit,
    extraActions: List<Pair<String, () -> Unit>>,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(160.dp)) {
        items((0 until 30).toList()) { index ->
            if (index == 1) {
                OwnerBox(
                    label = "Owner in Liste",
                    sheetOpen = sheetOpen,
                    payload = payload,
                    onPayloadIncrement = onPayloadIncrement,
                    onDismiss = onDismiss,
                    extraActions = extraActions,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )
            } else {
                BasicText("Listeneintrag $index", style = mono, modifier = Modifier.padding(6.dp))
            }
        }
    }
}

/** Läuft in der Composition des Hosts, obwohl das Lambda an der Call Site entsteht. */
@Composable
private fun SheetBody(
    ownerLabel: String,
    payload: Int,
    onPayloadIncrement: () -> Unit,
    onDismiss: () -> Unit,
    extraActions: List<Pair<String, () -> Unit>>,
) {
    val recompositions = rememberRecompositionCount()
    val density = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(density)
    val safeBottom = WindowInsets.safeDrawing.getBottom(density)
    val imeBottom = WindowInsets.ime.getBottom(density)

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("Sheet von $ownerLabel", style = heading)
        BasicText("LocalMarker           = ${LocalMarker.current}", style = mono)
        BasicText("payload (Call Site)   = $payload", style = mono)
        BasicText("Recompositions        = $recompositions", style = mono)
        BasicText("safeDrawing top/bottom= $safeTop / $safeBottom px", style = mono)
        BasicText("ime bottom            = $imeBottom px", style = mono)
        // Callbacks aus der Host-Composition zurück in den State der Call Site.
        Row {
            ProtoButton("payload++ von hier", onClick = onPayloadIncrement)
            ProtoButton("Schließen", onClick = onDismiss)
        }
        // Owner sterben lassen, während das Sheet offen ist.
        Row {
            extraActions.forEach { (label, action) ->
                ProtoButton(label, onClick = action)
            }
        }
    }
}

@Composable
fun SheetPanel(
    variant: HostVariant,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F2F2), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(bottom = 24.dp),
        ) {
            BasicText(
                "Host: ${variant.label}",
                style = mono,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
            content()
        }
    }
}

private class RecompositionCounter {
    var value = 0
}

@Composable
private fun rememberRecompositionCount(): Int {
    val counter = remember { RecompositionCounter() }
    counter.value++
    return counter.value
}

@Composable
private fun ProtoButton(
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
        BasicText(text, style = label)
    }
}
