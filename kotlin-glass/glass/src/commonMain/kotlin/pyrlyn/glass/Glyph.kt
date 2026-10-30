// Stroke icons as vectors, in a 24-point box.
package pyrlyn.glass

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph(
    val paths: List<String>,
) {
    Compass(listOf(CIRCLE_9, "m15.5 8.5-2 5-5 2 2-5z")),
    Box(listOf("M21 8 12 3 3 8v8l9 5 9-5z", "m3 8 9 5 9-5M12 13v8")),
    Refresh(listOf("M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5")),
    Pulse(listOf("M3 12h4l3-8 4 16 3-8h4")),
    Shield(listOf("M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z", "m9 12 2 2 4-4")),
    Gear(listOf("M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z", "M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1")),
    Back(listOf("m15 18-6-6 6-6")),
    Forward(listOf("m9 18 6-6-6-6")),
    Search(listOf("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z", "m20 20-3.5-3.5")),
    Copy(listOf("M11.5 9h7A2.5 2.5 0 0 1 21 11.5v7a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 9 18.5v-7A2.5 2.5 0 0 1 11.5 9z", "M5 15V5a2 2 0 0 1 2-2h10")),
    Window(listOf("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z", "M3 9h18")),
    Undo(listOf("M3 12a9 9 0 1 0 3-6.7", "M3 4v5h5")),
    Lock(listOf("M7.5 11h9A2.5 2.5 0 0 1 19 13.5v5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18.5v-5A2.5 2.5 0 0 1 7.5 11z", "M8 11V7a4 4 0 0 1 8 0v4")),
    Check(listOf("m5 12 5 5 9-10")),
    Warning(listOf("M12 8v5M12 16.5h.01", "M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z")),
    Cross(listOf("M18 6 6 18M6 6l12 12")),
    ArrowBack(listOf("M20 12H4", "m10 6-6 6 6 6")),
    ChevronDown(listOf("m6 9 6 6 6-6")),
    Download(listOf("M12 3v12", "m7 10 5 5 5-5", "M5 21h14")),
    Bell(listOf("M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.9 1.9 0 0 0 3.4 0")),
    Tiles(listOf("M4.5 3h5A1.5 1.5 0 0 1 11 4.5v5A1.5 1.5 0 0 1 9.5 11h-5A1.5 1.5 0 0 1 3 9.5v-5A1.5 1.5 0 0 1 4.5 3z", "M14.5 3h5A1.5 1.5 0 0 1 21 4.5v5a1.5 1.5 0 0 1-1.5 1.5h-5A1.5 1.5 0 0 1 13 9.5v-5A1.5 1.5 0 0 1 14.5 3z", "M4.5 13h5a1.5 1.5 0 0 1 1.5 1.5v5A1.5 1.5 0 0 1 9.5 21h-5A1.5 1.5 0 0 1 3 19.5v-5A1.5 1.5 0 0 1 4.5 13z", "M14.5 13h5a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5h-5a1.5 1.5 0 0 1-1.5-1.5v-5a1.5 1.5 0 0 1 1.5-1.5z")),
    Folder(listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")),
    Document(listOf("M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z", "M14 3v6h6M8 13h8M8 17h5")),
    Contrast(listOf(CIRCLE_9, "M12 3v18")),
    Palette(listOf("M12 22a10 10 0 1 1 10-10c0 2.5-2 3-3.5 3H16a2 2 0 0 0-1.5 3.3A2 2 0 0 1 12 22z", "M7.5 9.5a1 1 0 1 0 0 2 1 1 0 0 0 0-2z", "M12 6a1 1 0 1 0 0 2 1 1 0 0 0 0-2z", "M16.5 9.5a1 1 0 1 0 0 2 1 1 0 0 0 0-2z")),
    Waves(listOf("M3 17c3-4 6-4 9 0s6 4 9 0", "M3 11c3-4 6-4 9 0s6 4 9 0")),
    Info(listOf("M12 8h.01", "M12 11v5")),
}

private const val CIRCLE_9 = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"
private const val VIEWPORT = 24f
private const val STROKE = 1.8f

private fun Glyph.vector() =
    ImageVector
        .Builder(name, 24.dp, 24.dp, VIEWPORT, VIEWPORT)
        .apply {
            for (data in paths) {
                addPath(addPathNodes(data), stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()

private val VECTORS = Glyph.entries.associateWith { it.vector() }

/** A decorative icon: the words beside it say what it means. */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    tint: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) = Icon(VECTORS.getValue(glyph), contentDescription = null, tint = tint, modifier = modifier.size(size))
