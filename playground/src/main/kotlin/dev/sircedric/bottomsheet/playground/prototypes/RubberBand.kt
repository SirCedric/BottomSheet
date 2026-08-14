package dev.sircedric.bottomsheet.playground.prototypes

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Wegwerf-Prototyp für Issue #16 — Rubber-Band an gesperrten Sheet-Kanten.
 *
 * Der Kern: `Modifier.anchoredDraggable` nimmt einen `overscrollEffect` und reicht den
 * überschüssigen Delta- und Velocity-Anteil dorthin durch. Die Widerstandsfunktion ist
 * damit ein eigener `OverscrollEffect`, kein Nachbau der Gestenerkennung.
 */

enum class RubberEdge { None, Top, Bottom }

enum class Resistance(val label: String, val detail: String) {
    LinearDamped(
        "linear gedämpft",
        "über = roh × faktor — wächst ungebremst weiter, kein Maximum",
    ),
    Asymptotic(
        "asymptotisch",
        "über = max × (1 − e^(−roh × faktor / max)) — nähert sich dem Maximum an",
    ),
    IosRubberBand(
        "iOS-Formel",
        "über = (1 − 1/(roh × faktor / dim + 1)) × dim — UIScrollView-Kurve, dim = Bildschirmhöhe",
    ),
    ;

    fun over(raw: Float, maxPx: Float, factor: Float, dimensionPx: Float): Float {
        val magnitude = raw.absoluteValue
        val result = when (this) {
            LinearDamped -> magnitude * factor
            Asymptotic -> maxPx * (1f - exp(-magnitude * factor / maxPx))
            IosRubberBand -> (1f - 1f / (magnitude * factor / dimensionPx + 1f)) * dimensionPx
        }
        return result * raw.sign
    }
}

enum class ReturnCurve(val label: String, val detail: String) {
    SameAsDetent("wie Detent", "spring(0.9, 380) — dieselbe Kurve wie der Detent-Wechsel aus #9"),
    Firm("straffer", "spring(1.0, 900) — kurze Strecke, kurze Zeit"),
    Snappy("tween", "tween(180, FastOutSlowIn) — feste Dauer statt Feder"),
    ;

    val spec: AnimationSpec<Float>
        get() = when (this) {
            SameAsDetent -> spring(dampingRatio = 0.9f, stiffness = 380f)
            Firm -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)
            Snappy -> tween(durationMillis = 180, easing = FastOutSlowInEasing)
        }
}

enum class FlingBehaviourAtEdge(val label: String, val detail: String) {
    Capped("gedeckelt", "Restvelocity verfällt — der Überzug fährt direkt zurück"),
    CarryVelocity("federt weiter", "Restvelocity geht als Startgeschwindigkeit in die Rückfahrt"),
}

enum class AttemptTrigger(val label: String, val detail: String) {
    Threshold("Schwelle im Zug", "feuert, sobald der Überzug die Schwelle überschreitet"),
    OnRelease("beim Loslassen", "feuert erst beim Abheben, wenn Strecke ODER Velocity reicht"),
    EveryRelease("jedes Loslassen", "feuert bei jedem Abheben mit Überzug, ohne Schwelle"),
}

class RubberBandSettings(
    val resistance: Resistance,
    val factor: Float,
    val maxOverPx: Float,
    val dimensionPx: Float,
    val returnCurve: ReturnCurve,
    val fling: FlingBehaviourAtEdge,
    val trigger: AttemptTrigger,
    val thresholdPx: Float,
    val releaseVelocityPx: Float,
    val reportTopEdge: Boolean,
)

/**
 * Nimmt den Delta-Rest, den die Anchors nicht mehr aufnehmen, und übersetzt ihn in einen
 * gedämpften Überzug. `offsetPx` wird additiv auf den Sheet-Offset gelegt.
 */
class SheetRubberBand(
    private val settings: () -> RubberBandSettings,
    private val onAttempt: (RubberEdge, String) -> Unit,
) : OverscrollEffect {

    private var raw by mutableFloatStateOf(0f)

    var offsetPx by mutableFloatStateOf(0f)
        private set

    var edge by mutableStateOf(RubberEdge.None)
        private set

    private var attemptFiredInGesture = false
    private val returning = Animatable(0f)

    override val isInProgress: Boolean
        get() = raw != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val settings = settings()
        val incoming = delta.y

        // Ein bestehender Überzug wird zuerst abgebaut, bevor das Sheet wieder Delta bekommt —
        // sonst springt das Sheet los, während der Finger noch im Überzug zurückkommt.
        val unwound = if (raw != 0f && sign(incoming) != sign(raw)) {
            val next = if (raw > 0f) (raw + incoming).coerceAtLeast(0f) else (raw + incoming).coerceAtMost(0f)
            val consumed = next - raw
            raw = next
            consumed
        } else {
            0f
        }

        val forSheet = incoming - unwound
        val consumedBySheet = if (forSheet != 0f) performScroll(Offset(0f, forSheet)).y else 0f
        val overflow = forSheet - consumedBySheet

        if (overflow.absoluteValue > 0.5f) {
            raw += overflow
        }

        publish(settings)

        if (settings.trigger == AttemptTrigger.Threshold &&
            !attemptFiredInGesture &&
            offsetPx.absoluteValue >= settings.thresholdPx &&
            reportable(settings)
        ) {
            attemptFiredInGesture = true
            onAttempt(edge, "Schwelle ${settings.thresholdPx.roundToInt()} px im Zug überschritten")
        }

        return delta
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val settings = settings()

        if (raw.absoluteValue < 0.5f) {
            performFling(velocity)
            attemptFiredInGesture = false
            return
        }

        // Der Überzug bedeutet: das Sheet liegt schon am äußersten Anchor. Der Fling hat dort
        // nichts mehr zu holen, also darf die Rückfahrt parallel laufen statt hinterher.
        val leftoverVelocity = velocity.y

        if (!attemptFiredInGesture && reportable(settings)) {
            val reason = when (settings.trigger) {
                AttemptTrigger.EveryRelease -> "Loslassen mit Überzug"
                AttemptTrigger.OnRelease -> when {
                    offsetPx.absoluteValue >= settings.thresholdPx ->
                        "Strecke ${offsetPx.roundToInt()} px ≥ ${settings.thresholdPx.roundToInt()} px"

                    leftoverVelocity.absoluteValue >= settings.releaseVelocityPx ->
                        "Velocity ${leftoverVelocity.roundToInt()} px/s ≥ ${settings.releaseVelocityPx.roundToInt()} px/s"

                    else -> ""
                }

                AttemptTrigger.Threshold -> ""
            }
            if (reason.isNotEmpty()) {
                attemptFiredInGesture = true
                onAttempt(edge, reason)
            }
        }

        coroutineScope {
            launch { performFling(velocity) }

            returning.snapTo(raw)
            returning.animateTo(
                targetValue = 0f,
                animationSpec = settings.returnCurve.spec,
                initialVelocity = if (settings.fling == FlingBehaviourAtEdge.CarryVelocity) {
                    leftoverVelocity
                } else {
                    0f
                },
            ) {
                raw = value
                publish(settings)
            }
        }

        raw = 0f
        publish(settings)
        attemptFiredInGesture = false
    }

    private fun reportable(settings: RubberBandSettings): Boolean =
        edge == RubberEdge.Bottom || (edge == RubberEdge.Top && settings.reportTopEdge)

    private fun publish(settings: RubberBandSettings) {
        edge = when {
            raw > 0.5f -> RubberEdge.Bottom
            raw < -0.5f -> RubberEdge.Top
            else -> RubberEdge.None
        }
        offsetPx = settings.resistance.over(
            raw = raw,
            maxPx = settings.maxOverPx,
            factor = settings.factor,
            dimensionPx = settings.dimensionPx,
        )
        RubberBandMetrics.publishDrag(raw, offsetPx, edge)
    }
}

object RubberBandMetrics {

    private val handler = Handler(Looper.getMainLooper())

    var dragText by mutableStateOf("kein Überzug")
        private set

    var peakOverPx by mutableStateOf(0f)
        private set

    val attempts = mutableStateListOf<String>()

    fun publishDrag(raw: Float, over: Float, edge: RubberEdge) {
        val text = if (edge == RubberEdge.None) {
            "kein Überzug"
        } else {
            "Kante=$edge  roh=${raw.roundToInt()} px  über=${over.roundToInt()} px"
        }
        val peak = over.absoluteValue
        handler.post {
            dragText = text
            if (peak > peakOverPx) peakOverPx = peak
        }
    }

    fun logAttempt(edge: RubberEdge, reason: String) {
        handler.post {
            attempts.add(0, "$edge — $reason")
            while (attempts.size > 5) attempts.removeAt(attempts.lastIndex)
        }
    }

    fun reset() {
        handler.post {
            attempts.clear()
            peakOverPx = 0f
        }
    }
}

@Composable
fun RubberBandSheet(
    isPresented: Boolean,
    allowLarge: Boolean,
    dismissLocked: Boolean,
    settings: (Density) -> RubberBandSettings,
    onDismiss: () -> Unit,
    appContent: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit,
) {
    val state = remember { AnchoredDraggableState(initialValue = Detent.Hidden) }
    val density = LocalDensity.current
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)
    val currentSettings by rememberUpdatedState(settings)

    val rubberBand = remember {
        SheetRubberBand(
            settings = { currentSettings(density) },
            onAttempt = { edge, reason -> RubberBandMetrics.logAttempt(edge, reason) },
        )
    }

    val interactive = state.settledValue != Detent.Hidden || state.targetValue != Detent.Hidden

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val factor = 1f - 0.08f * state.sheetProgress()
                    scaleX = factor
                    scaleY = factor
                },
        ) {
            appContent()
        }

        if (isPresented || interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.sheetProgress() * 0.32f }
                    .background(Color.Black)
                    .then(
                        if (state.settledValue != Detent.Hidden && !state.isAnimationRunning) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (!dismissLocked) onDismiss() },
                            )
                        } else {
                            Modifier
                        },
                    ),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .offset {
                        val base = state.offset
                        val y = if (base.isNaN()) Int.MAX_VALUE / 2 else (base + rubberBand.offsetPx).roundToInt()
                        IntOffset(0, y)
                    }
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFFF7F7F7))
                    .anchoredDraggable(
                        state = state,
                        reverseDirection = false,
                        orientation = Orientation.Vertical,
                        enabled = interactive,
                        interactionSource = null,
                        overscrollEffect = rubberBand,
                        flingBehavior = flingBehavior,
                    ),
            ) {
                Column(Modifier.rubberBandLayout(state, topInset, allowLarge, dismissLocked, isPresented)) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(Color(0x33000000), RoundedCornerShape(2.dp)),
                        )
                    }
                    sheetContent()
                }
            }
        }
    }

    val presentedNow by rememberUpdatedState(isPresented)
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }
            .drop(1)
            .collect { settled ->
                if (settled == Detent.Hidden && presentedNow) onDismiss()
            }
    }

    LaunchedEffect(isPresented, allowLarge, dismissLocked) {
        val target = if (!isPresented) Detent.Hidden else if (allowLarge) Detent.Medium else Detent.Medium
        while (!state.anchors.hasPositionFor(target)) {
            withFrameNanos { }
        }
        state.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 380f))
    }
}

/**
 * Gesperrte Kanten entstehen dadurch, dass der Anchor fehlt — nicht dadurch, dass die Geste
 * abgewürgt wird. `Hidden` kommt zurück, sobald die App programmatisch schließt, sonst käme
 * die Exit-Animation nicht mehr an ihr Ziel.
 */
private fun Modifier.rubberBandLayout(
    state: AnchoredDraggableState<Detent>,
    topInset: Int,
    allowLarge: Boolean,
    dismissLocked: Boolean,
    isPresented: Boolean,
): Modifier = layout { measurable, constraints ->
    val containerHeight = constraints.maxHeight
    val largeHeight = (containerHeight - topInset).coerceAtLeast(0)

    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = largeHeight),
    )
    val contentHeight = placeable.height
    val mediumHeight = if (contentHeight >= largeHeight) {
        (largeHeight * 0.5f).roundToInt()
    } else {
        contentHeight
    }

    val anchors = DraggableAnchors {
        if (!dismissLocked || !isPresented) {
            Detent.Hidden at containerHeight.toFloat()
        }
        Detent.Medium at (containerHeight - mediumHeight).toFloat()
        if (allowLarge) {
            Detent.Large at topInset.toFloat()
        }
    }
    state.updateAnchors(anchors)

    layout(placeable.width, largeHeight) { placeable.place(0, 0) }
}

private fun AnchoredDraggableState<Detent>.sheetProgress(): Float {
    val current = offset
    if (current.isNaN() || anchors.size == 0) return 0f
    val bottom = anchors.maxPosition()
    val top = anchors.minPosition()
    if (bottom.isNaN() || top.isNaN() || bottom == top) return 0f
    return ((bottom - current) / (bottom - top)).coerceIn(0f, 1f)
}
