package com.fanjv.netproxy.feature.chain.presentation

import com.fanjv.netproxy.feature.chain.model.ChainPolicy
import dev.aiingjie.coui.CouiSmallButton
import dev.aiingjie.coui.CouiPanel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.SheetAction
import com.fanjv.netproxy.core.ui.component.showActionSheet
import com.fanjv.netproxy.feature.catalog.model.CatalogNodeGroup
import com.fanjv.netproxy.feature.chain.model.ChainFront
import com.fanjv.netproxy.feature.chain.model.ChainLanding
import com.fanjv.netproxy.feature.chain.model.ChainMode
import com.fanjv.netproxy.feature.chain.model.ChainState
import com.fanjv.netproxy.feature.smart.model.SmartNodeRef
import com.fanjv.netproxy.feature.smart.presentation.DelayText
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiDockSpace
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiLargeTitlePage
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRadioRow
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSearchField
import dev.aiingjie.coui.CouiStatus
import dev.aiingjie.coui.CouiSwitchJumpRow
import dev.aiingjie.coui.CouiTopTips
import dev.aiingjie.coui.LocalCouiOverlay

@Composable
internal fun ChainScreen(
    isActive: Boolean = true,
    viewModel: ChainViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val overlay = LocalCouiOverlay.current
    val navigator = LocalNavigator.current
    val chain = state.chain
    val mode = chain.chainMode
    val idle = state.busy.isEmpty()
    val testing = state.busy.startsWith("test")
    var showOrder by remember { mutableStateOf(false) }

    LifecycleResumeEffect(isActive) {
        viewModel.setVisible(isActive)
        onPauseOrDispose { if (isActive) viewModel.setVisible(false) }
    }

    val savedText = stringResource(R.string.chain_saved)
    val importedText = stringResource(R.string.chain_imported)
    LaunchedEffect(events) {
        if (events.isEmpty()) return@LaunchedEffect
        events.forEach { event ->
            when (event) {
                is ChainEvent.Failed -> overlay.showSnack(event.message)
                ChainEvent.Saved -> overlay.showSnack(savedText)
                is ChainEvent.Imported -> overlay.showSnack(importSummary(importedText, event))
            }
        }
        viewModel.consumeEvents()
    }

    val importTitle = stringResource(R.string.chain_import_title)
    val renameTitle = stringResource(R.string.chain_landing_rename)
    val deleteTitle = stringResource(R.string.chain_landing_delete)
    val testText = stringResource(R.string.chain_landing_test)
    val useLandingText = stringResource(R.string.chain_landing_use)
    val cancelText = stringResource(android.R.string.cancel)
    val deleteMessage = stringResource(R.string.chain_landing_delete_message)
    val frontTitle = stringResource(R.string.chain_front_pick)
    val nodeTitle = stringResource(R.string.chain_front_pick_node)
    val nodesTitle = stringResource(R.string.chain_front_pick_nodes)
    val landingTitle = stringResource(R.string.chain_landing_pick)
    val frontGroupName = stringResource(R.string.chain_front_custom_group)

    fun showImport() = overlay.showPanel(importTitle) {
        ImportPanel(busy = state.busy == "import") { content, type ->
            viewModel.importLandings(content, type) { overlay.dismissPanel() }
        }
    }

    fun showRename(landing: ChainLanding) = overlay.showPanel(renameTitle) {
        RenamePanel(landing.tag) { name ->
            overlay.dismissPanel()
            viewModel.renameLanding(landing.tag, name)
        }
    }

    fun showNodePicker() = overlay.showPanel(nodeTitle) {
        NodePicker(viewModel) { front ->
            overlay.dismissPanel()
            viewModel.setFront(front)
        }
    }

    fun showNodesPicker() = overlay.showPanel(nodesTitle) {
        NodesPicker(viewModel, frontGroupName) { members ->
            overlay.dismissPanel()
            viewModel.pickFrontNodes(frontGroupName, members)
        }
    }

    fun showFrontPicker() {
        viewModel.loadFrontChoices()
        overlay.showPanel(frontTitle) {
            FrontPicker(
                viewModel,
                onPick = { front ->
                    overlay.dismissPanel()
                    viewModel.setFront(front)
                },
                onPickNode = ::showNodePicker,
                onPickNodes = ::showNodesPicker,
                onNewGroup = {
                    overlay.dismissPanel()
                    navigator.push(Route.SmartGroupEdit(""))
                },
            )
        }
    }

    fun showLandingPicker() = overlay.showPanel(landingTitle) {
        LandingPicker(viewModel) { tag ->
            overlay.dismissPanel()
            viewModel.setManual(tag)
        }
    }

    fun showLandingActions(landing: ChainLanding) = overlay.showActionSheet(
        title = landing.tag,
        message = landingSummary(landing),
        actions = buildList {
            if (mode.usesManual && chain.manual != landing.tag) add(SheetAction(useLandingText) { viewModel.setManual(landing.tag) })
            if (chain.running) add(SheetAction(testText) { viewModel.testLandings(landing.tag) })
            add(SheetAction(renameTitle) { showRename(landing) })
            add(SheetAction(deleteTitle, danger = true) {
                overlay.showDialog(deleteTitle, String.format(deleteMessage, landing.tag), listOf(
                    CouiDialogButton(deleteTitle, CouiDialogButton.Kind.Danger) { viewModel.removeLanding(landing.tag) },
                    CouiDialogButton(cancelText),
                ))
            })
        },
        cancel = cancelText,
    )

    val modeLabels = ChainMode.entries.map { modeLabel(it) }
    val enabledModes = if (chain.landings.isEmpty()) listOf(ChainMode.Off) else ChainMode.entries
    val modeSummaryText = modeSummary(mode)
    val linkTitle = stringResource(R.string.chain_link)
    val frontLabel = stringResource(R.string.chain_front)
    val landingLabel = stringResource(R.string.chain_landing)
    val frontValue = frontValue(chain, state.smartGroups.associate { it.id to it.name }, state.nodeGroups)
    val frontSummary = frontSummary(chain)
    val landingValue = when (mode) {
        ChainMode.Off -> stringResource(R.string.chain_mode_off)
        ChainMode.Manual -> chain.manual
        ChainMode.Policy -> stringResource(R.string.chain_landing_by_policy)
        ChainMode.ManualPolicy -> stringResource(R.string.chain_landing_manual_policy, chain.manual)
    }
    val landingSummaryText = if (mode.usesManual) stringResource(R.string.chain_landing_pick_summary) else stringResource(R.string.chain_landing_policy_summary)
    val testTitle = stringResource(R.string.chain_test_link)
    val testSummary = when {
        !chain.running -> stringResource(R.string.chain_test_needs_service)
        chain.landings.isEmpty() -> stringResource(R.string.chain_policy_needs_landing)
        else -> stringResource(R.string.chain_test_link_summary)
    }
    val manualDelay = chain.delays[chain.manual]
    val frontDelayTitle = stringResource(R.string.chain_front_delay)
    val linkDelayTitle = stringResource(R.string.chain_link_delay)
    val linkFooter = stringResource(if (chain.running) R.string.chain_mode_footer else R.string.chain_service_stopped)

    CouiLargeTitlePage(
        title = stringResource(R.string.chain),
        bottomSpace = CouiDockSpace,
    ) {
        if (state.error.isNotEmpty() && !state.loaded) {
            item {
                CouiTopTips(CouiStatus.Error, stringResource(R.string.chain_load_failed), Modifier.padding(horizontal = 16.dp),
                    body = state.error)
            }
        }
        item {
            CouiGroup(first = true, title = linkTitle, footer = linkFooter) {
                CouiJumpRow(frontLabel, ::showFrontPicker, value = frontValue, summary = frontSummary, enabled = idle)
                CouiJumpRow(landingLabel, ::showLandingPicker, value = landingValue, summary = landingSummaryText,
                    enabled = idle && mode.usesManual && chain.landings.isNotEmpty())
                CouiRow(
                    title = testTitle,
                    summary = testSummary,
                    enabled = idle && chain.running && chain.landings.isNotEmpty(),
                    role = Role.Button,
                    titleColor = Coui.colors.accentText,
                    onClick = viewModel::testChain,
                ) { if (testing) CouiLoading() }
                if (state.tested && chain.frontDelay != null) {
                    CouiRow(frontDelayTitle, summary = chain.front.substringAfter('/', chain.front).ifEmpty { null }) {
                        DelayText(chain.frontDelay)
                    }
                }
                if (state.tested && mode.usesManual && manualDelay != null) {
                    CouiRow(linkDelayTitle, summary = chain.manual) { DelayText(manualDelay) }
                }
            }
        }
        item {
            CouiGroup(footer = modeSummaryText) {
                CouiMenuRow(
                    title = stringResource(R.string.chain_mode),
                    options = modeLabels,
                    selected = ChainMode.entries.indexOf(mode),
                    onSelect = { index ->
                        val option = ChainMode.entries[index]
                        if (option != mode && option in enabledModes) viewModel.setMode(option)
                    },
                    enabled = idle,
                )
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.chain_landings), footer = stringResource(R.string.chain_landings_footer)) {
                chain.landings.forEach { landing ->
                    CouiRow(
                        title = landing.tag,
                        summary = landingSummary(landing),
                        enabled = idle,
                        onClick = { showLandingActions(landing) },
                    ) {
                        LandingDelay(chain, landing.tag, state.busy == "test" || state.busy == "test:${landing.tag}")
                        if (mode.usesManual && chain.manual == landing.tag) CouiRadio(selected = true, Modifier.padding(start = 12.dp))
                    }
                }
                CouiActionRow(importTitle, onClick = ::showImport, enabled = idle)
                if (chain.landings.isNotEmpty()) {
                    CouiActionRow(
                        stringResource(R.string.chain_test_all), onClick = { viewModel.testLandings() },
                        enabled = idle && chain.running,
                        summary = if (chain.running) null else stringResource(R.string.chain_test_needs_service),
                    )
                }
            }
        }
        item {
            CouiGroup(
                title = stringResource(R.string.chain_policies),
                footer = stringResource(if (mode.usesPolicies) R.string.chain_policies_footer else R.string.chain_policies_inactive),
            ) {
                chain.policies.forEach { policy ->
                    CouiSwitchJumpRow(
                        title = policy.name,
                        checked = policy.enabled,
                        onCheckedChange = { viewModel.setPolicyEnabled(policy.id, it) },
                        onClick = { navigator.push(Route.ChainPolicy(policy.id)) },
                        summary = policySummary(policy),
                        enabled = idle,
                    )
                }
                if (chain.policies.size > 1) {
                    CouiActionRow(stringResource(R.string.chain_policy_reorder), onClick = { showOrder = true }, enabled = idle)
                }
                CouiActionRow(
                    stringResource(R.string.chain_policy_new),
                    onClick = { navigator.push(Route.ChainPolicy("")) },
                    enabled = idle && chain.landings.isNotEmpty(),
                    summary = if (chain.landings.isEmpty()) stringResource(R.string.chain_policy_needs_landing) else null,
                )
            }
        }
    }

    CouiPanel(visible = showOrder, title = stringResource(R.string.chain_policy_reorder), onDismissRequest = { showOrder = false }) {
        CouiGroup(first = true, footer = stringResource(R.string.chain_policies_footer)) {
            chain.policies.forEachIndexed { index, policy ->
                CouiRow(title = "${index + 1}. ${policy.name}", summary = policySummary(policy)) {
                    CouiSmallButton(stringResource(R.string.chain_policy_move_up), { viewModel.movePolicy(policy.id, -1) },
                        enabled = idle && index > 0)
                    Spacer(Modifier.width(8.dp))
                    CouiSmallButton(stringResource(R.string.chain_policy_move_down), { viewModel.movePolicy(policy.id, 1) },
                        enabled = idle && index < chain.policies.lastIndex)
                }
            }
        }
    }
}

/** 策略组摘要：匹配范围 → 出口，开启国内直连时附注。 */
@Composable
private fun policySummary(policy: ChainPolicy): String {
    val exit = when (policy.exit) {
        ChainPolicy.EXIT_FRONT -> stringResource(R.string.chain_policy_exit_front)
        ChainPolicy.EXIT_DIRECT -> stringResource(R.string.chain_policy_exit_direct)
        else -> policy.landing
    }
    val scope = when {
        !policy.isBlacklist -> stringResource(R.string.chain_policy_summary_whitelist, policy.packages.size, exit)
        policy.packages.isEmpty() -> stringResource(R.string.chain_policy_summary_all, exit)
        else -> stringResource(R.string.chain_policy_summary_blacklist, policy.packages.size, exit)
    }
    return if (policy.domesticDirect && policy.exit != ChainPolicy.EXIT_DIRECT) "$scope · ${stringResource(R.string.chain_policy_domestic)}" else scope
}

@Composable
private fun frontSummary(chain: ChainState): String {
    val setting = chain.frontSetting
    return when {
        chain.frontUnavailable -> stringResource(R.string.chain_front_unavailable)
        setting == null -> stringResource(R.string.chain_front_follow)
        setting.smartId.isNotEmpty() -> stringResource(R.string.chain_front_smart)
        setting.node.isEmpty() -> stringResource(R.string.chain_front_group_auto)
        else -> stringResource(R.string.chain_front_node)
    }
}

/** 前置的显示值：运行时显示实际使用的节点，否则显示设置本身。 */
@Composable
private fun frontValue(chain: ChainState, smartNames: Map<String, String>, groups: List<CatalogNodeGroup>): String {
    if (chain.running && chain.front.isNotEmpty()) return chain.front.substringAfter('/', chain.front)
    val setting = chain.frontSetting ?: return stringResource(R.string.chain_front_follow_short)
    val localName = stringResource(R.string.node_local_config)
    return when {
        setting.smartId.isNotEmpty() -> smartNames[setting.smartId] ?: setting.smartId
        setting.node.isNotEmpty() -> setting.node
        else -> groups.firstOrNull { it.group.id == setting.groupId }?.let { if (it.group.id == "default") localName else it.group.name }
            ?: setting.groupId
    }
}

@Composable
private fun FrontPicker(
    viewModel: ChainViewModel,
    onPick: (ChainFront?) -> Unit,
    onPickNode: () -> Unit,
    onPickNodes: () -> Unit,
    onNewGroup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val setting = state.chain.frontSetting
    val localName = stringResource(R.string.node_local_config)
    val availableText = stringResource(R.string.smart_available)
    val autoText = stringResource(R.string.chain_front_auto_summary)
    val manualText = stringResource(R.string.smart_mode_manual)
    CouiGroup(first = true, footer = stringResource(R.string.chain_front_picker_footer)) {
        CouiRadioRow(stringResource(R.string.chain_front_follow), setting == null, { onPick(null) },
            summary = stringResource(R.string.chain_front_follow_summary))
    }
    if (state.smartGroups.isNotEmpty()) {
        CouiGroup(title = stringResource(R.string.chain_front_smart_groups)) {
            state.smartGroups.forEach { group ->
                // 智能分组作为前置时沿用分组内的选择：手动选择的节点，或自动选择。
                val mode = group.manual?.let { String.format(manualText, it.node) } ?: autoText
                CouiRadioRow(
                    group.name, setting?.smartId == group.id, { onPick(ChainFront(smartId = group.id)) },
                    summary = String.format(availableText, group.available) + " · " + mode,
                    enabled = group.available > 0,
                )
            }
        }
    }
    if (state.nodeGroups.isNotEmpty()) {
        CouiGroup(title = stringResource(R.string.chain_front_node_groups)) {
            state.nodeGroups.forEach { group ->
                val id = group.group.id
                CouiRadioRow(
                    if (id == "default") localName else group.group.name,
                    setting != null && setting.smartId.isEmpty() && setting.node.isEmpty() && setting.groupId == id,
                    { onPick(ChainFront(groupId = id)) },
                    summary = String.format(availableText, group.nodes.size) + " · " + autoText,
                    enabled = group.nodes.isNotEmpty(),
                )
            }
        }
    }
    CouiGroup(title = stringResource(R.string.chain_front_more)) {
        val node = setting?.node.orEmpty()
        CouiJumpRow(stringResource(R.string.chain_front_pick_node), onPickNode,
            value = node.ifEmpty { null }, summary = stringResource(R.string.chain_front_pick_node_summary))
        CouiJumpRow(stringResource(R.string.chain_front_pick_nodes), onPickNodes,
            summary = stringResource(R.string.chain_front_pick_nodes_summary))
        CouiJumpRow(stringResource(R.string.smart_new_custom), onNewGroup,
            summary = stringResource(R.string.chain_front_new_group_summary))
    }
}

@Composable
private fun NodePicker(viewModel: ChainViewModel, onPick: (ChainFront) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val setting = state.chain.frontSetting
    var query by remember { mutableStateOf("") }
    val localName = stringResource(R.string.node_local_config)
    CouiSearchField(query, { query = it }, stringResource(R.string.smart_search_nodes), Modifier.padding(horizontal = 16.dp))
    filteredGroups(state.nodeGroups, query).forEach { (group, nodes) ->
        CouiGroup(title = if (group.group.id == "default") localName else group.group.name) {
            nodes.forEach { node ->
                val selected = setting != null && setting.groupId == group.group.id && setting.node == node.tag
                CouiRadioRow(node.tag, selected, { onPick(ChainFront(groupId = group.group.id, node = node.tag)) })
            }
        }
    }
}

@Composable
private fun NodesPicker(viewModel: ChainViewModel, frontGroupName: String, onSave: (List<SmartNodeRef>) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing = state.smartGroups.firstOrNull { it.custom && it.name == frontGroupName }
    var chosen by remember(existing) { mutableStateOf(existing?.members?.toSet().orEmpty()) }
    var query by remember { mutableStateOf("") }
    val localName = stringResource(R.string.node_local_config)
    CouiSearchField(query, { query = it }, stringResource(R.string.smart_search_nodes), Modifier.padding(horizontal = 16.dp))
    filteredGroups(state.nodeGroups, query).forEach { (group, nodes) ->
        CouiGroup(title = if (group.group.id == "default") localName else group.group.name) {
            nodes.forEach { node ->
                val ref = SmartNodeRef(group.group.id, node.tag)
                val selected = ref in chosen
                CouiRow(node.tag, role = Role.Checkbox, onClick = { chosen = if (selected) chosen - ref else chosen + ref }) {
                    CouiRadio(selected = selected)
                }
            }
        }
    }
    CouiButton(
        text = stringResource(R.string.chain_front_pick_nodes_action, chosen.size),
        onClick = { onSave(chosen.toList()) },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        enabled = chosen.isNotEmpty(),
    )
}

private fun filteredGroups(groups: List<CatalogNodeGroup>, query: String) = groups.mapNotNull { group ->
    val keyword = query.trim().lowercase()
    val nodes = group.nodes.filter { keyword.isEmpty() || it.tag.lowercase().contains(keyword) }
    if (nodes.isEmpty()) null else group to nodes
}

@Composable
private fun LandingPicker(viewModel: ChainViewModel, onPick: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chain = state.chain
    CouiGroup(first = true, footer = stringResource(R.string.chain_landing_picker_footer)) {
        chain.landings.forEach { landing ->
            CouiRow(
                title = landing.tag,
                summary = landingSummary(landing),
                role = Role.RadioButton,
                onClick = { if (chain.manual != landing.tag) onPick(landing.tag) },
            ) {
                chain.delays[landing.tag]?.let { DelayText(it) }
                Spacer(Modifier.width(12.dp))
                CouiRadio(selected = chain.manual == landing.tag)
            }
        }
    }
}

@Composable
private fun LandingDelay(chain: ChainState, tag: String, testing: Boolean) {
    if (testing) {
        Spacer(Modifier.width(16.dp))
        BasicText(stringResource(R.string.chain_testing), style = Coui.type.rowValue.copy(color = Coui.colors.labelSecondary))
        return
    }
    chain.delays[tag]?.let { DelayText(it) }
}

@Composable
private fun ImportPanel(busy: Boolean, onImport: (String, String) -> Unit) {
    var content by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("socks") }
    CouiGroup(first = true, footer = stringResource(R.string.chain_import_type_footer)) {
        CouiRadioRow("SOCKS5", type == "socks", { type = "socks" })
        CouiRadioRow("HTTP", type == "http", { type = "http" })
    }
    CouiGroup(title = stringResource(R.string.chain_import_content), footer = stringResource(R.string.chain_import_formats)) {
        TextArea(content, { content = it }, stringResource(R.string.chain_import_hint))
    }
    CouiButton(
        text = stringResource(if (busy) R.string.chain_importing else R.string.chain_import_action),
        onClick = { onImport(content, type) },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        enabled = !busy && content.isNotBlank(),
    )
}

@Composable
private fun RenamePanel(current: String, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    CouiGroup(first = true) {
        CouiInputRow(stringResource(R.string.chain_name), name, { name = it })
    }
    CouiButton(
        text = stringResource(R.string.chain_save),
        onClick = { onSave(name.trim()) },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        enabled = name.isNotBlank() && name.trim() != current,
    )
}

/** 多行输入框：一次粘贴多条后置代理。COUI 没有多行输入组件，按搜索框的容器色和圆角绘制。 */
@Composable
internal fun TextArea(
    value: String, onValueChange: (String) -> Unit, hint: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = Coui.colors
    Box(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(colors.container8)
            .padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 120.dp, max = 260.dp),
    ) {
        if (value.isEmpty()) BasicText(hint, style = Coui.type.body.copy(color = colors.labelTertiary))
        BasicTextField(
            value, onValueChange, Modifier.fillMaxWidth(),
            textStyle = Coui.type.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
        )
    }
}

@Composable
private fun modeLabel(mode: ChainMode): String = stringResource(
    when (mode) {
        ChainMode.Off -> R.string.chain_mode_off
        ChainMode.Manual -> R.string.chain_mode_manual
        ChainMode.Policy -> R.string.chain_mode_policy
        ChainMode.ManualPolicy -> R.string.chain_mode_manual_policy
    }
)

@Composable
private fun modeSummary(mode: ChainMode): String = stringResource(
    when (mode) {
        ChainMode.Off -> R.string.chain_mode_off_summary
        ChainMode.Manual -> R.string.chain_mode_manual_summary
        ChainMode.Policy -> R.string.chain_mode_policy_summary
        ChainMode.ManualPolicy -> R.string.chain_mode_manual_policy_summary
    }
)

private fun landingSummary(landing: ChainLanding): String {
    val type = when (landing.type) {
        "socks" -> "SOCKS5"
        "http" -> if (landing.tls) "HTTPS" else "HTTP"
        else -> landing.type.uppercase()
    }
    val host = if (landing.server.contains(':')) "[${landing.server}]" else landing.server
    val auth = if (landing.username.isNotEmpty()) " · ${landing.username}" else ""
    return "$type · $host:${landing.port}$auth"
}

private fun importSummary(template: String, event: ChainEvent.Imported): String {
    val result = event.result
    val base = String.format(template, result.added.size, result.skipped.size)
    val failed = result.diagnostics.firstOrNull() ?: return base
    return "$base · ${failed.index}: ${failed.message}"
}
