package de.cedrickummer.bottomsheet.internal

/**
 * Where the header building block puts its title, as pure arithmetic — the measure and place
 * policy of `CenterAlignedTopAppBar`, rebuilt without M3.
 *
 * All values are in pixels and relative to the start edge; right-to-left mirrors through
 * `placeRelative`.
 */
internal object HeaderTitlePlacement {

    /**
     * The side slots are measured first and unbounded; the title gets the rest. Wide actions
     * therefore always win, and the title shrinks — down to zero if need be.
     */
    fun maxWidth(width: Int, navigationWidth: Int, actionsWidth: Int): Int =
        (width - navigationWidth - actionsWidth).coerceAtLeast(0)

    /**
     * Centred on the **full** width, not between the side slots, and pushed towards the free side
     * only on a collision. A short title stays visually centred however uneven the sides are.
     */
    fun x(width: Int, navigationWidth: Int, actionsWidth: Int, titleWidth: Int): Int {
        val centred = (width - titleWidth) / 2
        val latestStart = width - actionsWidth - titleWidth
        return centred.coerceAtMost(latestStart).coerceAtLeast(navigationWidth)
    }
}
