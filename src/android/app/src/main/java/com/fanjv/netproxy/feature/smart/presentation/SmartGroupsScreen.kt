package com.fanjv.netproxy.feature.smart.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.CouiBarMenuButton
import com.fanjv.netproxy.core.ui.component.MenuAction
import com.fanjv.netproxy.feature.smart.model.SmartGroup
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInfoRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems
import dev.aiingjie.coui.couiPressable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 智能分组：手动按地区整理全部节点，或管理自建分组。点按分组进入组内选择自动选择或某个节点；
 * 点按右侧圆圈直接使用分组，沿用组内的选择，从未在组内选择过节点时使用自动选择。
 */
@Composable
internal fun SmartGroupsScreen(
    onBack: () -> Unit,
    viewModel: SmartGroupsViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val overlay = LocalCouiOverlay.current
    val navigator = LocalNavigator.current
    val smart = state.smart
    val idle = state.busy.isEmpty()
    // 地区分组名称按界面语言生成；中文界面使用中文地区名。
    val english = !LocalConfiguration.current.locales[0].language.startsWith("zh")

    LifecycleResumeEffect(Unit) {
        viewModel.setVisible(true)
        onPauseOrDispose { viewModel.setVisible(false) }
    }

    val generatedText = stringResource(R.string.smart_generated)
    val selectedText = stringResource(R.string.smart_selected)
    LaunchedEffect(events) {
        if (events.isEmpty()) return@LaunchedEffect
        events.forEach { event ->
            when (event) {
                is SmartEvent.Failed -> overlay.showSnack(event.message)
                is SmartEvent.Generated -> overlay.showSnack(
                    String.format(generatedText, event.summary.regions, event.summary.nodes, event.summary.informational)
                )
                SmartEvent.Selected -> overlay.showSnack(selectedText)
                SmartEvent.Saved -> Unit
            }
        }
        viewModel.consumeEvents()
    }

    val moreText = stringResource(R.string.more_actions)
    val regenerateText = stringResource(R.string.smart_regenerate)
    val regenerateMessage = stringResource(R.string.smart_regenerate_message)
    val generateText = stringResource(R.string.smart_generate_now)
    val newCustomText = stringResource(R.string.smart_new_custom)
    val testAllText = stringResource(R.string.smart_test_all)
    val useText = stringResource(R.string.smart_use)
    val cancelText = stringResource(android.R.string.cancel)
    val regionTitle = stringResource(R.string.smart_region_groups)
    val customTitle = stringResource(R.string.smart_custom_groups)
    val availableText = stringResource(R.string.smart_available)
    val emptyText = stringResource(R.string.smart_no_available)
    val editedText = stringResource(R.string.smart_edited)
    val autoText = stringResource(R.string.smart_auto)
    val autoNowText = stringResource(R.string.smart_mode_auto)
    val manualText = stringResource(R.string.smart_mode_manual)

    fun generate() {
        if (smart.groups.none { !it.custom }) {
            viewModel.generate(english)
            return
        }
        overlay.showDialog(regenerateText, regenerateMessage, listOf(
            CouiDialogButton(regenerateText, CouiDialogButton.Kind.Primary) { viewModel.generate(english) },
            CouiDialogButton(cancelText),
        ))
    }

    /** 分组内的选择：手动选择的节点，或自动选择（服务运行时附带它当前使用的节点）。 */
    fun mode(group: SmartGroup): String {
        val manual = group.manual
        return when {
            manual != null -> String.format(manualText, manual.node)
            smart.running && group.selectedNode.isNotEmpty() -> String.format(autoNowText, group.selectedNode)
            else -> autoText
        }
    }

    fun summary(group: SmartGroup): String = buildList {
        add(if (group.available > 0) String.format(availableText, group.available) else emptyText)
        if (group.edited) add(editedText)
        add(mode(group))
    }.joinToString(" · ")

    val inUse = smart.groups.firstOrNull { it.id == smart.active }
    val inUseTitle = stringResource(R.string.smart_in_use)
    val inUseValue = inUse?.name ?: stringResource(R.string.smart_not_in_use)
    val inUseSummary = inUse?.let(::mode)
    val introTitle = stringResource(R.string.smart_intro_title)
    val introSummary = stringResource(R.string.smart_intro_summary)
    val footer = stringResource(R.string.smart_groups_footer)
    val loadingText = stringResource(R.string.loading)
    val retryText = stringResource(R.string.smart_retry)

    CouiSubPage(
        title = stringResource(R.string.smart_groups),
        onBack = onBack,
        actions = {
            if (!idle) CouiLoading()
            CouiBarMenuButton(moreText, listOf(
                MenuAction(if (smart.groups.any { !it.custom }) regenerateText else generateText, enabled = idle, onClick = ::generate),
                MenuAction(newCustomText, enabled = idle) { navigator.push(Route.SmartGroupEdit("")) },
                MenuAction(testAllText, enabled = idle && smart.running && smart.groups.isNotEmpty()) { viewModel.test("") },
            ))
        },
    ) {
        if (!state.loaded) {
            item {
                CouiGroup(first = true, footer = state.error.ifEmpty { null }) {
                    if (state.loading) CouiRow(loadingText, leading = { CouiLoading() })
                    else CouiActionRow(retryText, onClick = { viewModel.refresh() })
                }
            }
            return@CouiSubPage
        }
        if (smart.groups.isEmpty()) {
            item {
                CouiGroup(first = true, footer = footer) {
                    CouiRow(introTitle, summary = introSummary)
                    CouiActionRow(generateText, onClick = ::generate, enabled = idle)
                    CouiActionRow(newCustomText, onClick = { navigator.push(Route.SmartGroupEdit("")) }, enabled = idle)
                }
            }
            return@CouiSubPage
        }
        item {
            CouiGroup(first = true) {
                CouiInfoRow(inUseTitle, inUseValue, summary = inUseSummary,
                    onClick = inUse?.let { group -> { navigator.push(Route.SmartGroupDetail(group.id)) } })
            }
        }
        val regions = smart.groups.filter { !it.custom }
        val customs = smart.groups.filter { it.custom }
        if (regions.isNotEmpty()) {
            couiCardItems(regions, key = { it.id }, title = regionTitle) { group ->
                SmartGroupRow(group, summary(group), smart.active == group.id, smart.running, idle, useText,
                    onOpen = { navigator.push(Route.SmartGroupDetail(group.id)) }, onSelect = { viewModel.select(group.id) })
            }
        }
        if (customs.isNotEmpty()) {
            couiCardItems(customs, key = { it.id }, title = customTitle) { group ->
                SmartGroupRow(group, summary(group), smart.active == group.id, smart.running, idle, useText,
                    onOpen = { navigator.push(Route.SmartGroupDetail(group.id)) }, onSelect = { viewModel.select(group.id) })
            }
        }
        item {
            CouiGroup(footer = footer) {
                CouiActionRow(regenerateText, onClick = ::generate, enabled = idle)
                CouiActionRow(newCustomText, onClick = { navigator.push(Route.SmartGroupEdit("")) }, enabled = idle)
            }
        }
    }
}

/** 点按行进入组内选择；点按右侧圆圈直接使用这个分组。 */
@Composable
private fun SmartGroupRow(
    group: SmartGroup,
    summary: String,
    active: Boolean,
    running: Boolean,
    idle: Boolean,
    useText: String,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    val usable = idle && group.available > 0
    CouiRow(
        title = group.name,
        summary = summary,
        enabled = idle,
        role = Role.Button,
        onClick = onOpen,
    ) {
        if (running && group.delay != 0) DelayText(group.delay)
        Box(
            Modifier.padding(start = 4.dp).size(44.dp)
                .couiPressable(enabled = usable, shape = CircleShape, role = Role.RadioButton) { if (!active) onSelect() }
                .semantics { contentDescription = useText },
            contentAlignment = Alignment.Center,
        ) { CouiRadio(selected = active, enabled = group.available > 0) }
    }
}

@Composable
internal fun DelayText(delay: Int, modifier: Modifier = Modifier) {
    val colors = Coui.colors
    val failed = delay < 0
    val text = if (failed) stringResource(R.string.chain_delay_failed) else "$delay ms"
    val color = when {
        failed -> colors.error
        delay <= 300 -> colors.success
        delay <= 800 -> colors.warning
        else -> colors.error
    }
    BasicText(text, modifier.padding(start = 12.dp), style = Coui.type.rowValue.copy(color = color))
}
