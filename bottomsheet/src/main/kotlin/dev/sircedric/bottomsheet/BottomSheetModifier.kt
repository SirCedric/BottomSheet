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
 * Hängt ein Bottom Sheet an dieses Composable.
 *
 * Der Owner bestimmt, *was* im Sheet steht — gezeichnet wird es im [BottomSheetHost], der über
 * der gesamten App liegt. Ohne Host kracht es beim ersten Rendern.
 *
 * @param isPresented ob das Sheet sichtbar sein soll. Der Zustand gehört der App.
 * @param onDismissRequest feuert **nur** bei Dismissals, die die Library ausgelöst hat —
 *   interaktives Dismiss und der Fall, dass der Owner die Composition verlässt. Setzt die App
 *   selbst `isPresented = false`, feuert der Callback nicht.
 * @param presentationDetents die erlaubten Ruhepositionen.
 * @param initialDetent gilt bei **jedem** Übergang von `false` nach `true`. Fehlt der Wert in
 *   [presentationDetents], greift der kleinste enthaltene Detent.
 * @param gesturesEnabled ob das Sheet überhaupt auf Nutzergesten reagiert. Ist der Wert `false`,
 *   wird ein angeforderter [dragHandle] **verworfen** — ein Griff, an dem nichts passiert, wäre
 *   eine Falschaussage.
 * @param interactiveDismissEnabled ob Nutzergesten schließen dürfen. Wird von [gesturesEnabled]
 *   gattert. Ist der Wert `false`, bietet die Library TalkBack **keine** Schließen-Aktion an;
 *   die App muss dann selbst einen Schließen-Weg in den Sheet-Content legen.
 * @param onDismissAttempt feuert, wenn eine Nutzergeste geschlossen hätte, aber gesperrt war —
 *   beim Überschreiten der Überzugs-Schwelle **während** der Geste, einmal pro Geste. Gedacht
 *   für die Rückfrage vor Datenverlust.
 * @param onExpandAttempt das Gegenstück an der oberen Kante, wenn `Large` nicht erlaubt ist.
 * @param paneTitle wird beim Öffnen angesagt. Ohne Wert sagt die Library nichts an — sie bringt
 *   keine eigenen Strings mit.
 * @param dragHandle der Griff am oberen Rand; `null` heißt kein Griff. Fehlt er und scrollt der
 *   Content, gibt es keine verlässliche Ziehfläche mehr, und die Library warnt.
 * @param colors überschreibt die Farben des Hosts; `null` heißt „nimm die des Hosts".
 * @param motion überschreibt die Kurve des Hosts.
 * @param shape überschreibt die Form des Hosts.
 * @param appContentMinScale überschreibt die Skalierung des App-Contents.
 * @param content der Inhalt des Sheets. Er wird erst beim Öffnen komponiert und bleibt bis zum
 *   Ende der Exit-Animation am Leben.
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

    // Bewusst ein Lesen ohne Beobachtung: der Host sitzt am Root der App und wechselt nie.
    // Deshalb ist die Registry ein staticCompositionLocalOf und der Modifier bleibt eine
    // gewoehnliche, nicht-composable Extension.
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
     * Der fehlende Host wird in der Layout-Phase gemeldet, nicht in [onAttach]: eine Exception
     * von dort lässt den Node halb attached zurück, und Compose überschreibt die Nachricht beim
     * Aufräumen mit einer eigenen. So kommt sie beim ersten Rendern und steht ganz oben.
     */
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        check(!hostMissing) {
            "Modifier.bottomSheet ohne BottomSheetHost verwendet.\n" +
                "Umschließe den Root deiner App mit BottomSheetHost { ... }."
        }
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun onDetach() {
        val wasPresented = entry.isPresented
        registry?.unregister(entry)
        registry = null

        // Verlässt der Owner die Composition — etwa beim Wegscrollen aus einer LazyColumn —,
        // verschwindet das Sheet ohne Exit-Animation. Ohne diese Meldung bliebe `isPresented`
        // an der Call Site auf `true` stehen und liefe still gegen das Bild.
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
