// The sizes and colours are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// What the window stands on: the wall, the glass over it, and the round icons. Compose has no live backdrop blur, so
// the wall blurs itself and the glass is a translucent fill over it, which reads the same because the wall never moves.

package pyrlyn.adwaita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The design system's corners. */
object Corners {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Window = RoundedCornerShape(15.dp)
    val Large = RoundedCornerShape(16.dp)
}

/** CSS's `linear-gradient([degrees], …)`: the line runs through the middle and its ends lie past the corners. */
internal fun cssGradient(
    degrees: Float,
    vararg stops: Pair<Float, Color>,
): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val angle = degrees * PI.toFloat() / 180f
            val across = abs(size.width * sin(angle)) + abs(size.height * cos(angle))
            val middle = Offset(size.width / 2, size.height / 2)
            val half = Offset(sin(angle), -cos(angle)) * (across / 2)
            return LinearGradientShader(middle - half, middle + half, stops.map { it.second }, stops.map { it.first })
        }
    }

private val WALL = cssGradient(150f, 0f to Color(0xFF171A35), 0.45f to Color(0xFF241B4A), 1f to Color(0xFF0E2C3A))

/** One of CSS's elliptical `radial-gradient`s: [color] at [centre], gone by [reach] of the ellipse [radii] of the size. */
private fun DrawScope.glow(
    color: Color,
    centre: Offset,
    radii: Size,
    reach: Float,
) {
    val rx = size.width * radii.width
    val ry = size.height * radii.height
    val at = Offset(size.width * centre.x, size.height * centre.y)
    scale(1f, ry / rx, at) {
        drawCircle(Brush.radialGradient(0f to color, reach to color.copy(alpha = 0f), center = at, radius = rx), rx, at)
    }
}

/** A lit sphere of the wall: CSS's `radial-gradient(circle at x y, …)`, with its highlight off the middle. */
private fun DrawScope.orb(
    middle: Offset,
    radius: Float,
    light: Offset,
    vararg stops: Pair<Float, Color>,
) {
    val box = middle - Offset(radius, radius)
    drawCircle(
        Brush.radialGradient(*stops, center = box + light * (radius * 2), radius = radius * 2),
        radius,
        middle,
    )
}

/** The wall under the window, blurred as the design's window blurs the desktop behind it. Fills what it is given. */
@Composable
fun Wall(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF171A35))) {
        Canvas(Modifier.fillMaxSize().blur(36.dp, BlurredEdgeTreatment.Rectangle)) {
            drawRect(WALL)
            glow(Color(0xFF3A2A7A), Offset(0.15f, 0.1f), Size(1.2f, 0.9f), 0.55f)
            glow(Color(0xFF0C6B62), Offset(0.9f, 0.85f), Size(0.9f, 0.8f), 0.6f)
            val unit = size.minDimension
            orb(
                Offset(size.width * 0.86f, size.height * 0.2f),
                unit * 0.2f,
                Offset(0.32f, 0.28f),
                0f to Color(0xFFFFD0C4),
                0.28f to Color(0xFFFF7A6B),
                0.62f to Color(0xFFB2345F),
                1f to Color(0xFF3B1340),
            )
            orb(
                Offset(size.width * 0.08f, size.height * 0.82f),
                unit * 0.17f,
                Offset(0.3f, 0.26f),
                0f to Color(0xFFE2FFF6),
                0.3f to Color(0xFF3DDCB0),
                0.66f to Color(0xFF0F7F6C),
                1f to Color(0xFF0A2F33),
            )
            orb(
                Offset(size.width * 0.5f, size.height * 1.02f),
                unit * 0.13f,
                Offset(0.34f, 0.3f),
                0f to Color(0xFFDCD4FF),
                0.34f to Color(0xFF8D72FF),
                0.7f to Color(0xFF3F2A9C),
                1f to Color(0xFF1A1240),
            )
        }
    }
}

/** The window's glass over the [Wall]. */
@Composable
fun WindowGlass(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(LocalAdwaita.current.glass))
}

/** A glass panel: a boxed list, an aside, a tile. */
@Composable
fun Modifier.boxed(shape: Shape = Corners.Medium): Modifier {
    val c = LocalAdwaita.current
    return dropShadow(shape, Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.18f), offset = DpOffset(0.dp, 10.dp)))
        .clip(shape)
        .background(c.fill1)
        .border(1.dp, c.line, shape)
}

/** How far a floating surface stands off the window. */
internal enum class Lift(
    val radius: Int,
    val offset: Int,
    val alpha: Float,
) {
    Menu(48, 24, 0.5f),
    Dialog(70, 30, 0.6f),
    Toast(34, 16, 0.5f),
}

/** A floating surface's soft drop and its lit top edge. */
internal fun Modifier.floating(
    shape: Shape,
    lift: Lift,
    fill: Color,
    highlight: Float = 0.22f,
): Modifier =
    dropShadow(shape, Shadow(radius = lift.radius.dp, color = Color.Black.copy(alpha = lift.alpha), offset = DpOffset(0.dp, lift.offset.dp)))
        .clip(shape)
        .background(fill)
        .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = highlight), offset = DpOffset(0.dp, 1.dp)))

/** The design system's icon gradients, CSS's 155 degrees top to bottom. */
enum class IconGradient(
    val top: Color,
    val bottom: Color,
) {
    Blue(Color(0xFF6FB1FF), Color(0xFF2563D9)),
    Orange(Color(0xFFFFC06B), Color(0xFFE8761E)),
    Magenta(Color(0xFFF58AD9), Color(0xFFB8338F)),
    Violet(Color(0xFFB49CFF), Color(0xFF6A43D6)),
    Coral(Color(0xFFFF9A8A), Color(0xFFD9473A)),
    Cyan(Color(0xFF7CE6F5), Color(0xFF1B97B8)),
    Mint(Color(0xFF7FF2CC), Color(0xFF1EA37C)),
    Night(Color(0xFF5A6488), Color(0xFF232843)),
    ;

    val brush: Brush = cssGradient(155f, 0f to top, 1f to bottom)

    companion object {
        /** Night stays out of it: it is the app's own, for the mark. */
        private val LETTERED = entries - Night

        /** A colour for what has no artwork, the same every time for the same [name]. */
        fun of(name: String) = LETTERED[name.hashCode().mod(LETTERED.size)]
    }
}

/** The app icon's three sizes. */
enum class AvatarSize(
    val box: Int,
    val text: Int,
) {
    Small(40, 13),
    Normal(52, 16),
    Dialog(72, 24),
    Large(96, 30),
}

private fun Modifier.sphere(
    gradient: IconGradient,
    highlight: Float,
    well: Int,
    drop: Int,
) = dropShadow(CircleShape, Shadow(radius = (drop * 2).dp, color = Color.Black.copy(alpha = 0.28f), offset = DpOffset(0.dp, drop.dp)))
    .clip(CircleShape)
    .background(gradient.brush)
    .innerShadow(CircleShape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = highlight), offset = DpOffset(0.dp, 2.dp)))
    .innerShadow(CircleShape, Shadow(radius = (well * 2).dp, color = Color.Black.copy(alpha = 0.26f), offset = DpOffset(0.dp, (-well).dp)))

/** An app's icon: [letters] on a sphere. Decorative, its name stands beside it. */
@Composable
fun AppAvatar(
    letters: String,
    gradient: IconGradient,
    size: AvatarSize = AvatarSize.Normal,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size.box.dp).sphere(gradient, 0.45f, 5, 8), contentAlignment = Alignment.Center) {
        Text(letters, color = Color.White, style = LocalAdwaitaType.current.mono.copy(fontSize = size.text.sp), textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** A row's round icon. */
@Composable
fun IconSphere(
    glyph: Glyph,
    gradient: IconGradient,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Box(modifier.size(if (small) 32.dp else 40.dp).sphere(gradient, 0.4f, 4, 6), contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, Color.White, if (small) 16.dp else 20.dp)
    }
}

/** The colour a page's opening panel stands in. */
enum class PanelTint(
    internal val brush: Brush,
    internal val highlight: Float,
    internal val depth: Float,
) {
    Mint(
        cssGradient(120f, 0f to Color(0xD90A3D36), 0.6f to Color(0x9913584F), 1f to Color(0x4D3DDCB0)),
        highlight = 0.3f,
        depth = 0.25f,
    ),
    Blue(
        cssGradient(120f, 0f to Color(0x472563D9), 1f to Color(0x0DFFFFFF)),
        highlight = 0.22f,
        depth = 0.22f,
    ),
}
