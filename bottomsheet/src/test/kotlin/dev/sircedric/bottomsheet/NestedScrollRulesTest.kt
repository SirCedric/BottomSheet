package dev.sircedric.bottomsheet

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.sircedric.bottomsheet.internal.NestedScrollRules
import org.junit.jupiter.api.Test

/**
 * The rule table from section 7 of the spec. Signs follow Compose: a drag up is negative, a drag
 * down positive.
 */
class NestedScrollRulesTest {

    @Test
    fun `upwards the sheet wins before the content while it can still move up`() {
        assertThat(NestedScrollRules.sheetConsumesPreScroll(-40f, sheetCanMoveUp = true)).isTrue()
    }

    @Test
    fun `upwards at large the sheet consumes nothing`() {
        assertThat(NestedScrollRules.sheetConsumesPreScroll(-40f, sheetCanMoveUp = false)).isFalse()
    }

    @Test
    fun `downwards the pre phase touches nothing`() {
        assertThat(NestedScrollRules.sheetConsumesPreScroll(40f, sheetCanMoveUp = true)).isFalse()
    }

    @Test
    fun `downwards the sheet only takes what the content leaves`() {
        assertThat(NestedScrollRules.sheetConsumesPostScroll(40f, sheetCanMoveDown = true)).isTrue()
    }

    @Test
    fun `downwards at the lowest edge the sheet consumes nothing`() {
        assertThat(NestedScrollRules.sheetConsumesPostScroll(40f, sheetCanMoveDown = false))
            .isFalse()
    }

    @Test
    fun `upwards the post phase touches nothing`() {
        assertThat(NestedScrollRules.sheetConsumesPostScroll(-40f, sheetCanMoveDown = true))
            .isFalse()
    }

    @Test
    fun `the consumable share ends at the anchor`() {
        val consumable = NestedScrollRules.consumableBySheet(
            deltaY = -300f,
            offset = 1200f,
            minPosition = 1000f,
            maxPosition = 2400f,
        )

        assertThat(consumable).isEqualTo(-200f)
    }

    @Test
    fun `at the anchor nothing is consumable — the rest belongs to the rubber band`() {
        val consumable = NestedScrollRules.consumableBySheet(
            deltaY = -120f,
            offset = 1000f,
            minPosition = 1000f,
            maxPosition = 2400f,
        )

        assertThat(consumable).isEqualTo(0f)
    }
}
