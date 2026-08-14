package dev.sircedric.bottomsheet.internal

import dev.sircedric.bottomsheet.PresentationDetent
import dev.sircedric.bottomsheet.SheetDetents
import kotlin.math.roundToInt

/** The internal detent set. Unlike [PresentationDetent] it knows [Hidden]. */
internal enum class Detent { Hidden, Medium, Large }

internal fun PresentationDetent.toDetent(): Detent = when (this) {
    PresentationDetent.Medium -> Detent.Medium
    PresentationDetent.Large -> Detent.Large
}

internal fun Detent.toPresentationDetent(): PresentationDetent? = when (this) {
    Detent.Hidden -> null
    Detent.Medium -> PresentationDetent.Medium
    Detent.Large -> PresentationDetent.Large
}

/**
 * The measured anchor positions in pixels, free of Compose types.
 *
 * Deliberately **no** `DraggableAnchors` in the return type: only that keeps [computeAnchors]
 * testable on the JVM without an Android runtime. The conversion is a one-liner in the layout
 * block.
 */
internal class SheetAnchors(
    val panelHeight: Int,
    /**
     * Where the sheet sits when closed — regardless of whether [hidden] exists as an anchor. The
     * progress driving scrim and app content is measured against this; otherwise the scrim would
     * be transparent whenever dismiss is locked.
     */
    val hiddenPosition: Float,
    val hidden: Float?,
    val medium: Float?,
    val large: Float?,
) {

    /** The position of the topmost reachable detent. */
    val topMostPosition: Float
        get() = large ?: medium ?: hiddenPosition

    /** 0 when closed, 1 at the topmost anchor. */
    fun progressAt(offset: Float): Float {
        if (offset.isNaN()) return 0f
        val span = hiddenPosition - topMostPosition
        if (span <= 0f) return 0f
        return ((hiddenPosition - offset) / span).coerceIn(0f, 1f)
    }

    fun positionOf(detent: Detent): Float? = when (detent) {
        Detent.Hidden -> hidden
        Detent.Medium -> medium
        Detent.Large -> large
    }

    fun contains(detent: Detent): Boolean = positionOf(detent) != null

    /** The topmost reachable detent — the smallest position wins. */
    val topMost: Detent
        get() = when {
            large != null -> Detent.Large
            medium != null -> Detent.Medium
            else -> Detent.Hidden
        }

    /** The lowest reachable detent. */
    val bottomMost: Detent
        get() = when {
            hidden != null -> Detent.Hidden
            medium != null -> Detent.Medium
            else -> Detent.Large
        }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SheetAnchors &&
                panelHeight == other.panelHeight &&
                hiddenPosition == other.hiddenPosition &&
                hidden == other.hidden &&
                medium == other.medium &&
                large == other.large
            )

    override fun hashCode(): Int {
        var result = panelHeight
        result = 31 * result + hiddenPosition.hashCode()
        result = 31 * result + (hidden?.hashCode() ?: 0)
        result = 31 * result + (medium?.hashCode() ?: 0)
        result = 31 * result + (large?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "SheetAnchors(panelHeight=$panelHeight, hiddenPosition=$hiddenPosition, " +
            "hidden=$hidden, medium=$medium, large=$large)"
}

/** Fraction of `large` that `medium` takes up once the content is at least that tall. */
private const val MediumFractionWhenContentOverflows = 0.5f

/**
 * Computes panel height and anchor positions from a measurement.
 *
 * [includeHidden] governs the locked lower edge: without `Hidden` no gesture can close the sheet
 * and a drag below it runs into the rubber band. The anchor returns as soon as the app closes
 * programmatically — otherwise the exit animation would have no target.
 */
internal fun computeAnchors(
    containerHeight: Int,
    topInset: Int,
    contentHeight: Int,
    detents: SheetDetents,
    includeHidden: Boolean,
): SheetAnchors {
    val panelHeight = (containerHeight - topInset).coerceAtLeast(0)

    val mediumHeight = if (contentHeight >= panelHeight) {
        (panelHeight * MediumFractionWhenContentOverflows).roundToInt()
    } else {
        contentHeight.coerceAtLeast(0)
    }

    return SheetAnchors(
        panelHeight = panelHeight,
        hiddenPosition = containerHeight.toFloat(),
        hidden = if (includeHidden) containerHeight.toFloat() else null,
        medium = if (detents.hasMedium) (containerHeight - mediumHeight).toFloat() else null,
        large = if (detents.hasLarge) topInset.toFloat() else null,
    )
}

/**
 * The detent a new presentation starts on.
 *
 * If the requested value is not part of [detents], the smallest contained one applies — here
 * `Medium` before `Large`.
 */
internal fun resolveInitialDetent(
    requested: PresentationDetent,
    detents: SheetDetents,
): Detent = when {
    requested == PresentationDetent.Medium && detents.hasMedium -> Detent.Medium
    requested == PresentationDetent.Large && detents.hasLarge -> Detent.Large
    detents.hasMedium -> Detent.Medium
    else -> Detent.Large
}

/**
 * The detent to snap to after a restore.
 *
 * If the saved value is not among the detents of the restored sheet, [resolveInitialDetent]
 * applies again.
 */
internal fun resolveRestoredDetent(
    restored: Detent?,
    requested: PresentationDetent,
    detents: SheetDetents,
): Detent {
    val candidate = restored?.toPresentationDetent()
    return when {
        candidate == PresentationDetent.Medium && detents.hasMedium -> Detent.Medium
        candidate == PresentationDetent.Large && detents.hasLarge -> Detent.Large
        else -> resolveInitialDetent(requested, detents)
    }
}
