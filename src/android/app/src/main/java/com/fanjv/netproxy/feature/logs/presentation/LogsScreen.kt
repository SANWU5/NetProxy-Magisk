package com.fanjv.netproxy.feature.logs.presentation

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiBarButton
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiDimens
import dev.aiingjie.coui.CouiGlyph
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiPressable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.fanjv.netproxy.core.ui.component.CardItem
import com.fanjv.netproxy.core.ui.component.groupedCardItems
import com.fanjv.netproxy.core.ui.theme.isInDarkTheme
import com.fanjv.netproxy.feature.logs.data.LogItem
import com.fanjv.netproxy.feature.logs.data.LogLevel
import com.fanjv.netproxy.feature.logs.data.LogType
import com.fanjv.netproxy.feature.logs.data.OutboundFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 日志页：查看并导出服务与内核日志。 */
@Composable
internal fun LogsScreen(
    viewModel: LogsViewModel = com.fanjv.netproxy.core.di.netProxyViewModel(),
    onBack: () -> Unit
) {
    val logsState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlay = LocalCouiOverlay.current
    val listState = rememberLazyListState()
    val sendLogTitle = stringResource(R.string.send_log)
    val saveLogTitle = stringResource(R.string.save_log)
    val logSavedMessage = stringResource(R.string.log_saved)
    val clearSuccessMessage = stringResource(R.string.clear_logs_success)
    val clearFailedMessage = stringResource(R.string.clear_logs_failed)
    val saveLocationFailed = stringResource(R.string.log_save_location_failed)
    val saveEmptyFailed = stringResource(R.string.log_save_empty_failed)
    val saveFailed = stringResource(R.string.log_save_failed)
    val shareFailed = stringResource(R.string.log_share_failed)
    val exportFailed = stringResource(R.string.log_export_failed)
    val shareFailedDetail = stringResource(R.string.log_share_failed_detail)
    val scrollTopText = stringResource(R.string.scroll_to_top)
    val scrollBottomText = stringResource(R.string.scroll_to_bottom)
    val clearText = stringResource(R.string.clear_logs_now)
    val cancelText = stringResource(android.R.string.cancel)

    fun showMessage(message: String) = overlay.showSnack(message)

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isCardView by remember { mutableStateOf(true) }

    val currentType = remember(selectedTabIndex) {
        when (selectedTabIndex) {
            0 -> LogType.SERVICE
            else -> LogType.KERNEL
        }
    }

    LaunchedEffect(currentType) {
        viewModel.refresh(currentType)
    }

    val content = logsState[currentType]
    val logs = content.entries
    LaunchedEffect(currentType, content.error) {
        if (content.error.isNotEmpty() && logs.isNotEmpty()) showMessage(content.error)
    }

    // 导出保存启动器
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val logFile = viewModel.createReport()
                    try {
                        val descriptor = context.contentResolver.openFileDescriptor(uri, "rwt")
                            ?: error(saveLocationFailed)
                        val copiedBytes =
                            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                                logFile.inputStream().use { input ->
                                    input.copyTo(output).also { output.flush() }
                                }
                            }
                        check(copiedBytes == logFile.length() && copiedBytes > 0L) {
                            saveEmptyFailed
                        }
                    } finally {
                        logFile.delete()
                    }
                }
                showMessage(logSavedMessage)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage(e.message ?: saveFailed)
            }
        }
    }

    fun sendLog() {
        scope.launch {
            try {
                val logFile = withContext(Dispatchers.IO) {
                    viewModel.createReport()
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    logFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gzip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(sendLogTitle, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, sendLogTitle))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage(e.message ?: shareFailedDetail.format(shareFailed))
            }
        }
    }

    fun saveLog() {
        try {
            val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
                .format(java.time.LocalDateTime.now())
            exportLauncher.launch("NetProxy_Logs_$timestamp.tar.gz")
        } catch (e: Exception) {
            showMessage(exportFailed.format(e.message ?: saveFailed))
        }
    }

    val viewText = stringResource(if (isCardView) R.string.raw_view else R.string.card_view)
    fun showActions() = overlay.showDialog(title = null, buttons = buildList {
        add(CouiDialogButton(sendLogTitle) { sendLog() })
        add(CouiDialogButton(saveLogTitle) { saveLog() })
        if (!content.loading && (logs.isNotEmpty() || content.error.isEmpty())) {
            add(CouiDialogButton(viewText) { isCardView = !isCardView })
            add(CouiDialogButton(scrollTopText) { scope.launch { listState.animateScrollToItem(0) } })
            add(CouiDialogButton(scrollBottomText) {
                scope.launch { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex) }
            })
            add(CouiDialogButton(clearText, CouiDialogButton.Kind.Danger) {
                viewModel.clear(currentType) { success ->
                    showMessage(if (success) clearSuccessMessage else clearFailedMessage)
                }
            })
        }
        add(CouiDialogButton(cancelText))
    })

    val tabs = listOf(
        stringResource(R.string.service_logs),
        stringResource(R.string.kernel_logs)
    )
    CouiSubPage(
        title = stringResource(R.string.logs),
        onBack = onBack,
        listState = listState,
        actions = { CouiBarButton(CouiGlyph.More, stringResource(R.string.more_options), ::showActions) },
        header = { LogTypeTabs(tabs, selectedTabIndex) { selectedTabIndex = it } },
    ) {
        if (logs.isEmpty()) {
            item {
                CouiGroup(first = true) {
                    if (content.loading) CouiRow(stringResource(R.string.loading), leading = { CouiLoading() })
                    else CouiRow(
                        stringResource(if (content.error.isNotEmpty()) R.string.logs_read_failed else R.string.no_logs),
                        summary = content.error.ifEmpty { null },
                    )
                }
            }
            return@CouiSubPage
        }
        if (isCardView) {
            items(logs) { item ->
                Box(Modifier.padding(horizontal = CouiDimens.cardMargin)) {
                    LogItemCard(item = item, type = currentType)
                }
            }
        } else {
            rawLogItems(logs = logs)
        }
    }
}

/** 服务日志与内核日志的分段切换。COUI 组件库没有分段控件，按搜索框的容器色与圆角绘制。 */
@Composable
private fun LogTypeTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = Coui.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(38.dp)
            .clip(RoundedCornerShape(19.dp)).background(colors.container8).padding(3.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .background(if (active) colors.card else Color.Transparent)
                    .couiPressable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label,
                    style = Coui.type.rowSummary.copy(
                        color = if (active) colors.label else colors.labelSecondary,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
fun LogItemCard(item: LogItem, type: LogType) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogLevelBadge(level = item.level)
                    if (item.tag.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        if (type == LogType.SERVICE) {
                            NativeComponentBadge(component = item.tag)
                        } else {
                            Text(
                                text = item.tag,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
                if (item.timestamp.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.timestamp,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }

            if (type == LogType.SERVICE && (item.event.isNotEmpty() || item.result.isNotEmpty())) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.event.isNotEmpty()) {
                        NativeEventBadge(event = item.event)
                    }
                    if (item.result.isNotEmpty()) {
                        NativeResultBadge(result = item.result)
                    }
                }
            }

            if (type == LogType.SERVICE && item.errorCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                NativeErrorCodeBadge(errorCode = item.errorCode)
            }

            if (item.connectionId != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LogBadge(text = "#${item.connectionId}", tone = LogBadgeTone.CONNECTION)
                    if (item.latency != null) {
                        LatencyBadge(latency = item.latency, latencyMs = item.latencyMs)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (type == LogType.KERNEL && item.outboundFlow != null) {
                OutboundFlowView(flow = item.outboundFlow)
            } else {
                Text(
                    text = item.message,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun NativeComponentBadge(component: String) {
    val label = when (component) {
        "service" -> stringResource(R.string.log_component_service)
        "worker" -> stringResource(R.string.log_component_worker)
        "subscription" -> stringResource(R.string.subscriptions)
        "node" -> stringResource(R.string.nodes)
        "mode" -> stringResource(R.string.log_component_mode)
        "app" -> stringResource(R.string.apps)
        "config" -> stringResource(R.string.log_component_config)
        "network" -> stringResource(R.string.log_component_network)
        "module" -> stringResource(R.string.log_component_module)
        else -> component
    }
    LogBadge(text = label, tone = LogBadgeTone.COMPONENT)
}

@Composable
fun NativeEventBadge(event: String) {
    val label = when (event) {
        "service.start" -> stringResource(R.string.log_event_start)
        "service.stop" -> stringResource(R.string.log_event_stop)
        "service.reload" -> stringResource(R.string.log_event_reload)
        "worker.start", "worker.run" -> "Worker"
        "network.watch" -> stringResource(R.string.log_event_network_watch)
        "network.read" -> stringResource(R.string.log_event_network_read)
        "network.policy" -> stringResource(R.string.log_event_network_policy)
        "subscription.add" -> stringResource(R.string.log_event_subscription_add)
        "subscription.edit" -> stringResource(R.string.subscription_editor_edit_title)
        "subscription.update" -> stringResource(R.string.log_event_subscription_update)
        "subscription.update-all" -> stringResource(R.string.log_event_subscription_update_all)
        "subscription.remove" -> stringResource(R.string.log_event_subscription_remove)
        "subscription.runtime-sync" -> stringResource(R.string.log_event_runtime_sync)
        "subscription.effect" -> stringResource(R.string.log_event_effect)
        "subscription.schedule" -> stringResource(R.string.log_event_schedule)
        "node.append" -> stringResource(R.string.node_add)
        "node.import" -> stringResource(R.string.log_event_node_import)
        "node.edit" -> stringResource(R.string.node_edit)
        "node.remove" -> stringResource(R.string.node_delete)
        "node.select", "node.selection" -> stringResource(R.string.log_event_node_select)
        "mode.apply" -> stringResource(R.string.log_event_mode_apply)
        "app-policy.update" -> stringResource(R.string.log_event_app_policy)
        "config.apply" -> stringResource(R.string.log_event_config_apply)
        "config.validate" -> stringResource(R.string.log_event_config_validate)
        "module.boot" -> stringResource(R.string.log_event_boot)
        "module.update" -> stringResource(R.string.log_event_module_update)
        else -> event
    }
    LogBadge(text = label, tone = LogBadgeTone.NEUTRAL)
}

@Composable
fun NativeResultBadge(result: String) {
    val (label, tone) = when (result) {
        "success", "recovered" -> stringResource(R.string.log_result_success) to LogBadgeTone.SUCCESS
        "failed", "forced" -> stringResource(R.string.log_result_failed) to LogBadgeTone.ERROR
        "persisted" -> stringResource(R.string.log_result_persisted) to LogBadgeTone.WARNING
        "fallback" -> stringResource(R.string.log_result_fallback) to LogBadgeTone.WARNING
        "started" -> stringResource(R.string.log_result_started) to LogBadgeTone.INFO
        "already-running" -> stringResource(R.string.log_result_running) to LogBadgeTone.INFO
        "stopped" -> stringResource(R.string.log_result_stopped) to LogBadgeTone.NEUTRAL
        "skipped" -> stringResource(R.string.log_result_skipped) to LogBadgeTone.NEUTRAL
        else -> result to LogBadgeTone.NEUTRAL
    }
    LogBadge(text = label, tone = tone)
}

private enum class LogBadgeTone {
    INFO, SUCCESS, WARNING, ERROR, NEUTRAL, COMPONENT, CONNECTION, PRIMARY
}

@Composable
private fun logBadgeColors(tone: LogBadgeTone): Pair<Color, Color> {
    val isDark = isInDarkTheme()
    return when (tone) {
        LogBadgeTone.INFO -> if (isDark) {
            Color(0xFF0D47A1).copy(alpha = 0.3f) to Color(0xFF64B5F6)
        } else {
            Color(0xFFE3F2FD) to Color(0xFF1976D2)
        }
        LogBadgeTone.SUCCESS -> if (isDark) {
            Color(0xFF1B5E20).copy(alpha = 0.3f) to Color(0xFF81C784)
        } else {
            Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        }
        LogBadgeTone.WARNING -> if (isDark) {
            Color(0xFFE65100).copy(alpha = 0.3f) to Color(0xFFFFB74D)
        } else {
            Color(0xFFFFF3E0) to Color(0xFFE65100)
        }
        LogBadgeTone.ERROR -> if (isDark) {
            Color(0xFFB71C1C).copy(alpha = 0.3f) to Color(0xFFE57373)
        } else {
            Color(0xFFFFEBEE) to Color(0xFFC62828)
        }
        LogBadgeTone.NEUTRAL -> if (isDark) {
            Color(0xFF37474F).copy(alpha = 0.3f) to Color(0xFFB0BEC5)
        } else {
            Color(0xFFF5F5F5) to Color(0xFF616161)
        }
        LogBadgeTone.COMPONENT -> if (isDark) {
            Color(0xFF283593).copy(alpha = 0.3f) to Color(0xFF9FA8DA)
        } else {
            Color(0xFFE8EAF6) to Color(0xFF3949AB)
        }
        LogBadgeTone.CONNECTION -> if (isDark) {
            Color(0xFF4A148C).copy(alpha = 0.3f) to Color(0xFFBA68C8)
        } else {
            Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
        }
        LogBadgeTone.PRIMARY -> MiuixTheme.colorScheme.primary.let { color ->
            color.copy(alpha = if (isDark) 0.3f else 0.1f) to color
        }
    }
}

@Composable
private fun LogBadge(text: String, tone: LogBadgeTone) {
    val (backgroundColor, textColor) = logBadgeColors(tone)
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NativeErrorCodeBadge(errorCode: String) {
    LogBadge(text = errorCode, tone = LogBadgeTone.ERROR)
}

@Composable
private fun LatencyBadge(latency: String, latencyMs: Int?) {
    val tone = when {
        latencyMs == null -> LogBadgeTone.NEUTRAL
        latencyMs < 800 -> LogBadgeTone.SUCCESS
        latencyMs < 1500 -> LogBadgeTone.WARNING
        else -> LogBadgeTone.ERROR
    }
    LogBadge(text = latency, tone = tone)
}

@Composable
fun LogLevelBadge(level: LogLevel) {
    val tone = when (level) {
        LogLevel.INFO -> LogBadgeTone.INFO
        LogLevel.WARN -> LogBadgeTone.WARNING
        LogLevel.ERROR -> LogBadgeTone.ERROR
        LogLevel.DEBUG -> LogBadgeTone.SUCCESS
        LogLevel.UNKNOWN -> LogBadgeTone.NEUTRAL
    }
    LogBadge(text = level.name, tone = tone)
}

@Composable
fun OutboundFlowView(flow: OutboundFlow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MiuixTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.source_label),
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = flow.source,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .size(16.dp)
                    .padding(horizontal = 2.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = stringResource(R.string.destination_label),
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = flow.target,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val outboundTone = when (flow.outbound.lowercase()) {
            "direct" -> LogBadgeTone.SUCCESS
            "block", "reject" -> LogBadgeTone.ERROR
            else -> LogBadgeTone.PRIMARY
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            LogBadge(text = flow.outbound.uppercase(), tone = outboundTone)
        }
    }
}

private fun LazyListScope.rawLogItems(logs: List<LogItem>) {
    groupedCardItems(
        keyPrefix = "raw-log",
        outerHorizontalPadding = CouiDimens.cardMargin,
        items = logs.mapIndexed { index, item ->
            CardItem(key = index.toString()) {
                Text(
                    text = item.rawLine,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = if (index == 0) 13.dp else 1.dp,
                            bottom = if (index == logs.lastIndex) 13.dp else 1.dp,
                        )
                )
            }
        }
    )
}
