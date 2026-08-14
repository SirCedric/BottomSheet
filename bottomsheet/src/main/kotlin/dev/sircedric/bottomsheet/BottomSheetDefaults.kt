package dev.sircedric.bottomsheet

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The appearance of the sheet.
 *
 * Scrim colour and max alpha stay separate: the alpha is animated, the colour is not — and only
 * a value of its own can carry a default that reads the API level.
 */
@Immutable
public class BottomSheetColors internal constructor(
    public val sheet: Color,
    public val handle: Color,
    public val scrim: Color,
    public val scrimMaxAlpha: Float,
)

/** The curve the sheet travels on between detents and towards `Hidden`. */
@Immutable
public class BottomSheetMotion internal constructor(
    public val animationSpec: AnimationSpec<Float>,
)

/**
 * The names of the detent states, used for `stateDescription`.
 *
 * The library ships **no** strings of its own; without values it announces no state. They live
 * on the host rather than the modifier because they are identical app-wide.
 */
@Immutable
public class BottomSheetDetentNames internal constructor(
    public val medium: String?,
    public val large: String?,
)

internal val LocalDragHandleColor: ProvidableCompositionLocal<Color> =
    compositionLocalOf { Color.Black.copy(alpha = 0.4f) }

public object BottomSheetDefaults {

    public val Shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Scale of the app content at full presentation. `1f` turns the effect off. */
    public const val AppContentMinScale: Float = 0.92f

    /**
     * `0.16f` from API 31 on, `0.32f` below.
     *
     * From Android 12 the blur carries part of the separation and the scrim steps back. Below
     * that there is no blur at all — `Modifier.blur` is silently ignored there — so the scrim
     * has to darken on its own.
     */
    public fun scrimMaxAlpha(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.16f else 0.32f

    public fun colors(
        sheet: Color = Color.White,
        handle: Color = Color.Black.copy(alpha = 0.4f),
        scrim: Color = Color.Black,
        scrimMaxAlpha: Float = scrimMaxAlpha(),
    ): BottomSheetColors = BottomSheetColors(
        sheet = sheet,
        handle = handle,
        scrim = scrim,
        scrimMaxAlpha = scrimMaxAlpha,
    )

    public fun motion(
        animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 380f),
    ): BottomSheetMotion = BottomSheetMotion(animationSpec = animationSpec)

    public fun detentNames(
        medium: String? = null,
        large: String? = null,
    ): BottomSheetDetentNames = BottomSheetDetentNames(medium = medium, large = large)

    /**
     * The handle at the top edge.
     *
     * It sits as a fixed header above the content slot and does not scroll with it — otherwise a
     * sheet with scrollable content could not be dragged at all. It carries no semantics of its
     * own.
     */
    @Composable
    public fun DragHandle(modifier: Modifier = Modifier) {
        // The colour comes from the host's resolved colours. A parameter would not work: the
        // default slot is built at the call site, where host and modifier are not resolved yet.
        Box(
            modifier = modifier
                .padding(vertical = 12.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(LocalDragHandleColor.current, RoundedCornerShape(2.dp)),
        )
    }
}
