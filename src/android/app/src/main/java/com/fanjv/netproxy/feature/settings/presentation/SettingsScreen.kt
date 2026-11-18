package com.fanjv.netproxy.feature.settings.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.LocalModuleAvailability
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiDockSpace
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiLargeTitlePage
import dev.aiingjie.coui.CouiSwitchRow

/** 设置页：模块开关与各功能入口。 */
@Composable
internal fun SettingsScreen(
    isActive: Boolean = true,
    viewModel: SettingsViewModel = netProxyViewModel()
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val available = LocalModuleAvailability.current?.available == true
    val navigator = LocalNavigator.current

    LifecycleResumeEffect(isActive, available) {
        viewModel.setVisible(isActive && available)
        onPauseOrDispose { if (isActive && available) viewModel.setVisible(false) }
    }

    CouiLargeTitlePage(title = stringResource(R.string.settings), bottomSpace = CouiDockSpace) {
        item {
            CouiGroup(title = stringResource(R.string.settings_section_proxy), first = true) {
                CouiJumpRow(
                    title = stringResource(R.string.inbound_settings),
                    summary = stringResource(R.string.inbound_settings_summary),
                    leading = { RowIcon(Icons.Rounded.Router) },
                    onClick = { navigator.push(Route.InboundSettings) },
                )
                CouiJumpRow(
                    title = stringResource(R.string.network_matching),
                    summary = stringResource(R.string.network_matching_summary),
                    leading = { RowIcon(Icons.Rounded.Wifi) },
                    onClick = { navigator.push(Route.NetworkMatching) },
                )
                CouiJumpRow(
                    title = stringResource(R.string.routing_rules),
                    summary = stringResource(R.string.routing_rules_summary),
                    leading = { RowIcon(Icons.AutoMirrored.Rounded.AltRoute) },
                    onClick = { navigator.push(Route.RoutingRules) },
                )
                CouiJumpRow(
                    title = stringResource(R.string.kernel_settings),
                    summary = stringResource(R.string.kernel_settings_summary),
                    leading = { RowIcon(Icons.Rounded.Memory) },
                    onClick = { navigator.push(Route.KernelSettings) },
                )
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.settings_section_general)) {
                if (available) {
                    CouiSwitchRow(
                        title = stringResource(R.string.auto_start),
                        checked = settings.autoStartEnabled,
                        onCheckedChange = { viewModel.setAutoStartEnabled(it) },
                        summary = stringResource(R.string.auto_start_summary),
                        enabled = settings.hasLoaded,
                        leading = { RowIcon(Icons.Rounded.PowerSettingsNew) },
                    )
                }
                CouiJumpRow(
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_summary),
                    leading = { RowIcon(Icons.Rounded.Palette) },
                    onClick = { navigator.push(Route.ThemeSettings) },
                )
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.settings_section_support)) {
                CouiJumpRow(
                    title = stringResource(R.string.logs),
                    leading = { RowIcon(Icons.Rounded.BugReport) },
                    onClick = { navigator.push(Route.Logs) },
                )
                CouiJumpRow(
                    title = stringResource(R.string.about),
                    leading = { RowIcon(Icons.Rounded.ContactPage) },
                    onClick = { navigator.push(Route.About) },
                )
            }
        }
    }
}

/** 设置行左侧的线条图标，颜色跟随一级文字。 */
@Composable
internal fun RowIcon(icon: ImageVector) {
    Image(icon, contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(Coui.colors.label))
}
