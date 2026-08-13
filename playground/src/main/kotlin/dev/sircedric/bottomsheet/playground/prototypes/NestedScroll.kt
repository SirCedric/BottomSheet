package dev.sircedric.bottomsheet.playground.prototypes

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.AnchoredDragScope
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Wegwerf-Prototyp für Issue #12 — Verzahnung von Sheet-Drag und Scroll-Container im Sheet.
 */

enum class ScrollInterlock(val label: String, val detail: String) {
    ContentFirst(
        "Content zuerst",
        "onPostScroll in beide Richtungen: das Sheet bewegt sich erst, wenn die Liste an\n" +
            "ihrer Grenze steht. Aus medium erreicht man large erst nach dem Scroll-Ende.",
    ),
    SheetFirstUp(
        "Sheet zuerst hoch",
        "M3/iOS-Konvention: aufwärts onPreScroll (Sheet expandiert sofort auf large,\n" +
            "danach scrollt die Liste), abwärts onPostScroll wie nebenan.",
    ),
    SheetFirstBoth(
        "Sheet zuerst beidseitig",
        "Kontrast-Variante: das Sheet gewinnt in beide Richtungen vor dem Content.\n" +
            "Die Liste scrollt erst, wenn das Sheet an seinem Anchor klemmt.",
    ),
}

enum class FlingHandover(val label: String, val detail: String) {
    Handover(
        "Velocity ans Sheet",
        "Restvelocity nach dem Listen-Fling treibt das Sheet weiter; die Decay-Projektion\n" +
            "aus #8 entscheidet über das Ziel. Rückgabe = available − remaining.",
    ),
    Drop(
        "Velocity verfällt",
        "Das Sheet rastet ohne Schwung ein, allein die Positionsschwelle entscheidet.\n" +
            "Ein kräftiger Wisch trägt damit nie über einen Anchor hinaus.",
    ),
    ConsumeUp(
        "M3: Aufwärts ganz schlucken",
        "Zusätzlich onPreFling: ein Fling nach oben wird komplett konsumiert, solange das\n" +
            "Sheet nicht oben steht — sonst fliegt die Liste gleichzeitig los.",
    ),
}

object NestedScrollMetrics {

    var lastPhase by mutableStateOf("—")
        private set

    private var preCalls = 0
    private var prePx = 0f
    private var postCalls = 0
    private var postPx = 0f
    private var passedPx = 0f
    private var flings = ""
    private var blocked = ""
    private var takeovers = 0

    fun reset() {
        preCalls = 0; prePx = 0f; postCalls = 0; postPx = 0f
        passedPx = 0f; flings = ""; blocked = ""; takeovers = 0
        publish()
    }

    fun takeover() {
        takeovers++; publish()
    }

    fun pre(consumed: Float) {
        preCalls++; prePx += consumed; publish()
    }

    fun passed(delta: Float) {
        passedPx += delta; publish()
    }

    fun post(consumed: Float) {
        postCalls++; postPx += consumed; publish()
    }

    fun fling(text: String) {
        flings = text; publish()
    }

    fun blocked(reason: String) {
        blocked = reason; publish()
    }

    private fun publish() {
        lastPhase = buildString {
            append("pre ${preCalls}× ${prePx.toInt()}px")
            append("  post ${postCalls}× ${postPx.toInt()}px")
            append("  durch ${passedPx.toInt()}px")
            if (takeovers > 0) append("  übernommen ${takeovers}×")
            if (flings.isNotEmpty()) append("\n$flings")
            if (blocked.isNotEmpty()) append("\nblockiert: $blocked")
        }
    }

    fun note(phase: String) {
        if (phase != lastPhase) lastPhase = phase
    }
}

@Composable
fun rememberSheetNestedScrollConnection(
    state: AnchoredDraggableState<Detent>,
    flingBehavior: TargetedFlingBehavior,
    interlock: ScrollInterlock,
    handover: FlingHandover,
): NestedScrollConnection {
    val interlockState = rememberUpdatedState(interlock)
    val handoverState = rememberUpdatedState(handover)
    val flingState = rememberUpdatedState(flingBehavior)
    val scope = rememberCoroutineScope()
    return remember(state) {
        SheetNestedScrollConnection(state, scope, flingState, interlockState, handoverState)
    }
}

private class SheetNestedScrollConnection(
    private val state: AnchoredDraggableState<Detent>,
    private val scope: CoroutineScope,
    private val flingBehavior: State<TargetedFlingBehavior>,
    private val interlock: State<ScrollInterlock>,
    private val handover: State<FlingHandover>,
) : NestedScrollConnection {

    // Recherche #4: dispatchRawDelta umgeht die MutatorMutex — waehrend einer laufenden
    // Animation wuerden beide um denselben Offset kaempfen. Statt die Geste zu ignorieren
    // (dann scrollt die Liste, waehrend das Sheet noch hereinfliegt) wird die Animation
    // uebernommen: ein neuer anchoredDrag reisst die Mutex an sich und bricht sie ab.
    // Kostet einen Frame, danach greift der normale Pfad.
    private fun takeOverAnimation() {
        if (!state.isAnimationRunning) return
        NestedScrollMetrics.takeover()
        android.util.Log.d(
            "NestedScrollProto",
            "Animation uebernommen bei offset=${state.offset.toInt()} target=${state.targetValue}",
        )
        scope.launch {
            state.anchoredDrag { }
            android.util.Log.d(
                "NestedScrollProto",
                "nach Uebernahme: offset=${state.offset.toInt()} " +
                    "animating=${state.isAnimationRunning}",
            )
        }
    }

    private fun sheetCanMove(): Boolean {
        if (state.offset.isNaN() || state.anchors.size <= 1) return false
        if (state.isAnimationRunning) {
            takeOverAnimation()
            return false
        }
        return true
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (!sheetCanMove()) {
            NestedScrollMetrics.blocked(
                "anim=${state.isAnimationRunning} nan=${state.offset.isNaN()} " +
                    "anchors=${state.anchors.size}",
            )
            return Offset.Zero
        }
        if (delta == 0f) return Offset.Zero

        val takeItFirst = when (interlock.value) {
            ScrollInterlock.ContentFirst -> false
            ScrollInterlock.SheetFirstUp -> delta < 0f
            ScrollInterlock.SheetFirstBoth -> true
        }
        if (!takeItFirst) {
            NestedScrollMetrics.passed(delta)
            return Offset.Zero
        }

        val consumed = state.dispatchRawDelta(delta)
        NestedScrollMetrics.pre(consumed)
        return Offset(0f, consumed)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || !sheetCanMove()) return Offset.Zero
        val delta = available.y
        if (delta == 0f) return Offset.Zero

        val taken = state.dispatchRawDelta(delta)
        NestedScrollMetrics.post(taken)
        return Offset(0f, taken)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (handover.value != FlingHandover.ConsumeUp || !sheetCanMove()) return Velocity.Zero
        val toFling = available.y
        if (toFling >= 0f || state.offset <= state.anchors.minPosition()) return Velocity.Zero

        NestedScrollMetrics.fling("preFling ${toFling.toInt()} ganz geschluckt")
        state.flingToNearestAnchor(flingBehavior.value, toFling)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!sheetCanMove()) return Velocity.Zero

        // Recherche #4: performFling liefert die *verbleibende* Velocity, nicht die konsumierte.
        // M3 gibt den Wert faelschlich als konsumiert zurueck — hier bewusst anders.
        return when (handover.value) {
            FlingHandover.Drop -> {
                NestedScrollMetrics.fling("postFling ${available.y.toInt()} verfallen")
                state.flingToNearestAnchor(flingBehavior.value, 0f)
                Velocity.Zero
            }

            else -> {
                val offered = available.y
                val remaining = state.flingToNearestAnchor(flingBehavior.value, offered)
                NestedScrollMetrics.fling(
                    "postFling ${offered.toInt()} rest ${remaining.toInt()}",
                )
                Velocity(0f, offered - remaining)
            }
        }
    }
}

/**
 * Adapter von [ScrollScope] auf [AnchoredDragScope] — ohne ihn laeuft ein FlingBehavior nicht
 * gegen den AnchoredDraggableState. Rueckgabe ist die verbleibende Velocity.
 */
private suspend fun AnchoredDraggableState<Detent>.flingToNearestAnchor(
    flingBehavior: TargetedFlingBehavior,
    velocity: Float,
): Float {
    var remaining = velocity
    anchoredDrag {
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                val before = requireOffset()
                dragTo(before + pixels)
                return requireOffset() - before
            }
        }
        with(flingBehavior) { remaining = scope.performFling(velocity) }
    }
    return remaining
}
