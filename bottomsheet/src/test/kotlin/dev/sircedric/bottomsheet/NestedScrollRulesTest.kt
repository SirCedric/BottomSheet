package dev.sircedric.bottomsheet

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.sircedric.bottomsheet.internal.NestedScrollRules
import org.junit.jupiter.api.Test

/**
 * Die Regeltabelle aus Abschnitt 7 der Spec. Vorzeichen wie in Compose: Zug nach oben ist
 * negativ, Zug nach unten positiv.
 */
class NestedScrollRulesTest {

    @Test
    fun `aufwaerts gewinnt das Sheet vor dem Content, solange es hoch kann`() {
        assertThat(NestedScrollRules.sheetConsumesPreScroll(-40f, sheetCanMoveUp = true)).isTrue()
    }

    @Test
    fun `aufwaerts auf large konsumiert das Sheet nichts mehr`() {
        assertThat(NestedScrollRules.sheetConsumesPreScroll(-40f, sheetCanMoveUp = false)).isFalse()
    }

    @Test
    fun `abwaerts fasst die Pre-Phase nichts an`() {
        assertThat(NestedScrollRules.sheetConsumesPreScroll(40f, sheetCanMoveUp = true)).isFalse()
    }

    @Test
    fun `abwaerts nimmt das Sheet erst, was der Content uebrig laesst`() {
        assertThat(NestedScrollRules.sheetConsumesPostScroll(40f, sheetCanMoveDown = true)).isTrue()
    }

    @Test
    fun `abwaerts an der untersten Kante konsumiert das Sheet nichts`() {
        assertThat(NestedScrollRules.sheetConsumesPostScroll(40f, sheetCanMoveDown = false))
            .isFalse()
    }

    @Test
    fun `aufwaerts fasst die Post-Phase nichts an`() {
        assertThat(NestedScrollRules.sheetConsumesPostScroll(-40f, sheetCanMoveDown = true))
            .isFalse()
    }

    @Test
    fun `der konsumierbare Anteil endet am Anchor`() {
        val consumable = NestedScrollRules.consumableBySheet(
            deltaY = -300f,
            offset = 1200f,
            minPosition = 1000f,
            maxPosition = 2400f,
        )

        assertThat(consumable).isEqualTo(-200f)
    }

    @Test
    fun `am Anchor bleibt nichts konsumierbar — der Rest gehoert dem Rubber-Band`() {
        val consumable = NestedScrollRules.consumableBySheet(
            deltaY = -120f,
            offset = 1000f,
            minPosition = 1000f,
            maxPosition = 2400f,
        )

        assertThat(consumable).isEqualTo(0f)
    }
}
