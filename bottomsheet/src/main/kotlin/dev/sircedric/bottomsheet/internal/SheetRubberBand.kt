package dev.sircedric.bottomsheet.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Factor of the resistance function from section 6 of the spec. */
private const val ResistanceFactor = 0.35f

/** Upper bound of the overdrag; the curve approaches it asymptotically. */
private val MaxOverdrag = 96.dp

/** From here on a drag counts as an attempt. A library gesture threshold, not a parameter. */
private val AttemptThreshold = 48.dp

private val ReturnSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

internal enum class SheetEdge { None, Top, Bottom }

/**
 * The rubber band at locked edges.
 *
 * A locked edge is a **missing anchor** — `anchoredDraggable` forwards exactly the share of
 * delta and velocity the anchors can no longer take. The overdrag is added on top of the sheet
 * offset; `Modifier.overscroll` is not needed because we draw it ourselves.
 *
 * [reportTopEdge] and [reportBottomEdge] decide whether an edge counts as an **attempt**.
 */
internal class SheetRubberBand(
    private val density: Density,
    private val reportTopEdge: () -> Boolean,
    private val reportBottomEdge: () -> Boolean,
    private val onDismissAttempt: () -> Unit,
    private val onExpandAttempt: () -> Unit,
) : OverscrollEffect {

    private var raw by mutableFloatStateOf(0f)

    /** The damped overdrag in pixels, added on top of the sheet offset. */
    var offsetPx: Float by mutableFloatStateOf(0f)
        private set

    private var attemptFiredInGesture = false

    private val returning = Animatable(0f)

    private val maxOverdragPx: Float get() = with(density) { MaxOverdrag.toPx() }

    private val thresholdPx: Float get() = with(density) { AttemptThreshold.toPx() }

    private val edge: SheetEdge
        get() = when {
            raw > 0.5f -> SheetEdge.Bottom
            raw < -0.5f -> SheetEdge.Top
            else -> SheetEdge.None
        }

    override val isInProgress: Boolean
        get() = raw != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val incoming = delta.y

        // An existing overdrag is unwound before the sheet gets delta again — otherwise it jumps
        // off while the finger is still travelling back through the overdrag.
        val unwound = if (raw != 0f && sign(incoming) != sign(raw)) {
            val next = if (raw > 0f) {
                (raw + incoming).coerceAtLeast(0f)
            } else {
                (raw + incoming).coerceAtMost(0f)
            }
            val consumed = next - raw
            raw = next
            consumed
        } else {
            0f
        }

        val forSheet = incoming - unwound
        val consumedBySheet = if (forSheet != 0f) performScroll(Offset(0f, forSheet)).y else 0f
        val overflow = forSheet - consumedBySheet

        if (overflow.absoluteValue > 0.5f) raw += overflow

        recompute()
        maybeReportDuringDrag()

        return delta
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (raw.absoluteValue < 0.5f) {
            performFling(velocity)
            attemptFiredInGesture = false
            return
        }

        // An overdrag means the sheet already sits at the outermost anchor — the fling has
        // nothing left to do there, so the return may run in parallel rather than after it.
        coroutineScope {
            launch { performFling(velocity) }
            springBack()
        }

        attemptFiredInGesture = false
    }

    /** A pull from the nested-scroll interlocking, which does not reach us via `performScroll`. */
    fun pull(deltaY: Float) {
        raw += deltaY
        recompute()
        maybeReportDuringDrag()
    }

    /** Release after a [pull]. */
    suspend fun release() {
        if (raw.absoluteValue < 0.5f) {
            attemptFiredInGesture = false
            return
        }
        springBack()
        attemptFiredInGesture = false
    }

    private suspend fun springBack() {
        returning.snapTo(raw)
        // Without an initial velocity: a fling against the edge does not spring further than a
        // slow drag — at a locked edge the outcome is fixed anyway.
        returning.animateTo(targetValue = 0f, animationSpec = ReturnSpec) {
            raw = value
            recompute()
        }
        raw = 0f
        recompute()
    }

    private fun recompute() {
        val magnitude = raw.absoluteValue
        val max = maxOverdragPx
        offsetPx = if (max <= 0f) {
            0f
        } else {
            max * (1f - exp(-magnitude * ResistanceFactor / max)) * raw.sign
        }
    }

    private fun maybeReportDuringDrag() {
        if (attemptFiredInGesture) return
        if (offsetPx.absoluteValue < thresholdPx) return

        when (edge) {
            SheetEdge.Bottom -> if (reportBottomEdge()) {
                attemptFiredInGesture = true
                onDismissAttempt()
            }

            SheetEdge.Top -> if (reportTopEdge()) {
                attemptFiredInGesture = true
                onExpandAttempt()
            }

            SheetEdge.None -> Unit
        }
    }
}
