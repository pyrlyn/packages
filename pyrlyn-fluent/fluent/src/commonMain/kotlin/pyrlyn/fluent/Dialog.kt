// The sizes are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

package pyrlyn.fluent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A modal dialog over a smoke layer: a tap on the smoke or Escape calls [onDismiss]. [footer] holds its buttons, which
 * share the width when each takes `Modifier.weight(1f)`.
 */
@Composable
fun ContentDialog(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalFluent.current
    val shape = Corners.Overlay
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, scrimColor = c.smoke)) {
        Box(
            Modifier.fillMaxSize().padding(24.dp).pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationX = LEAN
                        cameraDistance = PERSPECTIVE * density
                    }.dropShadow(shape, Shadow(radius = 64.dp, color = Color.Black.copy(alpha = 0.3f), offset = DpOffset(0.dp, 32.dp)))
                    .clip(shape)
                    .background(c.dialog)
                    .innerShadow(shape, Shadow(radius = 0.dp, color = c.rim, offset = DpOffset(0.dp, 1.dp)))
                    .border(1.dp, c.strokeWindow, shape)
                    // Keeps a tap on the dialog itself from reaching the smoke behind it.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        leading()
                        Text(title, color = c.ink, style = LocalFluentType.current.subtitle, modifier = Modifier.semantics { heading() })
                    }
                    content()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.stroke))
                Row(Modifier.fillMaxWidth().background(c.dialogFoot).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = footer)
            }
        }
    }
}

private const val LEAN = 3f

// cameraDistance counts 72nds of an inch, so this is the design's CSS perspective of 1400 pixels.
private const val PERSPECTIVE = 19.4f
