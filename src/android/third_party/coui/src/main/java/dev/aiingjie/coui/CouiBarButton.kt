package dev.aiingjie.coui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 标题栏的圆形玻璃按钮（40）。按下缩到 90%；按住拖动时与悬浮底栏一样跟手移动（橡皮筋阻尼，上限 10），
 * 沿拖动方向轻微拉伸，手指下有一团高光；松手弹回原位。松手时手指仍在按钮周围 24 以内才算点击，拖远则取消。
 */
@Composable
fun CouiBarButton(glyph: CouiGlyph, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = Coui.colors
    val dark = colors.page.luminance() < .5f
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    val press = remember { Animatable(1f) }
    val move = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var pressed by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf<Offset?>(null) }
    CouiGlass(
        Modifier.size(40.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (enabled) this.onClick { click(); true } else disabled()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val limit = 10.dp.toPx()
                val reach = 24.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    light = down.position
                    scope.launch { press.animateTo(.9f, spring(dampingRatio = .6f, stiffness = 600f)) }
                    var dragging = false
                    var inside = true
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) { released = true; break }
                        val delta = change.position - down.position
                        if (!dragging && delta.getDistance() > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            light = change.position
                            inside = change.position.x in -reach..size.width + reach && change.position.y in -reach..size.height + reach
                            pressed = inside
                            scope.launch { move.snapTo(Offset(rubber(delta.x, limit), rubber(delta.y, limit))) }
                        }
                    }
                    pressed = false
                    light = null
                    scope.launch { move.animateTo(Offset.Zero, spring(dampingRatio = .45f, stiffness = 420f)) }
                    scope.launch { press.animateTo(1f, spring(dampingRatio = .6f, stiffness = 600f)) }
                    if (released && inside) click()
                }
            }
            .graphicsLayer {
                val limit = 10.dp.toPx()
                val kx = (abs(move.value.x) / limit).coerceIn(0f, 1f)
                val ky = (abs(move.value.y) / limit).coerceIn(0f, 1f)
                translationX = move.value.x
                translationY = move.value.y
                scaleX = press.value * (1f + .06f * kx - .03f * ky)
                scaleY = press.value * (1f + .06f * ky - .03f * kx)
            },
    ) {
        CouiIcon(glyph, if (enabled) colors.label else colors.labelTertiary)
        if (pressed) Box(Modifier.matchParentSize().clip(CircleShape)
            .background(if (dark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .06f)))
        val glow = light
        if (glow != null) Box(Modifier.matchParentSize().clip(CircleShape).drawBehind {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (dark) .22f else .5f), Color.Transparent),
                    center = glow, radius = 26.dp.toPx(),
                ),
                radius = 26.dp.toPx(), center = glow,
            )
        })
    }
}

/** 与悬浮底栏相同的橡皮筋阻尼：越拉越难，最多接近 [limit]。 */
private fun rubber(distance: Float, limit: Float): Float =
    sign(distance) * limit * (1f - 1f / (1f + abs(distance) / (limit * 2.5f)))
