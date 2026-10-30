// The colours and offsets are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// What the look is made of: the wall, the window's material over it, a card with its lit edge, the lean of a raised card
// and the extruded icon. The wall is soft and still, so a translucent fill over it reads as frosted glass with no live
// backdrop blur, which Compose has no way to draw.

package pyrlyn.fluent

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon

/** The corners of the design system. */
object Corners {
    val Control = RoundedCornerShape(4.dp)
    val Item = RoundedCornerShape(6.dp)
    val Overlay = RoundedCornerShape(8.dp)
}

/** One leaf of the wall, placed on a 1440-wide canvas. */
class Petal(
    val side: Float,
    val x: Float,
    val y: Float,
    val turn: Float,
    val alpha: Float,
)

private val PETALS =
    listOf(
        Petal(760f, 800f, 120f, 18f, 0.55f),
        Petal(620f, 640f, 300f, -24f, 0.38f),
        Petal(520f, -140f, 520f, 62f, 0.32f),
    )

/** The wall the window's material sits on. */
@Composable
fun Wall(
    modifier: Modifier = Modifier,
    petals: List<Petal> = PETALS,
) {
    val colors = LocalFluent.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(colors.wallBase)
        ORBS.forEachIndexed { index, orb ->
            drawGlow(Offset(size.width * orb.x, size.height * orb.y), size.width * orb.width, size.height * orb.height, colors.wallOrbs[index])
        }
        val scale = size.width / WALL_WIDTH
        for (petal in petals) drawPetal(petal, scale, colors.petalTop, colors.petalBottom)
    }
}

private class Orb(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private val ORBS = listOf(Orb(0.12f, 0.28f, 0.55f, 0.65f), Orb(0.88f, 0.14f, 0.45f, 0.55f), Orb(0.72f, 1f, 0.7f, 0.7f))

/** CSS's elliptical radial gradient: a circle squashed to the ellipse, fading out at 70%. */
internal fun DrawScope.drawGlow(
    centre: Offset,
    width: Float,
    height: Float,
    color: Color,
) {
    scale(1f, height / width, centre) {
        drawCircle(Brush.radialGradient(0f to color, ORB_FADE to Color.Transparent, center = centre, radius = width), width, centre)
    }
}

private fun DrawScope.drawPetal(
    petal: Petal,
    scale: Float,
    top: Color,
    bottom: Color,
) {
    val side = petal.side * scale
    val leaf = Size(side, side)
    val shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 0, bottomEndPercent = 50, bottomStartPercent = 0)
    val path = Path().apply { addOutline(shape.createOutline(leaf, LayoutDirection.Ltr, this@drawPetal)) }
    translate(petal.x * scale, petal.y * scale) {
        rotate(petal.turn, Offset(side / 2, side / 2)) {
            drawPath(path, Brush.linearGradient(0f to top, PETAL_STOP to bottom, start = Offset.Zero, end = Offset(side, side)), alpha = petal.alpha)
        }
    }
}

/** The window's material over the wall, with the wash the person chose. */
@Composable
fun WindowMaterialLayer(modifier: Modifier = Modifier) {
    val colors = LocalFluent.current
    Box(modifier.fillMaxSize().background(colors.window).background(colors.washLayer))
}

/** A card: the fill, the 1-pixel stroke, the lit top edge and a small shadow; [flat] drops the shadow, as list rows do. */
@Composable
fun Modifier.card(
    shape: Shape = Corners.Overlay,
    fill: Color = LocalFluent.current.card,
    flat: Boolean = false,
): Modifier {
    val colors = LocalFluent.current
    val base = if (flat) this else dropShadow(shape, Shadow(radius = 6.dp, color = CARD_SHADOW, offset = DpOffset(0.dp, 2.dp)))
    return base
        .clip(shape)
        .background(fill)
        .innerShadow(shape, Shadow(radius = 0.dp, color = colors.rim, offset = DpOffset(0.dp, 1.dp)))
        .border(1.dp, colors.stroke, shape)
}

/** A surface leaning back 4 degrees, which straightens and rises under the pointer. */
@Composable
fun Modifier.lift(source: MutableInteractionSource = remember { MutableInteractionSource() }): Modifier {
    val hovered by source.collectIsHoveredAsState()
    val lean by animateFloatAsState(if (hovered) 0f else LEAN, tween(LIFT_MILLIS))
    val rise by animateFloatAsState(if (hovered) -2f else 0f, tween(LIFT_MILLIS))
    return hoverable(source)
        .graphicsLayer {
            rotationX = lean
            translationY = rise * density
            transformOrigin = TransformOrigin(0.5f, 1f)
            cameraDistance = LIFT_PERSPECTIVE * density
        }.dropShadow(Corners.Overlay, Shadow(radius = 36.dp, spread = (-10).dp, color = LIFT_SHADOW, offset = DpOffset(0.dp, 18.dp)))
}

/** The design system's icon gradients, top to bottom. */
enum class IconGradient(
    val top: Color,
    val bottom: Color,
) {
    Blue(Color(0xFF6CB6FF), Color(0xFF0873E0)),
    Orange(Color(0xFFFFC78A), Color(0xFFF07A12)),
    Magenta(Color(0xFFFF8FD0), Color(0xFFC92A86)),
    Violet(Color(0xFFB9A8FF), Color(0xFF6D5CE6)),
    Coral(Color(0xFFFFA79C), Color(0xFFE8503F)),
    Cyan(Color(0xFF7FE6FF), Color(0xFF0A9CC0)),
    Mint(Color(0xFF7FF0CC), Color(0xFF1FA88A)),
    Night(Color(0xFF3A4A6B), Color(0xFF0D1B2A)),
    Green(Color(0xFF6FE39E), Color(0xFF1F9D57)),
    Amber(Color(0xFFF6CF6A), Color(0xFFC98F00)),
    Red(Color(0xFFFF8E9A), Color(0xFFC62A44)),
    ;

    /** CSS's 160-degree gradient, whose ends lie past a square's corners. */
    val brush: Brush =
        object : ShaderBrush() {
            override fun createShader(size: Size) = LinearGradientShader(Offset(size.width * 0.28f, size.height * -0.1f), Offset(size.width * 0.72f, size.height * 1.1f), listOf(top, bottom))
        }

    companion object {
        private val LETTERED = listOf(Blue, Orange, Magenta, Violet, Coral, Cyan, Mint, Night)

        /** A colour for what has no artwork, the same every time for the same [name]. */
        fun of(name: String) = LETTERED[name.hashCode().mod(LETTERED.size)]
    }
}

enum class AppIconSize(
    val side: Dp,
    val radius: Dp,
    val text: Int,
    val depth: Int,
) {
    Small(40.dp, 10.dp, 14, 5),
    Medium(56.dp, 14.dp, 18, 5),
    Large(96.dp, 24.dp, 34, 5),
}

/** An icon leaning back with an extruded edge; [raised] straightens and lifts it, as under the pointer of its row. */
@Composable
private fun Modifier.extruded(
    shape: Shape,
    depth: Int,
    gradient: IconGradient,
    raised: Boolean,
): Modifier {
    val lean by animateFloatAsState(if (raised) 0f else ICON_LEAN, tween(ICON_MILLIS))
    val rise by animateFloatAsState(if (raised) -3f else 0f, tween(ICON_MILLIS))
    val grow by animateFloatAsState(if (raised) ICON_GROW else 1f, tween(ICON_MILLIS))
    var layers =
        graphicsLayer {
            rotationX = lean
            translationY = rise * density
            scaleX = grow
            scaleY = grow
            cameraDistance = ICON_PERSPECTIVE * density
        }.dropShadow(shape, Shadow(radius = 20.dp, color = ICON_SHADOW, offset = DpOffset(0.dp, 14.dp)))
    for (step in depth downTo 1) layers = layers.dropShadow(shape, Shadow(radius = 0.dp, color = Color.Black.copy(alpha = EXTRUDE_STEP * (depth - step + 1)), offset = DpOffset(0.dp, step.dp)))
    return layers
        .clip(shape)
        .background(gradient.brush)
        .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.55f), offset = DpOffset(0.dp, 1.5.dp)))
        .innerShadow(shape, Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.2f), offset = DpOffset(0.dp, (-5).dp)))
}

/** An app's icon: [letters] on a [gradient]. */
@Composable
fun AppIcon(
    letters: String,
    gradient: IconGradient,
    size: AppIconSize,
    modifier: Modifier = Modifier,
    raised: Boolean = false,
) {
    val type = LocalFluentType.current
    Box(modifier.size(size.side).extruded(RoundedCornerShape(size.radius), size.depth, gradient, raised), contentAlignment = Alignment.Center) {
        Text(letters, color = Color.White, style = type.subtitle.copy(fontSize = size.text.sp, fontWeight = FontWeight.Bold))
    }
}

/** A white [glyph] on a [gradient]: the icon of a feature or a statistic. */
@Composable
fun FeatureIcon(
    glyph: Glyph,
    gradient: IconGradient,
    modifier: Modifier = Modifier,
    raised: Boolean = false,
) {
    Box(modifier.size(48.dp).extruded(RoundedCornerShape(12.dp), 4, gradient, raised), contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, Color.White, 24.dp)
    }
}

/** A rounded tile with an extrusion under whatever [content] draws: a brand's mark. */
@Composable
fun BrandTile(
    fill: Brush,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .dropShadow(shape, Shadow(radius = 50.dp, color = Color.Black.copy(alpha = 0.45f), offset = DpOffset(0.dp, 30.dp)))
            .clip(shape)
            .background(fill)
            .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.25f), offset = DpOffset(0.dp, 2.dp)))
            .innerShadow(shape, Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, (-6).dp))),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private const val WALL_WIDTH = 1440f
private const val ORB_FADE = 0.7f
private const val PETAL_STOP = 0.7f
private const val LEAN = 4f
private const val LIFT_MILLIS = 300
private const val ICON_MILLIS = 250
private const val ICON_LEAN = 12f
private const val ICON_GROW = 1.04f
private const val EXTRUDE_STEP = 0.035f

// cameraDistance counts 72nds of an inch, so these are the design's CSS perspectives of 1200 and 300 pixels.
private const val LIFT_PERSPECTIVE = 16.7f
private const val ICON_PERSPECTIVE = 4.2f
private val CARD_SHADOW = Color(14, 40, 56, 13)
private val LIFT_SHADOW = Color(14, 40, 56, 77)
private val ICON_SHADOW = Color(14, 40, 56, 77)
