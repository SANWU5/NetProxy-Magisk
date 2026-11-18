package com.fanjv.netproxy.feature.theme.presentation

import androidx.compose.runtime.Immutable
import com.fanjv.netproxy.core.ui.theme.AppThemeDefaults

@Immutable
data class ThemeUiState(
    val colorMode: Int = AppThemeDefaults.colorMode.value,
    val darkStyle: Int = AppThemeDefaults.darkStyle.value,
    val accent: Int = AppThemeDefaults.accent.value,
    val enablePredictiveBack: Boolean = false,
    val pageScale: Float = 1.0f
)
