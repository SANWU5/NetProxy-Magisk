package com.fanjv.netproxy.feature.dashboard.presentation

import com.fanjv.netproxy.navigation.Route
import com.fanjv.netproxy.navigation.LocalNavigator
import dev.aiingjie.coui.CouiMenuRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.module.ModuleConnectionState
import com.fanjv.netproxy.core.ui.component.LocalModuleAvailability
import com.fanjv.netproxy.core.ui.resolve
import dev.aiingjie.coui.CouiDockSpace
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInfoRow
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiLargeTitlePage
import dev.aiingjie.coui.CouiStatus
import dev.aiingjie.coui.CouiTipsAction
import dev.aiingjie.coui.CouiTopTips
import dev.aiingjie.coui.LocalCouiOverlay
import kotlin.math.roundToInt

/** 仪表盘：完整就绪时间以模块状态机为准，运行数据来自本机控制接口。 */
@Composable
internal fun CatalogDashboardScreen(
    isActive: Boolean = true,
    onNavigateToNodes: () -> Unit = {},
    onNavigateToChain: () -> Unit = {},
    onReconnect: () -> Unit = {},
    viewModel: CatalogDashboardViewModel = netProxyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val availability = LocalModuleAvailability.current
    val overlay = LocalCouiOverlay.current
    LaunchedEffect(availability) { viewModel.setAvailability(availability) }
    val noModuleMessage =
        if (availability?.state == ModuleConnectionState.UNTRUSTED) stringResource(R.string.module_identity_failed)
        else stringResource(R.string.no_module_summary)

    LifecycleResumeEffect(isActive) {
        viewModel.setVisible(isActive)
        onPauseOrDispose { if (isActive) viewModel.setVisible(false) }
    }

    val notice = state.notice.resolve()
    LaunchedEffect(state.noticeId) {
        if (notice.isBlank()) return@LaunchedEffect
        viewModel.clearNotice()
        overlay.showSnack(notice)
    }

    val reconnectText = stringResource(R.string.module_reconnect)
    val navigator = LocalNavigator.current
    CouiLargeTitlePage(title = stringResource(R.string.dashboard), bottomSpace = CouiDockSpace) {
        if (state.connection != null && !state.connected) {
            item {
                CouiTopTips(
                    CouiStatus.Error, stringResource(R.string.no_module_title), Modifier.padding(horizontal = 16.dp),
                    body = noModuleMessage,
                    actions = listOf(CouiTipsAction(reconnectText, onClick = onReconnect)),
                )
            }
        } else if (state.serviceState == "failed" && state.serviceError.isNotBlank()) {
            item {
                CouiTopTips(CouiStatus.Error, stringResource(R.string.service_status), Modifier.padding(horizontal = 16.dp),
                    body = state.serviceError)
            }
        } else if (state.ipv6Bypassed) {
            // 未接管 IPv6：有 DNS 保护时只是提示；保护未能启用时 IPv6 的 DNS 与连接都绕过代理，可能被污染。
            item {
                CouiTopTips(
                    if (state.ipv6DnsGuard) CouiStatus.Info else CouiStatus.Warning,
                    stringResource(R.string.dashboard_ipv6_bypassed),
                    Modifier.padding(horizontal = 16.dp),
                    body = stringResource(if (state.ipv6DnsGuard) R.string.dashboard_ipv6_guarded else R.string.dashboard_ipv6_leaking),
                    actions = listOf(CouiTipsAction(stringResource(R.string.inbound_settings), onClick = {
                        navigator.push(Route.InboundSettings)
                    })),
                )
            }
        }

        item {
            val serviceSummary = when {
                state.isStarting -> stringResource(R.string.service_starting)
                state.isStopping -> stringResource(R.string.service_stopping)
                state.isReady -> stringResource(R.string.dashboard_service_up, formatUptime(state.uptimeSeconds))
                else -> stringResource(R.string.service_stopped)
            }
            SpeedChartCard(
                downloadSpeed = formatRate(state.downloadBytesPerSecond),
                uploadSpeed = formatRate(state.uploadBytesPerSecond),
                trafficSamples = state.trafficSamples,
                statusSummary = serviceSummary,
                isRunning = state.isReady,
                serviceControlEnabled = availability?.available == true && state.connected &&
                    !state.loading && !state.isServiceControlBusy,
                animateChart = isActive && state.isReady,
                onToggleService = viewModel::toggleService
            )
        }

        item {
            // 当前节点、链式代理与出站模式放在同一张卡片里；出站模式是一行，点开后在选择框里切换。
            val modes = state.availableOutboundModes
            val modeLabels = modes.map { modeLabel(it) }
            val modeIndex = modes.indexOf(state.outboundMode)
            CouiGroup {
                CouiJumpRow(
                    title = stringResource(R.string.dashboard_current_node),
                    value = state.currentNode.ifBlank { stringResource(R.string.dashboard_not_selected) },
                    enabled = availability?.available == true,
                    onClick = onNavigateToNodes,
                )
                if (state.chainMode != "off") {
                    CouiJumpRow(
                        title = stringResource(R.string.chain),
                        value = chainSummary(state.chainMode, state.chainLanding),
                        enabled = availability?.available == true,
                        onClick = onNavigateToChain,
                    )
                }
                CouiMenuRow(
                    title = stringResource(R.string.dashboard_outbound_mode),
                    options = modeLabels,
                    selected = modeIndex,
                    onSelect = { index -> modes.getOrNull(index)?.let { if (it != state.outboundMode) viewModel.setMode(it) } },
                    enabled = availability?.available == true && !state.loading && modes.isNotEmpty(),
                    value = modeLabels.getOrNull(modeIndex) ?: stringResource(R.string.dashboard_mode_unknown),
                )
            }
        }

        item {
            CouiGroup(title = stringResource(R.string.dashboard_overview)) {
                CouiInfoRow(stringResource(R.string.cpu_usage), "%.1f%%".format(state.cpuUsage))
                CouiInfoRow(stringResource(R.string.mem_usage), "%.1f%%".format(state.memoryUsage))
                CouiInfoRow(stringResource(R.string.dashboard_lan_ip), state.internalIp)
                CouiInfoRow(
                    stringResource(R.string.dashboard_total_traffic),
                    stringResource(R.string.dashboard_traffic, formatBytes(state.uploadTotal), formatBytes(state.downloadTotal)),
                )
            }
        }
    }
}

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    "Rule" -> stringResource(R.string.dashboard_mode_rule)
    "Proxy" -> stringResource(R.string.dashboard_mode_proxy)
    "Direct" -> stringResource(R.string.dashboard_mode_direct)
    "RuleAllowAds" -> stringResource(R.string.dashboard_mode_rule_allow_ads)
    else -> mode
}

@Composable
private fun chainSummary(mode: String, landing: String): String = when (mode) {
    "manual" -> landing
    "policy" -> stringResource(R.string.chain_landing_by_policy)
    "manual_policy" -> stringResource(R.string.chain_landing_manual_policy, landing)
    else -> stringResource(R.string.chain_mode_off)
}

private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remaining = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, remaining)
}

private fun formatRate(bytes: Long): String = "${formatBytes(bytes)}/s"

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return if (value >= 100) "${value.roundToInt()} ${units[index]}"
    else "%.1f %s".format(value, units[index])
}
