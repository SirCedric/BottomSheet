package de.cedrickummer.bottomsheet

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SheetTag = "sheet"
private const val AppTag = "app"
private const val HandleTag = "handle"
private const val HeaderTag = "header"
private const val ActionTag = "action"
private const val InnerTag = "inner"

private fun hasAction(key: SemanticsPropertyKey<*>) =
    SemanticsMatcher.keyIsDefined(key)

private fun lacksAction(key: SemanticsPropertyKey<*>) =
    SemanticsMatcher.keyNotDefined(key)

@RunWith(AndroidJUnit4::class)
class BottomSheetWiringTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun modifierWithoutHostFailsWithOwnMessage() {
        val error = runCatching {
            rule.setContent {
                Box(Modifier.bottomSheet(isPresented = false, onDismissRequest = {}) { })
            }
            rule.waitForIdle()
        }.exceptionOrNull()

        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")

        assertThat(message.contains("BottomSheetHost")).isTrue()
    }

    @Test
    fun contentIsComposedOnlyWhenOpening() {
        var compositions = 0
        var presented by mutableStateOf(false)

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = presented,
                        onDismissRequest = { presented = false },
                    ) {
                        SideEffect { compositions++ }
                        BasicText("Sheet-Inhalt", Modifier.testTag(SheetTag))
                    },
                )
            }
        }

        rule.waitForIdle()
        assertThat(compositions).isEqualTo(0)

        presented = true
        rule.waitForIdle()

        rule.onNodeWithTag(SheetTag).assertIsDisplayed()
        assertThat(compositions > 0).isTrue()
    }

    @Test
    fun contentGrowingAtRestMovesTheSheetInsteadOfDismissingIt() {
        var presented by mutableStateOf(true)
        var contentHeight by mutableStateOf(40.dp)

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = presented,
                        onDismissRequest = { presented = false },
                    ) {
                        Box(Modifier.fillMaxWidth().height(contentHeight).testTag(SheetTag))
                    },
                )
            }
        }
        rule.waitForIdle()

        // Grows by more than the visible part: the hidden anchor is now closer to the old
        // offset than the moved medium anchor.
        contentHeight = 200.dp
        rule.waitForIdle()

        assertThat(presented).isTrue()
        val rootBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val sheetBottom = rule.onNodeWithTag(SheetTag).fetchSemanticsNode().boundsInRoot.bottom
        assertThat(sheetBottom <= rootBottom).isTrue()
    }

    @Test
    fun scrimTapClosesOrElseReportsTheAttempt() {
        var presented by mutableStateOf(true)
        var attempts = 0
        var dismissEnabled by mutableStateOf(true)

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomSheet(
                            isPresented = presented,
                            onDismissRequest = { presented = false },
                            interactiveDismissEnabled = dismissEnabled,
                            onDismissAttempt = { attempts++ },
                        ) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()
        rule.tapScrim()
        assertThat(presented).isFalse()

        presented = true
        dismissEnabled = false
        rule.waitForIdle()
        rule.tapScrim()

        assertThat(presented).isTrue()
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun swipeBelowTheLowestDetentReportsDismiss() {
        var presented by mutableStateOf(true)

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomSheet(
                            isPresented = presented,
                            onDismissRequest = { presented = false },
                            presentationDetents = SheetDetents.Medium,
                        ) { TallSheetBody() },
                )
            }
        }

        rule.waitForIdle()
        // Swipe over a real distance: the positional threshold is half the way to the next
        // anchor, so a swipe across a one-line text node never gets there.
        rule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.55f, endY = height.toFloat(), durationMillis = 120)
        }
        rule.waitForIdle()

        assertThat(presented).isFalse()
    }

    @Test
    fun ownerLeavingTheCompositionReportsDismiss() {
        var ownerPresent by mutableStateOf(true)
        var dismissals = 0

        rule.setContent {
            BottomSheetHost {
                if (ownerPresent) {
                    Box(
                        Modifier.bottomSheet(
                            isPresented = true,
                            onDismissRequest = { dismissals++ },
                        ) { SheetBody() },
                    )
                }
            }
        }

        rule.waitForIdle()
        ownerPresent = false
        rule.waitForIdle()

        assertThat(dismissals).isEqualTo(1)
    }

    @Test
    fun appContentLeavesTheSemanticsTreeWhilePresented() {
        var presented by mutableStateOf(false)

        rule.setContent {
            BottomSheetHost {
                Column {
                    BasicText("App-Inhalt", Modifier.testTag(AppTag))
                    Box(
                        Modifier.bottomSheet(
                            isPresented = presented,
                            onDismissRequest = { presented = false },
                        ) { SheetBody() },
                    )
                }
            }
        }

        rule.onNodeWithTag(AppTag).assertIsDisplayed()

        presented = true
        rule.waitForIdle()

        assertThat(rule.onAllNodesWithTag(AppTag).fetchSemanticsNodes().isEmpty()).isTrue()
    }

    @Test
    fun actionsFollowTheDetentsAndTheDismissLock() {
        var detents by mutableStateOf(SheetDetents.MediumAndLarge)
        var dismissEnabled by mutableStateOf(true)

        rule.setContent {
            BottomSheetHost(detentNames = BottomSheetDefaults.detentNames("Halb", "Voll")) {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        presentationDetents = detents,
                        interactiveDismissEnabled = dismissEnabled,
                        paneTitle = "Details",
                    ) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()

        rule.panel().assert(hasAction(SemanticsActions.Expand))
        rule.panel().assert(lacksAction(SemanticsActions.Collapse))
        rule.panel().assert(hasAction(SemanticsActions.Dismiss))
        rule.panel().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Halb"),
        )

        dismissEnabled = false
        rule.waitForIdle()
        rule.panel().assert(lacksAction(SemanticsActions.Dismiss))

        detents = SheetDetents.Medium
        dismissEnabled = true
        rule.waitForIdle()
        rule.panel().assert(lacksAction(SemanticsActions.Expand))
        rule.panel().assert(lacksAction(SemanticsActions.Collapse))
        rule.panel().assert(lacksAction(SemanticsProperties.StateDescription))
    }

    @Test
    fun withoutAPaneTitleTheLibraryInventsNone() {
        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(isPresented = true, onDismissRequest = {}) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()

        val titled = rule.onAllNodes(hasAction(SemanticsProperties.PaneTitle)).fetchSemanticsNodes()
        assertThat(titled.isEmpty()).isTrue()
    }

    @Test
    fun scrimCreatesNoClickableNode() {
        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomSheet(
                            isPresented = true,
                            onDismissRequest = {},
                            dragHandle = null,
                        ) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()

        // The sheet content brings exactly one clickable node. The scrim must not add a second
        // one, even though its tap closes the sheet.
        val clickable = rule.onAllNodes(hasAction(SemanticsActions.OnClick)).fetchSemanticsNodes()
        assertThat(clickable.size).isEqualTo(1)
    }

    @Test
    fun panelCreatesNoFocusableNode() {
        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomSheet(
                            isPresented = true,
                            onDismissRequest = {},
                            dragHandle = null,
                            paneTitle = "Details",
                        ) { BasicText("nur Text") },
                )
            }
        }

        rule.waitForIdle()

        // focusTarget() holds the input focus but does not report it to accessibility.
        rule.panel().assert(lacksAction(SemanticsProperties.Focused))
    }

    @Test
    fun handleIsDiscardedWhenGesturesAreLocked() {
        var gesturesEnabled by mutableStateOf(true)

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        gesturesEnabled = gesturesEnabled,
                        dragHandle = {
                            Box(Modifier.testTag(HandleTag).fillMaxWidth().height(24.dp))
                        },
                    ) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()
        assertThat(rule.onAllNodesWithTag(HandleTag).fetchSemanticsNodes().size).isEqualTo(1)

        gesturesEnabled = false
        rule.waitForIdle()
        assertThat(rule.onAllNodesWithTag(HandleTag).fetchSemanticsNodes().isEmpty()).isTrue()
    }

    @Test
    fun handleTapSwitchesTheDetent() {
        var current: PresentationDetent? = null

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        dragHandle = {
                            Box(Modifier.testTag(HandleTag).fillMaxWidth().height(24.dp))
                        },
                    ) {
                        val detent = currentDetent
                        SideEffect { current = detent }
                        SheetBody()
                    },
                )
            }
        }

        rule.waitForIdle()
        assertThat(current).isEqualTo(PresentationDetent.Medium)

        rule.onNodeWithTag(HandleTag).performClick()
        rule.waitForIdle()

        assertThat(current).isEqualTo(PresentationDetent.Large)
    }

    @Test
    fun headerSitsBetweenHandleAndContent() {
        var withHeader by mutableStateOf(true)

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        dragHandle = { Box(Modifier.testTag(HandleTag).fillMaxWidth().height(24.dp)) },
                        header = if (withHeader) {
                            { Box(Modifier.testTag(HeaderTag).fillMaxWidth().height(40.dp)) }
                        } else {
                            null
                        },
                    ) { Box(Modifier.testTag(SheetTag).fillMaxWidth().height(80.dp)) },
                )
            }
        }

        rule.waitForIdle()
        val handle = rule.boundsOf(HandleTag)
        val header = rule.boundsOf(HeaderTag)
        assertThat(header.top).isEqualTo(handle.bottom)
        assertThat(rule.boundsOf(SheetTag).top).isEqualTo(header.bottom)

        withHeader = false
        rule.waitForIdle()
        assertThat(rule.boundsOf(SheetTag).top).isEqualTo(rule.boundsOf(HandleTag).bottom)
    }

    @Test
    fun headerStaysAndActsWhileLongContentIsScrolled() {
        var saves = 0

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomSheet(
                            isPresented = true,
                            onDismissRequest = {},
                            presentationDetents = SheetDetents.Large,
                            header = {
                                BottomSheetDefaults.Header(
                                    title = { BasicText("Spiel erfassen") },
                                    actions = {
                                        BasicText(
                                            "Sichern",
                                            Modifier
                                                .testTag(ActionTag)
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .clickable { saves++ },
                                        )
                                    },
                                )
                            },
                        ) {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                repeat(30) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                                Box(Modifier.testTag(SheetTag).fillMaxWidth().height(80.dp))
                            }
                        },
                )
            }
        }

        rule.waitForIdle()
        val before = rule.boundsOf(ActionTag)

        rule.onNodeWithTag(SheetTag).performScrollTo()
        rule.waitForIdle()

        rule.onNodeWithTag(SheetTag).assertIsDisplayed()
        rule.onNodeWithTag(ActionTag).assertIsDisplayed()
        assertThat(rule.boundsOf(ActionTag)).isEqualTo(before)
        rule.onNodeWithTag(ActionTag).performClick()
        assertThat(saves).isEqualTo(1)
    }

    @Test
    fun mediumSpansHandleHeaderAndContent() {
        var bottomInset = 0

        rule.setContent {
            bottomInset = WindowInsets.safeDrawing.getBottom(LocalDensity.current)
            BottomSheetHost {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomSheet(
                            isPresented = true,
                            onDismissRequest = {},
                            dragHandle = { Box(Modifier.testTag(HandleTag).fillMaxWidth().height(24.dp)) },
                            header = { Box(Modifier.testTag(HeaderTag).fillMaxWidth().height(40.dp)) },
                        ) { Box(Modifier.testTag(SheetTag).fillMaxWidth().height(80.dp)) },
                )
            }
        }

        rule.waitForIdle()
        val rootBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val handleTop = rule.boundsOf(HandleTag).top
        val expected = with(rule.density) { (24.dp + 40.dp + 80.dp).toPx() }

        // The content slot pads the bottom inset, so medium spans that as well.
        assertThat(abs(rootBottom - handleTop - expected - bottomInset) < 1f).isTrue()
    }

    @Test
    fun withoutADrawnHandleTheHeaderKeepsItsGapAndStays() {
        var gesturesEnabled by mutableStateOf(true)

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        gesturesEnabled = gesturesEnabled,
                        paneTitle = "Details",
                        dragHandle = null,
                        header = { Box(Modifier.testTag(HeaderTag).fillMaxWidth().height(40.dp)) },
                    ) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()
        val gap = with(rule.density) { 12.dp.toPx() }
        val panelTop = rule.panel().fetchSemanticsNode().boundsInRoot.top
        assertThat(abs(rule.boundsOf(HeaderTag).top - panelTop - gap) < 1f).isTrue()

        gesturesEnabled = false
        rule.waitForIdle()
        rule.onNodeWithTag(HeaderTag).assertIsDisplayed()
    }

    @Test
    fun headerTapLeavesTheDetentAlone() {
        var current: PresentationDetent? = null

        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        header = { Box(Modifier.testTag(HeaderTag).fillMaxWidth().height(40.dp)) },
                    ) {
                        val detent = currentDetent
                        SideEffect { current = detent }
                        SheetBody()
                    },
                )
            }
        }

        rule.waitForIdle()
        rule.onNodeWithTag(HeaderTag).performTouchInput { click(center) }
        rule.waitForIdle()

        assertThat(current).isEqualTo(PresentationDetent.Medium)
    }

    @Test
    fun headerTitleIsAHeadingOnTheFocusableNode() {
        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        header = { BottomSheetDefaults.Header(title = { BasicText("Spiel erfassen") }) },
                    ) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()

        rule.onNodeWithText("Spiel erfassen").assert(hasAction(SemanticsProperties.Heading))
    }

    @Test
    fun headerFormsItsOwnTraversalGroupAheadOfTheContent() {
        rule.setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = true,
                        onDismissRequest = {},
                        paneTitle = "Details",
                        header = { Box(Modifier.testTag(HeaderTag).fillMaxWidth().height(40.dp)) },
                    ) { SheetBody() },
                )
            }
        }

        rule.waitForIdle()

        val group = rule.onNodeWithTag(HeaderTag, useUnmergedTree = true).fetchSemanticsNode().parent
        assertThat(group?.config?.getOrNull(SemanticsProperties.IsTraversalGroup)).isEqualTo(true)
        assertThat(group?.config?.contains(SemanticsProperties.PaneTitle)).isEqualTo(false)
    }

    @Test
    fun sideInsetsIndentHeaderAndContentButNotThePanel() {
        lateinit var view: View
        val inset = 96

        rule.setContent {
            view = LocalView.current
            BottomSheetHost {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomSheet(
                            isPresented = true,
                            onDismissRequest = {},
                            paneTitle = "Details",
                            header = { Box(Modifier.testTag(HeaderTag).fillMaxWidth().height(40.dp)) },
                        ) {
                            Box(Modifier.testTag(SheetTag).fillMaxWidth().safeDrawingPadding().height(80.dp)) {
                                Box(Modifier.testTag(InnerTag).fillMaxSize())
                            }
                        },
                )
            }
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(inset, 0, 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        rule.waitForIdle()

        assertThat(rule.panel().fetchSemanticsNode().boundsInRoot.left).isEqualTo(0f)
        assertThat(rule.boundsOf(HeaderTag).left).isEqualTo(inset.toFloat())
        assertThat(rule.boundsOf(SheetTag).left).isEqualTo(inset.toFloat())
        assertThat(rule.boundsOf(InnerTag).left).isEqualTo(inset.toFloat())
    }
}

private fun ComposeContentTestRule.boundsOf(tag: String) =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

private fun ComposeContentTestRule.panel() =
    onAllNodes(hasAction(SemanticsProperties.PaneTitle)).onFirst()

private fun ComposeContentTestRule.tapScrim() {
    onRoot().performTouchInput { click(Offset(width / 2f, 40f)) }
    waitForIdle()
}

@Composable
private fun TallSheetBody() {
    Column(Modifier.fillMaxWidth()) {
        BasicText("Sheet-Inhalt")
        Box(Modifier.height(320.dp).fillMaxWidth().background(Color.LightGray))
    }
}

@Composable
private fun SheetBody() {
    Column(Modifier.fillMaxWidth()) {
        BasicText("Sheet-Inhalt")
        Box(
            Modifier
                .height(48.dp)
                .fillMaxWidth()
                .background(Color.LightGray)
                .clickable { },
        )
    }
}
