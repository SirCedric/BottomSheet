package de.cedrickummer.bottomsheet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holds the presentation state outside the composition, the way an app keeps it in a ViewModel,
 * so it outlives the recreation.
 */
class RecreationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            presented = true
            dismissals = 0
        }
        setContent {
            BottomSheetHost {
                Box(
                    Modifier.bottomSheet(
                        isPresented = presented,
                        onDismissRequest = {
                            dismissals++
                            presented = false
                        },
                    ) { BasicText("Sheet-Inhalt") },
                )
            }
        }
    }

    companion object {
        var presented by mutableStateOf(true)
        var dismissals = 0
    }
}

@RunWith(AndroidJUnit4::class)
class ConfigurationChangeTest {

    @get:Rule
    val rule = createAndroidComposeRule<RecreationActivity>()

    @Test
    fun recreatingTheActivityKeepsTheSheetPresented() {
        rule.onNodeWithText("Sheet-Inhalt").assertExists()

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        assertThat(RecreationActivity.dismissals).isEqualTo(0)
        assertThat(RecreationActivity.presented).isTrue()
        rule.onNodeWithText("Sheet-Inhalt").assertExists()
    }
}
