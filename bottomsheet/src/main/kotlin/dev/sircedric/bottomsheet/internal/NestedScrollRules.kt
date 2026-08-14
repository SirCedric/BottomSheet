package dev.sircedric.bottomsheet.internal

/**
 * Die Verzahnung aus Abschnitt 7 der Spec, als reine Funktionen.
 *
 * Vorzeichen wie in Compose: ein Zug nach **oben** liefert negatives `y`, ein Zug nach **unten**
 * positives. Die Regeln sind rein deltabasiert — kein Sonderfall fragt `firstVisibleItemIndex`
 * ab, damit `reverseLayout` und verschachtelte Scroller automatisch stimmen.
 */
internal object NestedScrollRules {

    /**
     * Aufwärts gewinnt das Sheet **vor** dem Content: aus `medium` expandiert es zuerst auf
     * `large`, danach scrollt der Inhalt. Positive Deltas werden in dieser Phase nie angefasst.
     */
    fun sheetConsumesPreScroll(deltaY: Float, sheetCanMoveUp: Boolean): Boolean =
        deltaY < 0f && sheetCanMoveUp

    /**
     * Abwärts scrollt zuerst der Content; was an der Listen-Oberkante übrig bleibt, nimmt das
     * Sheet.
     */
    fun sheetConsumesPostScroll(deltaY: Float, sheetCanMoveDown: Boolean): Boolean =
        deltaY > 0f && sheetCanMoveDown

    /**
     * Der Anteil eines Deltas, den das Sheet aufnehmen kann, bevor es an einen Anchor stößt.
     * Der Rest geht ans Rubber-Band, nicht zurück an den Content — sonst scrollt der Inhalt an
     * einer gesperrten Kante weiter, obwohl der Nutzer das Sheet zieht.
     */
    fun consumableBySheet(deltaY: Float, offset: Float, minPosition: Float, maxPosition: Float): Float {
        val target = (offset + deltaY).coerceIn(minPosition, maxPosition)
        return target - offset
    }
}
