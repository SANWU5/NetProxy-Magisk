package com.fanjv.netproxy.feature.chain.presentation

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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.feature.apps.data.AppIconCache
import com.fanjv.netproxy.feature.chain.model.ChainAppChoice
import dev.aiingjie.coui.CouiAppIcon
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiButtonStyle
import dev.aiingjie.coui.CouiDialogButton
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRadioRow
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSearchField
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** 新建或编辑一个按应用策略：名称、启用、后置代理以及要接管的应用。id 为空表示新建。 */
@Composable
internal fun ChainPolicyScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: ChainViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val overlay = LocalCouiOverlay.current
    val chain = state.chain
    val existing = chain.policies.firstOrNull { it.id == id }

    var initialized by rememberSaveable(id) { mutableStateOf(false) }
    var name by rememberSaveable(id) { mutableStateOf("") }
    var enabled by rememberSaveable(id) { mutableStateOf(true) }
    var landing by rememberSaveable(id) { mutableStateOf("") }
    var packagesText by rememberSaveable(id) { mutableStateOf("") }
    var query by rememberSaveable(id) { mutableStateOf("") }
    val packages = packagesText.split('\n').filter(String::isNotEmpty)

    LifecycleResumeEffect(Unit) {
        viewModel.setVisible(true)
        viewModel.loadApps()
        onPauseOrDispose { viewModel.setVisible(false) }
    }

    LaunchedEffect(state.loaded, existing) {
        if (initialized || !state.loaded) return@LaunchedEffect
        if (existing != null) {
            name = existing.name
            enabled = existing.enabled
            landing = existing.landing
            packagesText = existing.packages.joinToString("\n")
        } else {
            landing = chain.manual.ifEmpty { chain.landings.firstOrNull()?.tag.orEmpty() }
        }
        initialized = true
    }

    LaunchedEffect(events) {
        val failure = events.filterIsInstance<ChainEvent.Failed>().firstOrNull() ?: return@LaunchedEffect
        overlay.showSnack(failure.message)
        viewModel.consumeEvents()
    }

    val listState = rememberLazyListState()
    val apps = state.apps
    val labels = remember(apps) { apps.associateBy(ChainAppChoice::packageName) }
    val filtered = remember(apps, query) {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) apps
        else apps.filter { it.label.lowercase().contains(keyword) || it.packageName.lowercase().contains(keyword) }
    }
    // 只为屏幕上的应用读取图标，滚动时按需补齐。
    LaunchedEffect(listState, filtered) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } to AppIconCache.generation }
            .distinctUntilChanged()
            .collectLatest { (keys, _) ->
                viewModel.loadIcons(keys.mapNotNull { labels[it]?.iconKey })
            }
    }

    fun toggle(packageName: String) {
        packagesText = if (packageName in packages) packages.filter { it != packageName }.joinToString("\n")
        else (packages + packageName).joinToString("\n")
    }

    val deleteTitle = stringResource(R.string.chain_policy_delete)
    val deleteMessage = stringResource(R.string.chain_policy_delete_message)
    val cancelText = stringResource(android.R.string.cancel)
    val saving = state.busy == "policy"
    val appsTitle = stringResource(R.string.chain_policy_apps)

    CouiSubPage(
        title = stringResource(if (existing == null) R.string.chain_policy_new else R.string.chain_policy_edit),
        onBack = onBack,
        listState = listState,
        header = { CouiSearchField(query, { query = it }, stringResource(R.string.app_search)) },
    ) {
        item {
            CouiGroup(first = true) {
                CouiInputRow(stringResource(R.string.chain_name), name, { name = it }, hint = stringResource(R.string.chain_policy_name_hint))
                CouiSwitchRow(stringResource(R.string.chain_policy_enabled), enabled, { enabled = it })
            }
        }
        item {
            CouiGroup(title = stringResource(R.string.chain_policy_landing)) {
                chain.landings.forEach { option ->
                    CouiRadioRow(option.tag, landing == option.tag, { landing = option.tag })
                }
            }
        }
        if (packages.isNotEmpty()) {
            item {
                CouiGroup(title = stringResource(R.string.chain_policy_selected, packages.size),
                    footer = stringResource(R.string.chain_policy_selected_footer)) {
                    packages.forEach { packageName ->
                        val app = labels[packageName]
                        CouiRow(
                            title = app?.label ?: packageName,
                            summary = packageName,
                            leading = { AppIcon(app) },
                            onClick = { toggle(packageName) },
                        ) { CouiRadio(selected = true) }
                    }
                }
            }
        }
        when {
            state.appsLoading -> item {
                CouiGroup(title = stringResource(R.string.chain_policy_apps)) {
                    CouiRow(stringResource(R.string.chain_policy_apps_loading), leading = { CouiLoading() })
                }
            }
            state.appsError.isNotEmpty() -> item {
                CouiGroup(title = stringResource(R.string.chain_policy_apps), footer = state.appsError) {
                    CouiRow(stringResource(R.string.chain_policy_apps_retry), onClick = viewModel::loadApps)
                }
            }
            else -> couiCardItems(
                items = filtered,
                key = ChainAppChoice::packageName,
                title = appsTitle,
            ) { app ->
                CouiRow(
                    title = app.label,
                    summary = app.packageName,
                    leading = { AppIcon(app) },
                    onClick = { toggle(app.packageName) },
                ) { CouiRadio(selected = app.packageName in packages) }
            }
        }
        item {
            CouiButton(
                text = stringResource(if (saving) R.string.chain_saving else R.string.chain_save),
                onClick = {
                    viewModel.savePolicy(existing?.id, name.trim(), enabled, landing, packages, onBack)
                },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
                enabled = !saving && initialized && name.isNotBlank() && landing.isNotEmpty() && (!enabled || packages.isNotEmpty()),
            )
        }
        if (existing != null) {
            item {
                CouiButton(
                    text = deleteTitle,
                    onClick = {
                        overlay.showDialog(deleteTitle, String.format(deleteMessage, existing.name), listOf(
                            CouiDialogButton(deleteTitle, CouiDialogButton.Kind.Danger) { viewModel.deletePolicy(existing.id, onBack) },
                            CouiDialogButton(cancelText),
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    style = CouiButtonStyle.Danger,
                    enabled = !saving,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(app: ChainAppChoice?) {
    val revision = AppIconCache.revision
    val key = app?.iconKey.orEmpty()
    val bitmap = remember(key, revision) { if (key.isEmpty()) null else AppIconCache.cachedIcon(key) }
    CouiAppIcon(bitmap)
}
