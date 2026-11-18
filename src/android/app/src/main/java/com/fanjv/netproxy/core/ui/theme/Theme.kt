package com.fanjv.netproxy.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController as MiuixThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import dev.aiingjie.coui.CouiColors
import dev.aiingjie.coui.CouiDarkColors
import dev.aiingjie.coui.CouiLightColors
import dev.aiingjie.coui.CouiTheme
import dev.aiingjie.coui.cardOpaque
import dev.aiingjie.coui.withAccent
import dev.aiingjie.coui.withDarkGray

/**
 * 主题配色模式：跟随系统、浅色、深色。
 * 3–6 是旧版 Monet 动态色与纯黑模式的存储值，升级后按对应的明暗读取。
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == SYSTEM.value || value == MONET_SYSTEM.value
    val isDark: Boolean get() = value == DARK.value || value == MONET_DARK.value || value == DARK_AMOLED.value

    /** 主题页对应的选项：跟随系统、浅色或深色。 */
    val appearance: ColorMode
        get() = when {
            isSystem -> SYSTEM
            isDark -> DARK
            else -> LIGHT
        }

    /** 这个模式在系统处于 [systemDark] 时实际使用深色。 */
    fun resolveDark(systemDark: Boolean): Boolean = isDark || (isSystem && systemDark)
}

/** 深色风格：增强是纯黑背景，中等与柔和按 COUI 的深色灰度逐级调亮背景与浮层。 */
enum class DarkStyle(val value: Int, val grayLevel: Float) {
    ENHANCED(0, 0f),
    MEDIUM(1, 0.5f),
    SOFT(2, 1f);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: ENHANCED
    }
}

/** 主题色：浅色与深色各一档，深色比浅色亮一档；深色的主色文字再亮一些，保证在黑底上易读。 */
enum class AccentColor(val value: Int, val light: Color, val dark: Color, val darkText: Color) {
    BLUE(0, Color(0xFF0080FF), Color(0xFF1A8CFF), Color(0xFF5C9DFF)),
    GREEN(1, Color(0xFF14A85C), Color(0xFF22B86A), Color(0xFF52C98A)),
    ORANGE(2, Color(0xFFFF7700), Color(0xFFFF8A1F), Color(0xFFFFA352)),
    RED(3, Color(0xFFE5483D), Color(0xFFFF5A50), Color(0xFFFF7A71)),
    PURPLE(4, Color(0xFF7B5CFF), Color(0xFF8C72FF), Color(0xFFA693FF)),
    TEAL(5, Color(0xFF00A3B5), Color(0xFF12B4C5), Color(0xFF4CC8D6));

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: BLUE
    }
}

object AppThemeDefaults {
    val colorMode = ColorMode.SYSTEM
    val darkStyle = DarkStyle.ENHANCED
    val accent = AccentColor.BLUE
}

/** 应用主题设置快照（明暗、深色风格、主题色与预测式返回）。 */
data class AppThemeSettings(
    val colorMode: ColorMode,
    val darkStyle: DarkStyle,
    val accent: AccentColor,
    val enablePredictiveBack: Boolean,
)

/** 按明暗、深色风格与主题色生成 COUI 配色；默认蓝色与 COUI 原始配色完全一致。 */
fun appCouiColors(dark: Boolean, darkStyle: DarkStyle, accent: AccentColor): CouiColors =
    if (dark) CouiDarkColors.withDarkGray(darkStyle.grayLevel).withAccent(accent.dark, accent.darkText)
    else CouiLightColors.withAccent(accent.light)

/**
 * 应用根主题：COUI 配色同时用于 COUI 组件与尚未改写的 Miuix 部件，避免同一应用里出现两套配色。
 * [dark] 与 [colors] 由调用方按设置算好，系统栏、窗口底色与底栏也使用同一份结果。
 */
@Composable
fun NetProxyTheme(
    appThemeSettings: AppThemeSettings,
    dark: Boolean,
    colors: CouiColors,
    content: @Composable () -> Unit
) {
    val miuixLight = remember(appThemeSettings.accent) {
        couiMiuixColors(appCouiColors(false, appThemeSettings.darkStyle, appThemeSettings.accent))
    }
    val miuixDark = remember(appThemeSettings.darkStyle, appThemeSettings.accent) {
        couiMiuixColors(appCouiColors(true, appThemeSettings.darkStyle, appThemeSettings.accent))
    }
    val controller = MiuixThemeController(
        if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
        lightColors = miuixLight,
        darkColors = miuixDark,
        isDark = dark,
    )

    MiuixTheme(
        controller = controller,
    ) {
        CompositionLocalProvider(
            LocalColorMode provides appThemeSettings.colorMode.value,
            LocalEnablePredictiveBack provides appThemeSettings.enablePredictiveBack,
            // COUI 设计规范里卡片类一律使用平滑圆角，剩余的 Miuix 部件保持一致。
            LocalSquircleEnabled provides true,
        ) {
            CouiTheme(dark = dark, colors = colors, content = content)
        }
    }
}

/** 把 COUI 配色映射到 Miuix 的颜色角色。 */
private fun couiMiuixColors(coui: CouiColors) = if (coui.isDark) {
    darkColorScheme(
        primary = coui.accent, onPrimary = Color.White,
        primaryContainer = coui.accentSecondary, onPrimaryContainer = coui.accentText,
        secondaryContainer = coui.container8, onSecondaryContainer = coui.label,
        error = coui.error,
        background = coui.page, onBackground = coui.label,
        surface = coui.page, onSurface = coui.label,
        onSurfaceSecondary = coui.labelSecondary, onSurfaceVariantSummary = coui.labelSecondary,
        onSurfaceVariantActions = coui.labelSecondary,
        surfaceContainer = coui.cardOpaque, onSurfaceContainer = coui.label,
        surfaceContainerHigh = coui.surface, surfaceContainerHighest = coui.surfaceTop,
        dividerLine = coui.divider, outline = coui.divider,
    )
} else {
    lightColorScheme(
        primary = coui.accent, onPrimary = Color.White,
        primaryContainer = coui.accentSecondary, onPrimaryContainer = coui.accentText,
        secondaryContainer = coui.container8, onSecondaryContainer = coui.label,
        error = coui.error,
        background = coui.page, onBackground = coui.label,
        surface = coui.page, onSurface = coui.label,
        onSurfaceSecondary = coui.labelSecondary, onSurfaceVariantSummary = coui.labelSecondary,
        onSurfaceVariantActions = coui.labelSecondary,
        surfaceContainer = coui.cardOpaque, onSurfaceContainer = coui.label,
        surfaceContainerHigh = coui.surface, surfaceContainerHighest = coui.surfaceTop,
        dividerLine = coui.divider, outline = coui.divider,
    )
}

/** 当前是否处于深色主题（供 Composable 只读读取）。 */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean = ColorMode.fromValue(LocalColorMode.current).resolveDark(isSystemInDarkTheme())

val LocalColorMode = staticCompositionLocalOf { ColorMode.SYSTEM.value }

val LocalEnablePredictiveBack = staticCompositionLocalOf { false }
