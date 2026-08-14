package dev.sircedric.bottomsheet

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.sircedric.bottomsheet.internal.Detent
import dev.sircedric.bottomsheet.internal.computeAnchors
import dev.sircedric.bottomsheet.internal.resolveInitialDetent
import dev.sircedric.bottomsheet.internal.resolveRestoredDetent
import org.junit.jupiter.api.Test

private const val Container = 2400
private const val TopInset = 121
private const val PanelHeight = Container - TopInset

class AnchorsTest {

    @Test
    fun `medium sits at the content height while the content stays below large`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = 800,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = true,
        )

        assertThat(anchors.panelHeight).isEqualTo(PanelHeight)
        assertThat(anchors.hidden).isEqualTo(Container.toFloat())
        assertThat(anchors.medium).isEqualTo((Container - 800).toFloat())
        assertThat(anchors.large).isEqualTo(TopInset.toFloat())
    }

    @Test
    fun `medium does not drop out for tall content but sits at half of large`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = PanelHeight,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = true,
        )

        // Measured in #9: container 2400, topInset 121, so medium lands at 1260 px.
        assertThat(anchors.medium).isEqualTo(1260f)
    }

    @Test
    fun `content of zero height puts medium at the bottom edge`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = 0,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = true,
        )

        assertThat(anchors.medium).isEqualTo(Container.toFloat())
    }

    @Test
    fun `without a topInset large reaches the top edge`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = 0,
            contentHeight = 500,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = true,
        )

        assertThat(anchors.large).isEqualTo(0f)
        assertThat(anchors.panelHeight).isEqualTo(Container)
    }

    @Test
    fun `a very large topInset never makes the panel height negative`() {
        val anchors = computeAnchors(
            containerHeight = 400,
            topInset = 900,
            contentHeight = 100,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = true,
        )

        assertThat(anchors.panelHeight).isEqualTo(0)
    }

    @Test
    fun `allowing only medium leaves out the large anchor`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = 600,
            detents = SheetDetents.Medium,
            includeHidden = true,
        )

        assertThat(anchors.medium).isNotNull()
        assertThat(anchors.large).isNull()
        assertThat(anchors.topMost).isEqualTo(Detent.Medium)
    }

    @Test
    fun `allowing only large leaves out the medium anchor`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = 600,
            detents = SheetDetents.Large,
            includeHidden = true,
        )

        assertThat(anchors.medium).isNull()
        assertThat(anchors.large).isNotNull()
    }

    @Test
    fun `a locked dismiss removes the hidden anchor but keeps the hidden position`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = 600,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = false,
        )

        assertThat(anchors.hidden).isNull()
        assertThat(anchors.hiddenPosition).isEqualTo(Container.toFloat())
        assertThat(anchors.bottomMost).isEqualTo(Detent.Medium)
    }

    @Test
    fun `progress is measured against the hidden position even without the anchor`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = 600,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = false,
        )

        assertThat(anchors.progressAt(Container.toFloat())).isEqualTo(0f)
        assertThat(anchors.progressAt(TopInset.toFloat())).isEqualTo(1f)
    }

    @Test
    fun `initialDetent falls back to the smallest contained detent`() {
        assertThat(resolveInitialDetent(PresentationDetent.Medium, SheetDetents.Large))
            .isEqualTo(Detent.Large)
        assertThat(resolveInitialDetent(PresentationDetent.Large, SheetDetents.Medium))
            .isEqualTo(Detent.Medium)
        assertThat(resolveInitialDetent(PresentationDetent.Large, SheetDetents.MediumAndLarge))
            .isEqualTo(Detent.Large)
    }

    @Test
    fun `a restored detent only applies while it is still allowed`() {
        assertThat(
            resolveRestoredDetent(Detent.Large, PresentationDetent.Medium, SheetDetents.MediumAndLarge),
        ).isEqualTo(Detent.Large)

        assertThat(
            resolveRestoredDetent(Detent.Large, PresentationDetent.Medium, SheetDetents.Medium),
        ).isEqualTo(Detent.Medium)

        assertThat(
            resolveRestoredDetent(null, PresentationDetent.Large, SheetDetents.MediumAndLarge),
        ).isEqualTo(Detent.Large)
    }
}
