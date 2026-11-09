package pyrlyn.macos

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ControlsTest {
    @Test
    fun aButtonPressesAndADisabledOneDoesNot() =
        runComposeUiTest {
            var presses = 0
            setContent {
                MacTheme {
                    Column {
                        Button("Go", { presses++ }, Modifier.testTag("go"), icon = MacIcon.Plus)
                        Button("Wait", { presses++ }, Modifier.testTag("off"), enabled = false)
                    }
                }
            }
            onNodeWithTag("go").performClick()
            onNodeWithTag("off").performClick()
            assertEquals(1, presses)
            onNodeWithTag("off").assertIsNotEnabled()
        }

    @Test
    fun aPickerShowsItsValueAndDetailAndOpens() =
        runComposeUiTest {
            var opened = 0
            setContent {
                MacTheme(MacColors.Light) {
                    PickerButton("Opus", { opened++ }, detail = "High", icon = MacIcon.Sparkle)
                }
            }
            onNodeWithText("High").assertExists()
            onNodeWithText("Opus").performClick()
            assertEquals(1, opened)
        }

    @Test
    fun theSendButtonIsNamedAndWaitsForSomethingToSend() =
        runComposeUiTest {
            var sent = 0
            setContent {
                MacTheme {
                    Column {
                        SendButton({ sent++ }, description = "Start task")
                        SendButton({ sent++ }, Modifier.testTag("empty"), enabled = false)
                    }
                }
            }
            onNodeWithContentDescription("Start task").performClick()
            onNodeWithTag("empty").assertIsNotEnabled()
            assertEquals(1, sent)
        }

    @Test
    fun theSwitchToggles() =
        runComposeUiTest {
            var on by mutableStateOf(false)
            setContent { MacTheme { Switch(on, { on = it }, Modifier.testTag("switch")) } }
            onNodeWithTag("switch").assertIsOff().performClick()
            onNodeWithTag("switch").assertIsOn()
        }

    @Test
    fun aPopUpButtonPicksFromItsMenu() =
        runComposeUiTest {
            var picked by mutableStateOf("One")
            setContent { MacTheme { PopUpButton(picked, listOf("One", "Two"), { picked = it }) } }
            onNodeWithText("One").performClick()
            onNodeWithText("Two").performClick()
            assertEquals("Two", picked)
            onNodeWithText("One").assertDoesNotExist()
        }
}
