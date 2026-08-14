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
 * A registered sheet.
 *
 * The fields are observable because the modifier overwrites them on every change at the call
 * site while the host reads them. The entry itself survives recompositions — only its contents
 * are swapped.
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

    /** Where the sheet comes from — only used for the warning about two open sheets. */
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
     * The sheet that gets drawn: the most recently registered open entry.
     *
     * The promise of "exactly one sheet at a time" applies to the picture, not the registry —
     * two open sheets are a bug in the app, but not a crash.
     */
    val presented: SheetEntry?
        get() = entries.lastOrNull { it.isPresented }

    fun warnIfMultiplePresented() {
        val open = entries.filter { it.isPresented }
        if (open.size > 1) {
            Log.w(
                LogTag,
                "More than one sheet is presented at the same time; the most recently " +
                    "registered one is drawn. Affected: " +
                    open.joinToString { it.ownerDescription.ifEmpty { "unknown call site" } },
            )
        }
    }
}

internal val LocalSheetRegistry: ProvidableCompositionLocal<SheetRegistry?> =
    staticCompositionLocalOf { null }
