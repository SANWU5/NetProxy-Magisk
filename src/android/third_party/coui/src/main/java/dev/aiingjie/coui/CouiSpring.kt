package dev.aiingjie.coui

import android.view.Choreographer
import kotlin.math.PI
import kotlin.math.abs

class CouiSpring(initial: Float, response: Float, bounce: Float, private val epsilon: Float = 0.001f) {
    var value = initial
        private set
    var target = initial
        private set
    private var velocity = 0f
    private var stiffness = 0f
    private var damping = 0f

    init { tune(response, bounce) }

    fun tune(response: Float, bounce: Float) {
        val omega = (2.0 * PI / response).toFloat()
        stiffness = omega * omega
        damping = 2f * (1f - bounce) * omega
    }

    fun animateTo(value: Float, response: Float? = null, bounce: Float? = null) {
        if (response != null && bounce != null) tune(response, bounce)
        target = value
    }

    fun snapTo(value: Float) {
        this.value = value
        target = value
        velocity = 0f
    }

    val settled: Boolean get() = abs(value - target) < epsilon && abs(velocity) < epsilon * 10f

    fun step(seconds: Float) {
        if (settled) { value = target; velocity = 0f; return }
        var left = seconds.coerceAtMost(0.064f)
        while (left > 0f) {
            val dt = minOf(left, 0.004f)
            velocity += (-stiffness * (value - target) - damping * velocity) * dt
            value += velocity * dt
            left -= dt
        }
    }
}

class CouiSpringDriver(private val springs: List<CouiSpring>, private val onFrame: () -> Unit) : Choreographer.FrameCallback {
    private var running = false
    private var last = 0L

    fun kick() {
        if (running) return
        running = true
        last = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        val dt = if (last == 0L) 1f / 120f else (frameTimeNanos - last) / 1e9f
        last = frameTimeNanos
        springs.forEach { it.step(dt) }
        onFrame()
        if (springs.all { it.settled }) running = false
        else Choreographer.getInstance().postFrameCallback(this)
    }
}
