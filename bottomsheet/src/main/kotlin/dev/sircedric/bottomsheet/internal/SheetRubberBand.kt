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

/** Faktor der Widerstandsfunktion aus Abschnitt 6 der Spec. */
private const val ResistanceFactor = 0.35f

/** Obergrenze des Überzugs; die Kurve nähert sich ihr asymptotisch an. */
private val MaxOverdrag = 96.dp

/** Ab hier gilt ein Zug als Versuch. Gestenschwelle der Library, kein Parameter. */
private val AttemptThreshold = 48.dp

private val ReturnSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

internal enum class SheetEdge { None, Top, Bottom }

/**
 * Das Rubber-Band an gesperrten Kanten.
 *
 * Eine gesperrte Kante ist ein **fehlender Anchor** — `anchoredDraggable` reicht genau den
 * Delta- und Velocity-Anteil hierher durch, den die Anchors nicht mehr aufnehmen. Der Überzug
 * wird additiv auf den Sheet-Offset gelegt; `Modifier.overscroll` wird nicht gebraucht, weil
 * wir ihn selbst zeichnen.
 *
 * [reportTopEdge] und [reportBottomEdge] entscheiden, ob eine Kante als **Versuch** gilt.
 */
internal class SheetRubberBand(
    private val density: Density,
    private val reportTopEdge: () -> Boolean,
    private val reportBottomEdge: () -> Boolean,
    private val onDismissAttempt: () -> Unit,
    private val onExpandAttempt: () -> Unit,
) : OverscrollEffect {

    private var raw by mutableFloatStateOf(0f)

    /** Der gedämpfte Überzug in Pixeln, additiv auf den Sheet-Offset. */
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

        // Ein bestehender Überzug wird zuerst abgebaut, bevor das Sheet wieder Delta bekommt —
        // sonst springt es los, während der Finger noch im Überzug zurückkommt.
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

        // Der Überzug bedeutet, dass das Sheet schon am äußersten Anchor liegt — der Fling hat
        // dort nichts mehr zu holen, also darf die Rückfahrt parallel laufen statt hinterher.
        coroutineScope {
            launch { performFling(velocity) }
            springBack()
        }

        attemptFiredInGesture = false
    }

    /** Zug aus der Nested-Scroll-Verzahnung, die den Effekt nicht über `performScroll` erreicht. */
    fun pull(deltaY: Float) {
        raw += deltaY
        recompute()
        maybeReportDuringDrag()
    }

    /** Loslassen nach einem [pull]. */
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
        // Ohne Startgeschwindigkeit: ein Wurf gegen die Kante federt nicht weiter aus als ein
        // langsamer Zug — an einer gesperrten Kante ist das Ergebnis ohnehin festgelegt.
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
