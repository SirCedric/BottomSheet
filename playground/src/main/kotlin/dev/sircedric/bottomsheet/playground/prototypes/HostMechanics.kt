package dev.sircedric.bottomsheet.playground.prototypes

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wegwerf-Prototyp für Issue #6 — beantwortet, ob ein Modifier Sheet-Content an einen
 * Root-Host durchreichen kann. Kein Library-Code, keine Politur.
 */

private val nodeIdSource = AtomicInteger(0)

object ProtoLog {

    val lines = mutableStateListOf<String>()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Node-Callbacks laufen in der Layout-Phase; ein direkter Snapshot-Write dort, der in der
     * Composition gelesen wird, provoziert Invalidierungsschleifen. Deshalb über den Handler.
     */
    fun add(line: String) {
        mainHandler.post {
            lines.add(0, line)
            if (lines.size > 60) lines.removeAt(lines.lastIndex)
        }
    }

    fun clear() = mainHandler.post { lines.clear() }
}

/** Nur zur Messung: Welche CompositionLocals sieht der Sheet-Content? */
val LocalMarker = compositionLocalOf { "root" }

val LocalSheetRegistry = staticCompositionLocalOf<SheetRegistry?> { null }

class SheetEntry(val ownerLabel: String, val nodeId: Int) {
    var isPresented by mutableStateOf(false)
    var content by mutableStateOf<@Composable () -> Unit>({})
    var onDismiss by mutableStateOf({})
}

class SheetRegistry {

    val entries = mutableStateListOf<SheetEntry>()

    /** Genau ein Sheet gleichzeitig: der zuletzt registrierte offene Eintrag gewinnt. */
    val current: SheetEntry?
        get() = entries.lastOrNull { it.isPresented }

    fun register(entry: SheetEntry) {
        entries.add(entry)
        ProtoLog.add("registry: register node#${entry.nodeId} (${entry.ownerLabel}) → ${entries.size} Einträge")
    }

    fun unregister(entry: SheetEntry) {
        entries.remove(entry)
        ProtoLog.add("registry: unregister node#${entry.nodeId} (${entry.ownerLabel}) → ${entries.size} Einträge")
    }
}

fun Modifier.bottomSheetProto(
    ownerLabel: String,
    isPresented: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
): Modifier = this then BottomSheetElement(ownerLabel, isPresented, onDismiss, content)

private data class BottomSheetElement(
    val ownerLabel: String,
    val isPresented: Boolean,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
) : ModifierNodeElement<BottomSheetNode>() {

    override fun create(): BottomSheetNode = BottomSheetNode(ownerLabel, isPresented, onDismiss, content)

    override fun update(node: BottomSheetNode) {
        ProtoLog.add("element: update() auf node#${node.nodeId} — keine neue Node-Instanz")
        node.set(isPresented, onDismiss, content)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bottomSheetProto"
        properties["isPresented"] = isPresented
    }
}

private class BottomSheetNode(
    ownerLabel: String,
    private var isPresented: Boolean,
    private var onDismiss: () -> Unit,
    private var content: @Composable () -> Unit,
) : Modifier.Node(), CompositionLocalConsumerModifierNode {

    val nodeId = nodeIdSource.incrementAndGet()

    private val entry = SheetEntry(ownerLabel, nodeId)

    private var registry: SheetRegistry? = null

    init {
        ProtoLog.add("element: create() → neue Node-Instanz node#$nodeId ($ownerLabel)")
    }

    override fun onAttach() {
        val resolved = currentValueOf(LocalSheetRegistry)
            ?: error(
                "Modifier.bottomSheet ohne SheetHost verwendet. " +
                    "Umschließe den Root deiner App mit SheetHost { ... }."
            )
        registry = resolved
        entry.isPresented = isPresented
        entry.content = content
        entry.onDismiss = onDismiss
        resolved.register(entry)
        ProtoLog.add("node#$nodeId: onAttach")
    }

    override fun onDetach() {
        registry?.unregister(entry)
        registry = null
        ProtoLog.add("node#$nodeId: onDetach")
    }

    fun set(isPresented: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
        this.isPresented = isPresented
        this.onDismiss = onDismiss
        this.content = content
        if (isAttached) {
            entry.isPresented = isPresented
            entry.content = content
            entry.onDismiss = onDismiss
        }
    }
}

enum class HostVariant(val label: String) {
    Overlay("Overlay (in-composition)"),
    PopupWindow("Popup (eigenes Window)"),
    NoHost("Kein Host (crasht)"),
}

@Composable
fun SheetHost(
    variant: HostVariant,
    appContent: @Composable () -> Unit,
) {
    val registry = remember { SheetRegistry() }

    if (variant == HostVariant.NoHost) {
        // LocalSheetRegistry bleibt null — genau der Fall "Root-Host vergessen".
        appContent()
        return
    }

    CompositionLocalProvider(LocalSheetRegistry provides registry) {
        Box(Modifier.fillMaxSize()) {
            appContent()

            val entry = registry.current
            if (entry != null) {
                // Absichtlich hier gesetzt: Sieht der Sheet-Content "host" statt "call site",
                // ist bewiesen, dass der Content in der Composition des Hosts läuft.
                val sheet: @Composable () -> Unit = {
                    CompositionLocalProvider(LocalMarker provides "host") {
                        SheetSurface(
                            variant = variant,
                            onScrimTap = {
                                ProtoLog.add("scrim: Tap → onDismiss von node#${entry.nodeId}")
                                entry.onDismiss()
                            },
                            content = entry.content,
                        )
                    }
                }
                when (variant) {
                    HostVariant.NoHost -> Unit

                    HostVariant.Overlay -> {
                        BackHandler {
                            ProtoLog.add("overlay: BackHandler hat Back gefangen → onDismiss")
                            entry.onDismiss()
                        }
                        sheet()
                    }

                    HostVariant.PopupWindow -> Popup(
                        onDismissRequest = {
                            ProtoLog.add("popup: onDismissRequest (Back oder Outside) → onDismiss")
                            entry.onDismiss()
                        },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Box(Modifier.fillMaxSize()) { sheet() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSurface(
    variant: HostVariant,
    onScrimTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onScrimTap,
                ),
        )
        SheetPanel(variant = variant, content = content)
    }
}
