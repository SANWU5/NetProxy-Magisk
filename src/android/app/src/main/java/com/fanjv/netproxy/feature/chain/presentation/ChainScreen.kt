package com.fanjv.netproxy.feature.chain.presentation

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.feature.chain.model.ChainLanding
import com.fanjv.netproxy.feature.chain.model.ChainMode
import com.fanjv.netproxy.feature.chain.model.ChainState
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiDockSpace
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInfoRow
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiLargeTitlePage
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiRadioRow
import dev.aiingjie.coui.CouiRow
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
    val cancelText = stringResource(android.R.string.cancel)
    val deleteMessage = stringResource(R.string.chain_landing_delete_message)

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

    fun showLandingActions(landing: ChainLanding) = overlay.showDialog(
        title = landing.tag,
        message = landingSummary(landing),
        buttons = listOf(
            CouiDialogButton(testText, CouiDialogButton.Kind.Primary) { viewModel.testLandings(landing.tag) },
            CouiDialogButton(renameTitle) { showRename(landing) },
            CouiDialogButton(deleteTitle, CouiDialogButton.Kind.Danger) {
                overlay.showDialog(deleteTitle, String.format(deleteMessage, landing.tag), listOf(
                    CouiDialogButton(deleteTitle, CouiDialogButton.Kind.Danger) { viewModel.removeLanding(landing.tag) },
                    CouiDialogButton(cancelText),
                ))
            },
            CouiDialogButton(cancelText),
        ),
    )

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
        item { StatusGroup(chain) }
        item {
            CouiGroup(title = stringResource(R.string.chain_mode), footer = stringResource(R.string.chain_mode_footer)) {
                ChainMode.entries.forEach { option ->
                    CouiRadioRow(
                        title = modeLabel(option),
                        selected = mode == option,
                        onClick = { if (mode != option) viewModel.setMode(option) },
                        summary = modeSummary(option),
                        enabled = idle && (option == ChainMode.Off || chain.landings.isNotEmpty()),
                    )
                }
            }
        }
        if (mode.usesManual && chain.landings.isNotEmpty()) {
            item {
                val index = chain.landings.indexOfFirst { it.tag == chain.manual }
                CouiGroup(title = stringResource(R.string.chain_manual_landing)) {
                    CouiMenuRow(
                        title = stringResource(R.string.chain_manual_landing),
                        options = chain.landings.map { it.tag },
                        selected = index,
                        onSelect = { viewModel.setManual(chain.landings[it].tag) },
                        enabled = idle,
                        summary = stringResource(R.string.chain_manual_summary),
                    )
                }
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
                        DelayValue(chain, landing.tag, state.busy == "test" || state.busy == "test:${landing.tag}")
                    }
                }
                CouiActionRow(stringResource(R.string.chain_import_title), onClick = ::showImport, enabled = idle)
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
                        summary = stringResource(R.string.chain_policy_summary, policy.packages.size, policy.landing),
                        enabled = idle,
                    )
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
}

@Composable
private fun StatusGroup(chain: ChainState) {
    val mode = chain.chainMode
    val front = if (chain.running && chain.front.isNotEmpty()) chain.front else stringResource(R.string.chain_front_follow)
    val landing = when (mode) {
        ChainMode.Off -> stringResource(R.string.chain_mode_off)
        ChainMode.Manual -> chain.manual
        ChainMode.Policy -> stringResource(R.string.chain_landing_by_policy)
        ChainMode.ManualPolicy -> stringResource(R.string.chain_landing_manual_policy, chain.manual)
    }
    CouiGroup(
        first = true,
        footer = if (chain.running) null else stringResource(R.string.chain_service_stopped),
    ) {
        CouiInfoRow(stringResource(R.string.chain_front), front, summary = stringResource(R.string.chain_front_summary))
        CouiInfoRow(stringResource(R.string.chain_landing), landing)
    }
}

@Composable
private fun DelayValue(chain: ChainState, tag: String, testing: Boolean) {
    val colors = Coui.colors
    val delay = chain.delays[tag]
    val text = when {
        testing -> stringResource(R.string.chain_testing)
        delay == null -> return
        delay <= 0 -> stringResource(R.string.chain_delay_failed)
        else -> "$delay ms"
    }
    val color = when {
        testing || delay == null -> colors.labelSecondary
        delay <= 0 -> colors.error
        delay < 800 -> colors.success
        else -> colors.warning
    }
    Spacer(Modifier.width(16.dp))
    BasicText(text, style = Coui.type.rowValue.copy(color = color))
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
internal fun TextArea(value: String, onValueChange: (String) -> Unit, hint: String) {
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
