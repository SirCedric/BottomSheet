package dev.sircedric.bottomsheet.internal

import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import dev.sircedric.bottomsheet.BottomSheetColors
import dev.sircedric.bottomsheet.BottomSheetDetentNames
import dev.sircedric.bottomsheet.BottomSheetMotion
import dev.sircedric.bottomsheet.BottomSheetScope
import dev.sircedric.bottomsheet.LocalDragHandleColor
import dev.sircedric.bottomsheet.PresentationDetent
import androidx.compose.ui.graphics.Shape
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * What the host needs to know about the running sheet in order to drive app content and blur.
 *
 * Read exclusively in the draw phase — that is why the movement costs no recomposition.
 */
internal class SheetPresentation {

    var state: AnchoredDraggableState<Detent>? by mutableStateOf(null)

    var anchors: SheetAnchors? by mutableStateOf(null)

    fun progress(): Float {
        val currentState = state ?: return 0f
        val currentAnchors = anchors ?: return 0f
        return currentAnchors.progressAt(currentState.offset)
    }
}

@Composable
internal fun SheetLayer(
    entry: SheetEntry,
    presentation: SheetPresentation,
    hostColors: BottomSheetColors,
    hostMotion: BottomSheetMotion,
    hostShape: Shape,
    detentNames: BottomSheetDetentNames,
    onFullyHidden: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val colors = entry.colors ?: hostColors
    val motion = entry.motion ?: hostMotion
    val shape = entry.shape ?: hostShape
    val detents = entry.detents

    val topInset = WindowInsets.safeDrawing.getTop(density)

    // The detent survives rotation and process death; `isPresented` belongs to the app.
    var savedDetent by rememberSaveable { mutableStateOf<String?>(null) }

    val startDetent = remember {
        if (savedDetent != null && entry.isPresented) {
            resolveRestoredDetent(savedDetent?.let(::detentOf), entry.initialDetent, detents)
        } else {
            Detent.Hidden
        }
    }

    val state = remember { AnchoredDraggableState(initialValue = startDetent) }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)

    val dismissLocked = entry.gesturesEnabled && !entry.interactiveDismissEnabled
    val outcomeFor = { gesture: Gesture ->
        resolveGesture(
            gesture = gesture,
            gesturesEnabled = entry.gesturesEnabled,
            interactiveDismissEnabled = entry.interactiveDismissEnabled,
            detents = detents,
        )
    }

    val rubberBand = remember(density) {
        SheetRubberBand(
            density = density,
            reportTopEdge = { !detents.hasLarge && entry.gesturesEnabled },
            reportBottomEdge = { dismissLocked },
            onDismissAttempt = { entry.onDismissAttempt?.invoke() },
            onExpandAttempt = { entry.onExpandAttempt?.invoke() },
        )
    }

    val anchorsState = remember { mutableStateOf<SheetAnchors?>(null) }

    presentation.state = state
    presentation.anchors = anchorsState.value

    val interactive = state.settledValue != Detent.Hidden || state.targetValue != Detent.Hidden
    val settledAndStill = state.settledValue != Detent.Hidden && !state.isAnimationRunning

    val sheetScope = remember(state, detents) {
        SheetScopeImpl(
            state = state,
            detents = detents,
            scope = scope,
            motionProvider = { motion },
        )
    }

    val nestedScroll = remember(state, rubberBand, flingBehavior) {
        SheetNestedScrollConnection(
            state = state,
            rubberBand = rubberBand,
            flingBehavior = flingBehavior,
            anchorsProvider = { anchorsState.value },
            enabled = { entry.gesturesEnabled },
            scope = scope,
        )
    }

    val panelFocus = remember { FocusRequester() }

    // Scrim — deliberately without semantics. Modifier.clickable would put a full-screen,
    // unlabelled node into the accessibility tree.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val current = anchorsState.value?.progressAt(state.offset) ?: 0f
                alpha = current * colors.scrimMaxAlpha
            }
            .background(colors.scrim)
            .then(
                if (settledAndStill) {
                    Modifier.pointerInput(entry.gesturesEnabled, entry.interactiveDismissEnabled) {
                        detectTapGestures {
                            when (outcomeFor(Gesture.ScrimTap)) {
                                GestureOutcome.Dismiss -> entry.onDismissRequest()
                                GestureOutcome.ConsumeAndReportDismissAttempt ->
                                    entry.onDismissAttempt?.invoke()

                                else -> Unit
                            }
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
            .offset { IntOffset(0, sheetOffsetPx(state, rubberBand)) }
            .clip(shape)
            .background(colors.sheet)
            .focusRequester(panelFocus)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            // focusTarget instead of focusable: focusable() reports focusability to
            // accessibility, and a container should not do that.
            .focusTarget()
            .semantics {
                isTraversalGroup = true

                entry.paneTitle?.let { paneTitle = it }

                if (!detents.isSingle) {
                    val atLarge = state.settledValue == Detent.Large
                    val name = if (atLarge) detentNames.large else detentNames.medium
                    name?.let { stateDescription = it }

                    // Only ever the possible direction, never both.
                    if (atLarge) {
                        collapse {
                            sheetScope.animateTo(PresentationDetent.Medium)
                            true
                        }
                    } else {
                        expand {
                            sheetScope.animateTo(PresentationDetent.Large)
                            true
                        }
                    }
                }

                // Strictly per the gesture table: no permitted user dismiss means no action.
                if (entry.gesturesEnabled && entry.interactiveDismissEnabled) {
                    dismiss {
                        entry.onDismissRequest()
                        true
                    }
                }
            }
            .nestedScroll(nestedScroll)
            .anchoredDraggable(
                state = state,
                reverseDirection = false,
                orientation = Orientation.Vertical,
                enabled = entry.gesturesEnabled && interactive,
                interactionSource = null,
                overscrollEffect = rubberBand,
                flingBehavior = flingBehavior,
            )
            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        Column(
            Modifier.sheetLayout(
                state = state,
                anchorsState = anchorsState,
                topInset = topInset,
                detents = detents,
                includeHidden = !(dismissLocked && entry.isPresented),
            ),
        ) {
            // The handle sits as a fixed header above the content slot and does not scroll with
            // it — otherwise a sheet with scrollable content could not be dragged at all.
            val handle = entry.dragHandle.takeIf { entry.gesturesEnabled }
            if (handle != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(detents, state.settledValue) {
                            detectTapGestures {
                                if (outcomeFor(Gesture.HandleTap) == GestureOutcome.CycleDetent) {
                                    val target = cycleTarget(state.settledValue, detents)
                                    scope.launch { state.animateTo(target, motion.animationSpec) }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalDragHandleColor provides colors.handle) {
                        handle()
                    }
                }
            }

            Box(
                Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
            ) {
                with(sheetScope) { entry.content(this) }
            }
        }
    }

    PredictiveBackHandler(enabled = entry.isPresented) { progress ->
        when (outcomeFor(Gesture.PredictiveBack)) {
            GestureOutcome.FollowGesture -> {
                val anchors = anchorsState.value
                val from = anchors?.positionOf(state.settledValue)
                val to = anchors?.hiddenPosition
                try {
                    if (from != null && to != null) {
                        state.anchoredDrag {
                            progress.collect { event ->
                                dragTo(from + (to - from) * event.progress)
                            }
                        }
                    } else {
                        progress.collect { }
                    }
                    entry.onDismissRequest()
                } catch (cancellation: CancellationException) {
                    state.animateTo(state.settledValue, motion.animationSpec)
                    throw cancellation
                }
            }

            GestureOutcome.ConsumeAndReportDismissAttempt -> {
                progress.collect { }
                entry.onDismissAttempt?.invoke()
            }

            else -> progress.collect { }
        }
    }

    LaunchedEffect(entry.gesturesEnabled, entry.dragHandle) {
        if (entry.gesturesEnabled && entry.dragHandle == null) {
            Log.w(
                LogTag,
                "Sheet without a drag handle: if the content scrolls, no reliable drag surface " +
                    "is left.",
            )
        }
    }

    // Focus moves into the panel when the sheet opens and stays there — even when the content
    // holds no focusable element at all.
    LaunchedEffect(entry.isPresented) {
        if (entry.isPresented) {
            while (state.offset.isNaN()) withFrameNanos { }
            runCatching { panelFocus.requestFocus() }
        }
    }

    // A gesture commit is reported at once, not after the exit animation.
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }
            .drop(1)
            .collect { target ->
                if (target == Detent.Hidden && entry.isPresented) entry.onDismissRequest()
            }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { settled ->
            savedDetent = settled.takeIf { it != Detent.Hidden }?.name
        }
    }

    LaunchedEffect(entry.isPresented, detents) {
        if (entry.isPresented) {
            val target = resolveInitialDetent(entry.initialDetent, detents)
            while (anchorsState.value?.contains(target) != true) withFrameNanos { }
            // After a restore the sheet already sits correctly — then it snaps rather than
            // animates: another enter animation would claim a presentation that happened long
            // ago.
            if (state.settledValue == Detent.Hidden) {
                state.animateTo(target, motion.animationSpec)
            }
        } else {
            while (anchorsState.value?.contains(Detent.Hidden) != true) withFrameNanos { }
            state.animateTo(Detent.Hidden, motion.animationSpec)
            onFullyHidden()
        }
    }
}

private fun detentOf(name: String): Detent? = Detent.entries.firstOrNull { it.name == name }

private fun sheetOffsetPx(state: AnchoredDraggableState<Detent>, rubberBand: SheetRubberBand): Int {
    val base = state.offset
    if (base.isNaN()) return Int.MAX_VALUE / 2
    return (base + rubberBand.offsetPx).roundToInt()
}

/**
 * Measures the content and sets the anchors in the **same** layout pass — the documented way for
 * size-dependent anchors. No SubcomposeLayout, no flickering frame.
 */
private fun Modifier.sheetLayout(
    state: AnchoredDraggableState<Detent>,
    anchorsState: androidx.compose.runtime.MutableState<SheetAnchors?>,
    topInset: Int,
    detents: dev.sircedric.bottomsheet.SheetDetents,
    includeHidden: Boolean,
): Modifier = layout { measurable, constraints ->
    val containerHeight = constraints.maxHeight
    val panelHeight = (containerHeight - topInset).coerceAtLeast(0)

    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = panelHeight),
    )

    val anchors = computeAnchors(
        containerHeight = containerHeight,
        topInset = topInset,
        contentHeight = placeable.height,
        detents = detents,
        includeHidden = includeHidden,
    )

    if (anchorsState.value != anchors) anchorsState.value = anchors

    state.updateAnchors(
        DraggableAnchors {
            anchors.hidden?.let { Detent.Hidden at it }
            anchors.medium?.let { Detent.Medium at it }
            anchors.large?.let { Detent.Large at it }
        },
    )

    layout(placeable.width, anchors.panelHeight) { placeable.place(0, 0) }
}

private class SheetScopeImpl(
    private val state: AnchoredDraggableState<Detent>,
    private val detents: dev.sircedric.bottomsheet.SheetDetents,
    private val scope: CoroutineScope,
    private val motionProvider: () -> BottomSheetMotion,
) : BottomSheetScope {

    private var lastVisible: PresentationDetent =
        resolveInitialDetent(PresentationDetent.Medium, detents).toPresentationDetent()
            ?: PresentationDetent.Medium

    override val currentDetent: PresentationDetent
        get() {
            val settled = state.settledValue.toPresentationDetent()
            if (settled != null) lastVisible = settled
            return lastVisible
        }

    override fun animateTo(detent: PresentationDetent) {
        val target = detent.toDetent()
        if (state.anchors.positionOf(target).isNaN()) return
        scope.launch { state.animateTo(target, motionProvider().animationSpec) }
    }
}

/**
 * The interlocking with scrollable content: upwards the sheet wins before the content, downwards
 * only at the top of the list.
 */
private class SheetNestedScrollConnection(
    private val state: AnchoredDraggableState<Detent>,
    private val rubberBand: SheetRubberBand,
    private val flingBehavior: FlingBehavior,
    private val anchorsProvider: () -> SheetAnchors?,
    private val enabled: () -> Boolean,
    private val scope: CoroutineScope,
) : NestedScrollConnection {

    override fun onPreScroll(
        available: androidx.compose.ui.geometry.Offset,
        source: NestedScrollSource,
    ): androidx.compose.ui.geometry.Offset {
        if (!usable(source)) return androidx.compose.ui.geometry.Offset.Zero
        val delta = available.y
        if (!NestedScrollRules.sheetConsumesPreScroll(delta, canMoveUp())) {
            return androidx.compose.ui.geometry.Offset.Zero
        }
        return androidx.compose.ui.geometry.Offset(0f, consume(delta))
    }

    override fun onPostScroll(
        consumed: androidx.compose.ui.geometry.Offset,
        available: androidx.compose.ui.geometry.Offset,
        source: NestedScrollSource,
    ): androidx.compose.ui.geometry.Offset {
        if (!usable(source)) return androidx.compose.ui.geometry.Offset.Zero
        val delta = available.y
        if (!NestedScrollRules.sheetConsumesPostScroll(delta, canMoveDown())) {
            return androidx.compose.ui.geometry.Offset.Zero
        }
        return androidx.compose.ui.geometry.Offset(0f, consume(delta))
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!enabled()) return Velocity.Zero
        rubberBand.release()
        if (state.offset.isNaN()) return Velocity.Zero
        // The return value follows the contract: consumed is `available - remaining`.
        val remaining = flingToNearestAnchor(available.y)
        return Velocity(0f, available.y - remaining)
    }

    /**
     * A FlingBehavior does not run against an AnchoredDraggableState directly — it needs an
     * adapter from ScrollScope to AnchoredDragScope.
     */
    private suspend fun flingToNearestAnchor(velocity: Float): Float {
        var remaining = velocity
        state.anchoredDrag {
            val scrollScope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val before = state.requireOffset()
                    dragTo(before + pixels)
                    return state.requireOffset() - before
                }
            }
            remaining = with(flingBehavior) { scrollScope.performFling(velocity) }
        }
        return remaining
    }

    private fun usable(source: NestedScrollSource): Boolean =
        enabled() && source == NestedScrollSource.UserInput && !state.offset.isNaN()

    private fun canMoveUp(): Boolean {
        val anchors = anchorsProvider() ?: return false
        return state.offset > anchors.topMostPosition + 0.5f
    }

    private fun canMoveDown(): Boolean {
        val anchors = anchorsProvider() ?: return false
        return state.offset < anchors.hiddenPosition - 0.5f
    }

    /**
     * Moves the sheet and hands the remainder to the rubber band instead of returning it to the
     * content — otherwise the content keeps scrolling at a locked edge while the user is visibly
     * dragging the sheet.
     */
    private fun consume(delta: Float): Float {
        // A running animation is taken over rather than waited out: the new anchoredDrag seizes
        // the MutatorMutex. This one frame is dropped.
        if (state.isAnimationRunning) {
            scope.launch { state.anchoredDrag { } }
            return delta
        }

        val anchors = anchorsProvider() ?: return 0f
        val min = anchors.topMostPosition
        val max = anchors.hidden ?: anchors.medium ?: anchors.hiddenPosition
        val consumable = NestedScrollRules.consumableBySheet(delta, state.offset, min, max)

        if (consumable != 0f) state.dispatchRawDelta(consumable)

        val leftover = delta - consumable
        if (leftover != 0f) rubberBand.pull(leftover)

        return delta
    }
}
