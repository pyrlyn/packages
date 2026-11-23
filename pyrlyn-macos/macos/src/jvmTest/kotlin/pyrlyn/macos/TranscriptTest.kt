package pyrlyn.macos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TranscriptTest {
    @Test
    fun theComposerSendsOnlyWhatWasTyped() =
        runComposeUiTest {
            var draft by mutableStateOf("")
            val sent = mutableListOf<String>()
            setContent {
                MacTheme {
                    Composer(
                        draft,
                        { draft = it },
                        onSend = { sent += draft },
                        placeholder = "Message…",
                        trailing = { PickerButton("Agent", {}) },
                    ) { IconButton(MacIcon.Plus, "Attach files", {}) }
                }
            }
            onNodeWithText("Agent").assertExists()
            onNodeWithContentDescription("Attach files").assertExists()
            onNodeWithContentDescription("Send").assertIsNotEnabled()
            onNodeWithText("Message…").performTextInput("Fix the build")
            onNodeWithContentDescription("Send").assertIsEnabled().performClick()
            assertEquals(listOf("Fix the build"), sent)
        }

    @Test
    fun aDividerOffersItsAction() =
        runComposeUiTest {
            var reverted = 0
            setContent { MacTheme { LabeledDivider("Checkpoint 3", action = "Revert to here", onAction = { reverted++ }) } }
            onNodeWithText("Revert to here").performClick()
            assertEquals(1, reverted)
        }
}
