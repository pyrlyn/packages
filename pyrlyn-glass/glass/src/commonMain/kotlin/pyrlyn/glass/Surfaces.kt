// The colours are the design's own hex values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// What the look is made of: the wallpaper the glass sits on, the panes with their lit edge, the tilt of a card, and
// the extruded icon. The wallpaper is soft and still, so a translucent pane over it reads as frosted glass with no live
// backdrop blur, which Compose has no way to draw.

package pyrlyn.glass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The picture behind the window's glass: washes of colour with soft orbs over [GlassColors.base]. */
@Composable
fun Wallpaper(modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val strength = glass.wash
    Box(modifier.fillMaxSize().background(glass.base)) {
        Canvas(Modifier.fillMaxSize().blur(WALLPAPER_BLUR, BlurredEdgeTreatment.Rectangle)) {
            val fit = size.width / DESIGN_WIDTH
            for (wash in if (glass.dark) DARK_WASHES else LIGHT_WASHES) {
                glow(wash.color.copy(alpha = strength.coerceAtMost(1f)), Offset(size.width * wash.x, size.height * wash.y), wash.rx * fit, wash.ry * fit)
            }
            for (orb in ORBS) drawOrb(orb, strength)
        }
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(glass.windowStops, start = Offset.Zero, end = Offset(WINDOW_SHEEN, WINDOW_SHEEN))))
    }
}

private class Wash(
    val color: Color,
    val x: Float,
    val y: Float,
    val rx: Float,
    val ry: Float,
)

private class Orb(
    val light: Color,
    val deep: Color,
    val x: Float,
    val y: Float,
    val diameter: Float,
    val alpha: Float,
)

/** CSS's `radial-gradient(rx ry at center, color, transparent 60%)`. */
fun DrawScope.glow(
    color: Color,
    center: Offset,
    rx: Float,
    ry: Float,
) {
    withTransform({ scale(1f, ry / rx, pivot = center) }) {
        drawCircle(Brush.radialGradient(0f to color, GLOW_FADE to color.copy(alpha = 0f), center = center, radius = rx), rx, center)
    }
}

private fun DrawScope.drawOrb(
    orb: Orb,
    strength: Float,
) {
    val radius = orb.diameter * size.width / DESIGN_WIDTH / 2
    val center = Offset(size.width * orb.x, size.height * orb.y)
    val light = Offset(center.x - radius * ORB_LIGHT, center.y - radius * ORB_LIGHT)
    drawCircle(Brush.radialGradient(listOf(orb.light, orb.deep), center = light, radius = radius * 2 * ORB_REACH), radius, center, alpha = (orb.alpha * strength).coerceAtMost(1f))
}

private val LIGHT_WASHES =
    listOf(
        Wash(Color(0xFFBFE6FF), 0.12f, 0.08f, 1100f, 700f),
        Wash(Color(0xFFC8F2E4), 0.92f, 0.18f, 900f, 640f),
        Wash(Color(0xFFFFD9CF), 0.70f, 1f, 800f, 600f),
        Wash(Color(0xFFD9DCFF), 0.06f, 0.96f, 700f, 520f),
    )

private val DARK_WASHES =
    listOf(
        Wash(Color(0xFF0A3A66), 0.12f, 0.08f, 1100f, 700f),
        Wash(Color(0xFF0B4A40), 0.92f, 0.18f, 900f, 640f),
        Wash(Color(0xFF4A1F2A), 0.70f, 1f, 800f, 600f),
    )

private val ORBS =
    listOf(
        Orb(Color(0xFF4FB3FF), Color(0xFF0873E0), 0.24f, 0.42f, 520f, 0.55f),
        Orb(Color(0xFF7FF0CC), Color(0xFF1FA88A), 0.79f, 0.18f, 380f, 0.55f),
        Orb(Color(0xFFFFB4A8), Color(0xFFFF6B5B), 0.68f, 0.84f, 300f, 0.5f),
        Orb(Color(0xFFC9BDFF), Color(0xFF6E56CF), 0.46f, 0.57f, 240f, 0.35f),
    )

/** Fills the node with [pane]'s glass: its drop shadow, its gradient, the rim, the lit top and shaded bottom edge. */
@Composable
fun Modifier.glass(
    pane: Pane,
    shape: Shape = RoundedCornerShape(pane.radius),
): Modifier {
    val glass = LocalGlass.current
    val look = glass.pane(pane)
    val thin = if (glass.clear) CLEAR_GLASS else 1f
    var out = this
    for (lift in look.lift) out = out.dropShadow(shape, Shadow(radius = lift.blur, color = lift.color, offset = lift.offset))
    val fill = Brush.verticalGradient(listOf(look.top.copy(alpha = look.top.alpha * thin), look.bottom.copy(alpha = look.bottom.alpha * thin)))
    out = out.clip(shape).background(fill)
    glass.tint?.let { out = out.background(it.copy(alpha = glass.tintOpacity * thin)) }
    return out
        .border(1.dp, look.rim, shape)
        .innerShadow(shape, Shadow(radius = 0.dp, color = glass.highlight, offset = DpOffset(0.dp, EDGE_LIGHT)))
        .innerShadow(shape, Shadow(radius = 0.dp, color = glass.shade, offset = DpOffset(0.dp, -EDGE_SHADE)))
}

/** A card leaning back that lifts toward the pointer. */
@Composable
fun Modifier.tilt(source: MutableInteractionSource = remember { MutableInteractionSource() }): Modifier {
    val hovered by source.collectIsHoveredAsState()
    val back by animateFloatAsState(if (hovered) TILT_HOVER else TILT_REST, tween(TILT_MILLIS))
    val turn by animateFloatAsState(if (hovered) TILT_TURN else 0f, tween(TILT_MILLIS))
    val rise by animateDpAsState(if (hovered) -TILT_RISE else 0.dp, tween(TILT_MILLIS))
    return hoverable(source).graphicsLayer {
        rotationX = back
        rotationY = turn
        translationY = rise.toPx()
        transformOrigin = TransformOrigin(0.5f, 1f)
        cameraDistance = PERSPECTIVE * density
    }
}

enum class IconSize(
    val side: Dp,
    val radius: Dp,
    val text: Int,
    val depth: Int,
) {
    Small(40.dp, 12.dp, 14, 3),
    Medium(56.dp, 16.dp, 18, 5),
    Large(96.dp, 26.dp, 30, 5),
}

private val ICONS =
    listOf(
        Color(0xFF5AA8FF) to Color(0xFF0858C4),
        Color(0xFFFFB36B) to Color(0xFFE46C0A),
        Color(0xFF9B8CFF) to Color(0xFF5A3FC0),
        Color(0xFFD48CFF) to Color(0xFF8A3FC0),
        Color(0xFFFF8A7D) to Color(0xFFD9473A),
        Color(0xFF5FD3F0) to Color(0xFF0E86A8),
        Color(0xFF7FF0CC) to Color(0xFF139A7C),
        Color(0xFF3B4A6B) to Color(0xFF141C2E),
    )

/** An icon for what has no artwork: the first letters of [name] on a colour it picks, the same every time. */
@Composable
fun LetterIcon(
    name: String,
    size: IconSize,
    modifier: Modifier = Modifier,
) {
    val (top, bottom) = ICONS[name.hashCode().mod(ICONS.size)]
    val shape = RoundedCornerShape(size.radius)
    var layers = modifier.size(size.side).dropShadow(shape, Shadow(radius = EXTRUDE_BLUR, color = EXTRUDE_SHADOW, offset = DpOffset(0.dp, EXTRUDE_DROP)))
    for (step in size.depth downTo 1) layers = layers.dropShadow(shape, Shadow(radius = 0.dp, color = Color.Black.copy(alpha = EXTRUDE_STEP), offset = DpOffset(0.dp, step.dp)))
    Box(
        layers
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(ICON_SLANT, Float.POSITIVE_INFINITY)))
            .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = ICON_SHEEN), offset = DpOffset(0.dp, EDGE_LIGHT))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(ICON_INSET)
                .fillMaxWidth()
                .fillMaxHeight(ICON_SHINE)
                .clip(RoundedCornerShape(size.radius - ICON_INSET))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = ICON_SHINE_ALPHA), Color.Transparent))),
        )
        Text(name.take(2), color = Color.White, style = mono(size.text, FontWeight.Bold))
    }
}

/** A rounded colour block with an extrusion, under a white glyph. */
@Composable
fun ColorTile(
    top: Color,
    bottom: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(TILE_RADIUS)
    Box(
        Modifier
            .size(TILE_SIZE)
            .dropShadow(shape, Shadow(radius = EXTRUDE_BLUR, color = EXTRUDE_SHADOW, offset = DpOffset(0.dp, EXTRUDE_DROP)))
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(ICON_SLANT, Float.POSITIVE_INFINITY)))
            .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = ICON_SHEEN), offset = DpOffset(0.dp, EDGE_LIGHT))),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Text a person might type or compare: versions, paths, commands. */
fun mono(
    size: Int,
    weight: FontWeight = FontWeight.Medium,
) = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size.sp, fontWeight = weight)

/** A title's face, tight as large type wants. */
fun display(
    size: Int,
    weight: FontWeight = FontWeight.Bold,
) = TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = (-size * DISPLAY_TRACKING).sp)

fun body(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(fontSize = size.sp, fontWeight = weight)

/** The look's running text sizes. */
object TextSize {
    const val LABEL = 13f
    const val BODY = 13.5f
    const val CAPTION = 11f
    const val HEADING = 15f
}

private const val DESIGN_WIDTH = 1440f
private const val GLOW_FADE = 0.6f
private const val ORB_LIGHT = 0.3f
private const val ORB_REACH = 0.7f
private const val WINDOW_SHEEN = 1200f
private const val CLEAR_GLASS = 0.45f
private const val TILT_REST = 4f
private const val TILT_HOVER = 7f
private const val TILT_TURN = -2f
private const val TILT_MILLIS = 350

/** CSS's `perspective: 1400px` as the layer's camera distance, which counts in units of 72 pixels. */
private const val PERSPECTIVE = 19.4f
private const val ICON_SLANT = 400f
private const val ICON_SHEEN = 0.6f
private const val ICON_SHINE = 0.45f
private const val ICON_SHINE_ALPHA = 0.4f
private const val EXTRUDE_STEP = 0.14f
private const val DISPLAY_TRACKING = 0.02f
private val WALLPAPER_BLUR = 28.dp
private val EDGE_LIGHT = 1.5.dp
private val EDGE_SHADE = 2.dp
private val TILT_RISE = 5.dp
private val ICON_INSET = 3.dp
private val EXTRUDE_BLUR = 22.dp
private val EXTRUDE_DROP = 16.dp
private val EXTRUDE_SHADOW = Color(0x4D0E2838)
private val TILE_SIZE = 48.dp
private val TILE_RADIUS = 14.dp
