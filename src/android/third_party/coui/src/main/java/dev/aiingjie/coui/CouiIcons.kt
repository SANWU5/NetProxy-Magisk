package dev.aiingjie.coui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class CouiGlyph(val widthDp: Int, val heightDp: Int, val mirrored: Boolean = false) {
    Next(12, 24, mirrored = true),
    PopupNext(12, 24),
    Back(24, 24, mirrored = true),
    Check(24, 24),
    Search(24, 24),
    Clear(24, 24),
    Close(24, 24),
    More(24, 24),
}

@Composable
fun CouiIcon(glyph: CouiGlyph, color: Color, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl && glyph.mirrored
    Canvas(modifier.size(glyph.widthDp.dp, glyph.heightDp.dp)) {
        val u = size.width / glyph.widthDp
        scale(if (rtl) -1f else 1f, 1f) { drawGlyph(glyph, color, u) }
    }
}

private fun DrawScope.line(color: Color, u: Float, width: Float = 1.4f, vararg points: Pair<Float, Float>) {
    val path = Path()
    points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
    drawPath(path, color, style = Stroke(width = width * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawGlyph(glyph: CouiGlyph, color: Color, u: Float) {
    when (glyph) {
        CouiGlyph.Next -> line(color, u, 1.4f, 4.2f to 7.4f, 8.6f to 12f, 4.2f to 16.6f)
        CouiGlyph.PopupNext -> {
            line(color, u, 1.4f, 3.2f to 10f, 6f to 7.2f, 8.8f to 10f)
            line(color, u, 1.4f, 3.2f to 14f, 6f to 16.8f, 8.8f to 14f)
        }
        CouiGlyph.Back -> line(color, u, 1.4f, 15f to 5f, 8f to 12f, 15f to 19f)
        CouiGlyph.Check -> line(color, u, 1.6f, 5f to 12.4f, 9.8f to 17f, 19f to 7.6f)
        CouiGlyph.Search -> {
            drawCircle(color, radius = 6.6f * u, center = Offset(10.6f * u, 10.6f * u), style = Stroke(1.6f * u))
            line(color, u, 1.6f, 15.6f to 15.6f, 19.6f to 19.6f)
        }
        CouiGlyph.Clear -> {
            drawCircle(color, radius = 8f * u, center = Offset(12f * u, 12f * u))
            line(Color.White, u, 1.4f, 9.2f to 9.2f, 14.8f to 14.8f)
            line(Color.White, u, 1.4f, 14.8f to 9.2f, 9.2f to 14.8f)
        }
        CouiGlyph.Close -> {
            line(color, u, 1.6f, 6.5f to 6.5f, 17.5f to 17.5f)
            line(color, u, 1.6f, 17.5f to 6.5f, 6.5f to 17.5f)
        }
        CouiGlyph.More -> for (x in listOf(6f, 12f, 18f)) drawCircle(color, radius = 1.6f * u, center = Offset(x * u, 12f * u))
    }
}

enum class CouiStatus { Info, Warning, Error, Success }

@Composable
fun CouiStatusIcon(status: CouiStatus, modifier: Modifier = Modifier, sizeDp: Int = 20) {
    val colors = Coui.colors
    val fill = when (status) {
        CouiStatus.Info -> colors.accent
        CouiStatus.Warning -> colors.orange
        CouiStatus.Error -> colors.error
        CouiStatus.Success -> colors.success
    }
    Canvas(modifier.size(sizeDp.dp)) {
        val u = size.width / 20f
        drawCircle(fill, radius = 10f * u)
        val white = Color.White
        when (status) {
            CouiStatus.Info -> {
                drawCircle(white, radius = 1.15f * u, center = Offset(10f * u, 5.9f * u))
                line(white, u, 1.9f, 10f to 9.2f, 10f to 14.6f)
            }
            CouiStatus.Warning, CouiStatus.Error -> {
                line(white, u, 1.9f, 10f to 5.4f, 10f to 11.4f)
                drawCircle(white, radius = 1.15f * u, center = Offset(10f * u, 14.4f * u))
            }
            CouiStatus.Success -> line(white, u, 1.8f, 5.8f to 10.2f, 8.8f to 13.2f, 14.4f to 7.2f)
        }
    }
}
