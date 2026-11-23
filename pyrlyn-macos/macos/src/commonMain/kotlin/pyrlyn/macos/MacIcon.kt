// The library's 16-point stroke icons, path for path from the Figma file's SVG assets.
package pyrlyn.macos

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One icon: its stroke paths in a 16-point box, and the stroke width the library draws it with. */
enum class MacIcon(
    val paths: List<String>,
    val stroke: Float = 1.2f,
) {
    Terminal(listOf("M3.33334 4.66667L6.66668 8L3.33334 11.3333M8.00001 12H12.6667")),
    Plus(listOf("M8 3.33333V8M8 8V12.6667M8 8H3.33334M8 8H12.6667")),
    Sparkle(
        listOf(
            "M8 2V4.66667M8 11.3333V14M2 8H4.66667M11.3333 8H14M4 4L5.66667 5.66667M10.3333 10.3333L12 12M4 12L5.66667 10.3333M10.3333 5.66667L12 4",
        ),
    ),
    Eye(
        listOf(
            "M1.33334 8C1.33334 8 3.73334 4 8.00001 4C12.2667 4 14.6667 8 14.6667 8C14.6667 8 12.2667 12 8.00001 12C3.73334 12 1.33334 8 1.33334 8Z",
            "M8.00001 9.66667C8.92048 9.66667 9.66668 8.92048 9.66668 8C9.66668 7.07953 8.92048 6.33333 8.00001 6.33333C7.07954 6.33333 6.33334 7.07953 6.33334 8C6.33334 8.92048 7.07954 9.66667 8.00001 9.66667Z",
        ),
    ),
    Server(
        listOf(
            "M12 2.66667H4C3.26362 2.66667 2.66667 3.26362 2.66667 4V6C2.66667 6.73638 3.26362 7.33333 4 7.33333H12C12.7364 7.33333 13.3333 6.73638 13.3333 6V4C13.3333 3.26362 12.7364 2.66667 12 2.66667Z",
            "M12 8.66667H4C3.26362 8.66667 2.66667 9.26362 2.66667 10V12C2.66667 12.7364 3.26362 13.3333 4 13.3333H12C12.7364 13.3333 13.3333 12.7364 13.3333 12V10C13.3333 9.26362 12.7364 8.66667 12 8.66667Z",
            "M5.33333 5H5.34M5.33333 11H5.34",
        ),
    ),
    Gear(
        listOf(
            "M8 10C9.10457 10 10 9.10457 10 8C10 6.89543 9.10457 6 8 6C6.89543 6 6 6.89543 6 8C6 9.10457 6.89543 10 8 10Z",
            "M8 2V4M8 12V14M2 8H4M12 8H14M3.73333 3.73333L5.13333 5.13333M10.8667 10.8667L12.2667 12.2667M3.73333 12.2667L5.13333 10.8667M10.8667 5.13333L12.2667 3.73333",
        ),
    ),
    Search(
        listOf(
            "M7.33333 11.3333C9.54247 11.3333 11.3333 9.54247 11.3333 7.33333C11.3333 5.12419 9.54247 3.33333 7.33333 3.33333C5.12419 3.33333 3.33333 5.12419 3.33333 7.33333C3.33333 9.54247 5.12419 11.3333 7.33333 11.3333Z",
            "M13.3333 13.3333L10.3333 10.3333",
        ),
    ),
    Branch(
        listOf(
            "M3.99999 5.66667C4.92047 5.66667 5.66666 4.92047 5.66666 4C5.66666 3.07953 4.92047 2.33333 3.99999 2.33333C3.07952 2.33333 2.33333 3.07953 2.33333 4C2.33333 4.92047 3.07952 5.66667 3.99999 5.66667Z",
            "M3.99999 13.6667C4.92047 13.6667 5.66666 12.9205 5.66666 12C5.66666 11.0795 4.92047 10.3333 3.99999 10.3333C3.07952 10.3333 2.33333 11.0795 2.33333 12C2.33333 12.9205 3.07952 13.6667 3.99999 13.6667Z",
            "M12 7.66667C12.9205 7.66667 13.6667 6.92048 13.6667 6C13.6667 5.07953 12.9205 4.33333 12 4.33333C11.0795 4.33333 10.3333 5.07953 10.3333 6C10.3333 6.92048 11.0795 7.66667 12 7.66667Z",
            "M4 5.66667V10.3333M12 7.66667C12 9.66667 9.33333 10 5.66667 11",
        ),
    ),
    Split(
        listOf(
            "M12 3.33333H4C2.89543 3.33333 2 4.22876 2 5.33333V10.6667C2 11.7712 2.89543 12.6667 4 12.6667H12C13.1046 12.6667 14 11.7712 14 10.6667V5.33333C14 4.22876 13.1046 3.33333 12 3.33333Z",
            "M8 3.33333V12.6667",
        ),
    ),
    Clone(
        listOf(
            "M8 2.66667V9.33333M5.33334 6.66667L8 9.33333L10.6667 6.66667",
            "M3.33334 10.6667V12C3.33334 12.3536 3.47381 12.6928 3.72386 12.9428C3.97391 13.1929 4.31305 13.3333 4.66667 13.3333H11.3333C11.687 13.3333 12.0261 13.1929 12.2761 12.9428C12.5262 12.6928 12.6667 12.3536 12.6667 12V10.6667",
        ),
    ),
    Document(listOf("M4.66666 2H9.33332L12.6667 5.33333V14H4.66666V2Z", "M9.33334 2V5.33333H12.6667")),
    Continue(listOf("M4 3.33333V12.6667M6.66667 3.33333L12.6667 8L6.66667 12.6667V3.33333Z")),
    Shield(listOf("M7.99999 2L13.3333 4V8C13.3333 11 11.0667 13.2 7.99999 14C4.93332 13.2 2.66666 11 2.66666 8V4L7.99999 2Z")),
    ChevronRight(listOf("M6 12L10 8L6 4"), stroke = 1.33333f),
    ChevronDown(listOf("M4 6L8 10L12 6"), stroke = 1.33333f),
    ArrowUp(listOf("M8 12.6667V3.33333M8 3.33333L3.66667 7.66667M8 3.33333L12.3333 7.66667"), stroke = 1.6f),
    Folder(
        listOf(
            "M2 4.66667C2 3.93029 2.59695 3.33333 3.33333 3.33333H6L7.33333 4.66667H12.6667C13.403 4.66667 14 5.26362 14 6V11.3333C14 12.0697 13.403 12.6667 12.6667 12.6667H3.33333C2.59695 12.6667 2 12.0697 2 11.3333V4.66667Z",
        ),
    ),
    Gauge(listOf("M3.05 11.5A5.66667 5.66667 0 1 1 12.95 11.5", "M8 9.33333L10.6667 6.33333")),

    /** The file draws it at 14 points in menus; this is its 16-point master. */
    Check(listOf("M3.33334 8.33333L6.33334 11.3333L12.6667 5")),

    /** The pop-up button's up-and-down chevrons, drawn in a 10 × 14 box at the top left of the 16-point one. */
    Chevrons(listOf("M2 5L5 2L8 5M2 9L5 12L8 9"), stroke = 1.5f),
}

private const val VIEWPORT = 16f

private fun MacIcon.vector(): ImageVector =
    ImageVector
        .Builder(name, 16.dp, 16.dp, VIEWPORT, VIEWPORT)
        .apply {
            for (data in paths) {
                addPath(
                    addPathNodes(data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

private val VECTORS = MacIcon.entries.associateWith { it.vector() }

/** A decorative icon: the words beside it say what it means. [tint] defaults to `text/secondary`. */
@Composable
fun Icon(
    icon: MacIcon,
    modifier: Modifier = Modifier,
    tint: Color = Mac.colors.textSecondary,
    size: Dp = MacSize.icon,
) {
    Image(
        rememberVectorPainter(VECTORS.getValue(icon)),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
