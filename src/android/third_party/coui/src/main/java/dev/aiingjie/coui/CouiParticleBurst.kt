package dev.aiingjie.coui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pixel-particle transition for a small window.
 *
 * The window is snapshotted into a bitmap and cut into square cells (2.5 dp or larger, about 2 600 cells
 * at most). Dissolve: cells leave in a left-to-right sweep, drifting up and to the right while they shrink and
 * fade (720 ms). Gather: the reverse, cells fly in from the right and assemble the window (620 ms).
 * The effect runs in its own untouchable window placed over the real one, so it never blocks input.
 */
@SuppressLint("ViewConstructor")
class CouiParticleBurst private constructor(
    context: Context,
    bitmap: Bitmap,
    private val gather: Boolean,
    private val margin: Int,
) : View(context) {
    private val count: Int
    private val homeX: FloatArray
    private val homeY: FloatArray
    private val color: IntArray
    private val driftX: FloatArray
    private val driftY: FloatArray
    private val delay: FloatArray
    private val cell: Float
    private val paint = Paint()
    private var progress = 0f

    init {
        val d = resources.displayMetrics.density
        val w = bitmap.width
        val h = bitmap.height
        val step = max((2.5f * d).toInt(), sqrt(w * h / 2600f).toInt()).coerceAtLeast(2)
        cell = step.toFloat()
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val cs = ArrayList<Int>()
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bitmap.getPixel(min(x + step / 2, w - 1), min(y + step / 2, h - 1))
                if (Color.alpha(px) >= 32) { xs += x.toFloat(); ys += y.toFloat(); cs += px }
                x += step
            }
            y += step
        }
        count = xs.size
        homeX = FloatArray(count) { margin + xs[it] }
        homeY = FloatArray(count) { margin + ys[it] }
        color = IntArray(count) { cs[it] }
        val random = Random(System.nanoTime())
        val reach = 44f * d
        driftX = FloatArray(count) { (0.25f + random.nextFloat() * 0.95f) * reach * (if (gather) -1f else 1f) }
        driftY = FloatArray(count) { -(0.15f + random.nextFloat()) * reach + (random.nextFloat() - 0.5f) * 16f * d }
        delay = FloatArray(count) {
            val sweep = (xs[it] / max(1, w)) * SWEEP
            (if (gather) SWEEP - sweep else sweep) + random.nextFloat() * JITTER
        }
    }

    override fun onDraw(canvas: Canvas) {
        val span = 1f - SWEEP - JITTER
        for (i in 0 until count) {
            val local = ((progress - delay[i]) / span).coerceIn(0f, 1f)
            val eased = 1f - (1f - local) * (1f - local) * (1f - local)
            val away = if (gather) 1f - eased else eased
            val alpha = if (gather) eased else 1f - local
            if (alpha <= 0.01f) continue
            val size = cell * (if (gather) 0.55f + 0.45f * eased else 1f - 0.45f * local)
            val px = homeX[i] + driftX[i] * away
            val py = homeY[i] + driftY[i] * away
            paint.color = color[i]
            paint.alpha = (Color.alpha(color[i]) * alpha).toInt()
            canvas.drawRect(px, py, px + size, py + size, paint)
        }
    }

    /** A running burst; [cancel] removes its window right away. */
    class Running internal constructor(
        private val wm: WindowManager,
        private val view: View,
        private val animator: ValueAnimator,
        private val bitmap: Bitmap,
    ) {
        fun cancel() {
            animator.removeAllListeners()
            animator.cancel()
            runCatching { wm.removeView(view) }
            bitmap.recycle()
        }
    }

    companion object {
        private const val SWEEP = 0.42f
        private const val JITTER = 0.12f

        /** False when the user turned animations off (developer options / accessibility). */
        fun canPlay(): Boolean = ValueAnimator.areAnimatorsEnabled()

        /** Draws [view] at the given size into a new bitmap, or returns null. */
        fun snapshot(view: View, width: Int, height: Int): Bitmap? = runCatching {
            if (width <= 0 || height <= 0) return@runCatching null
            if (view.width != width || view.height != height) {
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }.getOrNull()

        /**
         * Plays the burst for a window whose top-left corner is at ([x], [y]). [params] must create layout
         * params of the same window type (and token) as the real window. The bitmap is recycled afterwards.
         */
        fun play(
            context: Context,
            wm: WindowManager,
            bitmap: Bitmap,
            x: Int,
            y: Int,
            gather: Boolean,
            params: (width: Int, height: Int) -> WindowManager.LayoutParams,
            onEnd: () -> Unit,
        ): Running? {
            val margin = (56 * context.resources.displayMetrics.density).toInt()
            val burst = CouiParticleBurst(context, bitmap, gather, margin)
            if (burst.count == 0) return null
            val lp = params(bitmap.width + margin * 2, bitmap.height + margin * 2).apply {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                gravity = Gravity.TOP or Gravity.START
                this.x = x - margin
                this.y = y - margin
                windowAnimations = 0
            }
            if (runCatching { wm.addView(burst, lp) }.isFailure) return null
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (gather) 620 else 720
                interpolator = LinearInterpolator()
                addUpdateListener { burst.progress = it.animatedValue as Float; burst.invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onEnd()
                        burst.postDelayed({
                            runCatching { wm.removeView(burst) }
                            bitmap.recycle()
                        }, 32)
                    }
                })
                start()
            }
            return Running(wm, burst, animator, bitmap)
        }
    }
}
