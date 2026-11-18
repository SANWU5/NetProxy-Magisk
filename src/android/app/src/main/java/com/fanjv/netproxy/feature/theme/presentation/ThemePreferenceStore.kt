package com.fanjv.netproxy.feature.theme.presentation

import android.content.SharedPreferences
import androidx.core.content.edit
import com.fanjv.netproxy.core.ui.theme.AppThemeDefaults

/** 主题偏好的 SharedPreferences 读写适配器。 */
internal class ThemePreferenceStore(
    private val prefs: SharedPreferences
) {
    fun read() = ThemePreferencesSnapshot(
        colorMode = prefs.getInt("color_mode", AppThemeDefaults.colorMode.value),
        darkStyle = prefs.getInt("dark_style", AppThemeDefaults.darkStyle.value),
        accent = prefs.getInt("accent_color", AppThemeDefaults.accent.value),
        enablePredictiveBack = prefs.getBoolean("enable_predictive_back", false),
        pageScale = prefs.getFloat("page_scale", 1.0f)
    )

    fun setThemeMode(mode: Int) = prefs.edit { putInt("color_mode", mode) }
    fun setDarkStyle(style: Int) = prefs.edit { putInt("dark_style", style) }
    fun setAccent(accent: Int) = prefs.edit { putInt("accent_color", accent) }
    fun setEnablePredictiveBack(enabled: Boolean) =
        prefs.edit { putBoolean("enable_predictive_back", enabled) }

    fun setPageScale(scale: Float) = prefs.edit { putFloat("page_scale", scale) }
}

internal data class ThemePreferencesSnapshot(
    val colorMode: Int,
    val darkStyle: Int,
    val accent: Int,
    val enablePredictiveBack: Boolean,
    val pageScale: Float,
)
