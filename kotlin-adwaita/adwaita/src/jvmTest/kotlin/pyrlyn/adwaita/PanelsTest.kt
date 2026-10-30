package pyrlyn.adwaita

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class PanelsTest {
    @Test
    fun aSelectOpensOnItsOptionsAndReportsThePick() =
        runComposeUiTest {
            var picked = 0
            setContent {
                AdwaitaTheme {
                    Select(listOf(1, 2), 1, { "Every $it" }, Modifier.testTag("select"), { Modifier.testTag("option-$it") }) { picked = it }
                }
            }
            onNodeWithText("Every 1").assertExists()
            onNodeWithTag("select").performClick()
            onNodeWithTag("option-2").performClick()
            assertEquals(2, picked)
        }

    @Test
    fun aRadioRowSaysItIsChosenAndPresses() =
        runComposeUiTest {
            var presses = 0
            setContent { AdwaitaTheme { RadioRow("uvx", selected = true, Modifier.testTag("row")) { presses++ } } }
            onNodeWithTag("row").assertIsSelected()
            onNodeWithTag("row").performClick()
            assertEquals(1, presses)
        }

    @Test
    fun aNameKeepsItsColourAndNeverTakesTheMarksOwn() {
        assertEquals(IconGradient.of("ripgrep"), IconGradient.of("ripgrep"))
        for (name in listOf("", "a", "ketch", "ripgrep", "fd", "uv", "bat", "zoxide")) assertNotEquals(IconGradient.Night, IconGradient.of(name))
    }
}
