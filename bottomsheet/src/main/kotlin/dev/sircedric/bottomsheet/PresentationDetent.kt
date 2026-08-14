package dev.sircedric.bottomsheet

import androidx.compose.runtime.Immutable

/**
 * A named resting position a sheet settles on.
 *
 * Deliberately a sealed interface rather than an enum: free heights modelled on SwiftUI's
 * `.height(x)` and `.fraction(x)` can be added later without breaking the binary API.
 *
 * The closed state is **not** represented here — that is what `isPresented` expresses.
 */
@Immutable
public sealed interface PresentationDetent {

    /** The sheet is as tall as its content, but at most half the height of [Large]. */
    public data object Medium : PresentationDetent

    /** The sheet reaches up to the top edge of the safe drawing area. */
    public data object Large : PresentationDetent
}

/**
 * The set of detents a sheet may rest on — guaranteed non-empty.
 *
 * The constructor is private and [of] requires a first argument, so the empty set is ruled out
 * at compile time and needs neither a runtime check nor a test.
 */
@Immutable
public class SheetDetents private constructor(
    internal val values: Set<PresentationDetent>,
) {

    internal val hasMedium: Boolean = PresentationDetent.Medium in values

    internal val hasLarge: Boolean = PresentationDetent.Large in values

    internal val isSingle: Boolean = values.size == 1

    override fun equals(other: Any?): Boolean =
        this === other || (other is SheetDetents && values == other.values)

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "SheetDetents(${values.joinToString()})"

    public companion object {

        public val Medium: SheetDetents = SheetDetents(setOf(PresentationDetent.Medium))

        public val Large: SheetDetents = SheetDetents(setOf(PresentationDetent.Large))

        public val MediumAndLarge: SheetDetents =
            SheetDetents(setOf(PresentationDetent.Medium, PresentationDetent.Large))

        public fun of(
            first: PresentationDetent,
            vararg rest: PresentationDetent,
        ): SheetDetents = SheetDetents(setOf(first, *rest))
    }
}
