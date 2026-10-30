package pyrlyn.adwaita

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class SurfacesTest {
    @Test
    fun theWallCoversAllItIsGivenAndKeepsItsColours() =
        runComposeUiTest {
            setContent { Wall(Modifier.size(200.dp, 120.dp).testTag("wall")) }
            val pixels = onNodeWithTag("wall").captureToImage().toPixelMap()
            val corners = listOf(pixels[0, 0], pixels[pixels.width - 1, 0], pixels[0, pixels.height - 1], pixels[pixels.width - 1, pixels.height - 1])
            for (corner in corners) assertEquals(1f, corner.alpha)
            assertNotEquals(corners.first(), corners.last())
        }

    @Test
    fun aWallWithNoRoomLeftDrawsNothingAndDoesNotFail() =
        runComposeUiTest {
            setContent { Wall(Modifier.size(0.dp).testTag("wall")) }
            onNodeWithTag("wall").assertExists()
        }
}
