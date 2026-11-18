package com.fanjv.netproxy.feature.theme.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.ui.theme.AccentColor
import com.fanjv.netproxy.core.ui.theme.ColorMode
import com.fanjv.netproxy.core.ui.theme.DarkStyle
import com.fanjv.netproxy.core.ui.theme.appCouiColors
import com.fanjv.netproxy.navigation.LocalNavigator
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiColors
import dev.aiingjie.coui.CouiGlyph
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiIcon
import dev.aiingjie.coui.CouiMotion
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiSliderRow
import dev.aiingjie.coui.CouiSmoothShape
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import dev.aiingjie.coui.cardOpaque
import dev.aiingjie.coui.couiPressable
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween

/**
 * 主题页：按 COUI 的浅色与深色设计提供两张外观预览，点按即切换；可以跟随系统，
 * 并选择深色风格（纯黑到柔和深灰）、主题色、界面缩放与预测性返回。所有选择立即生效。
 */
@Composable
internal fun ThemeSettingsScreen(viewModel: ThemeViewModel) {
    val theme by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val mode = ColorMode.fromValue(theme.colorMode)
    val followSystem = mode.appearance == ColorMode.SYSTEM
    val dark = mode.resolveDark(isSystemInDarkTheme())
    val darkStyle = DarkStyle.fromValue(theme.darkStyle)
    val accent = AccentColor.fromValue(theme.accent)

    val lightText = stringResource(R.string.settings_theme_mode_light)
    val darkText = stringResource(R.string.settings_theme_mode_dark)
    val styleLabels = mapOf(
        DarkStyle.ENHANCED to stringResource(R.string.settings_dark_style_enhanced),
        DarkStyle.MEDIUM to stringResource(R.string.settings_dark_style_medium),
        DarkStyle.SOFT to stringResource(R.string.settings_dark_style_soft),
    )
    val accentLabels = mapOf(
        AccentColor.BLUE to stringResource(R.string.settings_accent_blue),
        AccentColor.GREEN to stringResource(R.string.settings_accent_green),
        AccentColor.ORANGE to stringResource(R.string.settings_accent_orange),
        AccentColor.RED to stringResource(R.string.settings_accent_red),
        AccentColor.PURPLE to stringResource(R.string.settings_accent_purple),
        AccentColor.TEAL to stringResource(R.string.settings_accent_teal),
    )

    CouiSubPage(title = stringResource(R.string.settings_theme), onBack = { navigator.pop() }) {
        item {
            CouiGroup(first = true, title = stringResource(R.string.settings_theme_appearance)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AppearanceOption(lightText, remember(darkStyle, accent) { appCouiColors(false, darkStyle, accent) }, !dark) {
                        viewModel.setThemeMode(ColorMode.LIGHT.value)
                    }
                    AppearanceOption(darkText, remember(darkStyle, accent) { appCouiColors(true, darkStyle, accent) }, dark) {
                        viewModel.setThemeMode(ColorMode.DARK.value)
                    }
                }
                CouiSwitchRow(
                    title = stringResource(R.string.settings_theme_mode_system),
                    checked = followSystem,
                    // 关闭跟随系统时保持当前的明暗，界面不会突然变化。
                    onCheckedChange = { on ->
                        viewModel.setThemeMode(
                            when {
                                on -> ColorMode.SYSTEM.value
                                dark -> ColorMode.DARK.value
                                else -> ColorMode.LIGHT.value
                            }
                        )
                    },
                    summary = stringResource(R.string.settings_theme_follow_system_summary),
                )
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.settings_dark_style), footer = stringResource(R.string.settings_dark_style_footer)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DarkStyle.entries.forEach { style ->
                        DarkStyleOption(
                            label = styleLabels.getValue(style),
                            colors = remember(style, accent) { appCouiColors(true, style, accent) },
                            selected = style == darkStyle,
                            modifier = Modifier.weight(1f),
                        ) { viewModel.setDarkStyle(style.value) }
                    }
                }
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.settings_accent)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AccentColor.entries.forEach { option ->
                        AccentOption(
                            color = if (dark) option.dark else option.light,
                            label = accentLabels.getValue(option),
                            selected = option == accent,
                        ) { viewModel.setAccent(option.value) }
                    }
                }
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.settings_display)) {
                var scale by remember(theme.pageScale) { mutableFloatStateOf(theme.pageScale) }
                CouiSliderRow(
                    title = stringResource(R.string.settings_page_scale),
                    valueText = stringResource(R.string.percentage_value, (scale * 100).roundToInt()),
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = 0.8f..1.1f,
                    steps = 2,
                    summary = stringResource(R.string.settings_page_scale_summary),
                    // 松手后再应用：拖动中整页缩放会让滑杆跟着移动。
                    onValueChangeFinished = { viewModel.setPageScale(scale) },
                )
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.settings_interaction)) {
                CouiSwitchRow(
                    title = stringResource(R.string.settings_enable_predictive_back),
                    checked = theme.enablePredictiveBack,
                    onCheckedChange = viewModel::setEnablePredictiveBack,
                    summary = stringResource(R.string.settings_enable_predictive_back_summary),
                )
            }
        }
    }
}

/** 一张外观预览：用对应配色画出的页面缩略图，下面是名称与单选。 */
@Composable
private fun AppearanceOption(label: String, colors: CouiColors, selected: Boolean, onClick: () -> Unit) {
    val ui = Coui.colors
    val border by animateColorAsState(if (selected) ui.accent else ui.divider, tween(CouiMotion.VERY_FAST), label = "appearance")
    val width by animateDpAsState(if (selected) 2.dp else 1.dp, tween(CouiMotion.VERY_FAST), label = "appearance-width")
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .couiPressable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageSketch(colors, Modifier.size(width = 100.dp, height = 178.dp).border(width, border, CouiSmoothShape(18.dp)).padding(3.dp))
        Spacer(Modifier.height(12.dp))
        BasicText(label, style = Coui.type.rowTitle.copy(color = ui.label))
        Spacer(Modifier.height(10.dp))
        CouiRadio(selected = selected)
    }
}

/** 页面缩略图：大标题、两张卡片（行文字与开启的开关）和底栏，颜色全部来自 [colors]。 */
@Composable
private fun PageSketch(colors: CouiColors, modifier: Modifier) {
    Column(
        modifier.clip(CouiSmoothShape(15.dp)).background(colors.page).padding(horizontal = 7.dp, vertical = 9.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Line(colors.label, 44.dp, 7.dp)
        Spacer(Modifier.height(10.dp))
        SketchCard(colors) {
            SketchRow(colors, 38.dp, toggle = true)
            SketchRow(colors, 30.dp, toggle = false)
        }
        Spacer(Modifier.height(6.dp))
        SketchCard(colors) {
            SketchRow(colors, 44.dp, toggle = false)
            SketchRow(colors, 26.dp, toggle = true)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(58.dp, 13.dp).clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF262628) else Color(0xFFF7F7F7))
                .border(0.5.dp, if (colors.isDark) Color(0x1AFFFFFF) else Color(0x14000000), CircleShape),
        )
    }
}

@Composable
private fun SketchCard(colors: CouiColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.cardOpaque).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun SketchRow(colors: CouiColors, width: Dp, toggle: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Line(colors.label.copy(alpha = 0.75f), width, 4.dp)
            Spacer(Modifier.height(3.dp))
            Line(colors.labelTertiary, width * 0.6f, 3.dp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(15.dp, 9.dp).clip(CircleShape).background(if (toggle) colors.accent else colors.divider),
            contentAlignment = if (toggle) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.padding(1.5.dp).size(6.dp).clip(CircleShape).background(colors.thumb)) }
    }
}

@Composable
private fun Line(color: Color, width: Dp, height: Dp) {
    Box(Modifier.size(width, height).clip(CircleShape).background(color))
}

/** 深色风格：小块深色页面色样，选中时描主色边；三项按宽度均分，窄屏放大界面时也不会超出卡片。 */
@Composable
private fun DarkStyleOption(label: String, colors: CouiColors, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ui = Coui.colors
    val border by animateColorAsState(if (selected) ui.accent else ui.divider, tween(CouiMotion.VERY_FAST), label = "dark-style")
    val labelColor by animateColorAsState(if (selected) ui.accentText else ui.labelSecondary, tween(CouiMotion.VERY_FAST), label = "dark-style-label")
    Column(
        modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .couiPressable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = label }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 84.dp).height(52.dp).border(if (selected) 2.dp else 1.dp, border, CouiSmoothShape(14.dp)).padding(3.dp)
                .clip(CouiSmoothShape(11.dp)).background(colors.page).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(colors.cardOpaque))
            Box(Modifier.width(36.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface))
        }
        Spacer(Modifier.height(8.dp))
        BasicText(label, style = Coui.type.rowSummary.copy(color = labelColor))
    }
}

/** 主题色色块：选中时中间显示对勾并加一圈描边。 */
@Composable
private fun AccentOption(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) color.copy(alpha = 0.35f) else Color.Transparent, tween(CouiMotion.VERY_FAST), label = "accent")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(3.dp, ring, CircleShape)
            .couiPressable(role = Role.RadioButton, shape = CircleShape, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = label }
            .padding(6.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) CouiIcon(CouiGlyph.Check, Color.White, Modifier.size(20.dp))
    }
}
