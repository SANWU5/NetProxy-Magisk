package dev.aiingjie.coui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Continuous ("squircle") corners like ColorOS / iOS cards.
 * The corner-smoothing math follows figma-squircle (MIT License, Copyright (c) 2021 Tien Pham),
 * https://github.com/phamfoo/figma-squircle. `smoothing` = 0 gives a plain rounded corner.
 */
class CouiSmoothShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {
    constructor(radius: Dp, smoothing: Float = 0.6f) : this(radius, radius, radius, radius, smoothing)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val ltr = layoutDirection == LayoutDirection.Ltr
        val px = { d: Dp -> with(density) { d.toPx() } }
        val tl = px(if (ltr) topStart else topEnd)
        val tr = px(if (ltr) topEnd else topStart)
        val br = px(if (ltr) bottomEnd else bottomStart)
        val bl = px(if (ltr) bottomStart else bottomEnd)
        if (tl == 0f && tr == 0f && br == 0f && bl == 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(path(size.width, size.height, tl, tr, br, bl))
    }

    private class Corner(val a: Float, val b: Float, val c: Float, val d: Float, val p: Float, val arc: Float,
                         val r: Float, val sweep: Float, val alpha: Float)

    private fun corner(radius: Float, budget: Float): Corner? {
        if (radius <= 0f) return null
        val r = min(radius, budget)
        var s = smoothing
        var p = (1 + s) * r
        val maxSmoothing = budget / r - 1f
        s = min(s, maxSmoothing).coerceAtLeast(0f)
        p = min(p, budget)
        val arcMeasure = 90f * (1f - s)
        val arcSection = sin(Math.toRadians(arcMeasure / 2.0)).toFloat() * r * sqrt(2f)
        val angleAlpha = (90f - arcMeasure) / 2f
        val p3ToP4 = r * tan(Math.toRadians(angleAlpha / 2.0)).toFloat()
        val angleBeta = 45f * s
        val c = p3ToP4 * cos(Math.toRadians(angleBeta.toDouble())).toFloat()
        val d = c * tan(Math.toRadians(angleBeta.toDouble())).toFloat()
        val b = (p - arcSection - c - d) / 3f
        val a = 2f * b
        return Corner(a, b, c, d, p, arcSection, r, arcMeasure, angleAlpha)
    }

    private fun path(w: Float, h: Float, tl: Float, tr: Float, br: Float, bl: Float): Path {
        val budget = min(w, h) / 2f
        val cTL = corner(tl, budget); val cTR = corner(tr, budget)
        val cBR = corner(br, budget); val cBL = corner(bl, budget)
        val path = Path()
        fun drawCorner(c: Corner, sx: Float, sy: Float, ux: Float, uy: Float, vx: Float, vy: Float,
                       cx: Float, cy: Float, startAngle: Float) {
            fun x(u: Float, v: Float) = sx + u * ux + v * vx
            fun y(u: Float, v: Float) = sy + u * uy + v * vy
            path.cubicTo(x(c.a, 0f), y(c.a, 0f), x(c.a + c.b, 0f), y(c.a + c.b, 0f), x(c.a + c.b + c.c, c.d), y(c.a + c.b + c.c, c.d))
            path.arcTo(Rect(cx - c.r, cy - c.r, cx + c.r, cy + c.r), startAngle + c.alpha, c.sweep, false)
            val eu = c.a + c.b + c.c + c.arc
            val ev = c.d + c.arc
            path.cubicTo(x(eu + c.d, ev + c.c), y(eu + c.d, ev + c.c), x(eu + c.d, ev + c.b + c.c), y(eu + c.d, ev + c.b + c.c),
                x(eu + c.d, ev + c.a + c.b + c.c), y(eu + c.d, ev + c.a + c.b + c.c))
        }
        path.moveTo(cTL?.p ?: 0f, 0f)
        path.lineTo(w - (cTR?.p ?: 0f), 0f)
        if (cTR != null) drawCorner(cTR, w - cTR.p, 0f, 1f, 0f, 0f, 1f, w - cTR.r, cTR.r, -90f) else path.lineTo(w, 0f)
        path.lineTo(w, h - (cBR?.p ?: 0f))
        if (cBR != null) drawCorner(cBR, w, h - cBR.p, 0f, 1f, -1f, 0f, w - cBR.r, h - cBR.r, 0f) else path.lineTo(w, h)
        path.lineTo(cBL?.p ?: 0f, h)
        if (cBL != null) drawCorner(cBL, cBL.p, h, -1f, 0f, 0f, -1f, cBL.r, h - cBL.r, 90f) else path.lineTo(0f, h)
        path.lineTo(0f, cTL?.p ?: 0f)
        if (cTL != null) drawCorner(cTL, 0f, cTL.p, 0f, -1f, 1f, 0f, cTL.r, cTL.r, 180f) else path.lineTo(0f, 0f)
        path.close()
        return path
    }

    override fun equals(other: Any?): Boolean = other is CouiSmoothShape && other.topStart == topStart &&
        other.topEnd == topEnd && other.bottomEnd == bottomEnd && other.bottomStart == bottomStart && other.smoothing == smoothing

    override fun hashCode(): Int = ((((topStart.hashCode() * 31) + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31 +
        bottomStart.hashCode()) * 31 + smoothing.hashCode()
}

object CouiShapes {
    val card = CouiSmoothShape(CouiDimens.cardRadiusSmooth)
    val panel = CouiSmoothShape(34.dp)
    val dialog = CouiSmoothShape(32.dp)
    val menu = CouiSmoothShape(24.dp)
    val tips = CouiSmoothShape(CouiDimens.cardRadiusSmooth)
    val snack = CouiSmoothShape(16.dp)
    val search = CouiSmoothShape(18.dp, 0f)
}
