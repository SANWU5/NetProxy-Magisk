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
        enableBlur = prefs.getBoolean("enable_blur", true),
        enablePredictiveBack = prefs.getBoolean("enable_predictive_back", false),
        enableSmoothCorner = prefs.getBoolean("enable_smooth_corner", true),
        pageScale = prefs.getFloat("page_scale", 1.0f)
    )

    fun setThemeMode(mode: Int) = prefs.edit { putInt("color_mode", mode) }
    fun setEnableBlur(enabled: Boolean) = prefs.edit { putBoolean("enable_blur", enabled) }
    fun setEnablePredictiveBack(enabled: Boolean) =
        prefs.edit { putBoolean("enable_predictive_back", enabled) }

    fun setEnableSmoothCorner(enabled: Boolean) =
        prefs.edit { putBoolean("enable_smooth_corner", enabled) }

    fun setPageScale(scale: Float) = prefs.edit { putFloat("page_scale", scale) }
}

internal data class ThemePreferencesSnapshot(
    val colorMode: Int,
    val enableBlur: Boolean,
    val enablePredictiveBack: Boolean,
    val enableSmoothCorner: Boolean,
    val pageScale: Float,
)
