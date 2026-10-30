package pyrlyn.fluent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import pyrlyn.glass.Glyph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ControlsTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) = FluentTheme(FluentColors.Light, content = content)

    @Test
    fun aButtonCallsBackOnlyWhenEnabled() =
        runComposeUiTest {
            var clicks = 0
            var enabled by mutableStateOf(true)
            setContent { Themed { FluentButton("Go", ButtonStyle.Accent, Modifier.testTag("go"), enabled) { clicks++ } } }
            onNodeWithTag("go").assertIsEnabled().performClick()
            assertEquals(1, clicks)
            enabled = false
            waitForIdle()
            onNodeWithTag("go").assertIsNotEnabled().performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun aTerminalBlockShowsTheCommandAndAlignsItsLines() =
        runComposeUiTest {
            setContent { Themed { TerminalOutput("ketch why rg", listOf(TerminalLine("asset", "rg.zip"), TerminalLine("sha256", "ok", good = true)), Modifier.testTag("out")) } }
            onNodeWithTag("out").assertTextEquals("> ketch why rg\nasset  rg.zip\nsha256 ok")
        }

    @Test
    fun anIconButtonIsNamedForScreenReaders() =
        runComposeUiTest {
            var clicks = 0
            setContent { Themed { FluentIconButton(Glyph.Copy, "Copy command") { clicks++ } } }
            onNodeWithContentDescription("Copy command").performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun aToggleSaysItsStateInWords() =
        runComposeUiTest {
            var on by mutableStateOf(false)
            setContent { Themed { ToggleSwitch(on, "Notify", Modifier.testTag("toggle")) { on = it } } }
            onNodeWithText("Off").assertExists()
            onNodeWithTag("toggle").assertIsOff().performClick()
            waitForIdle()
            onNodeWithTag("toggle").assertIsOn()
            onNodeWithText("On").assertExists()
        }

    @Test
    fun aSearchBoxReportsWhatIsTyped() =
        runComposeUiTest {
            var query by mutableStateOf("")
            setContent { Themed { SearchBox(query, "Search", Modifier.testTag("search")) { query = it } } }
            onNodeWithTag("search").performTextInput("rip")
            waitForIdle()
            assertEquals("rip", query)
        }

    @Test
    fun aSearchBoxTakesATextTheCallerSets() =
        runComposeUiTest {
            var query by mutableStateOf("rip")
            setContent { Themed { SearchBox(query, "Search", Modifier.testTag("search")) { query = it } } }
            query = ""
            waitForIdle()
            onNodeWithText("Search").assertExists()
        }

    @Test
    fun aComboBoxPicksFromItsMenu() =
        runComposeUiTest {
            var picked by mutableStateOf("Every hour")
            setContent { Themed { ComboBox(listOf("Every hour", "Every day"), picked, { it }, Modifier.testTag("combo")) { picked = it } } }
            onNodeWithTag("combo").performClick()
            waitForIdle()
            onNodeWithText("Every day").performClick()
            waitForIdle()
            assertEquals("Every day", picked)
        }

    @Test
    fun aSliderHasItsNameAndItsRange() =
        runComposeUiTest {
            setContent { Themed { FluentSlider(5f, 0f..20f, "Wash") {} } }
            onNodeWithContentDescription("Wash").assertRangeInfoEquals(ProgressBarRangeInfo(5f, 0f..20f))
        }

    @Test
    fun radioButtonsMarkTheChosenOne() =
        runComposeUiTest {
            var picked by mutableStateOf("Mica")
            setContent {
                Themed {
                    RadioButtons(listOf("Mica", "Acrylic"), picked, { it }, optionModifier = { Modifier.testTag(it) }) { picked = it }
                }
            }
            onNodeWithTag("Mica").assertIsSelected()
            onNodeWithTag("Acrylic").assertIsNotSelected().performClick()
            waitForIdle()
            onNodeWithTag("Acrylic").assertIsSelected()
        }

    @Test
    fun aSwatchSaysItIsSelected() =
        runComposeUiTest {
            var picks = 0
            setContent { Themed { Swatch(Color.Red, "Red", selected = true) { picks++ } } }
            onNodeWithContentDescription("Red").assertIsSelected().performClick()
            assertEquals(1, picks)
        }

    @Test
    fun aSelectorBarSwitchesViews() =
        runComposeUiTest {
            var picked by mutableStateOf("Overview")
            setContent { Themed { SelectorBar(listOf("Overview", "Files"), picked, { it }, itemModifier = { Modifier.testTag(it) }) { picked = it } } }
            onNodeWithTag("Overview").assertIsSelected()
            onNodeWithTag("Files").performClick()
            waitForIdle()
            onNodeWithTag("Files").assertIsSelected()
        }

    @Test
    fun aNavItemIsSelectedAndShowsItsCount() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                Themed {
                    NavItem("Updates", true, Modifier.testTag("nav"), badge = 3, badgeUpdate = true, leading = { NavIcon(Glyph.Refresh, true) }) { clicks++ }
                }
            }
            onNodeWithTag("nav").assertIsSelected().performClick()
            onNodeWithText("3").assertExists()
            assertEquals(1, clicks)
        }

    @Test
    fun captionButtonsAreNamedAndTheMaximizeOneFollowsTheWindow() =
        runComposeUiTest {
            var maximized by mutableStateOf(false)
            var closed = false
            setContent { Themed { Row { CaptionButtons(maximized, {}, { maximized = !maximized }, { closed = true }) } } }
            onNodeWithContentDescription("Maximize").performClick()
            waitForIdle()
            onNodeWithContentDescription("Restore").assertExists()
            onNodeWithContentDescription("Minimize").assertExists()
            onNodeWithContentDescription("Close").performClick()
            assertTrue(closed)
        }

    @Test
    fun theFrameClicksACaptionButtonThatTheContentNeverSaw() =
        runComposeUiTest {
            val zones = WindowZones()
            var maximized by mutableStateOf(false)
            setContent {
                CompositionLocalProvider(LocalWindowZones provides zones) {
                    Themed { Row { CaptionButtons(maximized, {}, { maximized = !maximized }, {}) } }
                }
            }
            waitForIdle()
            zones.press(WindowZone.Maximize)
            zones.release(WindowZone.Maximize)
            waitForIdle()
            onNodeWithContentDescription("Restore").assertExists()
        }

    @Test
    fun aFlyoutShowsHowFarItIs() =
        runComposeUiTest {
            setContent { Themed { ActivityFlyout("Upgrading ripgrep", status = "Verifying", percent = 72) } }
            onNodeWithText("72%").assertExists()
        }

    @Test
    fun aFlyoutWithoutAPercentShowsNone() =
        runComposeUiTest {
            setContent { Themed { ActivityFlyout("Updating") } }
            onNodeWithText("Updating").assertExists()
        }

    @Test
    fun aFlyoutNoticeShowsItsWordsAndAction() =
        runComposeUiTest {
            var clicks = 0
            setContent { Themed { FlyoutNotice("Something went wrong", "The download stopped.", action = { FluentButton("Dismiss") { clicks++ } }) } }
            onNodeWithText("The download stopped.").assertExists()
            onNodeWithText("Dismiss").performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun factsListTheirNamesAndValues() =
        runComposeUiTest {
            setContent { Themed { FactsList(listOf("Installed" to "14.1.0", "Linked to" to "C:\\bin")) } }
            onNodeWithText("Linked to").assertExists()
            onNodeWithText("C:\\bin").assertTextEquals("C:\\bin")
        }

    @Test
    fun aDialogClosesFromTheSmokeButNotFromItself() =
        runComposeUiTest {
            var dismissed = 0
            setContent {
                Themed {
                    ContentDialog("Uninstall ripgrep?", { dismissed++ }, { FluentButton("Cancel", onClick = {}) }, Modifier.testTag("dialog")) {}
                }
            }
            onNodeWithTag("dialog").performClick()
            waitForIdle()
            assertEquals(0, dismissed)
        }

    @Test
    fun aColourForANameIsAlwaysTheSame() {
        assertEquals(IconGradient.of("ripgrep"), IconGradient.of("ripgrep"))
    }

    @Test
    fun letteredIconsNeverTakeTheStatusColours() {
        val status = setOf(IconGradient.Green, IconGradient.Amber, IconGradient.Red)
        assertFalse(listOf("a", "bb", "ripgrep", "fd", "uv", "zoxide", "starship", "lazygit").any { IconGradient.of(it) in status })
    }

    @Test
    fun anAccentBringsItsOwnStatesAndInk() {
        val light = FluentColors.Light.withAccent(Color(0xFF3DDCB0))
        assertEquals(Color(0xFF3DDCB0), light.accent)
        assertNotEquals(light.accent, light.accentHover)
        assertNotEquals(light.accent, light.accentPressed)
        assertNotEquals(Color.White, light.accentOn)
        assertEquals(Color.White, FluentColors.Light.withAccent(Color(0xFF12305C)).accentOn)
    }

    @Test
    fun aComboBoxTakesOnlyTheWidthItNeeds() =
        runComposeUiTest {
            setContent { Themed { Box(Modifier.width(600.dp)) { ComboBox(listOf("Daily"), "Daily", { it }, Modifier.testTag("combo")) {} } } }
            onNodeWithTag("combo").assertWidthIsEqualTo(200.dp)
        }

    @Test
    fun aStatCardFillsItsCell() =
        runComposeUiTest {
            setContent { Themed { Box(Modifier.width(600.dp)) { StatCard("1", "failed", Glyph.Cross, IconGradient.Red, Modifier.testTag("stat")) } } }
            onNodeWithTag("stat").assertWidthIsEqualTo(600.dp)
        }

    @Test
    fun theWashIsATintWhenThereIsOne() {
        val tinted = FluentColors.Light.copy(tint = Color.Red, wash = 0.1f).washLayer
        assertEquals(Color.Red.copy(alpha = 0.1f), tinted)
        assertEquals(
            0.1f,
            FluentColors.Light
                .copy(tint = null, wash = 0.1f)
                .washLayer.alpha,
            0.01f,
        )
    }

    @Test
    fun theWindowFollowsItsMaterial() {
        val light = FluentColors.Light
        assertEquals(light.windowMica, light.copy(material = WindowMaterial.Mica).window)
        assertEquals(light.windowAcrylic, light.copy(material = WindowMaterial.Acrylic).window)
        assertEquals(light.windowClear, light.copy(material = WindowMaterial.Transparent).window)
    }
}
