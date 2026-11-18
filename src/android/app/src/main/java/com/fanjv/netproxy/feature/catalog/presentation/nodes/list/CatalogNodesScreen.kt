package com.fanjv.netproxy.feature.catalog.presentation.nodes.list

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.UiText
import com.fanjv.netproxy.core.ui.resolve
import com.fanjv.netproxy.feature.catalog.model.CatalogNode
import com.fanjv.netproxy.feature.catalog.model.CatalogNodeGroup
import com.fanjv.netproxy.feature.catalog.presentation.nodes.CatalogNodesViewModel
import com.fanjv.netproxy.feature.catalog.presentation.nodes.selectedAutoNodeTag
import com.fanjv.netproxy.feature.chain.presentation.TextArea
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route.NodeEdit
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiBarButton
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiDockSpace
import dev.aiingjie.coui.CouiGlyph
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiIcon
import dev.aiingjie.coui.CouiLargeTitlePage
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRadioRow
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems
import dev.aiingjie.coui.couiPressable

/** Catalog 节点页：离线读取分组，运行时仅负责选择与测速。 */
@Composable
internal fun CatalogNodesScreen(
    isActive: Boolean = true,
    viewModel: CatalogNodesViewModel = netProxyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedAutoNode = remember(state.groups, state.selection) {
        state.groups.firstOrNull { it.group.id == state.selection.activeGroupId }
            ?.let { selectedAutoNodeTag(it, state.selection) }
            .orEmpty()
    }
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val overlay = LocalCouiOverlay.current
    val displayPreferences = remember(context) {
        context.getSharedPreferences("node_display", android.content.Context.MODE_PRIVATE)
    }
    var sortMode by remember { mutableIntStateOf(displayPreferences.getInt("sort", 0).coerceIn(0, 3)) }

    LifecycleResumeEffect(isActive) {
        viewModel.setVisible(isActive)
        onPauseOrDispose { if (isActive) viewModel.setVisible(false) }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importFile(it) }
    }

    val noticeText = (if (state.error !is UiText.Empty) state.error else state.notice).resolve()
    LaunchedEffect(state.noticeId) {
        if (noticeText.isBlank()) return@LaunchedEffect
        viewModel.clearNotice()
        overlay.showSnack(noticeText)
    }

    LaunchedEffect(state.exportedNodeLinkId) {
        val link = state.exportedNodeLink
        if (link.isBlank()) return@LaunchedEffect
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("NetProxy node", link))
        viewModel.nodeLinkCopied()
    }

    val selectedIndex = state.groups.indexOfFirst { it.group.id == state.selectedGroupId }.coerceAtLeast(0)
    val selectedGroup = state.groups.getOrNull(selectedIndex)
    val busy = state.operation.isNotEmpty()

    val addTitle = stringResource(R.string.node_add_single)
    val linkLabel = stringResource(R.string.node_link_label)
    val addText = stringResource(R.string.common_add)
    val moreText = stringResource(R.string.more_actions)
    val singleLinkText = stringResource(R.string.node_single_link)
    val localFileText = stringResource(R.string.node_local_file)
    val testGroupText = stringResource(R.string.node_test_current_group)
    val sortText = stringResource(R.string.node_sort)
    val cancelText = stringResource(R.string.common_cancel)
    val sortOptions = listOf(
        stringResource(R.string.node_sort_default), stringResource(R.string.node_sort_name),
        stringResource(R.string.node_sort_protocol), stringResource(R.string.node_sort_latency),
    )
    val testText = stringResource(R.string.node_test_delay)
    val editText = stringResource(R.string.node_edit)
    val exportText = stringResource(R.string.node_export)
    val deleteText = stringResource(R.string.node_delete)

    fun showAddLink() = overlay.showPanel(addTitle) {
        var link by remember { mutableStateOf("") }
        CouiGroup(first = true) { TextArea(link, { link = it }, linkLabel) }
        CouiButton(
            text = addText,
            onClick = { viewModel.addNode(link); overlay.dismissPanel() },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            enabled = link.isNotBlank(),
        )
    }

    fun showSort() = overlay.showPanel(sortText) {
        CouiGroup(first = true) {
            sortOptions.forEachIndexed { index, label ->
                CouiRadioRow(label, sortMode == index, {
                    sortMode = index
                    displayPreferences.edit { putInt("sort", index) }
                    overlay.dismissPanel()
                })
            }
        }
    }

    fun showMore() = overlay.showDialog(
        title = null,
        buttons = listOf(
            CouiDialogButton(singleLinkText) { showAddLink() },
            CouiDialogButton(localFileText) { fileLauncher.launch(arrayOf("*/*")) },
            CouiDialogButton(testGroupText) { selectedGroup?.group?.id?.let(viewModel::testGroupDelay) },
            CouiDialogButton(sortText) { showSort() },
            CouiDialogButton(cancelText),
        ),
    )

    fun showNodeActions(group: CatalogNodeGroup, node: CatalogNode) = overlay.showDialog(
        title = node.tag,
        message = node.serverLabel(),
        buttons = listOf(
            CouiDialogButton(testText, CouiDialogButton.Kind.Primary) { viewModel.testDelay("${group.group.id}/${node.tag}") },
            CouiDialogButton(editText) { navigator.push(NodeEdit("${group.group.id}/${node.tag}")) },
            CouiDialogButton(exportText) { viewModel.exportNode(group.group.id, node.tag) },
            CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) { viewModel.removeNode(group.group.id, node.tag) },
            CouiDialogButton(cancelText),
        ),
    )

    val groupTitle = stringResource(R.string.node_count, selectedGroup?.nodes?.size ?: 0)
    val nodes = remember(selectedGroup, sortMode, state.latencies) {
        selectedGroup?.let { sortCatalogNodes(it.nodes, sortMode, it.group.id, state.latencies) }.orEmpty()
    }

    CouiLargeTitlePage(
        title = stringResource(R.string.nodes),
        bottomSpace = CouiDockSpace,
        actions = { CouiBarButton(CouiGlyph.More, moreText, onClick = ::showMore) },
    ) {
        if (selectedGroup == null) {
            item {
                CouiGroup(first = true) {
                    when {
                        state.loading -> CouiRow(stringResource(R.string.loading), leading = { CouiLoading() })
                        else -> CouiRow(stringResource(if (state.loadFailed) R.string.nodes_read_failed else R.string.node_empty))
                    }
                    CouiRow(singleLinkText, onClick = ::showAddLink)
                    CouiRow(localFileText, onClick = { fileLauncher.launch(arrayOf("*/*")) })
                }
            }
            return@CouiLargeTitlePage
        }
        val groupId = selectedGroup.group.id
        val autoSelected = state.selection.selectorMode == "urltest" && state.selection.activeGroupId == groupId
        item {
            CouiGroup(first = true) {
                if (state.groups.size > 1) {
                    CouiMenuRow(
                        title = stringResource(R.string.node_group),
                        options = state.groups.map { it.group.name },
                        selected = selectedIndex,
                        onSelect = { index -> state.groups.getOrNull(index)?.group?.id?.let(viewModel::selectGroup) },
                    )
                }
                CouiRow(
                    title = stringResource(R.string.node_auto_fastest),
                    summary = selectedAutoNode.takeIf { autoSelected && it.isNotBlank() },
                    enabled = !busy && selectedGroup.nodes.isNotEmpty(),
                    role = Role.RadioButton,
                    onClick = { viewModel.useAuto(groupId) },
                ) {
                    LatencyText(state.latencies["Auto/$groupId"])
                    Spacer(Modifier.width(12.dp))
                    CouiRadio(selected = autoSelected)
                }
            }
        }
        if (nodes.isEmpty()) {
            item { CouiGroup(title = groupTitle) { CouiRow(stringResource(R.string.node_group_empty)) } }
        } else {
            couiCardItems(nodes, key = { it.tag }, title = groupTitle) { node ->
                val ref = "$groupId/${node.tag}"
                val manualSelected = state.selection.selectorMode == "manual" && state.selection.selected == ref
                CouiRow(
                    title = node.tag,
                    summary = node.serverLabel(),
                    enabled = !busy,
                    role = Role.RadioButton,
                    onClick = { viewModel.useNode(groupId, node.tag) },
                ) {
                    LatencyText(state.latencies[ref])
                    Box(
                        Modifier.padding(start = 4.dp).size(36.dp)
                            .couiPressable(shape = CircleShape, role = Role.Button) { showNodeActions(selectedGroup, node) },
                        contentAlignment = Alignment.Center,
                    ) { CouiIcon(CouiGlyph.More, Coui.colors.labelTertiary) }
                    CouiRadio(selected = manualSelected)
                }
            }
        }
    }
}

@Composable
private fun LatencyText(value: String?) {
    if (value == null) return
    val colors = Coui.colors
    val text = when (value) {
        "failed" -> stringResource(R.string.latency_failed)
        "timeout" -> stringResource(R.string.latency_timeout)
        "testing" -> stringResource(R.string.latency_testing)
        else -> "$value ms"
    }
    val color = when (value) {
        "failed", "timeout" -> colors.error
        "testing" -> colors.labelSecondary
        else -> when (value.toIntOrNull() ?: Int.MAX_VALUE) {
            in 0..300 -> colors.success
            in 301..800 -> colors.warning
            else -> colors.error
        }
    }
    BasicText(text, Modifier.padding(start = 12.dp), style = Coui.type.rowValue.copy(color = color))
}

private fun CatalogNode.serverLabel(): String = buildString {
    if (protocol.isNotBlank()) append(protocol.uppercase()).append(" · ")
    append(server.ifBlank { "--" })
    if (port > 0) append(':').append(port)
}

private fun sortCatalogNodes(
    nodes: List<CatalogNode>,
    sortMode: Int,
    groupId: String,
    latencies: Map<String, String>
): List<CatalogNode> = when (sortMode) {
    1 -> nodes.sortedBy { it.tag.lowercase() }
    2 -> nodes.sortedWith(compareBy<CatalogNode> { it.protocol }.thenBy { it.tag })
    3 -> nodes.sortedBy { latencies["$groupId/${it.tag}"]?.toIntOrNull() ?: Int.MAX_VALUE }
    else -> nodes
}
