package pyrlyn.macos

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OverlaysTest {
    @Test
    fun aMenuShowsItsSectionsAndSkipsADisabledItem() =
        runComposeUiTest {
            val picks = mutableListOf<String>()
            setContent {
                MacTheme {
                    Menu(expanded = true, onDismissRequest = {}) {
                        MenuSection("Local")
                        MenuItem("This computer", { picks += "local" }, checked = true, icon = MacIcon.Terminal)
                        MenuSeparator()
                        MenuSection("Remote")
                        MenuItem("Builder", { picks += "remote" }, enabled = false)
                    }
                }
            }
            onNodeWithText("Local").assertExists()
            onNodeWithText("Builder").assertIsNotEnabled().performClick()
            onNodeWithText("This computer").performClick()
            assertEquals(listOf("local"), picks)
        }

    @Test
    fun aClosedMenuDrawsNothing() =
        runComposeUiTest {
            setContent { MacTheme { Menu(expanded = false, onDismissRequest = {}) { MenuItem("Hidden", {}) } } }
            onNodeWithText("Hidden").assertDoesNotExist()
        }

    @Test
    fun aHoverCardShowsWhileThePointerRestsOnIt() =
        runComposeUiTest {
            setContent { MacTheme { HoverCard(MacIcon.Gauge, "Tokens") { Label("12k sent") } } }
            onNodeWithText("12k sent").assertDoesNotExist()
            onNodeWithContentDescription("Tokens").performMouseInput { enter(center) }
            onNodeWithText("12k sent").assertExists()
            onNodeWithContentDescription("Tokens").performMouseInput { exit() }
            onNodeWithText("12k sent").assertDoesNotExist()
        }

    @Test
    fun aSplitButtonRunsItsActionAndOpensItsMenu() =
        runComposeUiTest {
            val runs = mutableListOf<String>()
            setContent {
                MacTheme {
                    SplitButton(
                        { runs += "Finder" },
                        menuDescription = "Open in",
                        menu = { close ->
                            MenuItem("Terminal", {
                                runs += "Terminal"
                                close()
                            })
                        },
                    ) { Label("Open location") }
                }
            }
            onNodeWithText("Open location").performClick()
            onNodeWithText("Terminal").assertDoesNotExist()
            onNodeWithContentDescription("Open in").performClick()
            onNodeWithText("Terminal").performClick()
            onNodeWithText("Terminal").assertDoesNotExist()
            assertEquals(listOf("Finder", "Terminal"), runs)
        }
}
