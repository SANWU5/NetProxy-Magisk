package dev.aiingjie.coui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun rememberPressMask(source: InteractionSource): Animatable<Float, *> {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(source) {
        var pressIn: Job? = null
        source.interactions.collect { interaction: Interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressIn = launch { alpha.animateTo(1f, tween(CouiMotion.PRESS_IN, easing = LinearEasing)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val waitFor = pressIn
                    launch {
                        waitFor?.join()
                        alpha.animateTo(0f, tween(CouiMotion.PRESS_OUT, easing = CouiMotion.PressRelease))
                    }
                }
            }
        }
    }
    return alpha
}

val LocalCouiCardEdge = compositionLocalOf<Dp> { 0.dp }

/**
 * 卡片在布局中的位置。卡片内的按压遮罩据此延伸到卡片上下边缘，
 * 行外面再包几层布局（Column、Box 等）也能把按压态铺满整块卡片。
 */
class CouiCardBounds internal constructor() {
    internal var coordinates: LayoutCoordinates? = null
}

val LocalCouiCardBounds = compositionLocalOf<CouiCardBounds?> { null }

private class PlacedHolder {
    var coordinates: LayoutCoordinates? = null
}

/** 返回遮罩需要向上、向下延伸的距离：只有贴着卡片上下边缘（内边距之内）的行才延伸。 */
private fun cardReach(own: LayoutCoordinates?, card: CouiCardBounds?, edge: Float): Pair<Float, Float> {
    if (own == null || !own.isAttached || edge <= 0f) return 0f to 0f
    val container = card?.coordinates?.takeIf { it.isAttached } ?: own.parentLayoutCoordinates ?: return 0f to 0f
    val top = container.localPositionOf(own, Offset.Zero).y
    val gap = container.size.height - top - own.size.height
    return (if (top > 0f && top <= edge + 1f) top else 0f) to (if (gap > 0f && gap <= edge + 1f) gap else 0f)
}

fun Modifier.couiPressable(
    enabled: Boolean = true,
    role: Role? = null,
    maskColor: Color? = null,
    shape: Shape? = null,
    source: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val own = remember { MutableInteractionSource() }
    val interactions = source ?: own
    val alpha = rememberPressMask(interactions)
    val color = maskColor ?: Coui.colors.press
    val edge = with(LocalDensity.current) { LocalCouiCardEdge.current.toPx() }
    val card = LocalCouiCardBounds.current
    val placed = remember { PlacedHolder() }
    val reach = if (edge <= 0f || shape != null) Modifier else Modifier.onPlaced { placed.coordinates = it }
    this
        .then(reach)
        .drawWithContent {
            drawContent()
            val a = alpha.value
            if (a > 0f) {
                val tint = color.copy(alpha = color.alpha * a)
                if (shape == null) {
                    // 绘制时才计算延伸：此时卡片与行都已完成布局，不受回调先后顺序影响。
                    val (above, below) = cardReach(placed.coordinates, card, edge)
                    drawRect(tint, Offset(0f, -above), Size(size.width, size.height + above + below))
                } else {
                    val path = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
                    drawPath(path, tint)
                }
            }
        }
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = role, onClick = onClick)
}

fun Modifier.couiScaleOnPress(source: InteractionSource, pressedScale: Float = 0.98f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch { scale.animateTo(pressedScale, tween(200, easing = CouiMotion.Ease)) }
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    launch { scale.animateTo(1f, CouiMotion.switchSpring()) }
            }
        }
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
