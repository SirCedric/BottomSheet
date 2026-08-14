package dev.sircedric.bottomsheet

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.sircedric.bottomsheet.internal.Detent
import dev.sircedric.bottomsheet.internal.Gesture
import dev.sircedric.bottomsheet.internal.GestureOutcome
import dev.sircedric.bottomsheet.internal.cycleTarget
import dev.sircedric.bottomsheet.internal.resolveGesture
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * The decision table from section 5 of the spec, cell by cell.
 *
 * Column A = both switches on, B = dismiss locked, C = gestures off.
 */
internal class GesturePolicyTest {

    @ParameterizedTest(name = "{0} in column A yields {1}")
    @MethodSource("columnA")
    fun `column A`(gesture: Gesture, expected: GestureOutcome) {
        assertThat(
            resolveGesture(
                gesture = gesture,
                gesturesEnabled = true,
                interactiveDismissEnabled = true,
                detents = SheetDetents.MediumAndLarge,
            ),
        ).isEqualTo(expected)
    }

    @ParameterizedTest(name = "{0} in column B yields {1}")
    @MethodSource("columnB")
    fun `column B`(gesture: Gesture, expected: GestureOutcome) {
        assertThat(
            resolveGesture(
                gesture = gesture,
                gesturesEnabled = true,
                interactiveDismissEnabled = false,
                detents = SheetDetents.MediumAndLarge,
            ),
        ).isEqualTo(expected)
    }

    @ParameterizedTest(name = "{0} in column C yields {1}")
    @MethodSource("columnC")
    fun `column C`(gesture: Gesture, expected: GestureOutcome) {
        assertThat(
            resolveGesture(
                gesture = gesture,
                gesturesEnabled = false,
                interactiveDismissEnabled = true,
                detents = SheetDetents.MediumAndLarge,
            ),
        ).isEqualTo(expected)
    }

    @Test
    fun `the upper edge only reports when large is not allowed at all`() {
        assertThat(
            resolveGesture(
                gesture = Gesture.DragAboveHighestDetent,
                gesturesEnabled = true,
                interactiveDismissEnabled = true,
                detents = SheetDetents.Medium,
            ),
        ).isEqualTo(GestureOutcome.ResistAndReportExpandAttempt)

        assertThat(
            resolveGesture(
                gesture = Gesture.DragAboveHighestDetent,
                gesturesEnabled = true,
                interactiveDismissEnabled = true,
                detents = SheetDetents.MediumAndLarge,
            ),
        ).isEqualTo(GestureOutcome.ResistSilently)
    }

    @Test
    fun `a single detent makes the handle tap a no-op`() {
        assertThat(
            resolveGesture(
                gesture = Gesture.HandleTap,
                gesturesEnabled = true,
                interactiveDismissEnabled = true,
                detents = SheetDetents.Medium,
            ),
        ).isEqualTo(GestureOutcome.Ignored)
    }

    @Test
    fun `the handle tap switches between the two detents`() {
        assertThat(cycleTarget(Detent.Medium, SheetDetents.MediumAndLarge)).isEqualTo(Detent.Large)
        assertThat(cycleTarget(Detent.Large, SheetDetents.MediumAndLarge)).isEqualTo(Detent.Medium)
    }

    companion object {

        @JvmStatic
        fun columnA(): List<Arguments> = listOf(
            Arguments.of(Gesture.DragBetweenDetents, GestureOutcome.SnapToNearestDetent),
            Arguments.of(Gesture.DragBelowLowestDetent, GestureOutcome.Dismiss),
            Arguments.of(Gesture.DragAboveHighestDetent, GestureOutcome.ResistSilently),
            Arguments.of(Gesture.FlingDown, GestureOutcome.Dismiss),
            Arguments.of(Gesture.ScrimTap, GestureOutcome.Dismiss),
            Arguments.of(Gesture.Back, GestureOutcome.Dismiss),
            Arguments.of(Gesture.PredictiveBack, GestureOutcome.FollowGesture),
            Arguments.of(Gesture.HandleTap, GestureOutcome.CycleDetent),
            Arguments.of(Gesture.DragDuringEnterAnimation, GestureOutcome.Grab),
            Arguments.of(Gesture.DragDuringExitAnimation, GestureOutcome.Ignored),
            Arguments.of(Gesture.ScrimTapDuringDrag, GestureOutcome.Ignored),
        )

        @JvmStatic
        fun columnB(): List<Arguments> = listOf(
            Arguments.of(Gesture.DragBetweenDetents, GestureOutcome.SnapToNearestDetent),
            Arguments.of(
                Gesture.DragBelowLowestDetent,
                GestureOutcome.ResistAndReportDismissAttempt,
            ),
            Arguments.of(Gesture.FlingDown, GestureOutcome.ResistAndReportDismissAttempt),
            Arguments.of(Gesture.ScrimTap, GestureOutcome.ConsumeAndReportDismissAttempt),
            Arguments.of(Gesture.Back, GestureOutcome.ConsumeAndReportDismissAttempt),
            Arguments.of(
                Gesture.PredictiveBack,
                GestureOutcome.ConsumeAndReportDismissAttempt,
            ),
            Arguments.of(Gesture.HandleTap, GestureOutcome.CycleDetent),
            Arguments.of(Gesture.DragDuringEnterAnimation, GestureOutcome.Grab),
            Arguments.of(Gesture.DragDuringExitAnimation, GestureOutcome.Ignored),
        )

        @JvmStatic
        fun columnC(): List<Arguments> = listOf(
            Arguments.of(Gesture.DragBetweenDetents, GestureOutcome.Ignored),
            Arguments.of(Gesture.DragBelowLowestDetent, GestureOutcome.Ignored),
            Arguments.of(Gesture.DragAboveHighestDetent, GestureOutcome.Ignored),
            Arguments.of(Gesture.FlingDown, GestureOutcome.Ignored),
            Arguments.of(Gesture.HandleTap, GestureOutcome.Ignored),
            Arguments.of(Gesture.DragDuringEnterAnimation, GestureOutcome.Ignored),
            Arguments.of(Gesture.DragDuringExitAnimation, GestureOutcome.Ignored),
            // Back is swallowed so the app does not navigate away behind the sheet. It is not
            // reported, because without interaction there is no attempt.
            Arguments.of(Gesture.Back, GestureOutcome.ConsumeSilently),
            Arguments.of(Gesture.PredictiveBack, GestureOutcome.ConsumeSilently),
            Arguments.of(Gesture.ScrimTap, GestureOutcome.ConsumeSilently),
        )
    }
}
