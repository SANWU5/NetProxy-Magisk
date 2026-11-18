package dev.aiingjie.coui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class CouiStarfieldColors(
    val skyTop: Color,
    val skyBottom: Color,
    val glow: Color,
    val star: Color,
    val coldStar: Color,
)

object CouiStarfieldDefaults {
    val Navy = CouiStarfieldColors(
        skyTop = Color(0xFF102673), skyBottom = Color(0xFF07174F), glow = Color(0xFF3F63D6),
        star = Color(0xFFFCF6EF), coldStar = Color(0xFFD9E2FF),
    )
    val Night = CouiStarfieldColors(
        skyTop = Color(0xFF1B1B2F), skyBottom = Color(0xFF0B0B14), glow = Color(0xFF6A5ACD),
        star = Color(0xFFFFF8F0), coldStar = Color(0xFFDDE4FF),
    )

    /** Seconds per loop. Every layer drifts a whole number of widths per loop, so the loop is seamless. */
    const val LOOP_SECONDS = 120f
}

/** A clock for [CouiStarfield]: seconds from 0 to [CouiStarfieldDefaults.LOOP_SECONDS], repeating. */
@Composable
fun rememberCouiStarfieldTime(): () -> Float {
    val loop = CouiStarfieldDefaults.LOOP_SECONDS
    val clock = rememberInfiniteTransition(label = "starfield")
        .animateFloat(0f, loop, infiniteRepeatable(tween((loop * 1000).toInt(), easing = LinearEasing)), label = "time")
    return { clock.value }
}

/**
 * Animated night sky: three parallax star layers that twinkle, four sparkles, and a shooting star every few
 * seconds. Stars fade out inside an ellipse ([clearRadius] around [clearCenter], fractions of the size) so a
 * logo can sit on top. Reading [time] only in the draw phase means the sky animates without recomposition.
 */
@Composable
fun CouiStarfield(
    modifier: Modifier = Modifier,
    colors: CouiStarfieldColors = CouiStarfieldDefaults.Navy,
    clearRadius: DpSize = DpSize(52.dp, 62.dp),
    clearCenter: Offset = Offset(0.5f, 0.44f),
    time: () -> Float = rememberCouiStarfieldTime(),
) {
    Canvas(modifier) { drawStars(time(), colors, clearRadius, clearCenter) }
}

private fun hash(i: Int, k: Int): Float {
    val v = sin(i * 12.9898f + k * 78.233f) * 43758.547f
    return v - floor(v)
}

private fun wave(t: Float, period: Float, phase: Float) = 0.5f + 0.5f * sin((t / period + phase) * 2f * PI.toFloat())

private class Layer(val seed: Int, val count: Int, val laps: Int, val minR: Float, val maxR: Float,
                    val minA: Float, val maxA: Float, val periods: List<Float>, val halo: Boolean)

private val Layers = listOf(
    Layer(seed = 1, count = 90, laps = 1, minR = 0.45f, maxR = 0.75f, minA = 0.12f, maxA = 0.42f, periods = listOf(6f, 8f, 10f, 12f), halo = false),
    Layer(seed = 2, count = 36, laps = 2, minR = 0.8f, maxR = 1.2f, minA = 0.28f, maxA = 0.72f, periods = listOf(4f, 5f, 6f, 8f), halo = false),
    Layer(seed = 3, count = 11, laps = 3, minR = 1.3f, maxR = 2.0f, minA = 0.55f, maxA = 1f, periods = listOf(2.5f, 3f, 4f, 5f), halo = true),
)

private fun DrawScope.drawStars(t: Float, c: CouiStarfieldColors, clearRadius: DpSize, clearCenter: Offset) {
    val w = size.width
    val h = size.height
    val loop = CouiStarfieldDefaults.LOOP_SECONDS
    drawRect(Brush.verticalGradient(listOf(c.skyTop, c.skyBottom)))
    drawRect(Brush.radialGradient(listOf(c.glow.copy(alpha = 0.2f + 0.05f * wave(t, 8f, 0f)), Color.Transparent),
        center = Offset(w * 0.5f, h * 0.45f), radius = h * 0.95f))

    val cx = w * clearCenter.x
    val cy = h * clearCenter.y
    val ax = clearRadius.width.toPx()
    val ay = clearRadius.height.toPx()
    fun clear(x: Float, y: Float): Float {
        if (ax <= 0f || ay <= 0f) return 1f
        val dx = (x - cx) / ax
        val dy = (y - cy) / ay
        val s = ((sqrt(dx * dx + dy * dy) - 0.85f) / 0.35f).coerceIn(0f, 1f)
        return s * s * (3f - 2f * s)
    }

    for (layer in Layers) {
        val drift = (t / loop) * layer.laps * w
        for (i in 0 until layer.count) {
            val id = layer.seed * 1000 + i
            val x = ((hash(id, 1) * w - drift) % w + w) % w
            val y = hash(id, 2) * h
            val m = clear(x, y)
            if (m <= 0f) continue
            val r = (layer.minR + (layer.maxR - layer.minR) * hash(id, 3)).dp.toPx()
            val tw = wave(t, layer.periods[i % layer.periods.size], hash(id, 4))
            val a = (layer.minA + (layer.maxA - layer.minA) * tw) * m
            val color = if (hash(id, 5) < 0.3f) c.coldStar else c.star
            if (layer.halo) {
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a * 0.35f), Color.Transparent), center = Offset(x, y), radius = r * 5f),
                    radius = r * 5f, center = Offset(x, y))
            }
            drawCircle(color.copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }

    for (k in 0 until 4) {
        val id = 9000 + k
        val drift = (t / loop) * 3 * w
        val x = ((hash(id, 1) * w - drift) % w + w) % w
        val y = (0.08f + hash(id, 2) * 0.7f) * h
        val m = clear(x, y)
        if (m <= 0f) continue
        val s = wave(t, listOf(3f, 4f, 5f, 6f)[k], hash(id, 3))
        val len = (3f + 5f * s).dp.toPx()
        val color = c.star.copy(alpha = (0.25f + 0.65f * s) * m)
        val stroke = 0.8f.dp.toPx()
        drawLine(color, Offset(x - len, y), Offset(x + len, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(x, y - len), Offset(x, y + len), stroke, StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = len * 0.6f),
            radius = len * 0.6f, center = Offset(x, y))
    }

    val lanes = listOf(3.2f to 0f, 4.6f to 1.7f, 6f to 3.1f)
    lanes.forEachIndexed { lane, (cycle, offset) ->
        val shifted = t + offset
        val n = floor(shifted / cycle).toInt()
        val local = shifted - n * cycle
        val duration = 0.75f + 0.3f * hash(n * 7 + lane, 23)
        if (local < duration) {
            val p = local / duration
            val seed = n * 7 + lane
            val sx = (0.2f + hash(seed, 21) * 0.75f) * w
            val sy = (0.04f + hash(seed, 22) * 0.35f) * h
            val angle = 0.85f + 0.5f * hash(seed, 24)
            val dx = -w * 0.3f * angle
            val dy = h * 0.42f * (1.4f - angle * 0.5f)
            val head = Offset(sx + dx * p, sy + dy * p)
            val trail = 0.22f + 0.12f * hash(seed, 25)
            val tail = Offset(head.x - dx * trail, head.y - dy * trail)
            val fade = sin(p * PI.toFloat()) * clear(head.x, head.y)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, c.star.copy(alpha = 0.85f * fade)), start = tail, end = head),
                tail, head, (1f + 0.4f * hash(seed, 26)).dp.toPx(), StrokeCap.Round)
            drawCircle(c.star.copy(alpha = fade), radius = 1.3f.dp.toPx(), center = head)
        }
    }
}
