package de.cedrickummer.bottomsheet

import assertk.assertThat
import assertk.assertions.isEqualTo
import de.cedrickummer.bottomsheet.internal.HeaderTitlePlacement
import org.junit.jupiter.api.Test

/** The title placement of the header building block from section 3 of the spec. */
class HeaderTitlePlacementTest {

    @Test
    fun `the title gets everything the side slots leave`() {
        val maxWidth = HeaderTitlePlacement.maxWidth(width = 400, navigationWidth = 60, actionsWidth = 90)

        assertThat(maxWidth).isEqualTo(250)
    }

    @Test
    fun `without room the title width is zero, never negative`() {
        val maxWidth = HeaderTitlePlacement.maxWidth(width = 300, navigationWidth = 200, actionsWidth = 200)

        assertThat(maxWidth).isEqualTo(0)
    }

    @Test
    fun `a short title sits exactly centred on the full width, even with uneven sides`() {
        val x = HeaderTitlePlacement.x(width = 400, navigationWidth = 50, actionsWidth = 120, titleWidth = 100)

        assertThat(x).isEqualTo(150)
    }

    @Test
    fun `wide actions push the title towards the start`() {
        val x = HeaderTitlePlacement.x(width = 400, navigationWidth = 50, actionsWidth = 200, titleWidth = 140)

        assertThat(x).isEqualTo(60)
    }

    @Test
    fun `a wide navigation action pushes the title towards the end`() {
        val x = HeaderTitlePlacement.x(width = 400, navigationWidth = 200, actionsWidth = 50, titleWidth = 140)

        assertThat(x).isEqualTo(200)
    }

    @Test
    fun `without room the title stays behind the navigation action`() {
        val x = HeaderTitlePlacement.x(width = 300, navigationWidth = 200, actionsWidth = 200, titleWidth = 0)

        assertThat(x).isEqualTo(200)
    }
}
