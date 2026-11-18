package dev.aiingjie.coui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class CouiDividerInset(val start: Dp)

private class DividerPlan {
    var count = 0
    var y = FloatArray(16)
    var start = FloatArray(16)
    fun reset() { count = 0 }
    fun add(yPx: Float, startPx: Float) {
        if (count == y.size) { y = y.copyOf(count * 2); start = start.copyOf(count * 2) }
        y[count] = yPx; start[count] = startPx; count++
    }
}

@Composable
fun CouiCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = Coui.colors
    val plan = remember { DividerPlan() }
    val bounds = remember { CouiCardBounds() }
    Layout(
        content = {
            CompositionLocalProvider(LocalCouiCardEdge provides CouiDimens.cardEdgeExtra, LocalCouiCardBounds provides bounds, content = content)
        },
        modifier = modifier
            .padding(horizontal = CouiDimens.cardMargin)
            .fillMaxWidth()
            .clip(CouiShapes.card)
            .background(colors.card)
            .onPlaced { bounds.coordinates = it }
            .drawWithContent {
                drawContent()
                val t = max(1f, CouiDimens.dividerThickness.toPx())
                val end = size.width - CouiDimens.rowInnerPadding.toPx()
                for (i in 0 until plan.count) {
                    val s = plan.start[i]
                    if (end > s) drawRect(colors.divider, Offset(s, plan.y[i] - t), Size(end - s, t))
                }
            },
    ) { measurables, constraints ->
        val edge = CouiDimens.cardEdgeExtra.roundToPx()
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val last = placeables.indexOfLast { it.height > 0 }
        val height = placeables.sumOf { it.height } + if (last < 0) 0 else 2 * edge
        layout(constraints.maxWidth, height) {
            plan.reset()
            var y = if (last < 0) 0 else edge
            placeables.forEachIndexed { i, p ->
                p.place(0, y)
                y += p.height
                if (p.height > 0 && i < last) {
                    val inset = (measurables[i].layoutId as? CouiDividerInset)?.start ?: CouiDimens.rowInnerPadding
                    plan.add(y.toFloat(), inset.toPx())
                }
            }
        }
    }
}

@Composable
fun CouiCategory(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier.fillMaxWidth().heightIn(min = 16.dp).padding(start = CouiDimens.textInset, end = CouiDimens.textInset, top = 8.dp, bottom = 8.dp),
        style = Coui.type.category.copy(color = Coui.colors.labelSecondary),
    )
}

@Composable
fun CouiFooter(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier.fillMaxWidth().padding(start = CouiDimens.textInset, end = CouiDimens.textInset, top = 8.dp),
        style = Coui.type.footer.copy(color = Coui.colors.labelSecondary),
    )
}

@Composable
fun CouiGroup(
    title: String? = null,
    footer: String? = null,
    first: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (!first) Spacer(Modifier.height(CouiDimens.categoryGap))
        if (title != null) CouiCategory(title)
        CouiCard(content = content)
        if (footer != null) CouiFooter(footer)
    }
}

@Composable
fun CouiRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    titleColor: Color? = null,
    minHeight: Dp = CouiDimens.rowMinHeight,
    leading: (@Composable () -> Unit)? = null,
    role: Role? = null,
    source: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = Coui.colors
    val type = Coui.type
    val clickable = if (onClick != null) Modifier.couiPressable(enabled = enabled, role = role, source = source, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).then(clickable).padding(horizontal = CouiDimens.rowInnerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(CouiDimens.iconToText))
        }
        Column(Modifier.weight(1f).padding(vertical = CouiDimens.textBlockVertical)) {
            BasicText(title, style = type.rowTitle.copy(color = if (!enabled) colors.labelTertiary else titleColor ?: colors.label))
            if (!summary.isNullOrEmpty()) {
                Spacer(Modifier.height(CouiDimens.titleSummaryGap))
                BasicText(summary, style = type.rowSummary.copy(color = if (enabled) colors.labelSecondary else colors.labelTertiary))
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun RowScope.JumpTrail(value: String?, dot: Boolean, enabled: Boolean, glyph: CouiGlyph) {
    val colors = Coui.colors
    if (dot) Box(Modifier.padding(start = 16.dp).size(6.dp).clip(CircleShape).background(colors.redDot))
    if (!value.isNullOrEmpty()) {
        BasicText(
            value,
            Modifier.padding(start = if (dot) 4.dp else CouiDimens.titleToValue).widthIn(max = CouiDimens.jumpValueMaxWidth),
            style = Coui.type.jumpValue.copy(color = if (enabled) colors.labelSecondary else colors.labelTertiary, textAlign = TextAlign.End),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.width(CouiDimens.arrowGap))
    CouiIcon(glyph, if (enabled) colors.labelTertiary else colors.labelQuaternary)
}

@Composable
fun CouiJumpRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    summary: String? = null,
    enabled: Boolean = true,
    dot: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    CouiRow(title, modifier, summary, enabled, leading = leading, role = Role.Button, onClick = onClick,
        minHeight = if (leading != null) 60.dp else CouiDimens.rowMinHeight) {
        JumpTrail(value, dot, enabled, CouiGlyph.Next)
    }
}

@Composable
fun CouiMenuRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    value: String? = options.getOrNull(selected),
) {
    val overlay = LocalCouiOverlay.current
    val bounds = remember { mutableStateOf(Rect.Zero) }
    CouiRow(title, modifier.onGloballyPositioned { bounds.value = it.boundsInRoot() }, summary, enabled, role = Role.DropdownList,
        onClick = { overlay.showMenu(bounds.value, options, selected, onSelect) }) {
        JumpTrail(value, false, enabled, CouiGlyph.PopupNext)
    }
}

@Composable
fun CouiSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    CouiRow(title, modifier, summary, enabled, leading = leading, role = Role.Switch, source = source,
        minHeight = if (leading != null) 60.dp else CouiDimens.rowMinHeight, onClick = { onCheckedChange(!checked) }) {
        Spacer(Modifier.width(CouiDimens.widgetGap))
        CouiSwitch(checked, null, enabled = enabled, pressed = pressed)
    }
}

@Composable
fun CouiSwitchJumpRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    switchEnabled: Boolean = enabled,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = Coui.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(modifier.fillMaxWidth().heightIn(min = if (leading != null) 60.dp else CouiDimens.rowMinHeight),
        verticalAlignment = Alignment.CenterVertically) {
        CouiRow(title, Modifier.weight(1f), summary, enabled, leading = leading, role = Role.Button,
            minHeight = if (leading != null) 60.dp else CouiDimens.rowMinHeight, onClick = onClick)
        Box(Modifier.width(1.dp).height(22.dp).background(colors.divider))
        Box(
            Modifier.heightIn(min = if (leading != null) 60.dp else CouiDimens.rowMinHeight)
                .couiPressable(enabled = switchEnabled, role = Role.Switch, source = source) { onCheckedChange(!checked) }
                .padding(start = CouiDimens.widgetGap, end = CouiDimens.rowInnerPadding),
            contentAlignment = Alignment.Center,
        ) { CouiSwitch(checked, null, enabled = switchEnabled, pressed = pressed) }
    }
}

@Composable
fun CouiRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    CouiRow(title, modifier, summary, enabled, role = Role.RadioButton, onClick = onClick) {
        Spacer(Modifier.width(CouiDimens.widgetGap))
        CouiRadio(selected, enabled = enabled)
    }
}

@Composable
fun CouiInfoRow(title: String, value: String?, modifier: Modifier = Modifier, summary: String? = null, onClick: (() -> Unit)? = null) {
    val colors = Coui.colors
    CouiRow(title, modifier, summary, onClick = onClick) {
        if (!value.isNullOrEmpty()) BasicText(value, Modifier.padding(start = CouiDimens.titleToValue).widthIn(max = 200.dp),
            style = Coui.type.rowValue.copy(color = colors.labelSecondary, textAlign = TextAlign.End), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CouiActionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true,
                  summary: String? = null) {
    val colors = Coui.colors
    CouiRow(title, modifier, summary, enabled, titleColor = if (danger) colors.error else colors.accentText, role = Role.Button, onClick = onClick)
}

@Composable
fun CouiButtonRow(title: String, button: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null,
                  enabled: Boolean = true) {
    CouiRow(title, modifier, summary, enabled) {
        Spacer(Modifier.width(CouiDimens.widgetGap))
        CouiSmallButton(button, onClick, enabled = enabled)
    }
}

@Composable
fun CouiSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    summary: String? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = Coui.colors
    Column(modifier.fillMaxWidth().padding(top = CouiDimens.textBlockVertical, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = CouiDimens.rowInnerPadding), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, Modifier.weight(1f), style = Coui.type.rowTitle.copy(color = if (enabled) colors.label else colors.labelTertiary))
            BasicText(valueText, Modifier.widthIn(min = 36.dp), style = Coui.type.jumpValue.copy(
                color = if (enabled) colors.labelSecondary else colors.labelTertiary, textAlign = TextAlign.End))
        }
        if (!summary.isNullOrEmpty()) BasicText(summary, Modifier.padding(start = CouiDimens.rowInnerPadding, end = CouiDimens.rowInnerPadding, top = 2.dp),
            style = Coui.type.rowSummary.copy(color = colors.labelSecondary))
        CouiSeekBar(value, onValueChange, Modifier.padding(horizontal = CouiDimens.rowInnerPadding), valueRange, steps, enabled, onValueChangeFinished)
    }
}

@Composable
fun CouiAppIcon(bitmap: ImageBitmap?, modifier: Modifier = Modifier, sizeDp: Int = 36) {
    if (bitmap != null) Image(bitmap, null, modifier.size(sizeDp.dp).clip(CouiSmoothShape((sizeDp * 0.28f).dp)))
    else Box(modifier.size(sizeDp.dp).clip(CouiSmoothShape((sizeDp * 0.28f).dp)).background(Coui.colors.container8))
}

@Composable
fun CouiListTop() = Spacer(Modifier.height(CouiDimens.listTop))
