package dev.sircedric.bottomsheet

import androidx.compose.runtime.Stable

/**
 * Der Receiver des Sheet-Contents.
 *
 * Er ersetzt einen State-Holder: was die App real braucht — „auf welchem Detent stehe ich" und
 * „geh auf `large`" — braucht sie fast immer *im* Sheet, und dort liefert der Scope es ohne ein
 * einziges `remember`.
 *
 * Es gibt bewusst **kein** `dismiss()`: die App hält `isPresented` in der Hand, und ein zweiter
 * Schließweg daran vorbei ließe Zustand und Bild auseinanderlaufen.
 */
@Stable
public interface BottomSheetScope {

    /**
     * Der Detent, auf dem das Sheet **ruht** — nicht der laufende Offset. Sonst würde der Content
     * pro Frame rekomponiert.
     */
    public val currentDetent: PresentationDetent

    /**
     * Fährt auf [detent], sofern er in den `presentationDetents` des Sheets vorkommt.
     *
     * Bewusst nicht `suspend`: der Host hat ohnehin den Scope, der die Animation treibt, und die
     * Call Site braucht kein `rememberCoroutineScope()`. Preis ist, dass sich das Ende der
     * Animation nicht abwarten lässt.
     */
    public fun animateTo(detent: PresentationDetent)
}
