package dev.sircedric.bottomsheet

import androidx.compose.runtime.Stable

/**
 * The receiver of the sheet content.
 *
 * It replaces a state holder: what an app actually needs — "which detent am I on" and "go to
 * `large`" — it almost always needs *inside* the sheet, and there the scope provides it without
 * a single `remember`.
 *
 * There is deliberately **no** `dismiss()`: the app owns `isPresented`, and a second way to
 * close that bypasses it would let state and picture drift apart.
 */
@Stable
public interface BottomSheetScope {

    /**
     * The detent the sheet **rests** on — not the live offset, which would recompose the content
     * on every frame.
     */
    public val currentDetent: PresentationDetent

    /**
     * Animates to [detent], provided it is part of the sheet's `presentationDetents`.
     *
     * Deliberately not `suspend`: the host already owns the scope driving the animation, so the
     * call site needs no `rememberCoroutineScope()`. The price is that the end of the animation
     * cannot be awaited.
     */
    public fun animateTo(detent: PresentationDetent)
}
