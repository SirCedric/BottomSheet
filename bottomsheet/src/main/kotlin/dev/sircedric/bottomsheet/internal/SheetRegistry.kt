package dev.sircedric.bottomsheet.internal

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import dev.sircedric.bottomsheet.BottomSheetColors
import dev.sircedric.bottomsheet.BottomSheetMotion
import dev.sircedric.bottomsheet.BottomSheetScope
import dev.sircedric.bottomsheet.PresentationDetent
import dev.sircedric.bottomsheet.SheetDetents

internal const val LogTag: String = "BottomSheet"

/**
 * Ein registriertes Sheet.
 *
 * Die Felder sind beobachtbar, weil der Modifier sie bei jeder Änderung an der Call Site
 * überschreibt, während der Host sie liest. Der Eintrag selbst überlebt Recompositions —
 * getauscht wird nur sein Inhalt.
 */
internal class SheetEntry {

    var isPresented: Boolean by mutableStateOf(false)
    var onDismissRequest: () -> Unit by mutableStateOf({})
    var detents: SheetDetents by mutableStateOf(SheetDetents.MediumAndLarge)
    var initialDetent: PresentationDetent by mutableStateOf(PresentationDetent.Medium)
    var gesturesEnabled: Boolean by mutableStateOf(true)
    var interactiveDismissEnabled: Boolean by mutableStateOf(true)
    var onDismissAttempt: (() -> Unit)? by mutableStateOf(null)
    var onExpandAttempt: (() -> Unit)? by mutableStateOf(null)
    var paneTitle: String? by mutableStateOf(null)
    var dragHandle: (@Composable () -> Unit)? by mutableStateOf(null)
    var colors: BottomSheetColors? by mutableStateOf(null)
    var motion: BottomSheetMotion? by mutableStateOf(null)
    var shape: Shape? by mutableStateOf(null)
    var appContentMinScale: Float? by mutableStateOf(null)
    var content: @Composable BottomSheetScope.() -> Unit by mutableStateOf({})

    /** Woher das Sheet kommt — nur für die Warnung bei zwei offenen Sheets. */
    var ownerDescription: String by mutableStateOf("")
}

internal class SheetRegistry {

    private val entries = mutableStateListOf<SheetEntry>()

    fun register(entry: SheetEntry) {
        entries.add(entry)
        warnIfMultiplePresented()
    }

    fun unregister(entry: SheetEntry) {
        entries.remove(entry)
    }

    /**
     * Das Sheet, das gezeichnet wird: der zuletzt registrierte offene Eintrag.
     *
     * Die Zusage „genau ein Sheet gleichzeitig" gilt dem Bild, nicht der Registry — zwei offene
     * Sheets sind ein Fehler der App, aber kein Absturz.
     */
    val presented: SheetEntry?
        get() = entries.lastOrNull { it.isPresented }

    fun warnIfMultiplePresented() {
        val open = entries.filter { it.isPresented }
        if (open.size > 1) {
            Log.w(
                LogTag,
                "Mehr als ein Sheet ist gleichzeitig presented; gezeichnet wird das zuletzt " +
                    "registrierte. Betroffen: " +
                    open.joinToString { it.ownerDescription.ifEmpty { "unbekannte Stelle" } },
            )
        }
    }
}

internal val LocalSheetRegistry: ProvidableCompositionLocal<SheetRegistry?> =
    staticCompositionLocalOf { null }
