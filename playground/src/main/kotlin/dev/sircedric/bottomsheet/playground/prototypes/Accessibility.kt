package dev.sircedric.bottomsheet.playground.prototypes

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Wegwerf-Prototyp für Issue #17 — verdrahtet die in #14 entschiedenen Accessibility-Zusagen,
 * damit sie am Gerät mit TalkBack überhaupt prüfbar sind.
 *
 * Jede Zusage ist einzeln abschaltbar, damit der Unterschied hörbar wird statt nur behauptet.
 */

class A11ySwitches(
    val clearAppContentSemantics: Boolean,
    val trapFocus: Boolean,
    val paneTitle: String?,
    val allowLarge: Boolean,
    val dismissLocked: Boolean,
    val stateNameMedium: String,
    val stateNameLarge: String,
)

@Composable
fun A11ySheet(
    isPresented: Boolean,
    switches: A11ySwitches,
    onDismiss: () -> Unit,
    appContent: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit,
) {
    val state = remember { AnchoredDraggableState(initialValue = Detent.Hidden) }
    val density = LocalDensity.current
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)
    val scope = rememberCoroutineScope()
    val panelFocus = remember { FocusRequester() }

    val interactive = state.settledValue != Detent.Hidden || state.targetValue != Detent.Hidden
    val presented = isPresented || interactive

    // Zwei Detents nur, wenn large erlaubt ist — sonst gibt es keinen unterscheidbaren Zustand,
    // also weder expand/collapse noch stateDescription (#14).
    val hasTwoDetents = switches.allowLarge
    val atLarge = state.settledValue == Detent.Large

    val cycleDetent: () -> Unit = {
        if (hasTwoDetents) {
            val next = if (atLarge) Detent.Medium else Detent.Large
            scope.launch { state.animateTo(next, SheetSpring) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val factor = 1f - 0.08f * state.progress()
                    scaleX = factor
                    scaleY = factor
                }
                .then(
                    if (isPresented && switches.clearAppContentSemantics) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                ),
        ) {
            appContent()
        }

        if (presented) {
            // Der Scrim bleibt semantikfrei — auch dann, wenn sein Tap schliesst (#14).
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.progress() * 0.32f }
                    .background(Color.Black)
                    .then(
                        if (state.settledValue != Detent.Hidden && !state.isAnimationRunning) {
                            // Kein Modifier.clickable: das legt zwangslaeufig einen ganzflaechigen,
                            // unbeschrifteten Knoten in den Accessibility-Baum (am Geraet belegt).
                            Modifier.pointerInput(switches.dismissLocked) {
                                detectTapGestures {
                                    if (!switches.dismissLocked) onDismiss()
                                }
                            }
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
                        IntOffset(0, if (base.isNaN()) Int.MAX_VALUE / 2 else base.roundToInt())
                    }
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFFF7F7F7))
                    .then(
                        if (switches.trapFocus) {
                            Modifier
                                .focusRequester(panelFocus)
                                .focusProperties { onExit = { cancelFocusChange() } }
                                .focusGroup()
                                // focusTarget statt focusable: focusable() setzt Focused +
                                // RequestFocus in die Semantics, woraus TalkBack eine Aktion baut.
                                .focusTarget()
                        } else {
                            Modifier
                        },
                    )
                    .semantics {
                        isTraversalGroup = true

                        switches.paneTitle?.let { paneTitle = it }

                        if (hasTwoDetents) {
                            stateDescription = if (atLarge) {
                                switches.stateNameLarge
                            } else {
                                switches.stateNameMedium
                            }

                            // Nur die mögliche Richtung anbieten, nie beide (#14).
                            if (atLarge) {
                                collapse {
                                    scope.launch { state.animateTo(Detent.Medium, SheetSpring) }
                                    true
                                }
                            } else {
                                expand {
                                    scope.launch { state.animateTo(Detent.Large, SheetSpring) }
                                    true
                                }
                            }
                        }

                        // Strikt an #8: kein erlaubtes Nutzer-Dismiss ⇒ die Aktion existiert nicht.
                        if (!switches.dismissLocked) {
                            dismiss {
                                onDismiss()
                                true
                            }
                        }
                    }
                    .anchoredDraggable(
                        state = state,
                        reverseDirection = false,
                        orientation = Orientation.Vertical,
                        enabled = interactive,
                        flingBehavior = flingBehavior,
                    ),
            ) {
                Column(Modifier.a11yLayout(state, topInset, switches, isPresented)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(hasTwoDetents, atLarge) {
                                detectTapGestures { cycleDetent() }
                            }
                            .padding(vertical = 12.dp),
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

    LaunchedEffect(presented, switches.trapFocus) {
        if (presented && switches.trapFocus) {
            while (state.offset.isNaN()) {
                withFrameNanos { }
            }
            panelFocus.requestFocus()
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

    LaunchedEffect(isPresented, switches.allowLarge, switches.dismissLocked) {
        val target = if (isPresented) Detent.Medium else Detent.Hidden
        while (!state.anchors.hasPositionFor(target)) {
            withFrameNanos { }
        }
        state.animateTo(target, SheetSpring)
    }
}

private val SheetSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)

private fun Modifier.a11yLayout(
    state: AnchoredDraggableState<Detent>,
    topInset: Int,
    switches: A11ySwitches,
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
        if (!switches.dismissLocked || !isPresented) {
            Detent.Hidden at containerHeight.toFloat()
        }
        Detent.Medium at (containerHeight - mediumHeight).toFloat()
        if (switches.allowLarge) {
            Detent.Large at topInset.toFloat()
        }
    }
    state.updateAnchors(anchors)

    layout(placeable.width, largeHeight) { placeable.place(0, 0) }
}

private fun AnchoredDraggableState<Detent>.progress(): Float {
    val current = offset
    if (current.isNaN() || anchors.size == 0) return 0f
    val bottom = anchors.maxPosition()
    val top = anchors.minPosition()
    if (bottom.isNaN() || top.isNaN() || bottom == top) return 0f
    return ((bottom - current) / (bottom - top)).coerceIn(0f, 1f)
}
