package dev.aiingjie.coui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CouiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressed: Boolean = false,
) {
    val colors = Coui.colors
    val position = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { position.animateTo(if (checked) 1f else 0f, CouiMotion.switchSpring()) }
    val source = remember { MutableInteractionSource() }
    val selfPressed by source.collectIsPressedAsState()
    val stretch by animateFloatAsState(if (pressed || selfPressed) 1f else 0f, CouiMotion.move(CouiMotion.VERY_FAST), label = "stretch")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val on = if (enabled) colors.accent else colors.accentSecondary
    val off = if (enabled) colors.divider else colors.pressBackground
    val thumb = if (enabled) colors.thumb else colors.thumbDisabled
    val clickable = if (onCheckedChange != null) Modifier.clickable(source, indication = null, enabled = enabled, role = Role.Switch) {
        onCheckedChange(!checked)
    } else Modifier
    Canvas(modifier.size(44.dp, 24.dp).then(clickable).semantics { stateDescription = if (checked) "开启" else "关闭" }) {
        val p = position.value.coerceIn(-0.2f, 1.2f)
        val track = lerp(off, on, position.value.coerceIn(0f, 1f))
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
        val inset = 3.dp.toPx()
        val d = 18.dp.toPx()
        val extra = 4.dp.toPx() * stretch
        val travel = size.width - 2 * inset - d
        val leftAt = inset + travel * (if (rtl) 1f - p else p)
        val towardsStart = (if (rtl) 1f - p else p) > 0.5f
        val left = if (towardsStart) leftAt - extra else leftAt
        drawRoundRect(thumb, topLeft = Offset(left, inset), size = Size(d + extra, d), cornerRadius = CornerRadius(d / 2f))
    }
}

@Composable
fun CouiRadio(selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Coui.colors
    val progress by animateFloatAsState(if (selected) 1f else 0f, CouiMotion.move(CouiMotion.VERY_FAST), label = "radio")
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = Offset(12f * u, 12f * u)
        val tertiary = colors.labelTertiary
        if (progress < 1f) drawCircle(tertiary, radius = 9.2f * u, center = c, style = Stroke(1.3f * u), alpha = 1f - progress)
        if (progress > 0f) {
            val accent = if (enabled) colors.accent else colors.accentDisabled
            drawCircle(accent, radius = 10f * u * (0.6f + 0.4f * progress), center = c, alpha = progress)
            drawCircle(Color.White, radius = 6f * u * progress, center = c, alpha = progress)
        }
    }
}

@Composable
fun CouiSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = Coui.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragging by remember { mutableStateOf(false) }
    val thumbRadius by animateFloatAsState(if (dragging) 8.5f else 7.5f, CouiMotion.move(CouiMotion.VERY_FAST), label = "thumb")
    val deform by animateFloatAsState(if (dragging) 1f else 0f, CouiMotion.switchSpring(), label = "deform")
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val span = valueRange.endInclusive - valueRange.start
    fun fractionToValue(f: Float): Float {
        var v = valueRange.start + span * f.coerceIn(0f, 1f)
        if (steps > 0) {
            val stepSize = span / (steps + 1)
            v = valueRange.start + ((v - valueRange.start) / stepSize).roundToInt() * stepSize
        }
        return v.coerceIn(valueRange.start, valueRange.endInclusive)
    }
    val fraction = if (span == 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .pointerInput(enabled, rtl, valueRange, steps) {
                if (!enabled) return@pointerInput
                val track = 20.dp.toPx()
                fun at(x: Float): Float { val f = (x - track / 2f) / (size.width - track); return if (rtl) 1f - f else f }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                    if (drag != null) {
                        dragging = true
                        change(fractionToValue(at(drag.position.x)))
                        horizontalDrag(drag.id) { c -> change(fractionToValue(at(c.position.x))); c.consume() }
                        dragging = false
                        finished?.invoke()
                    } else {
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && !up.pressed && !up.isConsumed) {
                            change(fractionToValue(at(up.position.x)))
                            finished?.invoke()
                        }
                    }
                }
            }
    ) {
        val track = (20f + 2f * deform).dp.toPx()
        val y = size.height / 2f
        val left = 0f
        val right = size.width
        val corner = CornerRadius(track / 2f)
        drawRoundRect(colors.container12, Offset(left, y - track / 2), Size(right - left, track), corner)
        val fill = track + (right - left - track) * fraction
        val progressColor = if (enabled) colors.accent else colors.accentDisabled
        val fillLeft = if (rtl) right - fill else left
        drawRoundRect(progressColor, Offset(fillLeft, y - track / 2), Size(fill, track), corner)
        val cx = if (rtl) fillLeft + track / 2f else fillLeft + fill - track / 2f
        drawCircle(colors.thumb, radius = thumbRadius.dp.toPx(), center = Offset(cx, y))
    }
}

enum class CouiButtonStyle { Primary, Secondary, Danger }

@Composable
fun CouiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CouiButtonStyle = CouiButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val colors = Coui.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (bg, fg) = when (style) {
        CouiButtonStyle.Primary -> (if (enabled) colors.accent else colors.accentDisabled) to colors.onColor
        CouiButtonStyle.Secondary -> colors.container8 to (if (enabled) colors.label else colors.labelTertiary)
        CouiButtonStyle.Danger -> colors.container8 to (if (enabled) colors.error else colors.labelTertiary)
    }
    val darken by animateFloatAsState(if (pressed) (if (colors.isDark) 0.2f else 0.12f) else 0f, tween(CouiMotion.PRESS_IN), label = "btn")
    Box(
        modifier.widthIn(min = 132.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(50))
            .background(bg)
            .drawWithContent { drawContent(); if (darken > 0f) drawRect(Color.Black.copy(alpha = darken)) }
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Coui.type.buttonLarge.copy(color = fg, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CouiSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Coui.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fg by animateColorAsState(if (enabled) colors.accentText else colors.labelTertiary, label = "small")
    Box(
        modifier.widthIn(min = 52.dp).height(28.dp).clip(RoundedCornerShape(13.dp))
            .background(colors.container8)
            .drawWithContent { drawContent(); if (pressed) drawRect(colors.press) }
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Coui.type.buttonSmall.copy(color = fg), maxLines = 1)
    }
}

@Composable
fun CouiLoading(modifier: Modifier = Modifier, sizeDp: Int = 24, color: Color = Coui.colors.labelSecondary) {
    val turn by rememberInfiniteTransition(label = "loading")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Canvas(modifier.size(sizeDp.dp)) {
        val stroke = 2.dp.toPx()
        drawArc(color.copy(alpha = color.alpha * 0.25f), 0f, 360f, false, topLeft = Offset(stroke, stroke),
            size = Size(size.width - 2 * stroke, size.height - 2 * stroke), style = Stroke(stroke))
        drawArc(color, turn - 90f, 100f, false, topLeft = Offset(stroke, stroke),
            size = Size(size.width - 2 * stroke, size.height - 2 * stroke), style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}
