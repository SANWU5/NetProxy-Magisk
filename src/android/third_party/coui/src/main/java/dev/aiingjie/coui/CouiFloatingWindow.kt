package dev.aiingjie.coui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A small draggable floating window, like the module's live FPS monitor.
 *
 * - Drag it anywhere; on release it springs to the nearest side edge (380 ms, slight overshoot).
 * - Press scales it to 96 % and release springs it back. A tap that did not move calls [onTap] with the
 *   touch point in [content] coordinates.
 * - It appears with a particle gather and leaves with a particle dissolve ([CouiParticleBurst]); with system
 *   animations off it scales and fades instead.
 *
 * In-app (no permission needed): create it with an Activity; the window floats over that Activity only.
 * Over other apps: pass `overlay = true`; that needs the "display over other apps" permission
 * (`Settings.canDrawOverlays`).
 *
 * [content] measures itself (WRAP_CONTENT). After it changes size, call [relayout]. Call [dismissNow] when the
 * Activity is destroyed.
 */
class CouiFloatingWindow(
    private val context: Context,
    val content: View,
    private val overlay: Boolean = context !is Activity,
    private val onTap: (x: Float, y: Float) -> Unit = { _, _ -> },
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val d = context.resources.displayMetrics.density
    private val params = layoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
    private var attached = false
    private var placed = false
    private var lastWidth = 0
    private var gathering: CouiParticleBurst.Running? = null
    private var snap: ValueAnimator? = null

    val isShowing: Boolean get() = attached

    /** Layout params with this window's type and token; the particle windows use the same. */
    fun layoutParams(width: Int, height: Int): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width, height,
        if (overlay) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (!overlay) token = (context as? Activity)?.window?.decorView?.windowToken
    }

    private fun measure(): Pair<Int, Int> {
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        return content.measuredWidth to content.measuredHeight
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (attached) return
        if (!overlay) params.token = (context as? Activity)?.window?.decorView?.windowToken ?: return
        content.animate().cancel()
        content.scaleX = 1f
        content.scaleY = 1f
        val (w, h) = measure()
        lastWidth = w
        if (!placed) {
            val screen = context.resources.displayMetrics
            params.x = screen.widthPixels - w - (12 * d).toInt()
            params.y = (screen.heightPixels * .18f).toInt()
            placed = true
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        content.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snap?.cancel()
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    content.animate().scaleX(.96f).scaleY(.96f).setDuration(120).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(content, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    content.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(PathInterpolator(.3f, 1.4f, .5f, 1f)).start()
                    if (moved) settle()
                    else if (e.actionMasked == MotionEvent.ACTION_UP) onTap(e.x, e.y)
                }
            }
            true
        }
        val pieces = if (CouiParticleBurst.canPlay()) CouiParticleBurst.snapshot(content, w, h) else null
        content.alpha = 0f
        runCatching { wm.addView(content, params); attached = true }
        if (pieces != null && attached) {
            gathering = CouiParticleBurst.play(context, wm, pieces, params.x, params.y, gather = true, ::layoutParams) {
                gathering = null
                if (attached) content.alpha = 1f
            }
            if (gathering != null) return
        }
        pieces?.recycle()
        content.scaleX = .82f
        content.scaleY = .82f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(PathInterpolator(.3f, 1.35f, .5f, 1f)).start()
    }

    /** Call after [content] changed size: keeps the window against the edge it is docked to, then settles. */
    fun relayout() {
        if (!attached) return
        val (w, _) = measure()
        val right = params.x + lastWidth / 2 > context.resources.displayMetrics.widthPixels / 2
        if (right) params.x -= w - lastWidth
        lastWidth = w
        content.requestLayout()
        runCatching { wm.updateViewLayout(content, params) }
        settle()
    }

    private fun settle() {
        val screen = context.resources.displayMetrics
        val (w, h) = measure()
        val margin = (8 * d).toInt()
        val top = (32 * d).toInt()
        val left = params.x + w / 2 < screen.widthPixels / 2
        val targetX = if (left) margin else screen.widthPixels - w - margin
        val targetY = params.y.coerceIn(top, (screen.heightPixels - h - (48 * d).toInt()).coerceAtLeast(top))
        val fromX = params.x
        val fromY = params.y
        if (abs(targetX - fromX) < 2 && abs(targetY - fromY) < 2) return
        snap?.cancel()
        snap = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = PathInterpolator(.3f, 1.25f, .5f, 1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                params.x = (fromX + (targetX - fromX) * t).toInt()
                params.y = (fromY + (targetY - fromY) * t).toInt()
                if (attached) runCatching { wm.updateViewLayout(content, params) }
            }
            start()
        }
    }

    /** Leaves with the particle dissolve (or a quick scale and fade). */
    fun hide() {
        snap?.cancel()
        if (!attached) return
        attached = false
        content.setOnTouchListener(null)
        content.animate().cancel()
        gathering?.cancel()
        gathering = null
        val w = content.width.takeIf { it > 0 } ?: content.measuredWidth
        val h = content.height.takeIf { it > 0 } ?: content.measuredHeight
        val pieces = if (CouiParticleBurst.canPlay()) CouiParticleBurst.snapshot(content, w, h) else null
        if (pieces != null && CouiParticleBurst.play(context, wm, pieces, params.x, params.y, gather = false, ::layoutParams) {} != null) {
            content.postDelayed({ runCatching { wm.removeView(content) } }, 32)
            return
        }
        pieces?.recycle()
        content.animate().alpha(0f).scaleX(.82f).scaleY(.82f).setDuration(200)
            .setInterpolator(PathInterpolator(.4f, 0f, .7f, .2f))
            .withEndAction { runCatching { wm.removeView(content) } }.start()
    }

    /** Removes the window at once without animation, for example in Activity.onDestroy. */
    fun dismissNow() {
        snap?.cancel()
        gathering?.cancel()
        gathering = null
        if (!attached) return
        attached = false
        content.setOnTouchListener(null)
        content.animate().cancel()
        runCatching { wm.removeViewImmediate(content) }
    }
}
