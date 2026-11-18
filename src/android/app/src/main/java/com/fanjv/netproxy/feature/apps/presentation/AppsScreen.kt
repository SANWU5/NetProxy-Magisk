package com.fanjv.netproxy.feature.apps.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.rememberCommitOnLeave
import com.fanjv.netproxy.feature.apps.data.AppIconCache
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiActionRow
import dev.aiingjie.coui.CouiAppIcon
import dev.aiingjie.coui.CouiBarButton
import dev.aiingjie.coui.CouiGlyph
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiRadio
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSearchField
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.CouiSwitchRow
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** 应用图标：从缓存读取，未加载时显示占位。 */
@Composable
private fun AppIcon(app: AppInfoModel) {
    val iconRevision = AppIconCache.revision
    var icon by remember(app.id) { mutableStateOf(AppIconCache.cachedIcon(app.iconKey)) }
    LaunchedEffect(app.iconKey, iconRevision) {
        AppIconCache.cachedIcon(app.iconKey)?.let { icon = it }
    }
    CouiAppIcon(icon, sizeDp = 40)
}

/** 应用页：分应用代理的开关与应用选择。 */
@Composable
internal fun AppsScreen(
    onBack: () -> Unit,
    viewModel: AppsViewModel = netProxyViewModel(),
) {
    val apps by viewModel.state.collectAsStateWithLifecycle()
    val overlay = LocalCouiOverlay.current
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(listState, apps.masterAppList, lifecycle) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } to AppIconCache.generation
        }.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .distinctUntilChanged().collectLatest { (visible, _) ->
                try { viewModel.loadIcons(visible) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { }
            }
    }
    val resources = LocalConfiguration.current
    LaunchedEffect(resources.locales.toLanguageTags()) {
        viewModel.load(force = apps.hasLoadedApps)
    }
    LaunchedEffect(query) { viewModel.updateSearch(query) }

    val commitOnLeave = rememberCommitOnLeave(viewModel::requestPolicyFlush)
    val leave: () -> Unit = { commitOnLeave(onBack) }
    BackHandler(enabled = !overlay.isCovering && (apps.hasPendingPolicy || apps.requiresPolicyReload)) { leave() }

    val moreText = stringResource(R.string.more_actions)
    fun showOptions() = overlay.showPanel(moreText) {
        AppOptionsPanel(viewModel) { overlay.dismissPanel() }
    }
    val toggle: (AppInfoModel) -> Unit = { app ->
        if (apps.appSelectedFirst) {
            // 已选优先时勾选会重排列表，保持视口位置而不跟随条目移动。
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        viewModel.toggle(app.id)
    }
    val searching = query.isNotBlank()
    val visibleApps = remember(apps.allApps, apps.searchResults, apps.proxiedApps, apps.appSelectedFirst, apps.appReverseSort, searching) {
        if (searching) apps.orderedApps(apps.searchResults) else apps.orderedApps()
    }

    CouiSubPage(
        title = stringResource(R.string.proxy_apps),
        onBack = leave,
        listState = listState,
        actions = {
            if (apps.hasLoadedApps && apps.appProxyEnabled) CouiBarButton(CouiGlyph.More, moreText, ::showOptions)
        },
        header = if (apps.hasLoadedApps && apps.appProxyEnabled) {
            { CouiSearchField(query, { query = it }, stringResource(R.string.app_search)) }
        } else null,
    ) {
        if (!apps.hasLoadedApps || (apps.isLoadingApps && apps.allApps.isEmpty())) {
            item {
                CouiGroup(first = true) { CouiRow(stringResource(R.string.loading), leading = { CouiLoading() }) }
            }
            return@CouiSubPage
        }
        val notice = apps.error.isNotBlank() || apps.requiresPolicyReload
        if (notice) item("error") {
            CouiGroup(first = true) {
                if (apps.error.isNotBlank()) CouiRow(apps.error, titleColor = Coui.colors.error)
                if (apps.requiresPolicyReload) CouiActionRow(stringResource(R.string.routing_reload_draft), viewModel::discardPolicyAndReload)
            }
        }
        item("mode") {
            CouiGroup(first = !notice) {
                CouiMenuRow(
                    title = stringResource(R.string.app_proxy_mode),
                    options = listOf(
                        stringResource(R.string.proxy_mode_off),
                        stringResource(R.string.app_proxy_mode_blacklist),
                        stringResource(R.string.app_proxy_mode_whitelist),
                    ),
                    selected = if (!apps.appProxyEnabled) 0 else if (apps.appProxyMode == "blacklist") 1 else 2,
                    onSelect = { index ->
                        viewModel.setProxySettings(index != 0, when (index) {
                            1 -> "blacklist"
                            2 -> "whitelist"
                            else -> null
                        })
                    },
                )
            }
        }
        if (!apps.appProxyEnabled) return@CouiSubPage
        if (searching && visibleApps.isEmpty() && !apps.isFilteringApps) item("empty") {
            CouiGroup { CouiRow(stringResource(R.string.no_apps_found)) }
        }
        couiCardItems(visibleApps, key = AppInfoModel::id) { app ->
            AppRow(app, apps.appShowPackageName, app.id in apps.proxiedApps) { toggle(app) }
        }
    }
}

@Composable
private fun AppOptionsPanel(viewModel: AppsViewModel, onRefresh: () -> Unit) {
    val apps by viewModel.state.collectAsStateWithLifecycle()
    CouiGroup(first = true) {
        CouiSwitchRow(stringResource(R.string.show_system_apps), apps.showSystemApps, viewModel::setShowSystemApps)
        CouiSwitchRow(stringResource(R.string.app_selected_first), apps.appSelectedFirst, viewModel::setSelectedFirst)
        CouiSwitchRow(stringResource(R.string.app_reverse_sort), apps.appReverseSort, viewModel::setReverseSort)
        CouiSwitchRow(stringResource(R.string.app_show_package_name), apps.appShowPackageName, viewModel::setShowPackageName)
    }
    CouiGroup {
        CouiActionRow(stringResource(R.string.apps_refresh), {
            viewModel.load(force = true)
            onRefresh()
        }, enabled = !apps.isLoadingApps)
    }
}

@Composable
private fun AppRow(app: AppInfoModel, showPackageName: Boolean, proxied: Boolean, onClick: () -> Unit) {
    CouiRow(
        title = app.label,
        summary = if (showPackageName) app.packageName else null,
        minHeight = 60.dp,
        leading = { AppIcon(app) },
        role = Role.Checkbox,
        onClick = onClick,
    ) {
        if (app.userId != "0") {
            BasicText(
                stringResource(R.string.app_users_label, app.userId),
                Modifier.padding(start = 12.dp),
                style = Coui.type.rowSummary.copy(color = Coui.colors.labelSecondary),
            )
        }
        Spacer(Modifier.width(12.dp))
        CouiRadio(selected = proxied)
    }
}
