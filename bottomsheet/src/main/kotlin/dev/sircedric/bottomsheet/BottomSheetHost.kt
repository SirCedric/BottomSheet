package dev.sircedric.bottomsheet

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.sircedric.bottomsheet.internal.LocalSheetRegistry
import dev.sircedric.bottomsheet.internal.SheetEntry
import dev.sircedric.bottomsheet.internal.SheetLayer
import dev.sircedric.bottomsheet.internal.SheetPresentation
import dev.sircedric.bottomsheet.internal.SheetRegistry

/** Blur radius behind the sheet at full presentation. From API 31; below that nothing happens. */
private val MaxBlurRadius = 24.dp

/**
 * The one-time wrapper around the app content that draws every sheet.
 *
 * It belongs at the very top — inside `setContent`, around the root of the app. It renders
 * **in-composition** rather than in a window of its own: only that way does the sheet content
 * see the same `WindowInsets` as the app, and only that way does the scrim cover the status bar.
 *
 * The values set here are the app-wide defaults; any modifier may override them individually.
 * [detentNames] is the exception — the state names are identical app-wide.
 */
@Composable
public fun BottomSheetHost(
    modifier: Modifier = Modifier,
    colors: BottomSheetColors = BottomSheetDefaults.colors(),
    motion: BottomSheetMotion = BottomSheetDefaults.motion(),
    shape: Shape = BottomSheetDefaults.Shape,
    appContentMinScale: Float = BottomSheetDefaults.AppContentMinScale,
    detentNames: BottomSheetDetentNames = BottomSheetDefaults.detentNames(),
    content: @Composable () -> Unit,
) {
    val registry = remember { SheetRegistry() }
    val presentation = remember { SheetPresentation() }

    val presented: SheetEntry? = registry.presented

    // The entry outlives `isPresented` until the exit animation has finished — otherwise the
    // sheet would vanish at once instead of animating out.
    var rendered by remember { mutableStateOf<SheetEntry?>(null) }
    if (presented != null && presented !== rendered) rendered = presented

    val active = rendered
    val minScale = active?.appContentMinScale ?: appContentMinScale

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = presentation.progress()
                    if (progress > 0.01f) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val radius = MaxBlurRadius.toPx() * progress
                            renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                        }
                        val factor = 1f - (1f - minScale) * progress
                        scaleX = factor
                        scaleY = factor
                    }
                }
                .then(
                    // Shielding the accessibility tree: hideFromAccessibility() does not cut the
                    // subtree, clearAndSetSemantics does. Tied to `isPresented` so the app content
                    // becomes operable again the moment a dismiss commits.
                    if (active?.isPresented == true) Modifier.clearAndSetSemantics { } else Modifier,
                ),
        ) {
            CompositionLocalProvider(LocalSheetRegistry provides registry) {
                content()
            }
        }

        if (active != null) {
            key(active) {
                SheetLayer(
                    entry = active,
                    presentation = presentation,
                    hostColors = colors,
                    hostMotion = motion,
                    hostShape = shape,
                    detentNames = detentNames,
                    onFullyHidden = { if (rendered === active) rendered = null },
                )
            }
        }
    }
}
