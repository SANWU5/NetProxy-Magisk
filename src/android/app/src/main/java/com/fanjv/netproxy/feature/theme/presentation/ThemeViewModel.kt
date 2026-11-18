package com.fanjv.netproxy.feature.theme.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/** 管理仅属于 Android 客户端的主题与界面偏好。 */
internal class ThemeViewModel(
    private val manager: ThemeManager
) : ViewModel() {

    val state: StateFlow<ThemeUiState> = manager.state

    fun setThemeMode(mode: Int) = manager.setThemeMode(mode)
    fun setEnableBlur(enabled: Boolean) = manager.setEnableBlur(enabled)
    fun setEnablePredictiveBack(enabled: Boolean) = manager.setEnablePredictiveBack(enabled)
    fun setEnableSmoothCorner(enabled: Boolean) = manager.setEnableSmoothCorner(enabled)
    fun setPageScale(scale: Float) = manager.setPageScale(scale)
}

