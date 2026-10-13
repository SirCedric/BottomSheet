// PROTOTYPE for #48, throwaway. Three header variants, switchable via the floating bar at the bottom.
// A: M3 metrics (64.dp, collision shift), B: iOS-like compact text buttons with a symmetric
// centre, C: header merged into the handle row.
package de.cedrickummer.bottomsheet.playground.prototypes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.cedrickummer.bottomsheet.BottomSheetDefaults
import de.cedrickummer.bottomsheet.BottomSheetHost
import de.cedrickummer.bottomsheet.BottomSheetScope
import de.cedrickummer.bottomsheet.SheetDetents
import de.cedrickummer.bottomsheet.bottomSheet
import kotlin.math.max

private enum class Variant(val label: String) {
    A("M3-Maße"),
    B("iOS-kompakt"),
    C("Handle-Zeile"),
}

private class Knobs {
    var open by mutableStateOf(false)
    var longContent by mutableStateOf(true)
    var handle by mutableStateOf(true)
    var gestures by mutableStateOf(true)
    var navigation by mutableStateOf(true)
    var actionCount by mutableIntStateOf(1)
    var longTitle by mutableStateOf(false)
    var largeOnly by mutableStateOf(false)
    var rtl by mutableStateOf(false)
    var lastClick by mutableStateOf("—")
    var headerHeight by mutableStateOf(0.dp)
}

@Composable
fun HeaderPrototype() {
    var variantIndex by rememberSaveable { mutableIntStateOf(0) }
    val variant = Variant.entries[variantIndex]
    val knobs = remember { Knobs() }

    Box(Modifier.fillMaxSize()) {
        val direction = if (knobs.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            BottomSheetHost {
                ControlScreen(knobs, variant)
            }
        }
        Switcher(
            text = "${variant.name} · ${variant.label} · Header ${knobs.headerHeight} · Klick: ${knobs.lastClick}",
            onPrevious = { variantIndex = (variantIndex + Variant.entries.size - 1) % Variant.entries.size },
            onNext = { variantIndex = (variantIndex + 1) % Variant.entries.size },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlScreen(knobs: Knobs, variant: Variant) {
    val density = LocalDensity.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .safeDrawingPadding()
            .padding(16.dp)
            .bottomSheet(
                isPresented = knobs.open,
                onDismissRequest = { knobs.open = false },
                presentationDetents = if (knobs.largeOnly) SheetDetents.Large else SheetDetents.MediumAndLarge,
                gesturesEnabled = knobs.gestures,
                paneTitle = "Spiel erfassen",
                dragHandle = if (knobs.handle && variant != Variant.C) {
                    { BottomSheetDefaults.DragHandle() }
                } else {
                    null
                },
                header = {
                    Box(
                        Modifier.onSizeChanged {
                            knobs.headerHeight = with(density) { it.height.toDp() }
                        },
                    ) {
                        VariantHeader(variant, knobs)
                    }
                },
            ) {
                SheetContent(knobs.longContent)
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText("Prototyp #48: Header", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Inhalt: ${if (knobs.longContent) "lang" else "kurz"}") { knobs.longContent = !knobs.longContent }
            Chip("Handle: ${onOff(knobs.handle)}") { knobs.handle = !knobs.handle }
            Chip("Gesten: ${onOff(knobs.gestures)}") { knobs.gestures = !knobs.gestures }
            Chip("Navigation: ${onOff(knobs.navigation)}") { knobs.navigation = !knobs.navigation }
            Chip("Aktionen: ${knobs.actionCount}") { knobs.actionCount = (knobs.actionCount + 1) % 4 }
            Chip("Titel: ${if (knobs.longTitle) "lang" else "kurz"}") { knobs.longTitle = !knobs.longTitle }
            Chip("Detents: ${if (knobs.largeOnly) "Large" else "Medium+Large"}") { knobs.largeOnly = !knobs.largeOnly }
            Chip("RTL: ${onOff(knobs.rtl)}") { knobs.rtl = !knobs.rtl }
        }
        Spacer(Modifier.height(8.dp))
        Chip("Sheet öffnen", strong = true) { knobs.open = true }
        BasicText(
            "Variante C zeichnet den Handle selbst und setzt dragHandle = null. Der Tap-Wechsel " +
                "des Handles entfällt dort, die Library warnt im Log.",
            style = TextStyle(fontSize = 13.sp, color = Color.Gray),
        )
    }
}

@Composable
private fun VariantHeader(variant: Variant, knobs: Knobs) {
    val title = if (knobs.longTitle) "Spiel erfassen für den Stammtisch am Donnerstag" else "Spiel erfassen"
    val iconActions = listOf("✓" to "Sichern", "⋮" to "Mehr", "↺" to "Zurücksetzen").take(knobs.actionCount)
    val textActions = listOf("Sichern", "Teilen", "Löschen").take(knobs.actionCount)
    val click: (String) -> Unit = { knobs.lastClick = it }

    when (variant) {
        Variant.A -> CollisionShiftHeader(
            minHeight = 64.dp,
            navigation = if (knobs.navigation) {
                { IconAction("✕", "Schließen") { click("Schließen") } }
            } else {
                null
            },
            title = { TitleText(title, 22.sp, FontWeight.Normal) },
            actions = if (iconActions.isNotEmpty()) {
                { iconActions.forEach { (glyph, label) -> IconAction(glyph, label) { click(label) } } }
            } else {
                null
            },
        )

        Variant.B -> SymmetricHeader(
            minHeight = 52.dp,
            sidePadding = 8.dp,
            pill = false,
            navigation = if (knobs.navigation) {
                { TextAction("Abbrechen", bold = false) { click("Abbrechen") } }
            } else {
                null
            },
            title = { TitleText(title, 17.sp, FontWeight.SemiBold) },
            actions = if (textActions.isNotEmpty()) {
                { textActions.forEachIndexed { i, label -> TextAction(label, bold = i == 0) { click(label) } } }
            } else {
                null
            },
        )

        Variant.C -> SymmetricHeader(
            minHeight = 60.dp,
            sidePadding = 4.dp,
            pill = true,
            navigation = if (knobs.navigation) {
                { IconAction("✕", "Schließen") { click("Schließen") } }
            } else {
                null
            },
            title = { TitleText(title, 17.sp, FontWeight.SemiBold) },
            actions = if (iconActions.isNotEmpty()) {
                { iconActions.forEach { (glyph, label) -> IconAction(glyph, label) { click(label) } } }
            } else {
                null
            },
        )
    }
}

/** Variant A: the measure and place policy of CenterAlignedTopAppBar, rebuilt without M3. */
@Composable
private fun CollisionShiftHeader(
    minHeight: Dp,
    navigation: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    Layout(
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        content = {
            Box(Modifier.layoutId("nav").padding(start = 4.dp)) { navigation?.invoke() }
            Box(
                Modifier
                    .layoutId("title")
                    .padding(horizontal = 4.dp)
                    .semantics(mergeDescendants = true) { heading() },
            ) { title() }
            Row(Modifier.layoutId("actions").padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                actions?.invoke(this)
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val nav = measurables.first { it.layoutId == "nav" }.measure(loose)
        val act = measurables.first { it.layoutId == "actions" }.measure(loose)
        val startSpace = max(12.dp.roundToPx(), nav.width)
        val maxTitle = (constraints.maxWidth - startSpace - act.width).coerceAtLeast(0)
        val ttl = measurables.first { it.layoutId == "title" }.measure(loose.copy(maxWidth = maxTitle))
        val height = maxOf(minHeight.roundToPx(), ttl.height, nav.height, act.height)
        val width = constraints.maxWidth
        layout(width, height) {
            nav.placeRelative(0, (height - nav.height) / 2)
            var x = (width - ttl.width) / 2
            val end = width - act.width
            if (x < startSpace) x = startSpace else if (x + ttl.width > end) x = end - ttl.width
            ttl.placeRelative(x, (height - ttl.height) / 2)
            act.placeRelative(width - act.width, (height - act.height) / 2)
        }
    }
}

/** Variants B and C: both sides reserve the wider side's width, so the title sits exactly centred. */
@Composable
private fun SymmetricHeader(
    minHeight: Dp,
    sidePadding: Dp,
    pill: Boolean,
    navigation: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    Box(Modifier.fillMaxWidth()) {
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(horizontal = sidePadding)
                .padding(top = if (pill) 10.dp else 0.dp),
            content = {
                Box(Modifier.layoutId("nav")) { navigation?.invoke() }
                Box(
                    Modifier
                        .layoutId("title")
                        .padding(horizontal = 8.dp)
                        .semantics(mergeDescendants = true) { heading() },
                ) { title() }
                Row(Modifier.layoutId("actions"), verticalAlignment = Alignment.CenterVertically) {
                    actions?.invoke(this)
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val nav = measurables.first { it.layoutId == "nav" }.measure(loose)
            val act = measurables.first { it.layoutId == "actions" }.measure(loose)
            val side = max(nav.width, act.width)
            val maxTitle = (constraints.maxWidth - 2 * side).coerceAtLeast(0)
            val ttl = measurables.first { it.layoutId == "title" }.measure(loose.copy(maxWidth = maxTitle))
            val height = maxOf(constraints.minHeight, ttl.height, nav.height, act.height)
            val width = constraints.maxWidth
            layout(width, height) {
                nav.placeRelative(0, (height - nav.height) / 2)
                ttl.placeRelative((width - ttl.width) / 2, (height - ttl.height) / 2)
                act.placeRelative(width - act.width, (height - act.height) / 2)
            }
        }
        if (pill) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun TitleText(text: String, size: TextUnit, weight: FontWeight) {
    BasicText(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = size, fontWeight = weight, color = Color.Black),
    )
}

@Composable
private fun IconAction(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = TextStyle(fontSize = 22.sp, color = Color(0xFF1A73E8)))
    }
}

@Composable
private fun TextAction(label: String, bold: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            maxLines = 1,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                color = Color(0xFF007AFF),
            ),
        )
    }
}

@Composable
private fun BottomSheetScope.SheetContent(long: Boolean) {
    if (!long) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Kurzer Inhalt. Medium soll Handle + Header + diesen Block umfassen.")
            BasicText("Detent: $currentDetent")
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText("Detent: $currentDetent")
        repeat(30) { i ->
            var value by rememberSaveable { mutableStateOf("") }
            Column {
                BasicText("Feld ${i + 1}", style = TextStyle(fontSize = 13.sp, color = Color.Gray))
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF2F2F7), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, strong: Boolean = false, onClick: () -> Unit) {
    BasicText(
        text,
        style = TextStyle(fontSize = 15.sp, color = if (strong) Color.White else Color.Black),
        modifier = Modifier
            .background(if (strong) Color(0xFF1A73E8) else Color.White, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Switcher(text: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .background(Color(0xE6202124), RoundedCornerShape(24.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwitcherButton("‹", onPrevious)
            BasicText(text, style = TextStyle(fontSize = 12.sp, color = Color.White), modifier = Modifier.padding(horizontal = 4.dp))
            SwitcherButton("›", onNext)
        }
    }
}

@Composable
private fun SwitcherButton(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        BasicText(glyph, style = TextStyle(fontSize = 22.sp, color = Color.White))
    }
}

private fun onOff(value: Boolean) = if (value) "an" else "aus"
