package dev.sircedric.bottomsheet.playground.prototypes

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

/**
 * Wegwerf-Prototyp für Issue #7 (Layout, Anchors) und Issue #9 (Animation, Scrim).
 */

enum class Detent { Hidden, Medium, Large }

object DetentMetrics {

    private val handler = Handler(Looper.getMainLooper())

    var text by mutableStateOf("noch nicht gemessen")
        private set

    var measurePasses by mutableStateOf(0)
        private set

    var stateText by mutableStateOf("")
        private set

    fun publishState(value: String) {
        if (value == stateText) return
        handler.post { stateText = value }
    }

    private var lastPublished: String? = null
    private var passCount = 0

    fun publish(value: String) {
        passCount++
        val passes = passCount
        if (value == lastPublished) return
        lastPublished = value
        handler.post {
            text = value
            measurePasses = passes
        }
    }

    fun reset() {
        passCount = 0
        lastPublished = null
        handler.post {
            text = "noch nicht gemessen"
            measurePasses = 0
        }
    }
}

@Composable
fun DetentSheet(
    isPresented: Boolean,
    skipPartial: Boolean,
    allowLarge: Boolean,
    motion: MotionVariant,
    scrimMode: ScrimMode,
    scrimAlpha: ScrimAlpha,
    scrimColor: Color,
    backdrop: BackdropEffect,
    sheetBackground: Color,
    handleColor: Color,
    contentEffect: ContentEffect,
    onDismiss: () -> Unit,
    appContent: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit,
) {
    val state = remember { AnchoredDraggableState(initialValue = Detent.Hidden) }
    val density = LocalDensity.current
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)

    val interactive = state.settledValue != Detent.Hidden || state.targetValue != Detent.Hidden

    // Entscheidung: der Scrim nimmt Taps erst an, wenn das Sheet zur Ruhe gekommen ist —
    // sonst schliesst ein hektischer Doppeltipp das Sheet noch waehrend des Aufblendens.
    val scrimTappable = state.settledValue != Detent.Hidden && !state.isAnimationRunning

    DetentMetrics.publishState(
        "offset=${state.offset.let { if (it.isNaN()) "NaN" else it.roundToInt().toString() }}" +
            "  settled=${state.settledValue}  target=${state.targetValue}" +
            "  animating=${state.isAnimationRunning}  presented=$isPresented",
    )

    // Entschieden: mit Blur tritt der Scrim zurück, weil der Blur die Trennung schon leistet.
    val effectiveAlpha = when (backdrop) {
        BackdropEffect.BlurOnly -> 0f
        BackdropEffect.ScrimAndBlur -> ScrimAlphaWithBlur
        BackdropEffect.ScrimOnly -> scrimAlpha.value
    }

    // Entkoppelte Blende (M3-Verhalten): eigene Animation auf die Sichtbarkeit statt am Offset.
    val independentAlpha by animateFloatAsState(
        targetValue = if (state.targetValue != Detent.Hidden) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrim",
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = state.sheetFraction()
                    if (backdrop != BackdropEffect.ScrimOnly && progress > 0.01f) {
                        val radius = MaxBlurRadius * progress
                        renderEffect = BlurEffect(radius.toPx(), radius.toPx(), TileMode.Decal)
                    }
                    when (contentEffect) {
                        ContentEffect.Static -> Unit

                        ContentEffect.Scaled -> {
                            val factor = 1f - 0.08f * progress
                            scaleX = factor
                            scaleY = factor
                        }

                        ContentEffect.Translated -> translationY = -40.dp.toPx() * progress
                    }
                },
        ) {
            appContent()
        }

        if (isPresented || interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = when (scrimMode) {
                            ScrimMode.LinearToOffset -> state.sheetFraction() * effectiveAlpha
                            ScrimMode.EasedToOffset ->
                                FastOutSlowInEasing.transform(state.sheetFraction()) * effectiveAlpha

                            ScrimMode.FullAtMedium -> state.fractionUpToMedium() * effectiveAlpha
                            ScrimMode.IndependentFade -> independentAlpha * effectiveAlpha
                        }
                    }
                    .background(scrimColor)
                    .then(
                        if (scrimTappable) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { state.offsetOrHidden() }
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(sheetBackground)
                    .anchoredDraggable(
                        state = state,
                        reverseDirection = false,
                        orientation = Orientation.Vertical,
                        enabled = interactive,
                        flingBehavior = flingBehavior,
                    ),
            ) {
                Column(Modifier.detentLayout(state, topInset, skipPartial, allowLarge)) {
                    // Fester Kopf: scrollt nicht mit, bleibt damit immer als Ziehgriff erreichbar.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(handleColor, RoundedCornerShape(2.dp)),
                        )
                    }
                    sheetContent()
                }
            }
        }
    }

    // Entscheidung aus Issue #8: rastet eine Geste auf Hidden, meldet die Library das sofort
    // zurück — sonst sagt die App weiter "offen", während nichts mehr zu sehen ist.
    val presentedNow by rememberUpdatedState(isPresented)
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }
            .drop(1)
            .collect { settled ->
                if (settled == Detent.Hidden && presentedNow) onDismiss()
            }
    }

    LaunchedEffect(isPresented, skipPartial, allowLarge, motion) {
        while (state.anchors.size == 0) {
            withFrameNanos { }
        }
        val target = when {
            !isPresented -> Detent.Hidden
            state.anchors.hasPositionFor(Detent.Medium) -> Detent.Medium
            else -> Detent.Large
        }
        state.animateTo(target, motion.spec)
    }
}

/** Misst den Content und setzt die Anchors im selben Layout-Pass (siehe Issue #7). */
private const val MediumFractionWhenContentOverflows = 0.5f

private val MaxBlurRadius = 24.dp

private const val ScrimAlphaWithBlur = 0.16f

private fun Modifier.detentLayout(
    state: AnchoredDraggableState<Detent>,
    topInset: Int,
    skipPartial: Boolean,
    allowLarge: Boolean,
): Modifier = layout { measurable, constraints ->
    val containerHeight = constraints.maxHeight
    val largeHeight = (containerHeight - topInset).coerceAtLeast(0)

    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = largeHeight),
    )
    val contentHeight = placeable.height

    // Revision der Kappungs-Regel aus #7: medium fällt bei zu hohem Content nicht mehr weg,
    // sondern bekommt eine feste Höhe, damit die Zwischenstufe erhalten bleibt.
    val mediumHeight = if (contentHeight >= largeHeight) {
        (largeHeight * MediumFractionWhenContentOverflows).roundToInt()
    } else {
        contentHeight
    }
    val mediumDropped = skipPartial && allowLarge

    val anchors = DraggableAnchors {
        Detent.Hidden at containerHeight.toFloat()
        if (!mediumDropped) {
            Detent.Medium at (containerHeight - mediumHeight).toFloat()
        }
        if (allowLarge) {
            Detent.Large at topInset.toFloat()
        }
    }
    state.updateAnchors(anchors)

    DetentMetrics.publish(
        buildString {
            appendLine("container   = $containerHeight px")
            appendLine("topInset    = $topInset px")
            appendLine("largeHeight = $largeHeight px")
            appendLine("content     = $contentHeight px")
            appendLine("medium      = ${if (mediumDropped) "entfällt" else "${containerHeight - mediumHeight} px (Höhe $mediumHeight)"}")
            append("anchors     = ${anchors.size}")
        },
    )

    layout(placeable.width, largeHeight) { placeable.place(0, 0) }
}

private fun AnchoredDraggableState<Detent>.offsetOrHidden(): IntOffset {
    val current = offset
    return if (current.isNaN()) IntOffset(0, Int.MAX_VALUE / 2) else IntOffset(0, current.roundToInt())
}

/** 0 bei Hidden, 1 am obersten Anchor. */
private fun AnchoredDraggableState<Detent>.sheetFraction(): Float {
    val current = offset
    if (current.isNaN() || anchors.size == 0) return 0f
    val hidden = anchors.positionOf(Detent.Hidden)
    val topMost = anchors.minPosition()
    if (hidden.isNaN() || topMost.isNaN() || hidden == topMost) return 0f
    return ((hidden - current) / (hidden - topMost)).coerceIn(0f, 1f)
}

/** 0 bei Hidden, 1 bereits bei medium — medium→large ändert nichts mehr. */
private fun AnchoredDraggableState<Detent>.fractionUpToMedium(): Float {
    val current = offset
    if (current.isNaN() || anchors.size == 0) return 0f
    val hidden = anchors.positionOf(Detent.Hidden)
    val reference = if (anchors.hasPositionFor(Detent.Medium)) {
        anchors.positionOf(Detent.Medium)
    } else {
        anchors.minPosition()
    }
    if (hidden.isNaN() || reference.isNaN() || hidden == reference) return 0f
    return ((hidden - current) / (hidden - reference)).coerceIn(0f, 1f)
}
