package com.fanjv.netproxy.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.rememberCommitOnLeave
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import dev.aiingjie.coui.LocalCouiOverlay
import kotlinx.coroutines.CancellationException

@Composable
internal fun NetworkMatchingScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val enabled = state.hasLoaded && !state.requiresReload
    val commitOnLeave = rememberCommitOnLeave(viewModel::requestWifiFlush)
    val leave: () -> Unit = { commitOnLeave(onBack) }
    val wifi = state.wifi
    val overlay = LocalCouiOverlay.current
    BackHandler(!overlay.isCovering && (state.hasPendingWifi || state.isSaving || state.requiresReload)) { leave() }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val addTitle = stringResource(R.string.wifi_add)
    val editTitle = stringResource(R.string.wifi_edit)
    fun openEditor(original: String? = null) = overlay.showPanel(if (original == null) addTitle else editTitle) {
        WiFiNamePanel(viewModel, original) { overlay.dismissPanel() }
    }

    CouiSubPage(title = stringResource(R.string.network_matching), onBack = leave) {
        if (!state.hasLoaded) {
            item {
                CouiGroup(first = true) {
                    if (state.error.isBlank()) CouiRow(stringResource(R.string.loading), leading = { CouiLoading() })
                    else CouiRow(stringResource(R.string.network_read_failed), summary = state.error)
                }
            }
            return@CouiSubPage
        }
        val notice = state.error.isNotBlank() || state.requiresReload
        if (notice) item {
            CouiGroup(first = true) {
                if (state.error.isNotBlank()) CouiRow(state.error, titleColor = Coui.colors.error)
                if (state.requiresReload) CouiActionRow(stringResource(R.string.routing_reload_draft), viewModel::discardWifiAndReload)
            }
        }
        item {
            val modes = listOf("off", "blacklist", "whitelist")
            CouiGroup(title = stringResource(R.string.wifi_policy_title), first = !notice) {
                CouiMenuRow(
                    title = stringResource(R.string.wifi_auto_switch),
                    options = listOf(
                        stringResource(R.string.wifi_mode_off),
                        stringResource(R.string.wifi_ssid_mode_blacklist),
                        stringResource(R.string.wifi_ssid_mode_whitelist),
                    ),
                    selected = modes.indexOf(wifi.selection),
                    onSelect = { viewModel.setWifiSsidMode(modes[it]) },
                    enabled = enabled,
                )
                CouiSwitchRow(
                    stringResource(R.string.proxy_on_non_wifi), wifi.proxyOnNonWifi, viewModel::setProxyOnNonWifi,
                    enabled = enabled && wifi.enabled,
                )
            }
        }
        if (wifi.enabled) {
            item {
                val listTitle = if (wifi.mode == "whitelist") R.string.wifi_whitelist_title else R.string.wifi_blacklist_title
                CouiGroup(title = stringResource(listTitle)) {
                    if (wifi.ssids.isEmpty()) CouiRow(stringResource(R.string.wifi_empty))
                    wifi.ssids.forEach { name ->
                        CouiRow(name, enabled = enabled, role = Role.Button, onClick = { openEditor(name) })
                    }
                    CouiActionRow(addTitle, { openEditor() }, enabled = enabled)
                }
            }
        }
    }
}

@Composable
private fun WiFiNamePanel(viewModel: SettingsViewModel, original: String?, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state.wifi.ssids
    val enabled = state.hasLoaded && !state.requiresReload
    var input by remember { mutableStateOf(original.orEmpty()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var candidates by remember { mutableStateOf<List<String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (original == null) try {
            candidates = viewModel.savedWifiNetworks().filterNot { it in current }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }
    fun save(ssids: List<String>) {
        viewModel.setWifiSsids(ssids)
        onDone()
    }

    CouiGroup(first = true) {
        CouiInputRow(
            stringResource(R.string.wifi_name), input, { input = it; invalid = false },
            error = if (invalid) stringResource(R.string.wifi_invalid_name) else null,
        )
    }
    if (original == null) {
        val options = candidates
        CouiGroup {
            when {
                failed -> CouiRow(stringResource(R.string.wifi_saved_failed))
                options == null -> CouiRow(stringResource(R.string.loading), leading = { CouiLoading() })
                options.isEmpty() -> CouiRow(stringResource(R.string.wifi_saved_empty))
                else -> options.forEach { name ->
                    CouiRow(name, role = Role.Checkbox, onClick = {
                        selected = if (name in selected) selected - name else selected + name
                    }) { CouiRadio(selected = name in selected) }
                }
            }
        }
    } else {
        CouiGroup {
            CouiActionRow(stringResource(R.string.common_delete), { save(current - original) }, danger = true, enabled = enabled)
        }
    }
    CouiButton(
        text = stringResource(if (original == null) R.string.common_add else R.string.save_text),
        onClick = {
            if (input.isNotEmpty() && (input.toByteArray(Charsets.UTF_8).size > 32 || input.any { it.isISOControl() })) {
                invalid = true
            } else {
                save(if (original == null) (current + selected + listOfNotNull(input.takeIf(String::isNotEmpty))).distinct()
                    else current.map { if (it == original) input else it }.distinct())
            }
        },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        enabled = enabled && (input.isNotEmpty() || selected.isNotEmpty()),
    )
}
