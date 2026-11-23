package pyrlyn.adwaita

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import pyrlyn.glass.Glyph
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ControlsTest {
    @Test
    fun aPillPressesAndABusyOneDoesNot() =
        runComposeUiTest {
            var presses = 0
            setContent {
                AdwaitaTheme {
                    Column {
                        PillButton("Go", Modifier.testTag("go")) { presses++ }
                        PillButton("Installing…", Modifier.testTag("busy"), busy = true) { presses++ }
                    }
                }
            }
            onNodeWithTag("go").performClick()
            assertEquals(1, presses)
            onNodeWithTag("busy").assertIsNotEnabled()
        }

    @Test
    fun theSwitcherMarksItsViewAndCarriesItsBadge() =
        runComposeUiTest {
            var view by mutableStateOf("a")
            setContent {
                AdwaitaTheme {
                    ViewSwitcher(
                        listOf(
                            SwitcherItem("a", "One", Glyph.Box, modifier = Modifier.testTag("a")),
                            SwitcherItem("b", "Two", Glyph.Box, badge = "3", modifier = Modifier.testTag("b")),
                        ),
                        view,
                    ) { view = it }
                }
            }
            onNodeWithTag("a").assertIsSelected()
            onNodeWithText("3").assertExists()
            onNodeWithTag("b").performClick()
            onNodeWithTag("b").assertIsSelected()
            onNodeWithTag("a").assertIsNotSelected()
        }

    @Test
    fun chipsAndTheLinkedTogglePickOne() =
        runComposeUiTest {
            var chip by mutableStateOf("x")
            var tab by mutableStateOf("p")
            setContent {
                AdwaitaTheme {
                    Column {
                        Chips(listOf("x", "y"), chip, { it }, optionModifier = { Modifier.testTag("chip-$it") }) { chip = it }
                        LinkedToggle(listOf("p", "q"), tab, { it }, optionModifier = { Modifier.testTag("tab-$it") }) { tab = it }
                    }
                }
            }
            onNodeWithTag("chip-y").performClick()
            onNodeWithTag("tab-q").performClick()
            onNodeWithTag("chip-y").assertIsSelected()
            onNodeWithTag("tab-q").assertIsSelected()
            onNodeWithTag("chip-x").assertIsNotSelected()
        }

    @Test
    fun theSwitchIsNamedAndFlips() =
        runComposeUiTest {
            var on by mutableStateOf(false)
            setContent { AdwaitaTheme { ToggleSwitch(on, "Automatic updates") { on = it } } }
            onNodeWithContentDescription("Automatic updates").assertIsOff().performClick()
            onNodeWithContentDescription("Automatic updates").assertIsOn()
        }

    @Test
    fun anEntryReportsWhatIsTyped() =
        runComposeUiTest {
            var text by mutableStateOf("")
            setContent { AdwaitaTheme { Entry(text, "Search", Modifier.testTag("entry"), Glyph.Search) { text = it } } }
            onNodeWithTag("entry").performTextInput("rg")
            waitUntil { text == "rg" }
        }

    @Test
    fun theWindowControlsAreNamedAndSayRestoreWhenMaximized() =
        runComposeUiTest {
            var closed = false
            setContent { AdwaitaTheme { WindowControls(maximized = true, onMinimize = {}, onToggleMaximize = {}, onClose = { closed = true }) } }
            onNodeWithContentDescription("Restore").assertExists()
            onNodeWithContentDescription("Close").performClick()
            assertEquals(true, closed)
        }

    @Test
    fun progressReportsItsFractionAndStaysInRange() =
        runComposeUiTest {
            setContent { AdwaitaTheme { ProgressBar(1.5f, Modifier.testTag("bar")) } }
            onNodeWithTag("bar").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        }

    @Test
    fun aMenuItemPressesAndADialogButtonCloses() =
        runComposeUiTest {
            var picked = false
            var open by mutableStateOf(true)
            setContent {
                AdwaitaTheme {
                    Column { MenuItem("Activity", Modifier.testTag("item"), Glyph.Pulse, "Ctrl+1") { picked = true } }
                    if (open) {
                        AlertDialog("Uninstall?", { open = false }, footer = {
                            PillButton("Cancel", Modifier.testTag("cancel")) { open = false }
                        }) { DialogText("This removes it.") }
                    }
                }
            }
            onNodeWithText("This removes it.").assertExists()
            onNodeWithTag("cancel").performClick()
            onNodeWithTag("item").performClick()
            assertEquals(true, picked)
        }
}
