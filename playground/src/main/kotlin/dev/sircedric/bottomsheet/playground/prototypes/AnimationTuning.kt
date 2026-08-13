package dev.sircedric.bottomsheet.playground.prototypes

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Kandidaten für Issue #9. Wegwerf-Code — die Zahlen sind das Ergebnis, nicht der Code.
 */

enum class MotionVariant(val label: String, val detail: String) {
    M3Tween("M3-Tween", "tween(300, FastOutSlowIn) — was Material3 für sein Sheet nimmt"),
    SpringFirm("Spring straff", "spring(1.0, 400) — kein Überschwingen, zügig"),
    SpringIosLike("Spring iOS-nah", "spring(0.9, 380) — Hauch Nachgeben am Ende"),
    SpringSoft("Spring weich", "spring(1.0, 200) — deutlich langsamer, träger"),
    ;

    val spec: AnimationSpec<Float>
        get() = when (this) {
            M3Tween -> tween(durationMillis = 300, easing = FastOutSlowInEasing)
            SpringFirm -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)
            SpringIosLike -> spring(dampingRatio = 0.9f, stiffness = 380f)
            SpringSoft -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 200f)
        }
}

enum class ScrimMode(val label: String, val detail: String) {
    LinearToOffset("linear am Offset", "Alpha folgt der Position, steigt bis large weiter"),
    EasedToOffset("gekurvt am Offset", "wie linear, aber FastOutSlowIn auf den Fortschritt"),
    FullAtMedium("voll ab medium", "Alpha ist bei medium am Maximum, medium→large ändert nichts"),
    IndependentFade("eigene Blende", "entkoppelt vom Offset, blendet auf Sichtbarkeit — M3-Verhalten"),
}

enum class ContentEffect(val label: String) {
    Static("App starr"),
    Scaled("App skaliert"),
    Translated("App wandert hoch"),
}

enum class ScrimAlpha(val label: String, val value: Float) {
    Subtle("0.16", 0.16f),
    Material("0.32", 0.32f),
    Heavy("0.50", 0.50f),
}
