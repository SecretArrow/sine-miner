package com.secretarrow.sineminer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A simple sine wave illustration — used in the dashboard hero card and the
 * splash screen. When [animate] is true, the wave scrolls left continuously.
 *
 * This is purely decorative; it does not show real mining data.
 */
@Composable
fun SineWave(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00D4AA),
    animate: Boolean = true,
    amplitude: Float = 0.4f,
    frequency: Float = 1.5f,
    lineWidth: Float = 6f,
) {
    val transition = rememberInfiniteTransition(label = "sine")
    val phase by if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2
        val path = Path()
        val segments = 64
        for (i in 0..segments) {
            val x = i.toFloat() / segments * w
            val t = i.toFloat() / segments * frequency * 2 * Math.PI.toFloat() + (phase * 2 * Math.PI.toFloat())
            val y = cy + (amplitude * h * kotlin.math.sin(t.toDouble()).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = lineWidth),
        )
    }
}

@Composable
fun SineWaveFixedSize(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00D4AA),
    size: Dp = 48.dp,
    animate: Boolean = true,
) {
    SineWave(modifier = modifier.size(size), color = color, animate = animate)
}
