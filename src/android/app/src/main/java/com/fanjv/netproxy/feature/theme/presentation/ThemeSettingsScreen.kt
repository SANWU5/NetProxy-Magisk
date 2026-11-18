package com.fanjv.netproxy.feature.theme.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.ui.theme.ColorMode
import com.fanjv.netproxy.navigation.LocalNavigator
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiRadioRow
import dev.aiingjie.coui.CouiSliderRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import kotlin.math.roundToInt

/** 主题页：明暗模式、页面缩放、预测式返回，以及仍作用于 Miuix 子页面的模糊与平滑圆角。 */
@Composable
internal fun ThemeSettingsScreen(viewModel: ThemeViewModel) {
    val theme by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val selected = ColorMode.fromValue(theme.colorMode).appearance

    CouiSubPage(title = stringResource(R.string.settings_theme), onBack = { navigator.pop() }) {
        item {
            CouiGroup(first = true) {
                listOf(
                    ColorMode.SYSTEM to R.string.settings_theme_mode_system,
                    ColorMode.LIGHT to R.string.settings_theme_mode_light,
                    ColorMode.DARK to R.string.settings_theme_mode_dark,
                ).forEach { (option, label) ->
                    CouiRadioRow(stringResource(label), selected == option, { viewModel.setThemeMode(option.value) })
                }
            }
        }
        item {
            CouiGroup {
                var scale by remember(theme.pageScale) { mutableFloatStateOf(theme.pageScale) }
                CouiSliderRow(
                    title = stringResource(R.string.settings_page_scale),
                    valueText = stringResource(R.string.percentage_value, (scale * 100).roundToInt()),
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = 0.8f..1.1f,
                    steps = 2,
                    summary = stringResource(R.string.settings_page_scale_summary),
                    onValueChangeFinished = { viewModel.setPageScale(scale) },
                )
                CouiSwitchRow(
                    title = stringResource(R.string.settings_enable_predictive_back),
                    checked = theme.enablePredictiveBack,
                    onCheckedChange = viewModel::setEnablePredictiveBack,
                    summary = stringResource(R.string.settings_enable_predictive_back_summary),
                )
            }
        }
        item {
            CouiGroup {
                CouiSwitchRow(
                    title = stringResource(R.string.settings_enable_blur),
                    checked = theme.enableBlur,
                    onCheckedChange = viewModel::setEnableBlur,
                    summary = stringResource(R.string.settings_enable_blur_summary),
                )
                CouiSwitchRow(
                    title = stringResource(R.string.settings_smooth_corner),
                    checked = theme.enableSmoothCorner,
                    onCheckedChange = viewModel::setEnableSmoothCorner,
                    summary = stringResource(R.string.settings_smooth_corner_summary),
                )
            }
        }
    }
}
