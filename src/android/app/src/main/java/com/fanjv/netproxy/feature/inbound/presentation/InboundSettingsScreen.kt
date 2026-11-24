package com.fanjv.netproxy.feature.inbound.presentation

import com.fanjv.netproxy.core.ui.component.MenuAction
import com.fanjv.netproxy.core.ui.component.CouiBarMenuButton
import android.content.ClipData
import android.content.ClipboardManager
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.*
import com.fanjv.netproxy.feature.inbound.data.*
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiDialog
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiEmptyState
import dev.aiingjie.coui.CouiExpandContent
import dev.aiingjie.coui.CouiExpandRow
import dev.aiingjie.coui.CouiFooter
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiPanel
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiStatus
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import dev.aiingjie.coui.CouiTipsAction
import dev.aiingjie.coui.CouiTopTips
import dev.aiingjie.coui.LocalCouiOverlay
import com.fanjv.netproxy.feature.chain.presentation.TextArea
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.delay

private enum class InputKind { Text, List, Ports, Number, Duration, Cidr, RuleSet, Interface, Mac }
private data class FieldEdit(
    val path: String,
    val label: String,
    val value: String,
    val kind: InputKind = InputKind.List,
    val excludeKey: String? = null,
    val mode: String = "all",
    val backend: String = "",
    val revision: String? = null,
    val values: List<String> = emptyList()
)

@Composable
internal fun InboundSettingsScreen(
    onBack: () -> Unit,
    viewModel: InboundViewModel = netProxyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val overlay = LocalCouiOverlay.current
    var editing by remember { mutableStateOf<FieldEdit?>(null) }
    var showField by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var showDraft by remember { mutableStateOf(false) }
    var reviewingDraft by remember { mutableStateOf<InboundDraft?>(null) }
    var applyingField by remember { mutableStateOf<String?>(null) }
    val enabled = state.hasConfiguration
    LaunchedEffect(state.applyingField) {
        applyingField = null
        if (state.applyingField != null) {
            delay(1_000)
            applyingField = state.applyingField
        }
    }
    val commitOnLeave = rememberCommitOnLeave(viewModel::requestFlush)
    val commitAction = rememberCommitAction(viewModel::flush)
    val leave: () -> Unit = { commitOnLeave(onBack) }
    BackHandler(enabled = !showField && state.pendingBackend == null && !showDraft && !overlay.isCovering &&
        (state.hasPendingChanges || state.isSaving)) { leave() }
    fun openField(field: FieldEdit) {
        if (!state.editable) return
        editing = field.copy(backend = state.backend, revision = state.snapshot?.partitions?.get(state.backend)?.revision)
        showField = true
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val restartText = stringResource(R.string.restart_core)
    val diagnoseText = stringResource(R.string.ebpf_diagnostics)
    val fullJsonText = stringResource(R.string.inbound_full_json)
    val menu = listOf(
        MenuAction(restartText, enabled = state.editable) { viewModel.restart() },
        MenuAction(diagnoseText, enabled = state.hasConfiguration && !state.isDiagnosing) { viewModel.diagnose() },
        MenuAction(fullJsonText, enabled = state.editable) { commitAction { navigator.push(Route.JsonEdit("inbound")) } },
    )

    CouiSubPage(
        title = stringResource(R.string.inbound_settings),
        onBack = leave,
        actions = {
            if (applyingField == "restart") CouiLoading()
            CouiBarMenuButton(stringResource(R.string.more_actions), menu)
        },
    ) {
        if (state.snapshot == null && !state.hasPendingChanges) {
            item {
                if (state.isInitialLoading) CouiEmptyState(stringResource(R.string.loading), loading = true)
                else CouiEmptyState(
                    stringResource(R.string.inbound_read_failed), body = state.error.ifBlank { null }, status = CouiStatus.Error,
                    actionText = stringResource(R.string.retry), onAction = viewModel::refresh,
                )
            }
            return@CouiSubPage
        }
        val hasError = state.error.isNotBlank() || state.errorCode.isNotBlank()
        if (hasError || state.requiresReload) item("error") {
            val error = when (state.errorCode) {
                "config.conflict" -> stringResource(R.string.inbound_conflict)
                "inbound.not_confirmed" -> stringResource(R.string.inbound_apply_unconfirmed)
                "inbound.restart_required" -> stringResource(R.string.inbound_restart_required)
                else -> state.error
            }
            val reloadText = stringResource(if (state.hasPendingChanges) R.string.routing_reload_draft else R.string.inbound_reload)
            CouiTopTips(
                CouiStatus.Error, stringResource(R.string.operation_unfinished),
                body = error.ifBlank { null },
                actions = if (state.requiresReload) listOf(CouiTipsAction(reloadText, onClick = viewModel::discardAndReload)) else emptyList(),
            )
        }
        if (state.canReviewDraft) item("draft") {
            CouiGroup(first = !hasError && !state.requiresReload) {
                CouiJumpRow(stringResource(R.string.inbound_draft), {
                    reviewingDraft = state.draftForReview()
                    showDraft = true
                }, summary = stringResource(R.string.inbound_draft_hint))
            }
        }
        if (state.snapshot != null) {
            couiSection("general", { stringResource(R.string.inbound_general) }, (listOf(
                CardItem("backend") {
                    CouiMenuRow(
                        title = stringResource(R.string.inbound_backend),
                        options = listOf("eBPF", "TUN"),
                        selected = if (state.backend == "tun") 1 else 0,
                        enabled = enabled,
                        onSelect = { viewModel.requestBackend(if (it == 1) "tun" else "ebpf") }
                    )
                },
                CardItem("root_policy") {
                    val policies = listOf("default", "include", "exclude")
                    CouiMenuRow(
                        title = stringResource(R.string.inbound_root_processes),
                        options = listOf(stringResource(R.string.inbound_root_default),
                            stringResource(R.string.inbound_root_include), stringResource(R.string.inbound_root_exclude)),
                        selected = policies.indexOf(state.snapshot!!.rootPolicy),
                        enabled = enabled,
                        onSelect = { viewModel.setRootPolicy(policies[it]) }
                    )
                },
                CardItem("apps") {
                    CouiJumpRow(
                        title = stringResource(R.string.proxy_apps),
                        enabled = enabled,
                        onClick = { if (state.editable) commitAction { navigator.push(Route.Apps) } }
                    )
                }
            ) + if (state.snapshot?.status?.requiresBackendSwitch(state.backend) == true) listOf(
                CardItem("apply_backend") {
                    CouiJumpRow(
                        title = stringResource(R.string.inbound_apply_backend),
                        summary = stringResource(R.string.inbound_restart_required),
                        enabled = enabled,
                        onClick = { viewModel.requestBackend(state.backend) }
                    )
                }
            ) else emptyList()).withProgress(applyingField), first = !hasError && !state.requiresReload && !state.canReviewDraft)
            if (state.backend == "ebpf") {
                ebpfForm(state.native, enabled, applyingField, advanced, { advanced = !advanced }, viewModel, ::openField)
            } else {
                tunForm(state.native, enabled, applyingField, advanced, { advanced = !advanced }, viewModel, ::openField)
            }
            if (state.choicesError) item("choices_error") {
                CouiFooter(stringResource(R.string.inbound_choices_failed))
            }
        }
    }
    editing?.let { field ->
        FieldDialog(field, state, show = showField, canSave = state.editable,
            onDismiss = { showField = false }, onSave = { value, entries, mode ->
            if (field.excludeKey != null) viewModel.setFilter(field.path, field.excludeKey, mode, entries, field.backend, field.revision)
            else {
                val jsonValue = when (field.kind) {
                    InputKind.Text -> value.takeIf(String::isNotBlank)?.let(::JsonPrimitive)
                    InputKind.Number -> value.takeIf(String::isNotBlank)?.toLong()?.let(::JsonPrimitive)
                    InputKind.Duration -> value.takeIf(String::isNotBlank)?.let {
                        it.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(it)
                    }
                    InputKind.Ports -> JsonArray(entries.map { JsonPrimitive(it.toInt()) })
                    else -> stringArray(entries)
                }
                viewModel.setField(field.path, jsonValue, field.backend, field.revision)
            }
            showField = false
        })
    }
    reviewingDraft?.let { draft ->
        val title = stringResource(R.string.inbound_draft)
        CouiPanel(visible = showDraft, title = title, onDismissRequest = { showDraft = false }) {
            CouiGroup(first = true, footer = stringResource(R.string.inbound_draft_hint)) {
                SelectionContainer {
                    BasicText(draft.snapshot.content, Modifier.fillMaxWidth().padding(16.dp),
                        style = Coui.type.body.copy(color = Coui.colors.label))
                }
            }
            CouiGroup {
                CouiActionRow(stringResource(android.R.string.copy), {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(title, draft.snapshot.content))
                })
                CouiActionRow(stringResource(R.string.json_discard_changes), {
                    viewModel.discardDraft()
                    showDraft = false
                }, danger = true)
            }
            CouiButton(stringResource(android.R.string.ok), { showDraft = false },
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }
    }
    CouiDialog(
        visible = state.pendingBackend != null,
        title = stringResource(R.string.inbound_switch_title),
        message = stringResource(R.string.inbound_switch_warning),
        onDismissRequest = viewModel::cancelBackendSwitch,
        buttons = listOf(
            CouiDialogButton(stringResource(R.string.common_confirm), CouiDialogButton.Kind.Primary, viewModel::confirmBackendSwitch),
            CouiDialogButton(stringResource(android.R.string.cancel), onClick = viewModel::cancelBackendSwitch),
        ),
    )
    CouiDialog(
        visible = state.diagnostic != null,
        title = stringResource(R.string.ebpf_diagnostics),
        onDismissRequest = viewModel::dismissDiagnostic,
        content = {
            // 诊断是多行的键值文本，左对齐并允许滚动，不用对话框居中的说明样式。
            BasicText(state.diagnostic.orEmpty(),
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                style = Coui.type.body.copy(color = Coui.colors.labelSecondary))
        },
        buttons = listOf(CouiDialogButton(stringResource(android.R.string.ok), CouiDialogButton.Kind.Primary, viewModel::dismissDiagnostic)),
    )
}

private fun LazyListScope.ebpfForm(
    native: JsonObject, enabled: Boolean, applyingField: String?, advanced: Boolean, toggleAdvanced: () -> Unit,
    vm: InboundViewModel, edit: (FieldEdit) -> Unit
) {
    val local = native.objectAt("local")
    val shared = native.objectAt("shared")
    couiSection("ebpf_core", { stringResource(R.string.ebpf_core_settings) }, listOf(
        CardItem("paths") {
            val selected = if (local.flagAt("enabled") && shared.flagAt("enabled")) 2 else if (shared.flagAt("enabled")) 1 else 0
            CouiMenuRow(
                title = stringResource(R.string.ebpf_data_paths),
                options = listOf(stringResource(R.string.ebpf_data_paths_local), stringResource(R.string.ebpf_data_paths_shared), stringResource(R.string.ebpf_data_paths_both)),
                selected = selected, enabled = enabled,
                onSelect = { vm.setDataPaths(listOf("local", "shared", "both")[it]) }
            )
        },
        CardItem("network") {
            val networks = native.listAt("network")
            CouiMenuRow(
                title = stringResource(R.string.ebpf_network),
                options = listOf(stringResource(R.string.ebpf_network_all), stringResource(R.string.ebpf_network_tcp), stringResource(R.string.ebpf_network_udp)),
                selected = if (networks == listOf("tcp")) 1 else if (networks == listOf("udp")) 2 else 0,
                enabled = enabled,
                onSelect = { vm.setField("network", if (it == 0) null else JsonPrimitive(if (it == 1) "tcp" else "udp")) }
            )
        }
    ).withProgress(applyingField))
    for ((path, part, title) in listOf(Triple("local", local, R.string.ebpf_local_settings), Triple("shared", shared, R.string.ebpf_shared_settings))) {
        val isLocal = path == "local"
        val items = mutableListOf(
            CardItem("$path.data_plane") {
                NativeDropdown(R.string.ebpf_data_plane, part.ebpfDataPlane(),
                    if (isLocal) LOCAL_EBPF_DATA_PLANES else SHARED_EBPF_DATA_PLANES,
                    listOf(R.string.ebpf_data_plane_socket_assign,
                        if (isLocal) R.string.ebpf_data_plane_cgroup else R.string.ebpf_data_plane_packet_rewrite), enabled) {
                    vm.setEbpfDataPlane(path, it)
                }
            },
            CardItem("$path.dns_mode") {
                NativeDropdown(if (isLocal) R.string.ebpf_local_dns_mode else R.string.ebpf_shared_dns_mode,
                    part.ebpfDnsMode(), listOf("hijack", "respect_policy", "off"),
                    listOf(R.string.ebpf_dns_mode_hijack, R.string.ebpf_dns_mode_respect_policy, R.string.ebpf_dns_mode_off), enabled) {
                    vm.setField("$path.dns_mode", JsonPrimitive(it))
                }
            },
            if (isLocal) CardItem("$path.ipv6") {
                // 关闭本机 IPv6 接管时说明后果：IPv6 连接直连，IPv6 的 DNS 仍尽量交给代理以防污染。
                val checked = part.flagAt("ipv6", true)
                CouiSwitchRow(
                    title = stringResource(R.string.ebpf_local_ipv6), checked = checked, enabled = enabled,
                    summary = if (checked) null else stringResource(R.string.ebpf_local_ipv6_off_summary),
                    onCheckedChange = { vm.setField("$path.ipv6", JsonPrimitive(it)) },
                )
            } else toggleItem("$path.ipv6", R.string.ebpf_shared_ipv6, part.flagAt("ipv6", true), enabled) {
                vm.setField("$path.ipv6", JsonPrimitive(it))
            },
            toggleItem("$path.bypass_private_address", R.string.ebpf_bypass_private_address, part.flagAt("bypass_private_address", true), enabled) {
                vm.setField("$path.bypass_private_address", JsonPrimitive(it))
            },
            textItem("$path.bypass_port", if (isLocal) R.string.ebpf_local_bypass_ports else R.string.ebpf_shared_bypass_ports, part, enabled, InputKind.Ports, edit),
            textItem("$path.bypass_port_range", if (isLocal) R.string.ebpf_local_bypass_port_ranges else R.string.ebpf_shared_bypass_port_ranges, part, enabled, InputKind.List, edit),
            textItem("$path.bypass_rule_set", R.string.ebpf_bypass_rule_sets, part, enabled, InputKind.RuleSet, edit)
        )
        if (!isLocal) items += listOf(
            textItem("shared.interface", R.string.ebpf_shared_interfaces, part, enabled, InputKind.Interface, edit),
            textItem("shared.include_source_cidr", R.string.ebpf_shared_include_source_cidrs, part, enabled, InputKind.Cidr, edit),
            textItem("shared.exclude_source_cidr", R.string.ebpf_shared_exclude_source_cidrs, part, enabled, InputKind.Cidr, edit),
            textItem("shared.include_mac_address", R.string.ebpf_shared_include_mac_addresses, part, enabled, InputKind.Mac, edit),
            textItem("shared.exclude_mac_address", R.string.ebpf_shared_exclude_mac_addresses, part, enabled, InputKind.Mac, edit)
        )
        couiSection("ebpf_$path", { stringResource(title) }, items.withProgress(applyingField))
    }
    couiExpandSection("ebpf_advanced", advanced, toggleAdvanced, listOf(
        CardItem("shared.default_interface") {
            CouiSwitchRow(title = stringResource(R.string.ebpf_shared_default_interface),
                summary = stringResource(R.string.ebpf_shared_default_interface_hint),
                checked = shared.flagAt("default_interface"), enabled = enabled,
                onCheckedChange = { vm.setField("shared.default_interface", JsonPrimitive(it)) })
        }
    ).withProgress(applyingField))
}

private fun LazyListScope.tunForm(
    native: JsonObject, enabled: Boolean, applyingField: String?, advanced: Boolean, toggleAdvanced: () -> Unit,
    vm: InboundViewModel, edit: (FieldEdit) -> Unit
) {
    couiSection("tun", { stringResource(R.string.tun_settings) }, listOf(
        CardItem("dns_mode") {
            NativeDropdown(R.string.tun_dns, native.tunDnsMode(),
                listOf("hijack", "disabled", "native"),
                listOf(R.string.ebpf_dns_mode_hijack, R.string.tun_dns_disabled, R.string.tun_dns_native),
                enabled, selectableCount = 2) { vm.setField("dns_mode", JsonPrimitive(it)) }
        },
        CardItem("ipv6") {
            CouiSwitchRow(title = stringResource(R.string.tun_ipv6), summary = stringResource(R.string.tun_ipv6_hint),
                checked = native.listAt("address").any { ':' in it }, enabled = enabled, onCheckedChange = vm::setTunIpv6)
        },
        filterItem("include_interface", "exclude_interface", R.string.tun_interfaces, native, enabled, InputKind.Interface, edit),
        textItem("route_exclude_address", R.string.tun_exclude_addresses, native, enabled, InputKind.Cidr, edit),
        textItem("route_exclude_address_set", R.string.tun_exclude_rule_sets, native, enabled, InputKind.RuleSet, edit),
        CardItem("strict_route") {
            CouiSwitchRow(title = stringResource(R.string.tun_strict_route), summary = stringResource(R.string.tun_strict_route_hint),
                checked = native.flagAt("strict_route"), enabled = enabled,
                onCheckedChange = { vm.setField("strict_route", JsonPrimitive(it)) })
        }
    ).withProgress(applyingField))
    couiExpandSection("tun_advanced", advanced, toggleAdvanced, listOf(
        textItem("address", R.string.tun_address, native, enabled, InputKind.Cidr, edit),
        textItem("interface_name", R.string.tun_interface_name, native, enabled, InputKind.Text, edit),
        textItem("mtu", R.string.tun_mtu, native, enabled, InputKind.Number, edit),
        textItem("udp_timeout", R.string.tun_udp_timeout, native, enabled, InputKind.Duration, edit),
        textItem("route_address", R.string.tun_route_address, native, enabled, InputKind.Cidr, edit),
        textItem("route_address_set", R.string.tun_route_rule_sets, native, enabled, InputKind.RuleSet, edit),
        filterItem("include_mac_address", "exclude_mac_address", R.string.tun_mac, native, enabled, InputKind.Mac, edit)
    ).withProgress(applyingField))
}

private fun LazyListScope.couiSection(
    sectionKey: String, title: @Composable () -> String, items: List<CardItem>, first: Boolean = false,
) {
    if (items.isEmpty()) return
    item(sectionKey) {
        CouiGroup(title = title(), first = first) {
            items.forEach { item -> key(item.key) { Column(content = item.content) } }
        }
    }
}

/** 高级设置：卡片顶部是展开行，展开后各项在它下方逐行长出，收起时只留这一行。 */
private fun LazyListScope.couiExpandSection(sectionKey: String, expanded: Boolean, onToggle: () -> Unit, items: List<CardItem>) {
    if (items.isEmpty()) return
    item(sectionKey) {
        CouiGroup {
            CouiExpandRow(stringResource(R.string.inbound_advanced), expanded, onToggle)
            items.forEach { item -> key(item.key) { CouiExpandContent(expanded) { Column(content = item.content) } } }
        }
    }
}

private fun List<CardItem>.withProgress(applyingField: String?) = map { preference ->
    CardItem(preference.key) {
        Box {
            Column(content = preference.content)
            if (applyingField == preference.key) {
                val description = stringResource(R.string.inbound_applying)
                ApplyingBar(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp)
                        .semantics { stateDescription = description }
                )
            }
        }
    }
}

private fun toggleItem(key: String, @StringRes title: Int, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) =
    CardItem(key) { CouiSwitchRow(title = stringResource(title), checked = checked, enabled = enabled, onCheckedChange = change) }

private fun textItem(path: String, @StringRes title: Int, native: JsonObject, enabled: Boolean, kind: InputKind, edit: (FieldEdit) -> Unit) =
    CardItem(path) {
        val label = stringResource(title)
        val scalar = kind in setOf(InputKind.Text, InputKind.Number, InputKind.Duration)
        val values = if (scalar) emptyList() else native.listAt(path.substringAfterLast('.'))
        val value = if (scalar) native.textAt(path.substringAfterLast('.')) else values.joinToString("\n")
        CouiJumpRow(label, summary = value.ifBlank { stringResource(R.string.not_set) }, enabled = enabled,
            onClick = { edit(FieldEdit(path, label, value, kind, values = values)) })
    }

private fun filterItem(include: String, exclude: String, @StringRes title: Int, native: JsonObject, enabled: Boolean, kind: InputKind, edit: (FieldEdit) -> Unit) =
    CardItem(include) {
        val mode = native.filterMode(include, exclude)
        val values = native.listAt(if (mode == "exclude") exclude else include)
        val value = values.joinToString("\n")
        val label = stringResource(title)
        val summary = when (mode) {
            "include" -> stringResource(R.string.inbound_filter_include)
            "exclude" -> stringResource(R.string.inbound_filter_exclude)
            else -> stringResource(R.string.inbound_filter_all)
        }
        CouiJumpRow(label, summary = listOf(summary, value).filter(String::isNotBlank).joinToString(": "),
            enabled = enabled, onClick = { edit(FieldEdit(include, label, value, kind, exclude, mode, values = values)) })
    }

@Composable
private fun NativeDropdown(
    @StringRes title: Int, value: String, values: List<String>, labels: List<Int>, enabled: Boolean,
    selectableCount: Int = values.size, change: (String) -> Unit
) {
    val visibleValues = values.take(selectableCount).let { if (value in it) it else it + value }
    CouiMenuRow(
        title = stringResource(title),
        options = visibleValues.map { candidate -> labels.getOrNull(values.indexOf(candidate))?.let { stringResource(it) } ?: candidate },
        selected = visibleValues.indexOf(value).coerceAtLeast(0), enabled = enabled,
        onSelect = { if (it < selectableCount) change(visibleValues[it]) }
    )
}

@Composable
private fun FieldDialog(field: FieldEdit, state: InboundUiState, show: Boolean, canSave: Boolean,
    onDismiss: () -> Unit, onSave: (String, List<String>, String) -> Unit) {
    var value by remember(field) { mutableStateOf(field.value) }
    var listInput by remember(field) { mutableStateOf(InboundListInput(field.values)) }
    val isList = field.kind !in setOf(InputKind.Text, InputKind.Number, InputKind.Duration)
    var mode by remember(field) { mutableStateOf(field.mode) }
    var invalid by remember(field) { mutableStateOf(false) }
    val candidates = when (field.kind) {
        InputKind.Interface -> state.choices.interfaces
        InputKind.RuleSet -> state.choices.ruleSets
        else -> emptyList()
    }
    val hint = when {
        field.path.endsWith("bypass_port_range") -> R.string.settings_hint_port_ranges
        field.path.endsWith("bypass_rule_set") -> R.string.settings_hint_rule_sets
        else -> when (field.kind) {
            InputKind.Cidr -> R.string.settings_hint_cidrs
            InputKind.Mac -> R.string.settings_hint_macs
            InputKind.Interface -> R.string.settings_hint_interfaces
            InputKind.RuleSet -> R.string.settings_hint_rule_sets
            InputKind.Ports -> R.string.settings_hint_ports
            InputKind.Duration -> R.string.tun_udp_timeout_hint
            else -> R.string.value_label
        }
    }
    val modes = listOf("all", "include", "exclude")
    val invalidText = stringResource(R.string.inbound_invalid_value)
    val help = if (hint == R.string.value_label) null
        else stringResource(hint) + if (field.kind == InputKind.RuleSet) "\n" + stringResource(R.string.tun_rule_set_hint) else ""
    CouiPanel(visible = show, title = stringResource(R.string.modify_label, field.label), onDismissRequest = onDismiss) {
        if (field.excludeKey != null) CouiGroup(first = true) {
            CouiMenuRow(
                stringResource(R.string.inbound_filter_mode),
                listOf(stringResource(R.string.inbound_filter_all), stringResource(R.string.inbound_filter_include), stringResource(R.string.inbound_filter_exclude)),
                modes.indexOf(mode).coerceAtLeast(0),
                { mode = modes[it] },
            )
        }
        CouiGroup(first = field.excludeKey == null, footer = help) {
            if (isList) TextArea(listInput.text, { listInput = listInput.withText(it); invalid = false }, stringResource(R.string.value_label))
            else CouiInputRow(stringResource(R.string.value_label), value, { value = it; invalid = false },
                numeric = field.kind == InputKind.Number, error = if (invalid) invalidText else null)
        }
        // 候选项（网卡、规则集）勾选后写入上面的列表；面板本身可滚动，这里不用嵌套的懒加载列表。
        if (candidates.isNotEmpty()) CouiGroup {
            candidates.forEach { candidate ->
                CouiSwitchRow(candidate, candidate in listInput.values, { checked ->
                    listInput = listInput.withSelection(candidate, checked)
                    invalid = false
                })
            }
        }
        if (invalid && isList) CouiGroup { CouiRow(invalidText, titleColor = Coui.colors.error) }
        CouiButton(
            text = stringResource(R.string.save_text),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            enabled = canSave,
            onClick = {
                val entries = listInput.entries().let { values ->
                    if (field.kind in setOf(InputKind.Ports, InputKind.Cidr, InputKind.Mac) || field.path.endsWith("bypass_port_range")) {
                        values.map(String::trim).filter(String::isNotEmpty).distinct()
                    } else values
                }
                val scalarValue = value.trim()
                invalid = when {
                    field.excludeKey != null && mode != "all" && entries.isEmpty() -> true
                    field.kind == InputKind.Number -> scalarValue.isNotBlank() && scalarValue.toLongOrNull()?.let { it in 0..4294967295L } != true
                    field.kind == InputKind.Ports -> entries.any { it.toIntOrNull()?.let { number -> number in 0..65535 } != true }
                    field.kind == InputKind.RuleSet && !state.choicesError -> entries.any { it !in state.choices.ruleSets }
                    else -> false
                }
                if (!invalid) onSave(scalarValue, entries, mode)
            },
        )
    }
}

/** 正在应用某一项设置时贴在行底部的细进度条；COUI 没有线形进度组件，按主题色绘制。 */
@Composable
private fun ApplyingBar(modifier: Modifier) {
    val colors = Coui.colors
    val progress by rememberInfiniteTransition(label = "applying")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "offset")
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(colors.container8)
        val segment = size.width * 0.3f
        val start = (size.width + segment) * progress - segment
        val left = start.coerceAtLeast(0f)
        val right = (start + segment).coerceAtMost(size.width)
        if (right > left) drawRect(colors.accent, topLeft = Offset(left, 0f), size = Size(right - left, size.height))
    }
}
