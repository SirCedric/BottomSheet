package dev.sircedric.bottomsheet.internal

import dev.sircedric.bottomsheet.SheetDetents

/**
 * Die Nutzergesten aus der Entscheidungstabelle in Abschnitt 5 der Spec.
 *
 * Bewusst frei von Compose-Typen: die Tabelle ist der dichteste Verhaltensanspruch der Library
 * und wird als Parameter-Matrix auf der JVM geprüft, nicht Zelle für Zelle per UI-Test.
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

    /** Keine Reaktion — das Sheet nimmt die Geste nicht an. */
    Ignored,

    /** Das Sheet rastet nach den Schwellen auf den nächsten Anchor. */
    SnapToNearestDetent,

    /** Das Sheet schließt; die Library meldet das über `onDismissRequest`. */
    Dismiss,

    /** Gedämpfter Überzug an einer gesperrten Kante, plus `onDismissAttempt`. */
    ResistAndReportDismissAttempt,

    /** Gedämpfter Überzug an einer gesperrten oberen Kante, plus `onExpandAttempt`. */
    ResistAndReportExpandAttempt,

    /** Gedämpfter Überzug ohne Meldung — die Kante ist das Ende, aber nichts ist gesperrt. */
    ResistSilently,

    /** Die Geste wird verbraucht und als Versuch gemeldet. */
    ConsumeAndReportDismissAttempt,

    /** Die Geste wird verbraucht, ohne dass die App davon erfährt. */
    ConsumeSilently,

    /** Das Sheet folgt der laufenden Geste (Predictive Back). */
    FollowGesture,

    /** Wechsel zwischen den beiden Detents. */
    CycleDetent,

    /** Die laufende Animation ist greifbar, das Sheet folgt dem Finger. */
    Grab,
}

/**
 * Die Entscheidungstabelle Geste × Konfiguration als reine Funktion.
 *
 * [gesturesEnabled] gattert [interactiveDismissEnabled]: ist Ersteres `false`, ist Letzteres
 * bedeutungslos. `gesturesEnabled = false` meldet **keinen** Versuch — wo keine Interaktion
 * vorgesehen ist, gibt es keinen Versuch.
 */
internal fun resolveGesture(
    gesture: Gesture,
    gesturesEnabled: Boolean,
    interactiveDismissEnabled: Boolean,
    detents: SheetDetents,
): GestureOutcome {
    if (!gesturesEnabled) {
        return when (gesture) {
            // Back wird in jeder Konfiguration geschluckt, sonst navigiert die App hinter dem
            // offenen Sheet weg. Gemeldet wird er hier trotzdem nicht.
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

        // Oben federt es immer — gemeldet wird nur, wenn `large` gesperrt ist. Ein Zug über ein
        // erreichtes `large` verlangt nichts, was ihm verweigert würde.
        Gesture.DragAboveHighestDetent ->
            if (detents.hasLarge) GestureOutcome.ResistSilently
            else GestureOutcome.ResistAndReportExpandAttempt

        Gesture.ScrimTap ->
            if (mayDismiss) GestureOutcome.Dismiss
            else GestureOutcome.ConsumeAndReportDismissAttempt

        Gesture.Back ->
            if (mayDismiss) GestureOutcome.Dismiss
            else GestureOutcome.ConsumeAndReportDismissAttempt

        // Keine Vorschau, wenn die Geste nie committen kann.
        Gesture.PredictiveBack ->
            if (mayDismiss) GestureOutcome.FollowGesture
            else GestureOutcome.ConsumeAndReportDismissAttempt

        Gesture.HandleTap ->
            if (detents.isSingle) GestureOutcome.Ignored else GestureOutcome.CycleDetent

        Gesture.DragDuringEnterAnimation -> GestureOutcome.Grab

        // Beim Ausblenden steht `isPresented` bereits auf false; ein Zurückfangen widerspräche
        // dem Zustand der App.
        Gesture.DragDuringExitAnimation -> GestureOutcome.Ignored

        Gesture.ScrimTapDuringDrag -> GestureOutcome.Ignored
    }
}

/**
 * Der Detent, auf den ein Handle-Tap wechselt.
 *
 * Nur sinnvoll, wenn [resolveGesture] für [Gesture.HandleTap] auch [GestureOutcome.CycleDetent]
 * liefert.
 */
internal fun cycleTarget(currentDetent: Detent, detents: SheetDetents): Detent = when {
    currentDetent == Detent.Large && detents.hasMedium -> Detent.Medium
    detents.hasLarge -> Detent.Large
    else -> Detent.Medium
}
