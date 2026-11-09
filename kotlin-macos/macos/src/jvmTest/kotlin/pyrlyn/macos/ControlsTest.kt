package pyrlyn.macos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun aSidebarRowPutsItsMetaAtTheEndOfTheTitleLine() =
        runComposeUiTest {
            setContent {
                MacTheme {
                    SidebarRow("Fix the login bug", "Claude · Working", Tone.Busy, false, {}, Modifier.width(240.dp), meta = "3m")
                }
            }
            val title = onNodeWithText("Fix the login bug", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val subtitle = onNodeWithText("Claude · Working", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val meta = onNodeWithText("3m", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val middle = (meta.top + meta.bottom) / 2
            assertTrue(middle > title.top && middle < title.bottom && middle < subtitle.top, "the meta sits on the title line")
            assertTrue(meta.left >= title.right, "the meta follows the title")
        }

    @Test
    fun aSidebarRowWithAnIconWearsItsStatusAsABadgeOnTheIconsCorner() =
        runComposeUiTest {
            var ink = Color.Unspecified
            setContent {
                MacTheme {
                    ink = Tone.Busy.ink()
                    SidebarRow(
                        "Fix the login bug",
                        "Claude · Working",
                        Tone.Busy,
                        false,
                        {},
                        Modifier.width(240.dp),
                        icon = { Box(Modifier.size(24.dp).testTag("icon")) },
                    )
                }
            }
            val icon = onNodeWithTag("icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val title = onNodeWithText("Fix the login bug", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(icon.right <= title.left, "the icon leads the row")
            val pixels = onRoot().captureToImage().toPixelMap()
            // The dot's centre: the badge hangs 2dp past the corner and is 11dp across.
            val badge = with(density) { pixels[(icon.right - 3.5.dp).roundToPx(), (icon.bottom - 3.5.dp).roundToPx()] }
            assertTrue(
                abs(badge.red - ink.red) < 0.02f && abs(badge.green - ink.green) < 0.02f && abs(badge.blue - ink.blue) < 0.02f,
                "the status dot sits on the icon's bottom-right corner, got $badge",
            )
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
