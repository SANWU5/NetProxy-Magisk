package com.fanjv.netproxy

import com.fanjv.netproxy.navigation.Route.SmartGroupDetail
import com.fanjv.netproxy.navigation.Route.SmartGroupEdit
import com.fanjv.netproxy.navigation.Route.SmartGroups
import com.fanjv.netproxy.feature.smart.presentation.SmartGroupDetailScreen
import com.fanjv.netproxy.feature.smart.presentation.SmartGroupEditScreen
import com.fanjv.netproxy.feature.smart.presentation.SmartGroupsScreen
import android.os.Build
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.module.ModuleAccessViewModel
import com.fanjv.netproxy.core.ui.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.fanjv.netproxy.core.ui.component.LocalModuleAvailability
import com.fanjv.netproxy.core.ui.component.LocalModuleReconnect
import com.fanjv.netproxy.core.ui.component.ModulePage
import com.fanjv.netproxy.core.ui.theme.AccentColor
import com.fanjv.netproxy.core.ui.theme.AppThemeSettings
import com.fanjv.netproxy.core.ui.theme.ColorMode
import com.fanjv.netproxy.core.ui.theme.DarkStyle
import com.fanjv.netproxy.core.ui.theme.NetProxyTheme
import com.fanjv.netproxy.core.ui.theme.appCouiColors
import com.fanjv.netproxy.feature.about.presentation.AboutScreen
import com.fanjv.netproxy.feature.apps.presentation.AppsScreen
import com.fanjv.netproxy.feature.catalog.presentation.nodes.CatalogNodesViewModel
import com.fanjv.netproxy.feature.catalog.presentation.nodes.edit.SingBoxNodeEditScreen
import com.fanjv.netproxy.feature.catalog.presentation.nodes.list.CatalogNodesScreen
import com.fanjv.netproxy.feature.catalog.presentation.subscriptions.SubscriptionDetailsScreen
import com.fanjv.netproxy.feature.catalog.presentation.subscriptions.SubscriptionEditorScreen
import com.fanjv.netproxy.feature.catalog.presentation.subscriptions.SubscriptionsScreen
import com.fanjv.netproxy.feature.dashboard.presentation.CatalogDashboardScreen
import com.fanjv.netproxy.feature.kernel.presentation.SingBoxJsonEditScreen
import com.fanjv.netproxy.feature.kernel.presentation.SingBoxKernelSettingsScreen
import com.fanjv.netproxy.feature.logs.presentation.LogsScreen
import com.fanjv.netproxy.feature.chain.presentation.ChainPolicyScreen
import com.fanjv.netproxy.feature.chain.presentation.ChainScreen
import com.fanjv.netproxy.feature.inbound.presentation.InboundSettingsScreen
import com.fanjv.netproxy.feature.settings.presentation.NetworkMatchingScreen
import com.fanjv.netproxy.feature.routing.presentation.RoutingRulesScreen
import com.fanjv.netproxy.feature.settings.presentation.SettingsScreen
import com.fanjv.netproxy.feature.theme.presentation.ThemeSettingsScreen
import com.fanjv.netproxy.feature.theme.presentation.ThemeViewModel
import com.fanjv.netproxy.navigation.AppDestination
import com.fanjv.netproxy.navigation.DockHost
import com.fanjv.netproxy.navigation.LocalDockHost
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.LocalOpenNodesGroup
import com.fanjv.netproxy.navigation.Route.ChainPolicy
import com.fanjv.netproxy.navigation.Route.About
import com.fanjv.netproxy.navigation.Route.Apps
import com.fanjv.netproxy.navigation.Route.JsonEdit
import com.fanjv.netproxy.navigation.Route.KernelSettings
import com.fanjv.netproxy.navigation.Route.Logs
import com.fanjv.netproxy.navigation.Route.Main
import com.fanjv.netproxy.navigation.Route.NodeEdit
import com.fanjv.netproxy.navigation.Route.InboundSettings
import com.fanjv.netproxy.navigation.Route.NetworkMatching
import com.fanjv.netproxy.navigation.Route.RoutingRules
import com.fanjv.netproxy.navigation.Route.SubscriptionDetails
import com.fanjv.netproxy.navigation.Route.SubscriptionEdit
import com.fanjv.netproxy.navigation.Route.ThemeSettings
import com.fanjv.netproxy.navigation.rememberNavigator
import com.fanjv.netproxy.navigation.mainDestinations
import com.fanjv.netproxy.navigation.availableIn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiDockTab
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiInputRow
import dev.aiingjie.coui.CouiOverlayHost
import dev.aiingjie.coui.CouiPanel
import dev.aiingjie.coui.CouiTabs
import dev.aiingjie.coui.LocalCouiOverlay

class MainActivity : ComponentActivity() {
    private val dock by lazy { DockHost(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NetProxy)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val themeViewModel: ThemeViewModel = netProxyViewModel()

            val themeState by themeViewModel.state.collectAsStateWithLifecycle()
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, themeState.pageScale) {
                Density(systemDensity.density * themeState.pageScale, systemDensity.fontScale)
            }
            val appThemeSettings = AppThemeSettings(
                colorMode = ColorMode.fromValue(themeState.colorMode),
                darkStyle = DarkStyle.fromValue(themeState.darkStyle),
                accent = AccentColor.fromValue(themeState.accent),
                enablePredictiveBack = themeState.enablePredictiveBack,
            )
            val darkMode = appThemeSettings.colorMode.resolveDark(androidx.compose.foundation.isSystemInDarkTheme())
            val couiColors = remember(darkMode, appThemeSettings.darkStyle, appThemeSettings.accent) {
                appCouiColors(darkMode, appThemeSettings.darkStyle, appThemeSettings.accent)
            }

            // 系统栏图标、窗口底色与悬浮底栏都不在 Compose 主题里，按应用的明暗设置同步，
            // 避免强制浅色或深色时出现半边跟随系统的界面。
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkMode
                    isAppearanceLightNavigationBars = !darkMode
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                window.setBackgroundDrawable(ColorDrawable(couiColors.page.toArgb()))
                dock.setDark(darkMode)
            }

            CompositionLocalProvider(LocalDensity provides density, LocalDockHost provides dock) {
                NetProxyTheme(appThemeSettings = appThemeSettings, dark = darkMode, colors = couiColors) {
                    CouiOverlayHost {
                        NetProxyApp(themeViewModel)
                    }
                }
            }
        }
        dock.attach(findViewById(android.R.id.content))
    }
}

@Composable
internal fun NetProxyApp(themeViewModel: ThemeViewModel) {
    val navigator = rememberNavigator(Main)
    val catalogNodesViewModel: CatalogNodesViewModel = netProxyViewModel()
    val moduleAccess: ModuleAccessViewModel = netProxyViewModel()
    val availability by moduleAccess.state.collectAsStateWithLifecycle()
    var reconnect by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        moduleAccess.refresh()
        onPauseOrDispose { }
    }

    val openReconnect = remember { { reconnect = true } }
    val openNodesGroup: (String) -> Unit = remember(catalogNodesViewModel) { { id -> catalogNodesViewModel.openGroup(id) } }
    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalModuleAvailability provides availability,
        LocalModuleReconnect provides openReconnect,
        LocalOpenNodesGroup provides openNodesGroup,
    ) {
        Scaffold {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { navigator.pop() },
            ) {
                entry<Main> { MainScreen(themeViewModel, catalogNodesViewModel, onReconnect = openReconnect) }
                entry<Apps> {
                    ModulePage(R.string.proxy_apps, R.string.apps_read_failed, { navigator.pop() }) {
                        AppsScreen(onBack = { navigator.pop() })
                    }
                }
                entry<SubscriptionDetails> {
                    ModulePage(R.string.subscriptions, R.string.subscriptions_read_failed, { navigator.pop() }) {
                        SubscriptionDetailsScreen(
                            id = it.id,
                            onBack = { navigator.pop() }
                        )
                    }
                }
                entry<SubscriptionEdit> {
                    ModulePage(R.string.subscriptions, R.string.subscriptions_read_failed, { navigator.pop() }) {
                        SubscriptionEditorScreen(
                            id = it.id,
                            initialUrl = it.url,
                            onBack = { navigator.pop() }
                        )
                    }
                }
                entry<NodeEdit> {
                    ModulePage(R.string.node_edit, R.string.nodes_read_failed, { navigator.pop() }) {
                        SingBoxNodeEditScreen(
                            viewModel = catalogNodesViewModel,
                            nodeRef = it.nodeRef,
                            onBack = { navigator.pop() }
                        )
                    }
                }
                entry<InboundSettings> {
                    ModulePage(R.string.inbound_settings, R.string.inbound_read_failed, { navigator.pop() }) {
                        InboundSettingsScreen(onBack = { navigator.pop() })
                    }
                }
                entry<NetworkMatching> {
                    ModulePage(R.string.network_matching, R.string.network_read_failed, { navigator.pop() }) {
                        NetworkMatchingScreen(onBack = { navigator.pop() })
                    }
                }
                entry<RoutingRules> {
                    ModulePage(R.string.routing_rules, R.string.routing_read_failed, { navigator.pop() }) {
                        RoutingRulesScreen(onBack = { navigator.pop() })
                    }
                }
                entry<KernelSettings> {
                    ModulePage(R.string.kernel_settings, R.string.singbox_documents_load_failed, { navigator.pop() }) {
                        SingBoxKernelSettingsScreen(
                            onBack = { navigator.pop() }
                        )
                    }
                }
                entry<JsonEdit> {
                    ModulePage(R.string.kernel_settings, R.string.singbox_documents_load_failed, { navigator.pop() }) {
                        SingBoxJsonEditScreen(
                            documentId = it.documentId,
                            onBack = { navigator.pop() }
                        )
                    }
                }
                entry<ChainPolicy> {
                    ModulePage(R.string.chain, R.string.chain_load_failed, { navigator.pop() }) {
                        ChainPolicyScreen(id = it.id, onBack = { navigator.pop() })
                    }
                }
                entry<SmartGroups> {
                    ModulePage(R.string.smart_groups, R.string.smart_load_failed, { navigator.pop() }) {
                        SmartGroupsScreen(onBack = { navigator.pop() })
                    }
                }
                entry<SmartGroupDetail> {
                    ModulePage(R.string.smart_groups, R.string.smart_load_failed, { navigator.pop() }) {
                        SmartGroupDetailScreen(id = it.id, onBack = { navigator.pop() })
                    }
                }
                entry<SmartGroupEdit> {
                    ModulePage(R.string.smart_groups, R.string.smart_load_failed, { navigator.pop() }) {
                        SmartGroupEditScreen(id = it.id, onBack = { navigator.pop() })
                    }
                }
                entry<ThemeSettings> { ThemeSettingsScreen(viewModel = themeViewModel) }
                entry<About> { AboutScreen() }
                entry<Logs> {
                    ModulePage(R.string.logs, R.string.logs_read_failed, { navigator.pop() }) {
                        LogsScreen(onBack = { navigator.pop() })
                    }
                }
            }
        }
        ModuleConnectionDialog(reconnect, moduleAccess::reconnect) { reconnect = false }
    }
}

@Composable
private fun ModuleConnectionDialog(show: Boolean, connect: suspend (String) -> Unit, onDismiss: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("https://127.0.0.1:9091") }
    var connecting by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf("") }
    val invalidAddress = stringResource(R.string.module_address_invalid)
    val scope = rememberCoroutineScope()
    val dismiss = { request?.cancel(); onDismiss() }
    CouiPanel(visible = show, title = stringResource(R.string.module_reconnect), onDismissRequest = dismiss) {
        CouiGroup(first = true) {
            CouiInputRow(
                label = stringResource(R.string.module_address),
                value = address,
                onValueChange = { address = it; error = "" },
                enabled = !connecting,
                error = error.ifEmpty { null },
            )
        }
        CouiButton(
            text = stringResource(if (connecting) R.string.module_connecting else R.string.module_reconnect),
            onClick = {
                connecting = true
                error = ""
                request = scope.launch {
                    try { connect(address.trim()); onDismiss() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        error = if (failure is IllegalArgumentException || failure is java.net.URISyntaxException) invalidAddress
                            else failure.userMessage()
                    }
                    finally { connecting = false; request = null }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            enabled = !connecting && address.isNotBlank(),
        )
    }
}

@Composable
internal fun MainScreen(
    themeViewModel: ThemeViewModel,
    catalogNodesViewModel: CatalogNodesViewModel,
    onReconnect: () -> Unit = {},
) {
    val availability = LocalModuleAvailability.current
    var destination by rememberSaveable { mutableStateOf(AppDestination.Dashboard) }
    if (availability == null) {
        CatalogDashboardScreen(onReconnect = onReconnect)
        return
    }
    val destinations = mainDestinations(availability.available)
    // 权限改变会改变页码；保存目的地身份，避免“设置”被映射为“节点”。
    key(destinations) {
        MainPager(themeViewModel, catalogNodesViewModel, destinations,
            initialDestination = destination.availableIn(destinations),
            onDestinationChanged = { destination = it }, onReconnect = onReconnect)
    }
}

@Composable
private fun MainPager(
    themeViewModel: ThemeViewModel,
    catalogNodesViewModel: CatalogNodesViewModel,
    destinations: List<AppDestination>,
    initialDestination: AppDestination,
    onDestinationChanged: (AppDestination) -> Unit,
    onReconnect: () -> Unit,
) {
    val themeState by themeViewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(destinations.indexOf(initialDestination)) }
    val dock = LocalDockHost.current
    val navigator = LocalNavigator.current
    val overlay = LocalCouiOverlay.current

    val tabs = destinations.map { CouiDockTab(stringResource(it.labelRes), it.selected, it.idle) }
    SideEffect { dock.setTabs(tabs) }
    LaunchedEffect(selected) {
        dock.setPage(selected)
        onDestinationChanged(destinations[selected])
    }
    LaunchedEffect(dock) { dock.pageRequests.collect { selected = it.coerceIn(0, destinations.lastIndex) } }
    // 订阅页「查看节点」：回到主页并切到节点页，节点页已切换到对应分组。
    LaunchedEffect(catalogNodesViewModel) {
        catalogNodesViewModel.pageRequests.collect {
            val index = destinations.indexOf(AppDestination.Nodes)
            if (index != -1) {
                while (navigator.backStack.size > 1) navigator.pop()
                selected = index
            }
        }
    }
    // 底栏只在主分页可见：进入二级页或 COUI 弹出层盖住页面时隐藏，避免挡住内容。
    val rootVisible = navigator.backStack.size == 1 && !overlay.isCovering
    SideEffect { dock.setShown(rootVisible) }
    DisposableEffect(dock) { onDispose { dock.setShown(false) } }

    MainScreenBackHandler(
        selected = selected,
        navigator = navigator,
        enablePredictiveBack = themeState.enablePredictiveBack,
        onBackToFirst = { selected = 0 },
    )

    CouiTabs(selected, Modifier.fillMaxSize()) { pageIndex ->
        when (destinations[pageIndex]) {
            AppDestination.Dashboard -> CatalogDashboardScreen(
                isActive = selected == pageIndex,
                onReconnect = onReconnect,
                onNavigateToNodes = {
                    val nodesIndex = destinations.indexOf(AppDestination.Nodes)
                    if (nodesIndex != -1) selected = nodesIndex
                },
                onNavigateToChain = {
                    val chainIndex = destinations.indexOf(AppDestination.Chain)
                    if (chainIndex != -1) selected = chainIndex
                }
            )

            AppDestination.Nodes -> CatalogNodesScreen(
                isActive = selected == pageIndex,
                viewModel = catalogNodesViewModel
            )

            AppDestination.Chain -> ChainScreen(isActive = selected == pageIndex)

            AppDestination.Subscriptions -> SubscriptionsScreen(isActive = selected == pageIndex)

            AppDestination.Settings -> SettingsScreen(isActive = selected == pageIndex, colorMode = themeState.colorMode)
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    selected: Int,
    navigator: com.fanjv.netproxy.navigation.Navigator,
    enablePredictiveBack: Boolean,
    onBackToFirst: () -> Unit,
) {
    val overlay = LocalCouiOverlay.current
    // 弹窗或底部面板打开时，返回先关闭它们，不切回第一个分页。
    val isPagerBackHandlerEnabled by remember(selected, overlay) {
        derivedStateOf {
            navigator.backStack.lastOrNull() is Main &&
                    navigator.backStack.size == 1 &&
                    selected != 0 &&
                    !overlay.isCovering
        }
    }

    if (enablePredictiveBack) {
        val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
        NavigationBackHandler(
            state = navEventState,
            isBackEnabled = isPagerBackHandlerEnabled,
            onBackCompleted = onBackToFirst
        )
    } else {
        BackHandler(enabled = isPagerBackHandlerEnabled, onBack = onBackToFirst)
    }
}
