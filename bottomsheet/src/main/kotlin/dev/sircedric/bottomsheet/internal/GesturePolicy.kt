package dev.sircedric.bottomsheet.internal

import dev.sircedric.bottomsheet.SheetDetents

/**
 * The user gestures from the decision table in section 5 of the spec.
 *
 * Deliberately free of Compose types: the table is the densest behavioural claim the library
 * makes, and it is checked as a parameter matrix on the JVM rather than cell by cell via UI test.
 */
internal enum class Gesture {
    DragBetweenDetents,
    DragBelowLowestDetent,
    DragAboveHighestDetent,
    FlingDown,
    ScrimTap,
    Back,
    PredictiveBack,
    HandleTap,
    DragDuringEnterAnimation,
    DragDuringExitAnimation,
    ScrimTapDuringDrag,
}

internal enum class GestureOutcome {

    /** No reaction — the sheet does not accept the gesture. */
    Ignored,

    /** The sheet settles on the nearest anchor once the thresholds are met. */
    SnapToNearestDetent,

    /** The sheet closes; the library reports it through `onDismissRequest`. */
    Dismiss,

    /** Damped overdrag at a locked edge, plus `onDismissAttempt`. */
    ResistAndReportDismissAttempt,

    /** Damped overdrag at a locked upper edge, plus `onExpandAttempt`. */
    ResistAndReportExpandAttempt,

    /** Damped overdrag without a report — the edge is the end, but nothing is locked. */
    ResistSilently,

    /** The gesture is consumed and reported as an attempt. */
    ConsumeAndReportDismissAttempt,

    /** The gesture is consumed without the app hearing about it. */
    ConsumeSilently,

    /** The sheet follows the ongoing gesture (predictive back). */
    FollowGesture,

    /** Switch between the two detents. */
    CycleDetent,

    /** The running animation is grabbable; the sheet follows the finger. */
    Grab,
}

/**
 * The gesture × configuration decision table as a pure function.
 *
 * [gesturesEnabled] gates [interactiveDismissEnabled]: if the former is `false`, the latter is
 * meaningless. `gesturesEnabled = false` reports **no** attempt — where no interaction is
 * intended, there is no attempt.
 */
internal fun resolveGesture(
    gesture: Gesture,
    gesturesEnabled: Boolean,
    interactiveDismissEnabled: Boolean,
    detents: SheetDetents,
): GestureOutcome {
    if (!gesturesEnabled) {
        return when (gesture) {
            // Back is swallowed in every configuration, otherwise the app navigates away behind
            // the open sheet. It is still not reported here.
            Gesture.Back, Gesture.PredictiveBack -> GestureOutcome.ConsumeSilently
            Gesture.ScrimTap -> GestureOutcome.ConsumeSilently
            else -> GestureOutcome.Ignored
        }
    }

    val mayDismiss = interactiveDismissEnabled

    return when (gesture) {
        Gesture.DragBetweenDetents -> GestureOutcome.SnapToNearestDetent

        Gesture.DragBelowLowestDetent, Gesture.FlingDown ->
            if (mayDismiss) GestureOutcome.Dismiss
            else GestureOutcome.ResistAndReportDismissAttempt

        // The upper edge always springs — it only reports when `large` is locked. A drag past an
        // already reached `large` asks for nothing that is being denied.
        Gesture.DragAboveHighestDetent ->
            if (detents.hasLarge) GestureOutcome.ResistSilently
            else GestureOutcome.ResistAndReportExpandAttempt

        Gesture.ScrimTap ->
            if (mayDismiss) GestureOutcome.Dismiss
            else GestureOutcome.ConsumeAndReportDismissAttempt

        Gesture.Back ->
            if (mayDismiss) GestureOutcome.Dismiss
            else GestureOutcome.ConsumeAndReportDismissAttempt

        // No preview when the gesture can never commit.
        Gesture.PredictiveBack ->
            if (mayDismiss) GestureOutcome.FollowGesture
            else GestureOutcome.ConsumeAndReportDismissAttempt

        Gesture.HandleTap ->
            if (detents.isSingle) GestureOutcome.Ignored else GestureOutcome.CycleDetent

        Gesture.DragDuringEnterAnimation -> GestureOutcome.Grab

        // While animating out, `isPresented` is already false; catching the sheet again would
        // contradict the app's state.
        Gesture.DragDuringExitAnimation -> GestureOutcome.Ignored

        Gesture.ScrimTapDuringDrag -> GestureOutcome.Ignored
    }
}

/**
 * The detent a handle tap switches to.
 *
 * Only meaningful when [resolveGesture] returns [GestureOutcome.CycleDetent] for
 * [Gesture.HandleTap].
 */
internal fun cycleTarget(currentDetent: Detent, detents: SheetDetents): Detent = when {
    currentDetent == Detent.Large && detents.hasMedium -> Detent.Medium
    detents.hasLarge -> Detent.Large
    else -> Detent.Medium
}
