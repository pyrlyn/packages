package pyrlyn.glass

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ControlsTest {
    private val colors =
        GlassColors(
            dark = false,
            ink = Color.Black,
            ink2 = Color.DarkGray,
            accent = Color.Blue,
            accentPressed = Color.Blue,
            accentSubtle = Color.Cyan,
            accentOn = Color.White,
            accentInk = Color.Blue,
            success = Color.Green,
            successSubtle = Color.Green,
            attention = Color.Red,
            attentionSubtle = Color.Red,
            attentionFill = Color.Red,
            attentionOn = Color.White,
            progress = Color.Blue,
            progressSubtle = Color.Cyan,
            warning = Color.Yellow,
            warningSubtle = Color.Yellow,
            danger = Color.Red,
            dangerSubtle = Color.Red,
            base = Color.White,
            control = Color.White,
            highlight = Color.White,
            shade = Color.Gray,
            solid = Color.White,
            separator = Color.Gray,
            fill = Color.LightGray,
            hover = Color.LightGray,
            track = Color.LightGray,
            focus = Color.Blue,
            scrim = Color.Gray,
        )

    @Composable
    private fun Themed(content: @Composable () -> Unit) = GlassTheme(colors, content)

    @Test
    fun aClickEarnsNoFocusRingButTheKeyboardDoes() =
        runComposeUiTest {
            val source = MutableInteractionSource()
            var ring = false
            setContent { ring = keyboardFocus(source) }
            waitForIdle()
            val focus = FocusInteraction.Focus()
            source.tryEmit(focus)
            waitForIdle()
            assertTrue(ring)
            source.tryEmit(PressInteraction.Press(Offset.Zero))
            waitForIdle()
            assertFalse(ring)
            source.tryEmit(FocusInteraction.Unfocus(focus))
            source.tryEmit(FocusInteraction.Focus())
            waitForIdle()
            assertTrue(ring)
        }

    @Test
    fun aSwatchSaysItIsSelected() =
        runComposeUiTest {
            var picks = 0
            setContent { Themed { Swatch(Color.Red, "red", selected = true, Modifier.testTag("swatch")) { picks++ } } }
            onNodeWithTag("swatch").assertIsSelected().performClick()
            assertEquals(1, picks)
        }

    @Test
    fun aSliderHasItsNameAndItsRange() =
        runComposeUiTest {
            setContent { Themed { GlassSlider(5f, 0f..20f, "Wash", Modifier.testTag("slider")) {} } }
            onNodeWithContentDescription("Wash").assertRangeInfoEquals(ProgressBarRangeInfo(5f, 0f..20f))
        }

    @Test
    fun aButtonCallsBackOnlyWhenEnabled() =
        runComposeUiTest {
            var clicks = 0
            var enabled by mutableStateOf(true)
            setContent { Themed { GlassButton("Go", ButtonKind.Primary, Modifier.testTag("go"), enabled) { clicks++ } } }
            onNodeWithTag("go").assertIsEnabled().performClick()
            assertEquals(1, clicks)
            enabled = false
            waitForIdle()
            onNodeWithTag("go").assertIsNotEnabled().performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun aSwitchSaysItsStateAndReportsTheNewOne() =
        runComposeUiTest {
            var on by mutableStateOf(false)
            setContent { Themed { GlassSwitch(on, "Sounds") { on = it } } }
            onNodeWithContentDescription("Sounds").assertIsOff().performClick()
            assertEquals(true, on)
            onNodeWithContentDescription("Sounds").assertIsOn()
        }

    @Test
    fun aSegmentChoosesItsOption() =
        runComposeUiTest {
            var picked by mutableStateOf("One")
            setContent { Themed { Segmented(listOf("One", "Two"), picked, { it }) { picked = it } } }
            onNodeWithText("Two").performClick()
            assertEquals("Two", picked)
        }

    @Test
    fun anIconButtonIsNamedForScreenReaders() =
        runComposeUiTest {
            var clicks = 0
            setContent { Themed { GlassIconButton(Glyph.Copy, "Copy the command") { clicks++ } } }
            onNodeWithContentDescription("Copy the command").performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun theGridNeverUnderfillsItsColumns() {
        assertEquals(3, autoColumns(860.dp, 260.dp, 16.dp, count = 3))
        assertEquals(2, autoColumns(600.dp, 260.dp, 16.dp, count = 3))
        assertEquals(1, autoColumns(100.dp, 260.dp, 16.dp, count = 3))
        // Cells stretch to the row rather than leaving an empty column, as CSS's auto-fit does.
        assertEquals(2, autoColumns(2000.dp, 260.dp, 16.dp, count = 2))
        assertEquals(1, autoColumns(2000.dp, 260.dp, 16.dp, count = 0))
    }

    @Test
    fun everyPaneHasALookInEitherAppearance() {
        for (dark in listOf(false, true)) {
            val glass = colors.copy(dark = dark)
            for (pane in Pane.entries) assert(glass.pane(pane).lift.isNotEmpty()) { pane }
        }
    }

    @Test
    fun aBadgeShowsItsWordsInEveryKind() =
        runComposeUiTest {
            setContent { Themed { Column { for (kind in BadgeKind.entries) Badge(kind.name, kind) } } }
            for (kind in BadgeKind.entries) onNodeWithText(kind.name).assertExists()
        }
}
