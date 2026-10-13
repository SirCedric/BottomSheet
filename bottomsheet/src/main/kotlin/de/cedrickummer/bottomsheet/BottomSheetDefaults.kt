package de.cedrickummer.bottomsheet

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.cedrickummer.bottomsheet.internal.HeaderTitlePlacement

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

private val HeaderMinHeight = 52.dp
private val HeaderSidePadding = 8.dp
private val HeaderTitlePadding = 8.dp
private const val HeaderNavigation = "navigation"
private const val HeaderTitle = "title"
private const val HeaderActions = "actions"

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
     * It sits fixed above the header and the content slot and does not scroll with them —
     * otherwise a sheet with scrollable content could not be dragged at all. It carries no
     * semantics of its own.
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

    /**
     * A ready-made filling for the `header` slot of [bottomSheet]: an optional [navigationAction]
     * at the start, an optional [title] in the centre and optional [actions] at the end, all
     * vertically centred.
     *
     * The title is centred on the **full** width and only pushed towards the free side when it
     * would collide with the side slots, which are measured first and win. The library brings no
     * typography, no strings and no icons: [title] is the app's own `Text`. It should set
     * `maxLines = 1` with `TextOverflow.Ellipsis` — the building block does not truncate. The
     * title is marked as a heading for accessibility services.
     *
     * Each action needs a touch target of at least 48 × 48 dp, for instance through
     * `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`. Without Material3 the building
     * block cannot enforce it.
     *
     * The header is at least 52 dp high and grows with its tallest slot.
     */
    @Composable
    public fun Header(
        modifier: Modifier = Modifier,
        navigationAction: (@Composable () -> Unit)? = null,
        title: (@Composable () -> Unit)? = null,
        actions: (@Composable RowScope.() -> Unit)? = null,
    ) {
        Layout(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = HeaderMinHeight)
                .padding(horizontal = HeaderSidePadding),
            content = {
                Box(Modifier.layoutId(HeaderNavigation)) { navigationAction?.invoke() }
                Box(
                    Modifier
                        .layoutId(HeaderTitle)
                        .padding(horizontal = HeaderTitlePadding)
                        // Merged, or the wrapper is not focusable and the screen reader lands on
                        // the text below it, which is no heading.
                        .then(
                            if (title != null) {
                                Modifier.semantics(mergeDescendants = true) { heading() }
                            } else {
                                Modifier
                            },
                        ),
                ) { title?.invoke() }
                Row(Modifier.layoutId(HeaderActions), verticalAlignment = Alignment.CenterVertically) {
                    actions?.invoke(this)
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val navigation = measurables.first { it.layoutId == HeaderNavigation }.measure(loose)
            val actionRow = measurables.first { it.layoutId == HeaderActions }.measure(loose)
            val width = constraints.maxWidth
            val titleMaxWidth = HeaderTitlePlacement.maxWidth(width, navigation.width, actionRow.width)
            val titleText = measurables.first { it.layoutId == HeaderTitle }
                .measure(loose.copy(maxWidth = titleMaxWidth))
            val height = maxOf(constraints.minHeight, navigation.height, titleText.height, actionRow.height)

            layout(width, height) {
                navigation.placeRelative(0, (height - navigation.height) / 2)
                titleText.placeRelative(
                    HeaderTitlePlacement.x(width, navigation.width, actionRow.width, titleText.width),
                    (height - titleText.height) / 2,
                )
                actionRow.placeRelative(width - actionRow.width, (height - actionRow.height) / 2)
            }
        }
    }
}
