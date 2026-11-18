package dev.aiingjie.coui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object CouiMotion {
    val Ease = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
    val InEase = CubicBezierEasing(0f, 0f, 0.1f, 1f)
    val MoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
    val OutEase = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val PressRelease = CubicBezierEasing(0.17f, 0.17f, 0.67f, 1f)
    val PageEnter = CubicBezierEasing(0.3f, 0.1f, 0.3f, 1f)
    val PageExitBehind = CubicBezierEasing(0.3f, 0.15f, 0.3f, 1f)
    val PageReturnBehind = CubicBezierEasing(0.3f, 0.26f, 0.4f, 1f)
    val PageExit = CubicBezierEasing(0.25f, 0.1f, 0.3f, 1f)

    const val MOMENT = 100
    const val VERY_FAST = 200
    const val FAST = 240
    const val MOVE = 330
    const val PAGE = 350
    const val SLOW = 420
    const val PRESS_IN = 150
    const val PRESS_OUT = 367
    const val DIALOG_IN = 250
    const val DIALOG_OUT = 150

    fun <T> move(duration: Int = MOVE): TweenSpec<T> = tween(duration, easing = MoveEase)
    fun <T> out(duration: Int = VERY_FAST): TweenSpec<T> = tween(duration, easing = OutEase)
    fun <T> ease(duration: Int = VERY_FAST): TweenSpec<T> = tween(duration, easing = Ease)

    fun <T> switchSpring(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 246.7f)

    fun <T> pressSpring(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 438.6f)
}
