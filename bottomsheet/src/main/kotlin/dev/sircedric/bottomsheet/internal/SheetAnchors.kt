package dev.sircedric.bottomsheet.internal

import dev.sircedric.bottomsheet.PresentationDetent
import dev.sircedric.bottomsheet.SheetDetents
import kotlin.math.roundToInt

/** Der interne Detent-Satz. Anders als [PresentationDetent] kennt er [Hidden]. */
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
 * Die gemessenen Anchor-Positionen in Pixeln, ohne Compose-Typen.
 *
 * Bewusst **kein** `DraggableAnchors` im Rückgabetyp: nur so bleibt [computeAnchors] auf der
 * JVM testbar, ohne Android-Laufzeit. Die Umwandlung ist ein Einzeiler im Layout-Block.
 */
internal class SheetAnchors(
    val panelHeight: Int,
    /**
     * Wo das Sheet steht, wenn es geschlossen ist — unabhängig davon, ob [hidden] als Anchor
     * existiert. Der Fortschritt für Scrim und App-Content misst sich hieran, sonst wäre der
     * Scrim bei gesperrtem Dismiss durchsichtig.
     */
    val hiddenPosition: Float,
    val hidden: Float?,
    val medium: Float?,
    val large: Float?,
) {

    /** Die Position des obersten erreichbaren Detents. */
    val topMostPosition: Float
        get() = large ?: medium ?: hiddenPosition

    /** 0 bei geschlossen, 1 am obersten Anchor. */
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

    /** Der oberste erreichbare Detent — kleinste Position gewinnt. */
    val topMost: Detent
        get() = when {
            large != null -> Detent.Large
            medium != null -> Detent.Medium
            else -> Detent.Hidden
        }

    /** Der unterste erreichbare Detent. */
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

/** Anteil von `large`, den `medium` einnimmt, wenn der Content mindestens so hoch ist. */
private const val MediumFractionWhenContentOverflows = 0.5f

/**
 * Berechnet Panel-Höhe und Anchor-Positionen aus einer Messung.
 *
 * [includeHidden] steuert die untere gesperrte Kante: fehlt `Hidden`, kann keine Geste das Sheet
 * schließen, und der Zug darunter läuft ins Rubber-Band. Der Anchor kommt zurück, sobald die App
 * programmatisch schließt — sonst hätte die Exit-Animation kein Ziel.
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
 * Der Detent, auf dem eine neue Präsentation startet.
 *
 * Steht der gewünschte Wert nicht in [detents], greift der kleinste enthaltene — bei uns also
 * `Medium` vor `Large`.
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
 * Der Detent, auf den nach einer Wiederherstellung gesnappt wird.
 *
 * Kommt der gerettete Wert in den Detents des wiederhergestellten Sheets nicht vor, gilt wieder
 * [resolveInitialDetent].
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
