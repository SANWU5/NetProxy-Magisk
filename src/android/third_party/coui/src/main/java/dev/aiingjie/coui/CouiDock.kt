// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.aiingjie.coui

import android.annotation.SuppressLint
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

class CouiDockTab(val label: String, val selected: ImageVector, val idle: ImageVector)

internal class CouiDockTouch {
    var highlight by mutableIntStateOf(-1)
    var pressed by mutableIntStateOf(-1)
    var light by mutableFloatStateOf(0f)
    var lightAt by mutableStateOf(Offset.Zero)
    val cells = ArrayList<ClosedFloatingPointRange<Float>>()

    fun cellAt(x: Float): Int {
        if (cells.isEmpty()) return -1
        cells.forEachIndexed { i, r -> if (x in r) return i }
        var best = -1
        var gap = Float.MAX_VALUE
        cells.forEachIndexed { i, r ->
            val d = minOf(abs(x - r.start), abs(x - r.endInclusive))
            if (d < gap) { gap = d; best = i }
        }
        return best
    }
}

@SuppressLint("ViewConstructor")
class CouiDockBar(
    activity: ComponentActivity,
    tabs: List<CouiDockTab>,
    private val page: StateFlow<Int>,
    private val onPage: (Int) -> Unit,
) {
    private val density = activity.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
    private var dark = false
    private var materialApplied = false
    private val touch = CouiDockTouch()

    private val pressScale = CouiSpring(1f, .35f, .62f, .0002f)
    private val moveX = CouiSpring(0f, .35f, .3f, .3f)
    private val moveY = CouiSpring(0f, .35f, .3f, .3f)
    private val stretch = CouiSpring(0f, .35f, .3f, .0005f)
    private val glow = CouiSpring(0f, .2f, 0f, .002f)
    private val driver = CouiSpringDriver(listOf(pressScale, moveX, moveY, stretch, glow), ::applyMotion)

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracking = false

    val view: FrameLayout = object : FrameLayout(activity) {
        @SuppressLint("ClickableViewAccessibility")
        override fun dispatchTouchEvent(event: MotionEvent): Boolean = handle(this, event)
    }.apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        elevation = 12f * density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = 0x8A000000.toInt()
            outlineAmbientShadowColor = 0x8A000000.toInt()
        }
        visibility = View.GONE
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) refreshSurface()
        }
    }

    init {
        val content = ComposeView(activity).apply {
            setContent {
                val dark = isSystemInDarkTheme()
                SideEffect { updateSurface(dark) }
                val current by page.collectAsState()
                CouiDockContent(tabs, current, touch, dark, onPage)
            }
        }
        view.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }

    fun attach(root: FrameLayout) {
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (16 * density).roundToInt()
        })
    }

    fun setBottomInset(insetPx: Int) {
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        val margin = maxOf((16 * density).roundToInt(), insetPx)
        if (params.bottomMargin != margin) { params.bottomMargin = margin; view.layoutParams = params }
    }

    fun setShown(shown: Boolean) {
        val target = if (shown) View.VISIBLE else View.GONE
        if (view.visibility == target) return
        if (!shown) cancel()
        view.visibility = target
    }

    private fun handle(host: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                dragging = false
                downX = event.rawX
                downY = event.rawY
                touch.pressed = touch.cellAt(event.x)
                touch.lightAt = Offset(event.x, event.y)
                pressScale.animateTo(.965f, .13f, .18f)
                glow.animateTo(1f)
                driver.kick()
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    host.parent?.requestDisallowInterceptTouchEvent(true)
                }
                touch.lightAt = Offset(event.x, event.y)
                if (dragging) {
                    val limitX = 16f * density
                    val limitY = 8f * density
                    moveX.snapTo(rubber(dx, limitX))
                    moveY.snapTo(rubber(dy, limitY))
                    stretch.snapTo((abs(moveX.value) / limitX).coerceIn(0f, 1f))
                    val cell = touch.cellAt(event.x)
                    val shown = if (touch.highlight >= 0) touch.highlight else page.value
                    if (cell >= 0 && cell != shown) {
                        touch.highlight = cell
                        touch.pressed = cell
                        host.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                            else HapticFeedbackConstants.CLOCK_TICK)
                    }
                    applyMotion()
                }
            }
            MotionEvent.ACTION_UP -> if (tracking) {
                val target = if (dragging) touch.highlight.takeIf { it >= 0 } ?: touch.cellAt(event.x) else touch.cellAt(event.x)
                if (target >= 0 && target != page.value) {
                    if (!dragging) host.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onPage(target)
                }
                release()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    private fun cancel() {
        if (!tracking) return
        release()
    }

    private fun release() {
        tracking = false
        dragging = false
        touch.highlight = -1
        touch.pressed = -1
        pressScale.animateTo(1f, .35f, .62f)
        moveX.animateTo(0f, .45f, .32f)
        moveY.animateTo(0f, .45f, .32f)
        stretch.animateTo(0f, .45f, .32f)
        glow.animateTo(0f)
        driver.kick()
    }

    private fun applyMotion() {
        val s = pressScale.value
        val k = stretch.value
        view.translationX = moveX.value
        view.translationY = moveY.value
        view.scaleX = s * (1f + .045f * k)
        view.scaleY = s * (1f - .03f * k)
        touch.light = glow.value.coerceIn(0f, 1f)
    }

    private fun rubber(distance: Float, limit: Float): Float =
        sign(distance) * limit * (1f - 1f / (1f + abs(distance) / (limit * 2.5f)))

    private fun updateSurface(dark: Boolean) {
        if (this.dark == dark && (materialApplied || view.background != null)) return
        this.dark = dark
        refreshSurface()
    }

    private fun refreshSurface() {
        if (view.height <= 0) return
        val radius = view.height / 2f
        materialApplied = ColorOsMaterial.applyBottomBar(view, dark, radius)
        if (materialApplied) {
            view.background = null
        } else {
            ColorOsMaterial.clear(view)
            view.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(if (dark) 0xFF262628.toInt() else 0xFFF7F7F7.toInt())
                setStroke(maxOf(1, density.roundToInt() / 2), if (dark) 0x1AFFFFFF else 0x99FFFFFF.toInt())
            }
        }
    }
}
