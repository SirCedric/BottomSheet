package dev.sircedric.bottomsheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.InspectorInfo
import dev.sircedric.bottomsheet.internal.LocalSheetRegistry
import dev.sircedric.bottomsheet.internal.SheetEntry
import dev.sircedric.bottomsheet.internal.SheetRegistry

/**
 * Attaches a bottom sheet to this composable.
 *
 * The owner decides *what* the sheet shows — it is drawn by the [BottomSheetHost], which sits
 * above the entire app. Without a host this fails loudly on the first render.
 *
 * @param isPresented whether the sheet should be visible. The state belongs to the app.
 * @param onDismissRequest fires **only** for dismissals the library caused — an interactive
 *   dismiss, or the owner leaving the composition. If the app sets `isPresented = false` itself,
 *   the callback does not fire.
 * @param presentationDetents the resting positions the sheet is allowed to use.
 * @param initialDetent applies on **every** transition from `false` to `true`. If the value is
 *   not part of [presentationDetents], the smallest contained detent is used.
 * @param gesturesEnabled whether the sheet reacts to user gestures at all. When `false`, a
 *   requested [dragHandle] is **discarded** — a handle that does nothing would be a lie.
 * @param interactiveDismissEnabled whether user gestures may close the sheet. Gated by
 *   [gesturesEnabled]. When `false`, the library offers TalkBack **no** dismiss action; the app
 *   must then place a way to close inside the sheet content itself.
 * @param onDismissAttempt fires when a user gesture would have closed the sheet but was locked —
 *   on crossing the overdrag threshold **during** the gesture, once per gesture. Meant for
 *   confirming before data loss.
 * @param onExpandAttempt the counterpart at the upper edge, when `Large` is not allowed.
 * @param paneTitle announced when the sheet opens. Without a value the library announces
 *   nothing — it ships no strings of its own.
 * @param dragHandle the handle at the top edge; `null` means no handle. If it is missing and the
 *   content scrolls, no reliable drag surface is left, and the library warns.
 * @param colors overrides the host's colours; `null` means "use the host's".
 * @param motion overrides the host's curve.
 * @param shape overrides the host's shape.
 * @param appContentMinScale overrides the scaling of the app content.
 * @param content the content of the sheet. It is composed only when the sheet opens and stays
 *   alive until the exit animation has finished.
 */
public fun Modifier.bottomSheet(
    isPresented: Boolean,
    onDismissRequest: () -> Unit,
    presentationDetents: SheetDetents = SheetDetents.MediumAndLarge,
    initialDetent: PresentationDetent = PresentationDetent.Medium,
    gesturesEnabled: Boolean = true,
    interactiveDismissEnabled: Boolean = true,
    onDismissAttempt: (() -> Unit)? = null,
    onExpandAttempt: (() -> Unit)? = null,
    paneTitle: String? = null,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    colors: BottomSheetColors? = null,
    motion: BottomSheetMotion? = null,
    shape: Shape? = null,
    appContentMinScale: Float? = null,
    content: @Composable BottomSheetScope.() -> Unit,
): Modifier = this then BottomSheetElement(
    isPresented = isPresented,
    onDismissRequest = onDismissRequest,
    presentationDetents = presentationDetents,
    initialDetent = initialDetent,
    gesturesEnabled = gesturesEnabled,
    interactiveDismissEnabled = interactiveDismissEnabled,
    onDismissAttempt = onDismissAttempt,
    onExpandAttempt = onExpandAttempt,
    paneTitle = paneTitle,
    dragHandle = dragHandle,
    colors = colors,
    motion = motion,
    shape = shape,
    appContentMinScale = appContentMinScale,
    content = content,
)

private class BottomSheetElement(
    val isPresented: Boolean,
    val onDismissRequest: () -> Unit,
    val presentationDetents: SheetDetents,
    val initialDetent: PresentationDetent,
    val gesturesEnabled: Boolean,
    val interactiveDismissEnabled: Boolean,
    val onDismissAttempt: (() -> Unit)?,
    val onExpandAttempt: (() -> Unit)?,
    val paneTitle: String?,
    val dragHandle: (@Composable () -> Unit)?,
    val colors: BottomSheetColors?,
    val motion: BottomSheetMotion?,
    val shape: Shape?,
    val appContentMinScale: Float?,
    val content: @Composable BottomSheetScope.() -> Unit,
) : ModifierNodeElement<BottomSheetNode>() {

    override fun create(): BottomSheetNode = BottomSheetNode().also { apply(it) }

    override fun update(node: BottomSheetNode) {
        apply(node)
        node.publish()
    }

    private fun apply(node: BottomSheetNode) {
        node.isPresented = isPresented
        node.onDismissRequest = onDismissRequest
        node.presentationDetents = presentationDetents
        node.initialDetent = initialDetent
        node.gesturesEnabled = gesturesEnabled
        node.interactiveDismissEnabled = interactiveDismissEnabled
        node.onDismissAttempt = onDismissAttempt
        node.onExpandAttempt = onExpandAttempt
        node.paneTitle = paneTitle
        node.dragHandle = dragHandle
        node.colors = colors
        node.motion = motion
        node.shape = shape
        node.appContentMinScale = appContentMinScale
        node.content = content
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bottomSheet"
        properties["isPresented"] = isPresented
        properties["presentationDetents"] = presentationDetents
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BottomSheetElement) return false
        return isPresented == other.isPresented &&
            onDismissRequest === other.onDismissRequest &&
            presentationDetents == other.presentationDetents &&
            initialDetent == other.initialDetent &&
            gesturesEnabled == other.gesturesEnabled &&
            interactiveDismissEnabled == other.interactiveDismissEnabled &&
            onDismissAttempt === other.onDismissAttempt &&
            onExpandAttempt === other.onExpandAttempt &&
            paneTitle == other.paneTitle &&
            dragHandle === other.dragHandle &&
            colors === other.colors &&
            motion === other.motion &&
            shape == other.shape &&
            appContentMinScale == other.appContentMinScale &&
            content === other.content
    }

    override fun hashCode(): Int {
        var result = isPresented.hashCode()
        result = 31 * result + presentationDetents.hashCode()
        result = 31 * result + initialDetent.hashCode()
        result = 31 * result + gesturesEnabled.hashCode()
        result = 31 * result + interactiveDismissEnabled.hashCode()
        result = 31 * result + (paneTitle?.hashCode() ?: 0)
        result = 31 * result + (shape?.hashCode() ?: 0)
        result = 31 * result + (appContentMinScale?.hashCode() ?: 0)
        return result
    }
}

private class BottomSheetNode :
    Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    LayoutModifierNode {

    var isPresented: Boolean = false
    var onDismissRequest: () -> Unit = {}
    var presentationDetents: SheetDetents = SheetDetents.MediumAndLarge
    var initialDetent: PresentationDetent = PresentationDetent.Medium
    var gesturesEnabled: Boolean = true
    var interactiveDismissEnabled: Boolean = true
    var onDismissAttempt: (() -> Unit)? = null
    var onExpandAttempt: (() -> Unit)? = null
    var paneTitle: String? = null
    var dragHandle: (@Composable () -> Unit)? = null
    var colors: BottomSheetColors? = null
    var motion: BottomSheetMotion? = null
    var shape: Shape? = null
    var appContentMinScale: Float? = null
    var content: @Composable BottomSheetScope.() -> Unit = {}

    private var registry: SheetRegistry? = null
    private var hostMissing = false
    private val entry = SheetEntry()

    // Deliberately a read without observation: the host sits at the root of the app and never
    // changes. That is why the registry is a staticCompositionLocalOf and the modifier stays an
    // ordinary, non-composable extension.
    @Suppress("SuspiciousCompositionLocalModifierRead")
    override fun onAttach() {
        val found = currentValueOf(LocalSheetRegistry)
        if (found == null) {
            hostMissing = true
            return
        }
        registry = found
        entry.ownerDescription = toString()
        publish()
        found.register(entry)
    }

    /**
     * The missing host is reported from the layout phase, not from [onAttach]: throwing there
     * leaves the node half attached, and Compose overwrites the message with one of its own
     * during teardown. This way it arrives on the first render and stands at the top.
     */
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        check(!hostMissing) {
            "Modifier.bottomSheet used without a BottomSheetHost.\n" +
                "Wrap the root of your app in BottomSheetHost { ... }."
        }
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun onDetach() {
        val wasPresented = entry.isPresented
        registry?.unregister(entry)
        registry = null

        // When the owner leaves the composition — scrolled out of a LazyColumn, for instance —
        // the sheet disappears without an exit animation. Without this report `isPresented` would
        // stay `true` at the call site and drift silently against what is on screen.
        if (wasPresented) onDismissRequest()
    }

    fun publish() {
        entry.isPresented = isPresented
        entry.onDismissRequest = onDismissRequest
        entry.detents = presentationDetents
        entry.initialDetent = initialDetent
        entry.gesturesEnabled = gesturesEnabled
        entry.interactiveDismissEnabled = interactiveDismissEnabled
        entry.onDismissAttempt = onDismissAttempt
        entry.onExpandAttempt = onExpandAttempt
        entry.paneTitle = paneTitle
        entry.dragHandle = dragHandle
        entry.colors = colors
        entry.motion = motion
        entry.shape = shape
        entry.appContentMinScale = appContentMinScale
        entry.content = content
        registry?.warnIfMultiplePresented()
    }
}
