package com.fanjv.netproxy.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
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
import dev.aiingjie.coui.cardOpaque

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
}

object AppThemeDefaults {
    val colorMode = ColorMode.SYSTEM
}

/** 应用主题设置快照（明暗、圆角与预测式返回）。 */
data class AppThemeSettings(
    val colorMode: ColorMode,
    val enableSmoothCorner: Boolean,
    val enablePredictiveBack: Boolean,
)

/** 应用根主题：以 COUI 配色配置 Miuix 主题，并通过 CompositionLocal 下发各开关。 */
@Composable
fun NetProxyTheme(
    appThemeSettings: AppThemeSettings,
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        appThemeSettings.colorMode.isDark || (appThemeSettings.colorMode.isSystem && systemDarkTheme)

    // 界面统一为 COUI 风格：尚未改写的 Miuix 页面也使用 COUI 的页面、卡片、文字与主色，
    // 避免同一应用里出现两套配色。
    val controller = MiuixThemeController(
        if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
        lightColors = couiMiuixColors(CouiLightColors),
        darkColors = couiMiuixColors(CouiDarkColors),
        isDark = darkTheme,
    )

    MiuixTheme(
        controller = controller,
    ) {
        CompositionLocalProvider(
            LocalColorMode provides appThemeSettings.colorMode.value,
            LocalEnablePredictiveBack provides appThemeSettings.enablePredictiveBack,
            LocalSquircleEnabled provides appThemeSettings.enableSmoothCorner,
            content = content,
        )
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
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        ColorMode.LIGHT.value, ColorMode.MONET_LIGHT.value -> false
        ColorMode.DARK.value, ColorMode.MONET_DARK.value, ColorMode.DARK_AMOLED.value -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalColorMode = staticCompositionLocalOf { ColorMode.SYSTEM.value }

val LocalEnablePredictiveBack = staticCompositionLocalOf { false }
