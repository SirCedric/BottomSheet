package dev.sircedric.bottomsheet.internal

/**
 * The interlocking from section 7 of the spec, as pure functions.
 *
 * Signs follow Compose: a drag **up** yields a negative `y`, a drag **down** a positive one. The
 * rules are purely delta-based — no case asks for `firstVisibleItemIndex`, so `reverseLayout`
 * and nested scrollers are correct automatically.
 */
internal object NestedScrollRules {

    /**
     * Upwards the sheet wins **before** the content: from `medium` it expands to `large` first,
     * and only then does the content scroll. Positive deltas are never touched in this phase.
     */
    fun sheetConsumesPreScroll(deltaY: Float, sheetCanMoveUp: Boolean): Boolean =
        deltaY < 0f && sheetCanMoveUp

    /**
     * Downwards the content scrolls first; whatever is left at the top of the list goes to the
     * sheet.
     */
    fun sheetConsumesPostScroll(deltaY: Float, sheetCanMoveDown: Boolean): Boolean =
        deltaY > 0f && sheetCanMoveDown

    /**
     * The share of a delta the sheet can take before it hits an anchor. The remainder goes to the
     * rubber band rather than back to the content — otherwise the content would keep scrolling at
     * a locked edge while the user is visibly dragging the sheet.
     */
    fun consumableBySheet(deltaY: Float, offset: Float, minPosition: Float, maxPosition: Float): Float {
        val target = (offset + deltaY).coerceIn(minPosition, maxPosition)
        return target - offset
    }
}
