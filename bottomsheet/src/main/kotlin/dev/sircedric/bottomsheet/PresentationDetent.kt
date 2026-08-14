package dev.sircedric.bottomsheet

import androidx.compose.runtime.Immutable

/**
 * Eine benannte Rastposition, auf der ein Sheet zur Ruhe kommt.
 *
 * Bewusst ein Sealed Interface und kein Enum: freie Höhen nach dem Vorbild von SwiftUIs
 * `.height(x)` und `.fraction(x)` lassen sich so ohne Bruch der Binär-API ergänzen.
 *
 * Der geschlossene Zustand ist hier **nicht** vertreten — er wird über `isPresented` ausgedrückt.
 */
@Immutable
public sealed interface PresentationDetent {

    /** Das Sheet ist so hoch wie sein Content, höchstens jedoch halb so hoch wie [Large]. */
    public data object Medium : PresentationDetent

    /** Das Sheet reicht bis unter die obere Kante des sicheren Zeichenbereichs. */
    public data object Large : PresentationDetent
}

/**
 * Die Menge der Detents, auf denen ein Sheet ruhen darf — garantiert nicht leer.
 *
 * Der Konstruktor ist privat und [of] erzwingt ein erstes Argument; eine leere Menge ist damit
 * zur Compile-Zeit ausgeschlossen und braucht weder Laufzeit-Prüfung noch Test.
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
