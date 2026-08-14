package dev.sircedric.bottomsheet

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Erscheinungsbild des Sheets.
 *
 * Scrim-Farbe und Max-Alpha bleiben getrennt: das Alpha wird animiert, die Farbe nicht — und nur
 * ein eigener Wert kann als Default eine Funktion tragen, die das API-Level liest.
 */
@Immutable
public class BottomSheetColors internal constructor(
    public val sheet: Color,
    public val handle: Color,
    public val scrim: Color,
    public val scrimMaxAlpha: Float,
)

/** Die Kurve, mit der das Sheet zwischen Detents und nach `Hidden` fährt. */
@Immutable
public class BottomSheetMotion internal constructor(
    public val animationSpec: AnimationSpec<Float>,
)

/**
 * Die Namen der Detent-Zustände für `stateDescription`.
 *
 * Die Library bringt **keine** Strings mit; ohne Werte sagt sie keinen Zustand an. Sie stehen am
 * Host statt am Modifier, weil sie app-weit identisch sind.
 */
@Immutable
public class BottomSheetDetentNames internal constructor(
    public val medium: String?,
    public val large: String?,
)

internal val LocalDragHandleColor: ProvidableCompositionLocal<Color> =
    compositionLocalOf { Color.Black.copy(alpha = 0.4f) }

public object BottomSheetDefaults {

    public val Shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Skalierung des App-Contents bei voller Präsentation. `1f` schaltet den Effekt ab. */
    public const val AppContentMinScale: Float = 0.92f

    /**
     * `0.16f` ab API 31, sonst `0.32f`.
     *
     * Ab Android 12 trägt der Blur die Trennung mit und der Scrim tritt zurück; darunter gibt es
     * keinen Blur — `Modifier.blur` wird dort stillschweigend ignoriert —, also dunkelt der Scrim
     * allein ab.
     */
    public fun scrimMaxAlpha(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.16f else 0.32f

    public fun colors(
        sheet: Color = Color.White,
        handle: Color = Color.Black.copy(alpha = 0.4f),
        scrim: Color = Color.Black,
        scrimMaxAlpha: Float = scrimMaxAlpha(),
    ): BottomSheetColors = BottomSheetColors(
        sheet = sheet,
        handle = handle,
        scrim = scrim,
        scrimMaxAlpha = scrimMaxAlpha,
    )

    public fun motion(
        animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 380f),
    ): BottomSheetMotion = BottomSheetMotion(animationSpec = animationSpec)

    public fun detentNames(
        medium: String? = null,
        large: String? = null,
    ): BottomSheetDetentNames = BottomSheetDetentNames(medium = medium, large = large)

    /**
     * Der Griff am oberen Rand.
     *
     * Er sitzt als fester Kopf über dem Content-Slot und scrollt nicht mit — sonst ließe sich ein
     * Sheet mit scrollbarem Content gar nicht mehr ziehen. Eigene Semantics trägt er nicht.
     */
    @Composable
    public fun DragHandle(modifier: Modifier = Modifier) {
        // Die Farbe kommt aus den aufgelösten Colors des Hosts. Ein Parameter ginge nicht: der
        // Default-Slot wird an der Call Site gebaut, wo die Auflösung noch nicht passiert ist.
        Box(
            modifier = modifier
                .padding(vertical = 12.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(LocalDragHandleColor.current, RoundedCornerShape(2.dp)),
        )
    }
}
