package com.fanjv.netproxy.feature.catalog.presentation.subscriptions

import dev.aiingjie.coui.CouiRadioRow
import com.fanjv.netproxy.feature.catalog.data.NodeImportStore
import com.fanjv.netproxy.core.ui.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.OpenableColumns
import android.net.Uri
import android.content.Context
import com.fanjv.netproxy.core.ui.component.showActionSheet
import com.fanjv.netproxy.core.ui.component.SheetAction
import com.fanjv.netproxy.core.ui.component.MenuAction
import com.fanjv.netproxy.core.ui.component.CouiBarMenuButton
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.ui.UiText
import com.fanjv.netproxy.core.ui.resolve
import com.fanjv.netproxy.feature.catalog.model.CatalogGroupSummary
import com.fanjv.netproxy.feature.catalog.model.CatalogNode
import com.fanjv.netproxy.feature.catalog.model.SubscriptionHistoryEntry
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.LocalOpenNodesGroup
import com.fanjv.netproxy.navigation.Route
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.Role
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import com.fanjv.netproxy.feature.chain.presentation.TextArea
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiDockSpace
import dev.aiingjie.coui.CouiEmptyState
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiLargeTitlePage
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiPanel
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiStatus
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 订阅页：URL 订阅管理、流量状态与更新计划。 */
@Composable
internal fun SubscriptionsScreen(
    isActive: Boolean = true,
    viewModel: SubscriptionsViewModel = netProxyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val overlay = LocalCouiOverlay.current

    LifecycleResumeEffect(isActive) {
        viewModel.setVisible(isActive)
        onPauseOrDispose { if (isActive) viewModel.setVisible(false) }
    }

    val noticeText = (if (state.error !is UiText.Empty) state.error else state.notice).resolve()
    LaunchedEffect(state.noticeId) {
        if (noticeText.isBlank()) return@LaunchedEffect
        viewModel.clearNotice()
        overlay.showSnack(noticeText)
    }

    val busy = state.operation.isNotEmpty()
    val hasSubscriptions = state.groups.any { it.type == "subscription" }
    // 本地配置被清空后不再列出，像被删除一样；需要时从「导入节点」或节点页再加入。
    val shownGroups = state.groups.filterNot { it.id == "default" && it.nodeCount == 0 }
    val addText = stringResource(R.string.subscription_add_url)
    val updateAllText = stringResource(R.string.subscription_update_all)
    val cancelText = stringResource(R.string.common_cancel)
    val moreText = stringResource(R.string.more_actions)
    val viewNodesText = stringResource(R.string.subscription_view_nodes)
    val openNodesGroup = LocalOpenNodesGroup.current
    val updateText = stringResource(R.string.common_update)
    val deleteText = stringResource(R.string.common_delete)
    val deleteTitle = stringResource(R.string.subscription_delete_title)
    val deleteMessage = stringResource(R.string.subscription_delete_message)
    val subscriptionDetailsText = stringResource(R.string.subscription_details)
    val localDetailsText = stringResource(R.string.local_config_details)
    val localDeleteTitle = stringResource(R.string.local_group_delete_title)
    val localDeleteMessage = stringResource(R.string.local_group_delete_message)
    val localClearMessage = stringResource(R.string.local_config_clear_message)
    val importTitle = stringResource(R.string.subscription_import_nodes)
    val importFileText = stringResource(R.string.subscription_import_file)

    // 「导入节点」：从文件导入时先读出内容，再在同一个面板里选择导入到哪个分组。
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importStore = remember(context) { NodeImportStore(context) }
    var importFile by remember { mutableStateOf<ImportFile?>(null) }
    fun showImport() = overlay.showPanel(importTitle, onDismiss = { importFile = null }) {
        val groups = viewModel.state.collectAsStateWithLifecycle().value.groups
        ImportNodesPanel(
            groups = groups,
            file = importFile,
            onClearFile = { importFile = null },
            onAddUrl = { url -> overlay.dismissPanel(); navigator.push(Route.SubscriptionEdit("", url)) },
            onImport = { content, groupId, newGroup ->
                viewModel.importNodes(content, groupId, newGroup)
                overlay.dismissPanel()
            },
        )
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                importFile = ImportFile(displayName(context, uri), importStore.read(uri))
                showImport()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                overlay.showSnack(error.userMessage())
            }
        }
    }

    fun showGroupActions(group: CatalogGroupSummary) {
        val isSubscription = group.type == "subscription"
        overlay.showActionSheet(
            title = group.name,
            actions = buildList {
                // 订阅页只管理分组；代理用哪个分组、哪个节点在节点页选择。
                add(SheetAction(viewNodesText) { openNodesGroup(group.id) })
                if (isSubscription && !busy) add(SheetAction(updateText) { viewModel.updateSubscription(group.id) })
                add(SheetAction(if (isSubscription) subscriptionDetailsText else localDetailsText) {
                    navigator.push(Route.SubscriptionDetails(group.id))
                })
                // URL 订阅与自建本地分组整个删除；default（本地配置）只清空节点。
                if (!busy) add(SheetAction(deleteText, danger = true) {
                    val (title, message) = when {
                        isSubscription -> deleteTitle to String.format(deleteMessage, group.name)
                        group.id == "default" -> localDeleteTitle to localClearMessage
                        else -> localDeleteTitle to String.format(localDeleteMessage, group.name)
                    }
                    overlay.showDialog(title, message, listOf(
                        CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) { viewModel.remove(group.id) },
                        CouiDialogButton(cancelText),
                    ))
                })
            },
            cancel = cancelText,
        )
    }

    val updating = state.operation == "update" || state.operation == "update-all"
    val updatingText = stringResource(
        if (state.operation == "update-all") R.string.subscription_update_sheet_all else R.string.subscription_update_sheet_single
    )
    val listTitle = stringResource(R.string.subscriptions)
    CouiLargeTitlePage(
        title = stringResource(R.string.subscriptions),
        bottomSpace = CouiDockSpace,
        actions = {
            CouiBarMenuButton(moreText, listOf(
                MenuAction(addText) { navigator.push(Route.SubscriptionEdit("")) },
                MenuAction(importTitle, enabled = !busy) { importFile = null; showImport() },
                MenuAction(importFileText, enabled = !busy) { fileLauncher.launch(arrayOf("*/*")) },
                MenuAction(updateAllText, enabled = hasSubscriptions && !busy) { viewModel.updateAll() },
            ))
        },
    ) {
        if (updating) {
            item {
                CouiGroup(first = true) {
                    CouiRow(updatingText, leading = { CouiLoading() })
                }
            }
        }
        if (shownGroups.isEmpty()) {
            item {
                when {
                    state.loading -> CouiEmptyState(stringResource(R.string.loading), loading = true)
                    state.loadFailed -> CouiEmptyState(
                        stringResource(R.string.subscriptions_read_failed), status = CouiStatus.Error,
                        actionText = stringResource(R.string.retry), onAction = { viewModel.refresh() },
                    )
                    else -> CouiEmptyState(
                        stringResource(R.string.subscription_empty), body = stringResource(R.string.subscription_empty_summary),
                        actionText = addText, onAction = { navigator.push(Route.SubscriptionEdit("")) },
                    )
                }
            }
        } else {
            couiCardItems(shownGroups, key = CatalogGroupSummary::id, title = listTitle, first = !updating) { group ->
                CouiRow(
                    title = group.name,
                    summary = groupSummary(group),
                    role = Role.Button,
                    onClick = { showGroupActions(group) },
                ) {
                    // 只读标记：节点页当前使用的分组。
                    if (group.active) {
                        BasicText(
                            stringResource(R.string.subscription_active),
                            Modifier.padding(start = 12.dp),
                            style = Coui.type.rowValue.copy(color = Coui.colors.success),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun groupSummary(group: CatalogGroupSummary): String {
    val isSubscription = group.type == "subscription"
    val usage = group.usage
    val used = (usage?.upload ?: 0L) + (usage?.download ?: 0L)
    val total = usage?.total ?: 0L
    val updatedAt = group.lastSuccessAt.ifBlank { group.updatedAt }
    val first = when {
        !isSubscription -> stringResource(R.string.subscription_node_count, group.nodeCount)
        total > 0 -> stringResource(R.string.subscription_usage, formatBytes(used), formatBytes(total))
        else -> stringResource(R.string.subscription_no_usage)
    }
    val second = if (updatedAt.isNotBlank()) {
        stringResource(
            R.string.subscription_updated_at,
            stringResource(if (isSubscription) R.string.subscription_updated_label else R.string.local_config_modified_label),
            formatDate(updatedAt)
        )
    } else {
        stringResource(if (isSubscription) R.string.subscription_not_updated else R.string.local_config_modified_at)
    }
    return "$first\n$second"
}

/** 订阅详情：展示节点摘要和最近更新历史。 */
@Composable
internal fun SubscriptionDetailsScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: SubscriptionDetailsViewModel = netProxyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val overlay = LocalCouiOverlay.current
    val openNodesGroup = LocalOpenNodesGroup.current
    var editedNodeLink by remember { mutableStateOf("") }

    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.editingNodeRef, state.editingNodeLink) {
        if (state.editingNodeRef.isNotBlank()) editedNodeLink = state.editingNodeLink
    }
    LaunchedEffect(state.exportedNodeLinkId) {
        val link = state.exportedNodeLink
        if (link.isBlank()) return@LaunchedEffect
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("NetProxy node", link))
        viewModel.nodeLinkCopied()
    }
    val noticeText = (if (state.error !is UiText.Empty) state.error else state.notice).resolve()
    LaunchedEffect(state.noticeId) {
        if (noticeText.isBlank()) return@LaunchedEffect
        viewModel.clearNotice()
        overlay.showSnack(noticeText)
    }

    val testText = stringResource(R.string.node_test_delay)
    val editNodeText = stringResource(R.string.node_edit)
    val exportText = stringResource(R.string.node_export)
    val deleteNodeText = stringResource(R.string.node_delete)
    val cancelText = stringResource(R.string.common_cancel)
    val deleteText = stringResource(R.string.common_delete)
    val deleteTitle = stringResource(R.string.subscription_delete_title)
    val deleteMessage = stringResource(R.string.subscription_delete_details_message)
    val localDeleteTitle = stringResource(R.string.local_group_delete_title)
    val localDeleteMessage = stringResource(R.string.local_group_delete_message)
    val localClearMessage = stringResource(R.string.local_config_clear_message)
    val nodeDeleteConfirm = stringResource(R.string.node_delete_confirm)
    fun showNodeActions(node: CatalogNode) = overlay.showActionSheet(
        title = node.tag,
        actions = listOf(
            SheetAction(testText) { viewModel.testNode(id, node.tag) },
            SheetAction(editNodeText) { viewModel.editNode(id, node.tag) },
            SheetAction(exportText) { viewModel.exportNode(id, node.tag) },
            SheetAction(deleteNodeText, danger = true) {
                overlay.showDialog(deleteNodeText, String.format(nodeDeleteConfirm, node.tag), listOf(
                    CouiDialogButton(deleteNodeText, CouiDialogButton.Kind.Danger) { viewModel.removeNode(id, node.tag) },
                    CouiDialogButton(cancelText),
                ))
            },
        ),
        cancel = cancelText,
    )
    // URL 订阅与自建本地分组整个删除；default（本地配置）只清空节点。
    fun confirmDelete() {
        val group = state.details?.group ?: return
        val (title, message) = when {
            group.type == "subscription" -> deleteTitle to deleteMessage
            group.id == "default" -> localDeleteTitle to localClearMessage
            else -> localDeleteTitle to String.format(localDeleteMessage, group.name)
        }
        overlay.showDialog(title, message, listOf(
            CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) { viewModel.remove(id, onBack) },
            CouiDialogButton(cancelText),
        ))
    }

    val details = state.details
    val isSubscription = details?.group?.type == "subscription"
    val updating = state.operation == "update" || state.operation == "update-all"
    val updatingText = stringResource(
        if (state.operation == "update-all") R.string.subscription_update_sheet_all else R.string.subscription_update_sheet_single
    )
    val nodesTitle = stringResource(R.string.subscription_nodes_section, details?.nodes?.size ?: 0)
    val historyTitle = stringResource(R.string.subscription_history_section)
    CouiSubPage(
        title = stringResource(if (details?.group?.type == "local") R.string.local_config_details else R.string.subscription_details),
        onBack = onBack,
    ) {
        if (updating) item("updating") {
            CouiGroup(first = true) { CouiRow(updatingText, leading = { CouiLoading() }) }
        }
        if (details == null) {
            if (state.loading) item("loading") {
                CouiGroup(first = !updating) { CouiRow(stringResource(R.string.loading), leading = { CouiLoading() }) }
            }
            return@CouiSubPage
        }
        val group = details.group
        item("summary") {
            val status = subscriptionStatus(group)
            CouiGroup(first = !updating) {
                CouiRow(
                    title = group.name,
                    summary = listOf(
                        if (isSubscription) stringResource(R.string.subscription_summary, group.nodeCount, formatDuration(group.updateInterval))
                        else stringResource(R.string.local_config_summary, group.nodeCount),
                        group.profileTitle,
                    ).filter(String::isNotBlank).joinToString("\n"),
                ) {
                    BasicText(
                        if (group.active) stringResource(R.string.subscription_active) else status.label,
                        Modifier.padding(start = 12.dp),
                        style = Coui.type.rowValue.copy(color = if (group.active) Coui.colors.success else status.color),
                    )
                }
                CouiJumpRow(stringResource(R.string.subscription_view_nodes), { openNodesGroup(id) })
            }
        }
        if (isSubscription) item("actions") {
            CouiGroup {
                CouiJumpRow(stringResource(R.string.common_edit), { navigator.push(Route.SubscriptionEdit(id)) })
                CouiActionRow(stringResource(R.string.common_update), { viewModel.update(id) }, enabled = state.operation.isEmpty())
            }
        }
        couiCardItems(details.nodes, key = { "node:" + it.tag }, title = nodesTitle) { node ->
            CouiRow(
                title = node.tag,
                summary = buildString {
                    append("${node.protocol.uppercase()} · ${node.server}:${node.port}")
                    state.latencies["${group.id}/${node.tag}"]?.let { latency ->
                        append(" · ")
                        append(if (latency.all(Char::isDigit)) "$latency ms" else latency)
                    }
                },
                role = Role.Button,
                onClick = { showNodeActions(node) },
            )
        }
        if (isSubscription) {
            val history = state.history.takeLast(20).reversed().withIndex().toList()
            if (history.isEmpty()) item("history") {
                CouiGroup(title = historyTitle) { CouiRow(stringResource(R.string.subscription_history_empty)) }
            } else couiCardItems(history, key = { "history:" + it.index }, title = historyTitle) { entry ->
                HistoryRow(entry.value)
            }
        }
        item("delete") {
            CouiGroup { CouiActionRow(deleteText, ::confirmDelete, danger = true, enabled = state.operation.isEmpty()) }
        }
    }

    CouiPanel(
        visible = state.editingNodeRef.isNotBlank(),
        title = stringResource(R.string.node_edit),
        onDismissRequest = viewModel::dismissNodeEditor,
    ) {
        CouiGroup(
            first = true,
            title = stringResource(R.string.node_link_label),
            footer = stringResource(
                if (state.details?.group?.type == "subscription") R.string.subscription_edit_remote_note
                else R.string.subscription_edit_local_note
            ),
        ) {
            TextArea(editedNodeLink, { editedNodeLink = it }, "")
        }
        CouiButton(
            text = stringResource(R.string.save_text),
            onClick = { viewModel.saveEditedNode(editedNodeLink) },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            enabled = editedNodeLink.isNotBlank() && state.operation != "edit",
        )
    }
}

/** 新增/编辑 URL 订阅。敏感字段只通过 root CLI 的私有契约读取。 */
@Composable
internal fun SubscriptionEditorScreen(
    id: String,
    onBack: () -> Unit,
    initialUrl: String = "",
    viewModel: SubscriptionEditorViewModel = netProxyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val overlay = LocalCouiOverlay.current

    LaunchedEffect(id) {
        viewModel.load(id)
        // 新建时带入「导入节点」里粘贴的订阅地址；已填写过的地址不覆盖。
        if (id.isBlank() && initialUrl.isNotBlank()) viewModel.update { if (it.url.isBlank()) it.copy(url = initialUrl) else it }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    val errorText = state.error.resolve()
    LaunchedEffect(state.noticeId) {
        if (errorText.isBlank()) return@LaunchedEffect
        viewModel.clearError()
        overlay.showSnack(errorText)
    }

    val intervals = subscriptionIntervalOptions(draft.updateIntervalSeconds)
    val intervalLabels = listOf(
        stringResource(R.string.subscription_duration_minutes, 15),
        stringResource(R.string.subscription_duration_hours, 1),
        stringResource(R.string.subscription_duration_hours, 6),
        stringResource(R.string.subscription_duration_hours, 12),
        stringResource(R.string.subscription_duration_hours, 24),
        stringResource(R.string.subscription_duration_days, 3),
        stringResource(R.string.subscription_duration_days, 7)
    ) + if (intervals.size > 7) {
        listOf(stringResource(R.string.subscription_interval_custom_seconds, draft.updateIntervalSeconds))
    } else emptyList()
    val viaValues = listOf("auto", "always", "never")
    val viaLabels = listOf(
        stringResource(R.string.subscription_editor_network_auto),
        stringResource(R.string.subscription_editor_network_proxy),
        stringResource(R.string.subscription_editor_network_direct)
    )

    CouiSubPage(
        title = stringResource(
            if (state.id.isBlank()) R.string.subscription_editor_add_title else R.string.subscription_editor_edit_title
        ),
        onBack = onBack,
    ) {
        if (state.loading) {
            item { CouiGroup(first = true) { CouiRow(stringResource(R.string.loading), leading = { CouiLoading() }) } }
            return@CouiSubPage
        }
        item {
            CouiGroup(title = stringResource(R.string.subscription_editor_basic_section), first = true) {
                CouiInputRow(
                    stringResource(if (state.id.isBlank()) R.string.subscription_editor_name_auto else R.string.subscription_editor_name),
                    draft.name, { value -> viewModel.update { it.copy(name = value) } },
                )
                TextArea(draft.url, { value -> viewModel.update { it.copy(url = value) } }, stringResource(R.string.subscription_editor_url))
            }
        }
        item {
            CouiGroup {
                CouiMenuRow(
                    title = stringResource(R.string.subscription_editor_update_interval),
                    options = intervalLabels,
                    selected = intervals.indexOf(draft.updateIntervalSeconds),
                    onSelect = { index -> viewModel.update { it.copy(updateIntervalSeconds = intervals[index]) } },
                )
                CouiSwitchRow(
                    stringResource(R.string.subscription_editor_auto_update), draft.autoUpdate,
                    { enabled -> viewModel.update { it.copy(autoUpdate = enabled) } },
                )
                CouiMenuRow(
                    title = stringResource(R.string.subscription_editor_update_network),
                    options = viaLabels,
                    selected = viaValues.indexOf(draft.updateViaProxy).coerceAtLeast(0),
                    onSelect = { index -> viewModel.update { it.copy(updateViaProxy = viaValues[index]) } },
                )
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.subscription_editor_filter_section)) {
                CouiInputRow(stringResource(R.string.subscription_editor_include), draft.include,
                    { value -> viewModel.update { it.copy(include = value) } })
                CouiInputRow(stringResource(R.string.subscription_editor_exclude), draft.exclude,
                    { value -> viewModel.update { it.copy(exclude = value) } })
                CouiSwitchRow(
                    stringResource(R.string.subscription_editor_allow_insecure), draft.allowInsecure,
                    { enabled -> viewModel.update { it.copy(allowInsecure = enabled) } },
                    summary = stringResource(R.string.subscription_editor_allow_insecure_summary),
                )
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.subscription_editor_advanced_section)) {
                CouiInputRow(stringResource(R.string.subscription_editor_user_agent), draft.userAgent,
                    { value -> viewModel.update { it.copy(userAgent = value) } })
                CouiInputRow(stringResource(R.string.subscription_editor_hwid), draft.hwid,
                    { value -> viewModel.update { it.copy(hwid = value) } })
                CouiInputRow(stringResource(R.string.subscription_editor_timeout), draft.timeoutSeconds.toString(),
                    { value -> value.toIntOrNull()?.let { seconds -> viewModel.update { it.copy(timeoutSeconds = seconds) } } },
                    numeric = true)
                TextArea(state.headersText, viewModel::updateHeaders, stringResource(R.string.subscription_editor_headers))
            }
        }
        item {
            CouiButton(
                text = stringResource(if (state.saving) R.string.subscription_editor_validating_save else R.string.save_text),
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
                enabled = !state.saving,
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: SubscriptionHistoryEntry) {
    val nodeCount = entry.nodeCount?.let {
        stringResource(R.string.subscription_node_count, it)
    }
    val revision = entry.revision?.let {
        stringResource(R.string.subscription_revision, it)
    }
    CouiRow(
        title = when {
            !entry.ok -> stringResource(R.string.subscription_update_failed)
            entry.code == "subscription.not_modified" ->
                stringResource(R.string.subscription_not_modified)
            else -> stringResource(R.string.subscription_update_success)
        },
        summary = buildString {
            append(formatDate(entry.time))
            nodeCount?.let { append(" · ").append(it) }
            revision?.let { append(" · ").append(it) }
            if (!entry.ok) {
                entry.message.takeIf(String::isNotBlank)?.let {
                    append(" · ").append(it)
                }
            }
        },
        titleColor = if (entry.ok) null else Coui.colors.error,
    )
}

private data class SubscriptionStatus(val label: String, val color: Color)

private val activeProgressStages = setOf("download", "convert", "validate", "apply")

@Composable
private fun subscriptionStatus(subscription: CatalogGroupSummary): SubscriptionStatus {
    val expire = subscription.usage?.expire ?: 0L
    return when {
        subscription.progress?.stage?.let(activeProgressStages::contains) == true ->
            SubscriptionStatus(
                stringResource(R.string.subscription_update_in_progress),
                Coui.colors.accentText
            )

        subscription.lastError.isNotBlank() -> SubscriptionStatus(
            stringResource(R.string.subscription_update_failed),
            Coui.colors.error
        )
        expire > 0 && expire <= Instant.now().epochSecond -> SubscriptionStatus(
            stringResource(R.string.subscription_expired),
            Coui.colors.error
        )

        subscription.lastSuccessAt.isBlank() -> SubscriptionStatus(
            stringResource(R.string.subscription_never_updated),
            Coui.colors.labelSecondary
        )

        else -> SubscriptionStatus(stringResource(R.string.subscription_normal), Coui.colors.accentText)
    }
}

private fun formatDate(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value.ifBlank { "--" })

@Composable
private fun formatDuration(seconds: Long): String = when {
    seconds % 86400L == 0L ->
        stringResource(R.string.subscription_duration_days, seconds / 86400L)

    seconds % 3600L == 0L ->
        stringResource(R.string.subscription_duration_hours, seconds / 3600L)

    else -> stringResource(R.string.subscription_duration_minutes, seconds / 60L)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
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

/** 从文件导入时读出的内容；[name] 是文件名，只用于显示。 */
private data class ImportFile(val name: String, val content: String)

private const val NewGroupTarget = "+new"

/**
 * 「导入节点」面板：粘贴分享链接或配置内容（或已选的文件），选择导入到哪个本地分组，或新建分组。
 * 只粘贴了一个订阅地址时改为引导添加 URL 订阅。URL 订阅每次更新会整组替换，因此不能作为导入目标。
 */
@Composable
private fun ImportNodesPanel(
    groups: List<CatalogGroupSummary>,
    file: ImportFile?,
    onClearFile: () -> Unit,
    onAddUrl: (String) -> Unit,
    onImport: (content: String, groupId: String, newGroup: String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val localGroups = groups.filter { it.type == "local" }
    var target by rememberSaveable { mutableStateOf("default") }
    var newName by rememberSaveable { mutableStateOf("") }
    val url = text.trim().takeIf { file == null && '\n' !in it && ' ' !in it &&
        (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) }
    if (file != null) {
        CouiGroup(first = true) {
            CouiRow(stringResource(R.string.subscription_import_file_selected, file.name), summary = formatSize(file.content.length.toLong()))
            CouiActionRow(stringResource(R.string.subscription_import_file_clear), onClearFile)
        }
    } else {
        CouiGroup(first = true, footer = stringResource(if (url != null) R.string.subscription_import_url_hint else R.string.subscription_import_formats)) {
            TextArea(text, { text = it }, stringResource(R.string.subscription_import_content))
        }
    }
    if (url != null) {
        CouiButton(
            text = stringResource(R.string.subscription_import_as_url),
            onClick = { onAddUrl(url) },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        return
    }
    CouiGroup(title = stringResource(R.string.subscription_import_target), footer = stringResource(R.string.subscription_import_target_footer)) {
        if (localGroups.none { it.id == "default" }) {
            CouiRadioRow(stringResource(R.string.node_local_config), target == "default", { target = "default" })
        }
        localGroups.forEach { group ->
            CouiRadioRow(group.name, target == group.id, { target = group.id },
                summary = stringResource(R.string.local_config_summary, group.nodeCount))
        }
        CouiRadioRow(stringResource(R.string.subscription_import_new_group), target == NewGroupTarget, { target = NewGroupTarget })
        if (target == NewGroupTarget) {
            CouiInputRow(stringResource(R.string.subscription_import_group_name), newName, { newName = it })
        }
    }
    val content = file?.content ?: text
    val creating = target == NewGroupTarget
    CouiButton(
        text = stringResource(R.string.subscription_import_action),
        onClick = { onImport(content, if (creating) "" else target, if (creating) newName.trim() else "") },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        enabled = content.isNotBlank() && (!creating || newName.isNotBlank()),
    )
}

/** 文件选择器给出的显示名称；读不到时用路径最后一段。 */
private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

