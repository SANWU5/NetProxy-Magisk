package com.fanjv.netproxy.feature.smart.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.CouiBarMenuButton
import com.fanjv.netproxy.core.ui.component.MenuAction
import com.fanjv.netproxy.feature.smart.model.key
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems

/**
 * 智能分组详情：在分组内选择自动选择或某个节点，节点页随之改用这个分组。
 * 这里的选择按分组记住，从分组列表直接选择这个分组时沿用；从未选择过节点时使用自动选择。
 */
@Composable
internal fun SmartGroupDetailScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: SmartGroupsViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val overlay = LocalCouiOverlay.current
    val navigator = LocalNavigator.current
    val smart = state.smart
    val group = smart.groups.firstOrNull { it.id == id }
    val idle = state.busy.isEmpty()
    val testing = state.busy == "test:$id"

    LifecycleResumeEffect(Unit) {
        viewModel.setVisible(true)
        onPauseOrDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(events) {
        if (events.isEmpty()) return@LaunchedEffect
        events.filterIsInstance<SmartEvent.Failed>().forEach { overlay.showSnack(it.message) }
        viewModel.consumeEvents()
    }

    // 成员副标题显示来源分组与协议；已被订阅移除的节点查不到，按不可用显示。
    val localName = stringResource(R.string.node_local_config)
    val sources = remember(state.nodes, localName) {
        state.nodes.flatMap { source ->
            val name = if (source.group.id == "default") localName else source.group.name
            source.nodes.map { node ->
                "${source.group.id}/${node.tag}" to if (node.protocol.isBlank()) name else "$name · ${node.protocol.uppercase()}"
            }
        }.toMap()
    }

    val moreText = stringResource(R.string.more_actions)
    val testText = stringResource(R.string.smart_test)
    val editText = stringResource(R.string.smart_edit)
    val deleteText = stringResource(R.string.smart_delete)
    val deleteMessage = stringResource(R.string.smart_delete_message)
    val cancelText = stringResource(android.R.string.cancel)
    val currentText = stringResource(R.string.smart_current)
    val unavailableText = stringResource(R.string.smart_node_unavailable)
    val testingText = stringResource(R.string.latency_testing)
    val membersTitle = stringResource(R.string.node_count, group?.members?.size ?: 0)
    val autoTitle = stringResource(R.string.smart_auto)
    val autoSummary = stringResource(R.string.smart_auto_summary)
    val inUseFooter = stringResource(R.string.smart_detail_in_use)
    val notInUseFooter = stringResource(R.string.smart_detail_not_in_use)
    val loadingText = stringResource(R.string.loading)
    val missingText = stringResource(R.string.smart_group_missing)

    fun confirmDelete(name: String) = overlay.showDialog(deleteText, String.format(deleteMessage, name), listOf(
        CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) { viewModel.delete(id, onBack) },
        CouiDialogButton(cancelText),
    ))

    CouiSubPage(
        title = group?.name ?: stringResource(R.string.smart_groups),
        onBack = onBack,
        actions = {
            if (!idle) CouiLoading()
            if (group != null) CouiBarMenuButton(moreText, listOf(
                MenuAction(testText, enabled = idle && smart.running && group.available > 0) { viewModel.test(id) },
                MenuAction(editText, enabled = idle) { navigator.push(Route.SmartGroupEdit(id)) },
                MenuAction(deleteText, enabled = idle) { confirmDelete(group.name) },
            ))
        },
    ) {
        if (group == null) {
            item {
                CouiGroup(first = true, footer = state.error.ifEmpty { null }) {
                    if (!state.loaded || state.loading) CouiRow(loadingText, leading = { CouiLoading() })
                    else CouiRow(missingText)
                }
            }
            return@CouiSubPage
        }
        val active = smart.active == group.id
        val auto = group.manual == null
        item {
            CouiGroup(first = true, footer = if (active) inUseFooter else notInUseFooter) {
                CouiRow(
                    title = autoTitle,
                    summary = if (auto && smart.running && group.selectedNode.isNotEmpty()) String.format(currentText, group.selectedNode)
                    else autoSummary,
                    enabled = idle && group.available > 0,
                    role = Role.RadioButton,
                    onClick = { if (!auto || !active) viewModel.selectNode(id, null) },
                ) {
                    when {
                        testing -> TestingText(testingText)
                        auto && smart.running && group.delay != 0 -> DelayText(group.delay)
                    }
                    Spacer(Modifier.width(12.dp))
                    CouiRadio(selected = auto)
                }
            }
        }
        couiCardItems(group.members, key = { it.key }, title = membersTitle) { member ->
            val source = sources[member.key]
            val selected = group.manual == member
            val delay = group.delays[member.key] ?: if (member.key in state.failed) -1 else null
            CouiRow(
                title = member.node,
                summary = source ?: unavailableText,
                enabled = idle && source != null,
                role = Role.RadioButton,
                onClick = { if (!selected || !active) viewModel.selectNode(id, member) },
            ) {
                when {
                    source == null -> Unit
                    testing -> TestingText(testingText)
                    delay != null -> DelayText(delay)
                }
                Spacer(Modifier.width(12.dp))
                CouiRadio(selected = selected, enabled = source != null)
            }
        }
    }
}

@Composable
private fun TestingText(text: String) {
    BasicText(text, Modifier.padding(start = 12.dp), style = Coui.type.rowValue.copy(color = Coui.colors.labelSecondary))
}
