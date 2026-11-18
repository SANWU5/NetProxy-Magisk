package com.fanjv.netproxy.feature.smart.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.feature.catalog.model.CatalogNode
import com.fanjv.netproxy.feature.smart.model.SmartNodeRef
import dev.aiingjie.coui.CouiBarButton
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiButtonStyle
import dev.aiingjie.coui.CouiCategory
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiGlyph
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSearchField
import dev.aiingjie.coui.CouiSmallButton
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems

/** 成员在 rememberSaveable 中的编码：分组 id 与节点名之间、节点与节点之间用控制字符分隔。 */
private const val FIELD = '\u001F'
private const val RECORD = '\u001E'

private fun encode(members: Collection<SmartNodeRef>) = members.joinToString(RECORD.toString()) { it.groupId + FIELD + it.node }

private fun decode(value: String): List<SmartNodeRef> = if (value.isEmpty()) emptyList() else value.split(RECORD).map {
    SmartNodeRef(it.substringBefore(FIELD), it.substringAfter(FIELD))
}

/** 编辑智能分组或新建自建分组：名称与成员。id 为空表示新建自建分组。 */
@Composable
internal fun SmartGroupEditScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: SmartGroupsViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val overlay = LocalCouiOverlay.current
    val existing = state.smart.groups.firstOrNull { it.id == id }

    var initialized by rememberSaveable(id) { mutableStateOf(false) }
    var name by rememberSaveable(id) { mutableStateOf("") }
    var selectedText by rememberSaveable(id) { mutableStateOf("") }
    var query by rememberSaveable(id) { mutableStateOf("") }
    val selected = remember(selectedText) { decode(selectedText).toSet() }

    LifecycleResumeEffect(Unit) {
        viewModel.setVisible(true)
        onPauseOrDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(state.loaded, existing) {
        if (initialized || !state.loaded) return@LaunchedEffect
        if (existing != null) {
            name = existing.name
            selectedText = encode(existing.members)
        }
        initialized = true
    }
    LaunchedEffect(events) {
        val failure = events.filterIsInstance<SmartEvent.Failed>().firstOrNull() ?: return@LaunchedEffect
        overlay.showSnack(failure.message)
        viewModel.consumeEvents()
    }

    fun toggle(refs: List<SmartNodeRef>, on: Boolean) {
        selectedText = encode(if (on) selected + refs else selected - refs.toSet())
    }

    val keyword = query.trim().lowercase()
    val localName = stringResource(R.string.node_local_config)
    // 只列出搜索结果；已选但不在结果里的节点仍然保留。
    val sections = remember(state.nodes, keyword) {
        state.nodes.mapNotNull { group ->
            val nodes = group.nodes.filter { keyword.isEmpty() || it.tag.lowercase().contains(keyword) }
            if (nodes.isEmpty()) null else group to nodes
        }
    }
    val saving = state.busy == "save" || state.busy == "delete"
    val canSave = initialized && !saving && name.isNotBlank() && selected.isNotEmpty()
    val missing = selected.count { ref -> state.nodes.none { it.group.id == ref.groupId && it.nodes.any { node -> node.tag == ref.node } } }

    val saveText = stringResource(R.string.smart_save)
    val deleteText = stringResource(R.string.smart_delete)
    val deleteMessage = stringResource(R.string.smart_delete_message)
    val cancelText = stringResource(android.R.string.cancel)
    val nameLabel = stringResource(R.string.smart_name)
    val nameHint = stringResource(R.string.smart_name_hint)
    val nameFooter = when {
        existing?.custom == false -> stringResource(R.string.smart_region_edit_footer)
        else -> stringResource(R.string.smart_custom_edit_footer)
    }
    val selectedSummary = stringResource(R.string.smart_selected_count, selected.size)
    val missingSummary = if (missing > 0) stringResource(R.string.smart_missing_count, missing) else null
    val selectAllText = stringResource(R.string.smart_select_all)
    val clearText = stringResource(R.string.smart_clear_group)
    val emptyText = stringResource(if (keyword.isEmpty()) R.string.node_empty else R.string.smart_search_empty)
    val loadingText = stringResource(R.string.loading)
    val listState = rememberLazyListState()

    fun save() = viewModel.save(existing?.id, name.trim(), selected.toList()) { onBack() }

    CouiSubPage(
        title = stringResource(if (existing == null) R.string.smart_new_custom else R.string.smart_edit),
        onBack = onBack,
        listState = listState,
        header = { CouiSearchField(query, { query = it }, stringResource(R.string.smart_search_nodes)) },
        actions = {
            if (saving) CouiLoading() else CouiBarButton(CouiGlyph.Check, saveText, ::save, enabled = canSave)
        },
    ) {
        item {
            CouiGroup(first = true, footer = nameFooter) {
                CouiInputRow(nameLabel, name, { name = it }, hint = nameHint)
                CouiRow(selectedSummary, summary = missingSummary)
            }
        }
        if (!state.loaded) {
            item { CouiGroup { CouiRow(loadingText, leading = { CouiLoading() }) } }
        } else if (sections.isEmpty()) {
            item { CouiGroup { CouiRow(emptyText) } }
        }
        sections.forEach { (group, nodes) ->
            val refs = nodes.map { SmartNodeRef(group.group.id, it.tag) }
            val chosen = refs.count { it in selected }
            val all = chosen == refs.size
            val title = (if (group.group.id == "default") localName else group.group.name) + " · $chosen/${refs.size}"
            item(key = "header:${group.group.id}") {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CouiCategory(title, Modifier.weight(1f))
                    CouiSmallButton(if (all) clearText else selectAllText, { toggle(refs, !all) })
                }
            }
            couiCardItems(nodes, key = { "${group.group.id}/${it.tag}" }, first = true) { node ->
                NodeChoice(node, SmartNodeRef(group.group.id, node.tag) in selected) {
                    toggle(listOf(SmartNodeRef(group.group.id, node.tag)), it)
                }
            }
        }
        if (existing != null) {
            item {
                CouiButton(
                    text = deleteText,
                    onClick = {
                        overlay.showDialog(deleteText, String.format(deleteMessage, existing.name), listOf(
                            CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) { viewModel.delete(existing.id, onBack) },
                            CouiDialogButton(cancelText),
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
                    style = CouiButtonStyle.Danger,
                    enabled = !saving,
                )
            }
        }
    }
}

@Composable
private fun NodeChoice(node: CatalogNode, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val summary = buildString {
        if (node.protocol.isNotBlank()) append(node.protocol.uppercase()).append(" · ")
        append(node.server.ifBlank { "--" })
        if (node.port > 0) append(':').append(node.port)
    }
    CouiRow(title = node.tag, summary = summary, role = Role.Checkbox, onClick = { onToggle(!selected) }) {
        CouiRadio(selected = selected)
    }
}
