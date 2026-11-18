package com.fanjv.netproxy.feature.catalog.presentation.subscriptions

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.ui.UiText
import com.fanjv.netproxy.core.ui.resolve
import com.fanjv.netproxy.core.ui.component.AppSnackbarHost
import com.fanjv.netproxy.core.ui.component.BackIconButton
import com.fanjv.netproxy.core.ui.component.BlurredBar
import com.fanjv.netproxy.core.ui.component.SnackbarNoticeEffect
import com.fanjv.netproxy.core.ui.component.StatusTag
import com.fanjv.netproxy.core.ui.component.rememberAppSnackbarHostState
import com.fanjv.netproxy.core.ui.component.rememberBlurBackdrop
import com.fanjv.netproxy.feature.catalog.model.CatalogGroupSummary
import com.fanjv.netproxy.feature.catalog.model.CatalogNode
import com.fanjv.netproxy.feature.catalog.model.SubscriptionHistoryEntry
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.Role
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import com.fanjv.netproxy.feature.chain.presentation.TextArea
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiBarButton
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiDockSpace
import dev.aiingjie.coui.CouiGlyph
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiLargeTitlePage
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRow
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
    val addText = stringResource(R.string.subscription_add_url)
    val updateAllText = stringResource(R.string.subscription_update_all)
    val cancelText = stringResource(R.string.common_cancel)
    val moreText = stringResource(R.string.more_actions)
    val useText = stringResource(R.string.subscription_use)
    val updateText = stringResource(R.string.common_update)
    val deleteText = stringResource(R.string.common_delete)
    val deleteTitle = stringResource(R.string.subscription_delete_title)
    val deleteMessage = stringResource(R.string.subscription_delete_message)
    val subscriptionDetailsText = stringResource(R.string.subscription_details)
    val localDetailsText = stringResource(R.string.local_config_details)

    fun showActions() = overlay.showDialog(
        title = null,
        buttons = buildList {
            add(CouiDialogButton(addText, CouiDialogButton.Kind.Primary) { navigator.push(Route.SubscriptionEdit("")) })
            if (hasSubscriptions && !busy) add(CouiDialogButton(updateAllText) { viewModel.updateAll() })
            add(CouiDialogButton(cancelText))
        },
    )

    fun showGroupActions(group: CatalogGroupSummary) {
        val isSubscription = group.type == "subscription"
        overlay.showDialog(
            title = group.name,
            buttons = buildList {
                if (!group.active) add(CouiDialogButton(useText, CouiDialogButton.Kind.Primary) { viewModel.activate(group.id) })
                if (isSubscription && !busy) add(CouiDialogButton(updateText) { viewModel.updateSubscription(group.id) })
                add(CouiDialogButton(if (isSubscription) subscriptionDetailsText else localDetailsText) {
                    navigator.push(Route.SubscriptionDetails(group.id))
                })
                if (isSubscription && !busy) add(CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) {
                    overlay.showDialog(deleteTitle, String.format(deleteMessage, group.name), listOf(
                        CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) { viewModel.remove(group.id) },
                        CouiDialogButton(cancelText),
                    ))
                })
                add(CouiDialogButton(cancelText))
            },
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
        actions = { CouiBarButton(CouiGlyph.More, moreText, onClick = ::showActions) },
    ) {
        if (updating) {
            item {
                CouiGroup(first = true) {
                    CouiRow(updatingText, leading = { CouiLoading() })
                }
            }
        }
        if (state.groups.isEmpty()) {
            item {
                CouiGroup(first = !updating) {
                    if (state.loading) CouiRow(stringResource(R.string.loading), leading = { CouiLoading() })
                    else CouiRow(stringResource(if (state.loadFailed) R.string.subscriptions_read_failed else R.string.subscription_empty))
                    CouiActionRow(addText, onClick = { navigator.push(Route.SubscriptionEdit("")) })
                }
            }
        } else {
            couiCardItems(state.groups, key = CatalogGroupSummary::id, title = listTitle, first = !updating) { group ->
                CouiRow(
                    title = group.name,
                    summary = groupSummary(group),
                    role = Role.Button,
                    onClick = { showGroupActions(group) },
                ) {
                    if (group.active) {
                        BasicText(
                            stringResource(R.string.subscription_active),
                            Modifier.padding(start = 12.dp, end = 12.dp),
                            style = Coui.type.rowValue.copy(color = Coui.colors.success),
                        )
                    }
                    CouiRadio(selected = group.active)
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
    val snackbarHostState = rememberAppSnackbarHostState()
    val backdrop = rememberBlurBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    var showDelete by remember { mutableStateOf(false) }
    var actionNode by remember { mutableStateOf<CatalogNode?>(null) }
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
    val noticeText = if (state.error !is UiText.Empty) state.error else state.notice
    SnackbarNoticeEffect(
        eventId = state.noticeId,
        message = noticeText.resolve(),
        isError = state.error !is UiText.Empty,
        hostState = snackbarHostState,
        onConsumed = viewModel::clearNotice
    )

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(
                        if (state.details?.group?.type == "local") {
                            R.string.local_config_details
                        } else {
                            R.string.subscription_details
                        }
                    ),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackIconButton(onClick = onBack) },
                    actions = {
                        if (state.details?.group?.type == "subscription") {
                            IconButton(onClick = { navigator.push(Route.SubscriptionEdit(id)) }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.common_edit)
                                )
                            }
                            IconButton(
                                enabled = state.operation.isEmpty(),
                                onClick = { viewModel.update(id) }
                            ) {
                                Icon(
                                    MiuixIcons.Refresh,
                                    contentDescription = stringResource(R.string.common_update)
                                )
                            }
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val details = state.details
        if (state.loading && details == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding), contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator()
            }
        } else if (details != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null
            ) {
                item {
                    SubscriptionSummaryCard(details.group) {
                        viewModel.activate(id)
                    }
                }
                item {
                    SectionLabel(stringResource(R.string.subscription_nodes_section, details.nodes.size))
                }
                items(details.nodes, key = { it.tag }) { node ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = node.tag,
                            summary = buildString {
                                append("${node.protocol.uppercase()} · ${node.server}:${node.port}")
                                state.latencies["${details.group.id}/${node.tag}"]?.let { latency ->
                                    append(" · ")
                                    append(if (latency.all(Char::isDigit)) "$latency ms" else latency)
                                }
                            },
                            onClick = { actionNode = node }
                        )
                    }
                }
                if (details.group.type == "subscription") {
                    item { SectionLabel(stringResource(R.string.subscription_history_section)) }
                    if (state.history.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.subscription_history_empty),
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        items(state.history.takeLast(20).reversed()) { history ->
                            HistoryRow(history)
                        }
                    }
                    item {
                        TextButton(
                            text = stringResource(R.string.common_delete),
                            onClick = { showDelete = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = showDelete,
        title = stringResource(R.string.subscription_delete_title),
        onDismissRequest = { showDelete = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.subscription_delete_details_message))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showDelete = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(R.string.common_delete),
                    onClick = {
                        showDelete = false
                        viewModel.remove(id, onBack)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val selectedNode = actionNode
    OverlayBottomSheet(
        show = selectedNode != null,
        title = selectedNode?.tag.orEmpty(),
        onDismissRequest = { actionNode = null }
    ) {
        if (selectedNode != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                BasicComponent(
                    title = stringResource(R.string.node_test_delay),
                    startAction = { DetailActionIcon(Icons.Rounded.NetworkPing) },
                    onClick = {
                        viewModel.testNode(id, selectedNode.tag)
                        actionNode = null
                    }
                )
                BasicComponent(
                    title = stringResource(R.string.node_edit),
                    startAction = { DetailActionIcon(Icons.Rounded.Edit) },
                    onClick = {
                        viewModel.editNode(id, selectedNode.tag)
                        actionNode = null
                    }
                )
                BasicComponent(
                    title = stringResource(R.string.node_export),
                    startAction = { DetailActionIcon(Icons.Rounded.Share) },
                    onClick = {
                        viewModel.exportNode(id, selectedNode.tag)
                        actionNode = null
                    }
                )
                BasicComponent(
                    title = stringResource(R.string.node_delete),
                    startAction = { DetailActionIcon(MiuixIcons.Delete) },
                    onClick = {
                        viewModel.removeNode(id, selectedNode.tag)
                        actionNode = null
                    }
                )
            }
        }
    }

    OverlayDialog(
        show = state.editingNodeRef.isNotBlank(),
        title = stringResource(R.string.node_edit),
        onDismissRequest = viewModel::dismissNodeEditor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = editedNodeLink,
                onValueChange = { editedNodeLink = it },
                label = stringResource(R.string.node_link_label),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )
            Text(
                text = if (state.details?.group?.type == "subscription") {
                    stringResource(R.string.subscription_edit_remote_note)
                } else {
                    stringResource(R.string.subscription_edit_local_note)
                },
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    modifier = Modifier.weight(1f),
                    enabled = state.operation != "edit",
                    onClick = viewModel::dismissNodeEditor
                )
                TextButton(
                    text = stringResource(R.string.save_text),
                    modifier = Modifier.weight(1f),
                    enabled = editedNodeLink.isNotBlank() && state.operation != "edit",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { viewModel.saveEditedNode(editedNodeLink) }
                )
            }
        }
    }

    SubscriptionUpdateSheet(state.operation)
}

@Composable
private fun DetailActionIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = imageVector,
        modifier = Modifier.size(22.dp),
        tint = colorScheme.onSurfaceVariantSummary,
        contentDescription = null
    )
}

/** 新增/编辑 URL 订阅。敏感字段只通过 root CLI 的私有契约读取。 */
@Composable
internal fun SubscriptionEditorScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: SubscriptionEditorViewModel = netProxyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val overlay = LocalCouiOverlay.current

    LaunchedEffect(id) { viewModel.load(id) }
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
private fun SubscriptionSummaryCard(
    subscription: CatalogGroupSummary,
    onActivate: () -> Unit
) {
    val isSubscription = subscription.type == "subscription"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subscription.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusTag(
                    label = if (subscription.active) {
                        stringResource(R.string.subscription_active)
                    } else {
                        subscriptionStatus(subscription).label
                    },
                    backgroundColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = if (isSubscription) {
                    stringResource(
                        R.string.subscription_summary,
                        subscription.nodeCount,
                        formatDuration(subscription.updateInterval)
                    )
                } else {
                    stringResource(R.string.local_config_summary, subscription.nodeCount)
                },
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp
            )
            subscription.profileTitle.takeIf(String::isNotBlank)?.let {
                Text(it, color = colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
            }
            if (!subscription.active) {
                TextButton(
                    text = stringResource(
                        if (isSubscription) R.string.subscription_set_active
                        else R.string.local_config_set_active
                    ),
                    onClick = onActivate,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
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
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    SmallTitle(
        text = text,
        insideMargin = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 0.dp)
    )
}

@Composable
private fun SubscriptionUpdateSheet(operation: String) {
    val updateAll = operation == "update-all"
    OverlayBottomSheet(
        show = operation == "update" || updateAll,
        title = stringResource(
            if (updateAll) R.string.subscription_update_all else R.string.common_update
        ),
        onDismissRequest = {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfiniteProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (updateAll) {
                    stringResource(R.string.subscription_update_sheet_all)
                } else {
                    stringResource(R.string.subscription_update_sheet_single)
                },
                color = colorScheme.onSurface
            )
        }
    }
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
                colorScheme.primary
            )

        subscription.lastError.isNotBlank() -> SubscriptionStatus(
            stringResource(R.string.subscription_update_failed),
            colorScheme.error
        )
        expire > 0 && expire <= Instant.now().epochSecond -> SubscriptionStatus(
            stringResource(R.string.subscription_expired),
            colorScheme.error
        )

        subscription.lastSuccessAt.isBlank() -> SubscriptionStatus(
            stringResource(R.string.subscription_never_updated),
            colorScheme.onSurfaceVariantSummary
        )

        else -> SubscriptionStatus(stringResource(R.string.subscription_normal), colorScheme.primary)
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
