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

/** Radius des Hintergrund-Blurs bei voller Präsentation. Ab API 31; darunter passiert nichts. */
private val MaxBlurRadius = 24.dp

/**
 * Der einmalige Wrapper um den App-Content, der alle Sheets zeichnet.
 *
 * Er gehört ganz nach oben — in `setContent` um den Root der App. Er rendert **in-composition**,
 * nicht als eigenes Window: nur so sieht der Sheet-Content dieselben `WindowInsets` wie die App
 * und nur so liegt der Scrim auch über der Statusbar.
 *
 * Die hier gesetzten Werte sind die App-weiten Defaults; jeder Modifier darf sie einzeln
 * überschreiben. [detentNames] ist die Ausnahme — die Zustandsnamen sind app-weit identisch.
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

    // Der Eintrag bleibt über das Ende von `isPresented` hinaus am Leben, bis die Exit-Animation
    // durch ist — sonst verschwände das Sheet schlagartig statt auszublenden.
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
                    // Abschirmung des Accessibility-Baums: hideFromAccessibility() schneidet den
                    // Teilbaum nicht ab, clearAndSetSemantics schon. Gekoppelt an `isPresented`,
                    // damit der App-Content beim Dismiss-Commit sofort wieder bedienbar ist.
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
