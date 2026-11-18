package com.fanjv.netproxy.feature.routing.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.couiCardItems
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    OverlayDialog(show = state.draft != null, title = stringResource(if (draft?.index == null) R.string.routing_add else R.string.routing_edit),
        onDismissRequest = viewModel::dismiss, onDismissFinished = { retainedDraft = null }) {
        if (draft != null) {
            val file = state.files.getValue(draft.target)
            val textState = rememberTextFieldState(draft.text)
            LaunchedEffect(textState, draft.field) {
                draft.field?.let { field ->
                    snapshotFlow { textState.text.toString() }.collect { viewModel.updateDraft(field, it) }
                }
            }
            Column(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (draft.index == null) OverlayDropdownPreference(title = stringResource(R.string.routing_type),
                        items = LocalRuleSet.entries.map { stringResource(it.title()) }, selectedIndex = draft.target.ordinal,
                        enabled = !state.isSaving,
                        insideMargin = PaddingValues(vertical = 12.dp),
                        onSelectedIndexChange = { viewModel.selectTarget(LocalRuleSet.entries[it]) })
                    else Text(stringResource(R.string.routing_type) + ": " + stringResource(draft.target.title()),
                        style = MiuixTheme.textStyles.body2)
                    val field = draft.field
                    if (field != null) {
                        OverlayDropdownPreference(title = stringResource(R.string.routing_match),
                            items = RuleField.entries.map { stringResource(it.title()) }, selectedIndex = field.ordinal,
                            enabled = !state.isSaving,
                            insideMargin = PaddingValues(vertical = 12.dp),
                            onSelectedIndexChange = { viewModel.updateDraft(RuleField.entries[it], textState.text.toString()) })
                        TextField(state = textState,
                            label = stringResource(R.string.routing_values), lineLimits = TextFieldLineLimits.MultiLine(3, 6),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false,
                                keyboardType = if (field == RuleField.DomainKeyword) KeyboardType.Text else KeyboardType.Ascii),
                            modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving)
                        Text(stringResource(field.hint()), style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    } else {
                        Text(stringResource(R.string.routing_advanced_hint), style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    state.inputError?.let { Text(stringResource(it.message()), color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.body2) }
                    if (file.error.isNotEmpty() || file.requiresReload) RuleError(file, state, reload = { viewModel.refresh(discardDraft = true) })
                    if (file.invalidDocument) TextButton(stringResource(R.string.routing_raw), { rawEdit(draft.target) },
                        Modifier.fillMaxWidth(), enabled = !state.isLoading && !state.isSaving)
                }
                RuleButtons(state.isSaving, viewModel::dismiss,
                    confirm = { if (draft.field != null) {
                        viewModel.updateDraft(draft.field, textState.text.toString())
                        viewModel.save()
                    } else { rawEdit(draft.target) } },
                    enabled = state.editable(draft.target),
                    confirmLabel = stringResource(if (draft.field != null) R.string.save_text else R.string.routing_raw))
                draft.index?.let { index ->
                    TextButton(stringResource(R.string.common_delete), { viewModel.requestDelete(RuleIdentity(draft.target, index)) },
                        Modifier.fillMaxWidth(), enabled = state.editable(draft.target),
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error))
                }
            }
        }
    }
    OverlayDialog(show = state.deleting != null, title = stringResource(R.string.routing_delete),
        summary = listOfNotNull(state.deleting?.target?.let { stringResource(it.title()) }, stringResource(R.string.routing_delete_hint)).joinToString("\n"),
        onDismissRequest = viewModel::dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val target = state.deleting?.target
            val file = target?.let { state.files.getValue(it) }
            if (file != null && (file.error.isNotEmpty() || file.requiresReload)) RuleError(file, state, reload = { viewModel.refresh(discardDraft = true) })
            RuleButtons(state.isSaving, viewModel::dismiss,
                confirm = viewModel::delete, enabled = target?.let(state::editable) == true, confirmLabel = stringResource(R.string.common_delete))
        }
    }
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

@Composable
private fun RuleError(file: RuleFileState, state: RoutingRulesState, reload: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(file.errorText(), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
        if (file.requiresReload) TextButton(stringResource(if (state.draft != null || state.deleting != null) R.string.routing_reload_draft else R.string.inbound_reload),
            reload, Modifier.fillMaxWidth(), enabled = !state.isLoading && !state.isSaving)
    }
}

@Composable
private fun RuleButtons(saving: Boolean, cancel: () -> Unit, confirm: () -> Unit, enabled: Boolean, confirmLabel: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(stringResource(android.R.string.cancel), cancel, Modifier.weight(1f), enabled = !saving)
        if (saving) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { InfiniteProgressIndicator(Modifier.size(24.dp)) }
        else TextButton(confirmLabel, confirm, Modifier.weight(1f), enabled = enabled, colors = ButtonDefaults.textButtonColorsPrimary())
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
