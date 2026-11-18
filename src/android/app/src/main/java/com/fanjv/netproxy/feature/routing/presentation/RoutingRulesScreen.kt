package com.fanjv.netproxy.feature.routing.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.feature.routing.model.LocalRuleSet
import com.fanjv.netproxy.feature.routing.model.RuleField
import com.fanjv.netproxy.feature.routing.model.RuleInputError
import com.fanjv.netproxy.feature.routing.model.simpleRule
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiDialog
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiPanel
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.couiCardItems
import com.fanjv.netproxy.feature.chain.presentation.TextArea

@Composable
internal fun RoutingRulesScreen(
    onBack: () -> Unit,
    viewModel: RoutingRulesViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rules = remember(state.files) { state.rules }
    val navigator = LocalNavigator.current
    val rawEdit: (LocalRuleSet) -> Unit = { target -> viewModel.dismiss(); navigator.push(Route.JsonEdit(target.target)) }
    val reload: () -> Unit = { viewModel.refresh(discardDraft = true) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    BackHandler(state.isSaving) { }
    val matchesTitle = stringResource(R.string.routing_matches)

    CouiSubPage(title = stringResource(R.string.routing_rules), onBack = { if (!state.isSaving) onBack() }) {
        if (!state.hasDocuments && (state.isLoading || state.files.values.none { it.requiresReload })) {
            item {
                CouiGroup(first = true) { CouiRow(stringResource(R.string.loading), leading = { CouiLoading() }) }
            }
            return@CouiSubPage
        }
        val errors = LocalRuleSet.entries.filter { !state.hasDocuments || state.showPageError(it) }
        errors.forEachIndexed { index, target ->
            item("error:${target.tag}") { RuleFileError(target, state, reload, rawEdit, first = index == 0) }
        }
        if (!state.hasDocuments) return@CouiSubPage
        if (state.isEmpty) item("empty") {
            CouiGroup(first = errors.isEmpty()) {
                CouiRow(stringResource(R.string.routing_empty), summary = stringResource(R.string.routing_empty_hint))
            }
        } else couiCardItems(rules, key = { "${it.target.tag}:${it.index}" }, title = matchesTitle, first = errors.isEmpty()) { identity ->
            val rule = state.files.getValue(identity.target).document!!.rules[identity.index]
            val simple = remember(rule) { rule.simpleRule() }
            CouiJumpRow(
                title = simple?.values?.first()?.take(100) ?: stringResource(R.string.routing_advanced),
                onClick = { viewModel.edit(identity) },
                summary = stringResource(identity.target.title()) + " · " + (simple?.let {
                    stringResource(it.field.title()) + if (it.values.size > 1) " · " + stringResource(R.string.routing_values_count, it.values.size) else ""
                } ?: stringResource(R.string.routing_advanced_hint)),
                enabled = state.editable(identity.target),
            )
        }
        item("add") {
            CouiGroup {
                CouiActionRow(stringResource(R.string.routing_add), viewModel::add, enabled = state.canAdd)
            }
        }
    }

    var retainedDraft by remember { mutableStateOf<RuleDraft?>(null) }
    val draft = state.draft ?: retainedDraft
    SideEffect { if (state.draft != null) retainedDraft = state.draft }
    CouiPanel(
        visible = state.draft != null,
        title = stringResource(if (draft?.index == null) R.string.routing_add else R.string.routing_edit),
        onDismissRequest = { if (!state.isSaving) viewModel.dismiss() },
    ) {
        if (draft != null) RuleDraftPanel(draft, state, viewModel, rawEdit)
    }
    val deleteTarget = state.deleting?.target
    val deleteFile = deleteTarget?.let { state.files.getValue(it) }
    val deleteBlocked = deleteFile != null && (deleteFile.error.isNotEmpty() || deleteFile.requiresReload)
    val cancelText = stringResource(android.R.string.cancel)
    val deleteText = stringResource(if (state.isSaving) R.string.loading else R.string.common_delete)
    val reloadText = stringResource(R.string.routing_reload_draft)
    CouiDialog(
        visible = state.deleting != null,
        title = stringResource(R.string.routing_delete),
        message = listOfNotNull(
            deleteTarget?.let { stringResource(it.title()) },
            if (deleteBlocked) deleteFile.errorText() else stringResource(R.string.routing_delete_hint),
        ).joinToString("\n"),
        onDismissRequest = { if (!state.isSaving) viewModel.dismiss() },
        cancelable = !state.isSaving,
        buttons = buildList {
            if (deleteBlocked && deleteFile.requiresReload) add(CouiDialogButton(reloadText) { viewModel.refresh(discardDraft = true) })
            else if (!deleteBlocked) add(CouiDialogButton(deleteText, CouiDialogButton.Kind.Danger) {
                if (!state.isSaving && deleteTarget?.let(state::editable) == true) viewModel.delete()
            })
            add(CouiDialogButton(cancelText) { if (!state.isSaving) viewModel.dismiss() })
        },
    )
}

@Composable
private fun RuleFileError(
    target: LocalRuleSet, state: RoutingRulesState, reload: () -> Unit, rawEdit: (LocalRuleSet) -> Unit, first: Boolean,
) {
    val file = state.files.getValue(target)
    val idle = !state.isLoading && !state.isSaving
    CouiGroup(title = stringResource(target.title()), first = first) {
        CouiRow(file.errorText(), titleColor = Coui.colors.error)
        if (file.requiresReload) CouiActionRow(stringResource(
            if (state.draft != null || state.deleting != null) R.string.routing_reload_draft else R.string.inbound_reload),
            reload, enabled = idle)
        if (file.invalidDocument) CouiActionRow(stringResource(R.string.routing_raw), { rawEdit(target) }, enabled = idle)
    }
}

@Composable
private fun RuleFileState.errorText(): String {
    val message = when {
        invalidDocument -> stringResource(R.string.routing_invalid_document)
        error.isNotEmpty() -> error
        else -> stringResource(R.string.routing_read_failed)
    }
    return listOf(message, error).filter(String::isNotBlank).distinct().joinToString("\n")
}

/** 新增或编辑一条规则的底部面板内容；草稿与校验结果都来自 ViewModel。 */
@Composable
private fun RuleDraftPanel(
    draft: RuleDraft, state: RoutingRulesState, viewModel: RoutingRulesViewModel, rawEdit: (LocalRuleSet) -> Unit,
) {
    val file = state.files.getValue(draft.target)
    val idle = !state.isLoading && !state.isSaving
    val field = draft.field
    var text by remember(draft.target, draft.index) { mutableStateOf(draft.text) }
    CouiGroup(first = true) {
        if (draft.index == null) CouiMenuRow(
            stringResource(R.string.routing_type), LocalRuleSet.entries.map { stringResource(it.title()) }, draft.target.ordinal,
            { viewModel.selectTarget(LocalRuleSet.entries[it]) }, enabled = !state.isSaving,
        ) else CouiRow(stringResource(R.string.routing_type), summary = stringResource(draft.target.title()))
        if (field != null) CouiMenuRow(
            stringResource(R.string.routing_match), RuleField.entries.map { stringResource(it.title()) }, field.ordinal,
            { viewModel.updateDraft(RuleField.entries[it], text) }, enabled = !state.isSaving,
        )
    }
    if (field != null) CouiGroup(title = stringResource(R.string.routing_values), footer = stringResource(field.hint())) {
        TextArea(
            text, { text = it; viewModel.updateDraft(field, it) }, "",
            KeyboardOptions(autoCorrectEnabled = false,
                keyboardType = if (field == RuleField.DomainKeyword) KeyboardType.Text else KeyboardType.Ascii),
        )
    } else CouiGroup { CouiRow(stringResource(R.string.routing_advanced_hint)) }
    state.inputError?.let { CouiGroup { CouiRow(stringResource(it.message()), titleColor = Coui.colors.error) } }
    if (file.error.isNotEmpty() || file.requiresReload || file.invalidDocument) CouiGroup {
        CouiRow(file.errorText(), titleColor = Coui.colors.error)
        if (file.requiresReload) CouiActionRow(stringResource(R.string.routing_reload_draft), { viewModel.refresh(discardDraft = true) }, enabled = idle)
        if (file.invalidDocument) CouiActionRow(stringResource(R.string.routing_raw), { rawEdit(draft.target) }, enabled = idle)
    }
    CouiButton(
        text = stringResource(if (state.isSaving) R.string.loading else if (field != null) R.string.save_text else R.string.routing_raw),
        onClick = { if (field != null) { viewModel.updateDraft(field, text); viewModel.save() } else rawEdit(draft.target) },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        enabled = !state.isSaving && state.editable(draft.target),
    )
    draft.index?.let { index ->
        CouiGroup {
            CouiActionRow(stringResource(R.string.common_delete), { viewModel.requestDelete(RuleIdentity(draft.target, index)) },
                danger = true, enabled = !state.isSaving && state.editable(draft.target))
        }
    }
}

private fun LocalRuleSet.title(): Int = when (this) {
    LocalRuleSet.Proxy -> R.string.routing_proxy
    LocalRuleSet.Direct -> R.string.routing_direct
    LocalRuleSet.Block -> R.string.routing_block
}

private fun RuleField.title(): Int = when (this) {
    RuleField.DomainSuffix -> R.string.routing_domain_suffix
    RuleField.Domain -> R.string.routing_domain
    RuleField.DomainKeyword -> R.string.routing_domain_keyword
    RuleField.DomainRegex -> R.string.routing_domain_regex
    RuleField.IpCidr -> R.string.routing_ip_cidr
    RuleField.Port -> R.string.routing_port
    RuleField.PortRange -> R.string.routing_port_range
    RuleField.PackageName -> R.string.routing_package_name
}

private fun RuleField.hint(): Int = when (this) {
    RuleField.DomainSuffix -> R.string.routing_domain_suffix_hint
    RuleField.Domain -> R.string.routing_domain_hint
    RuleField.DomainKeyword -> R.string.routing_domain_keyword_hint
    RuleField.DomainRegex -> R.string.routing_domain_regex_hint
    RuleField.IpCidr -> R.string.routing_ip_cidr_hint
    RuleField.Port -> R.string.routing_port_hint
    RuleField.PortRange -> R.string.routing_port_range_hint
    RuleField.PackageName -> R.string.routing_package_name_hint
}

private fun RuleInputError.message(): Int = when (this) {
    RuleInputError.Empty -> R.string.routing_error_empty
    RuleInputError.Domain -> R.string.routing_error_domain
    RuleInputError.Port -> R.string.routing_error_port
    RuleInputError.PortRange -> R.string.routing_error_port_range
    RuleInputError.PackageName -> R.string.routing_error_package_name
}
