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
    fun `medium liegt auf der Content-Hoehe, solange der Content unter large bleibt`() {
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
    fun `medium faellt bei zu hohem Content nicht weg, sondern liegt bei der Haelfte von large`() {
        val anchors = computeAnchors(
            containerHeight = Container,
            topInset = TopInset,
            contentHeight = PanelHeight,
            detents = SheetDetents.MediumAndLarge,
            includeHidden = true,
        )

        // Gemessen in #9: container 2400, topInset 121 ⇒ medium liegt bei 1260 px.
        assertThat(anchors.medium).isEqualTo(1260f)
    }

    @Test
    fun `Content von null Hoehe legt medium auf den unteren Rand`() {
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
    fun `ohne topInset reicht large bis an den oberen Rand`() {
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
    fun `ein sehr grosser topInset laesst die Panel-Hoehe nicht negativ werden`() {
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
    fun `nur medium erlaubt laesst den large-Anchor weg`() {
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
    fun `nur large erlaubt laesst den medium-Anchor weg`() {
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
    fun `gesperrtes Dismiss nimmt den Hidden-Anchor heraus, die Hidden-Position bleibt`() {
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
    fun `der Fortschritt misst sich an der Hidden-Position, auch wenn der Anchor fehlt`() {
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
    fun `initialDetent faellt auf den kleinsten enthaltenen zurueck`() {
        assertThat(resolveInitialDetent(PresentationDetent.Medium, SheetDetents.Large))
            .isEqualTo(Detent.Large)
        assertThat(resolveInitialDetent(PresentationDetent.Large, SheetDetents.Medium))
            .isEqualTo(Detent.Medium)
        assertThat(resolveInitialDetent(PresentationDetent.Large, SheetDetents.MediumAndLarge))
            .isEqualTo(Detent.Large)
    }

    @Test
    fun `ein geretteter Detent gilt nur, wenn er noch erlaubt ist`() {
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
