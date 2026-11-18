package com.fanjv.netproxy.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

/**
 * 类型安全的 Miuix Nav 路由，以及后退栈持有者和向页面暴露它的 CompositionLocal。
 */
@Serializable
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object Apps : Route

    @Parcelize
    @Serializable
    data class SubscriptionDetails(val id: String) : Route

    @Parcelize
    @Serializable
    data class SubscriptionEdit(val id: String) : Route

    @Parcelize
    @Serializable
    data class NodeEdit(val nodeRef: String) : Route

    @Parcelize
    @Serializable
    data object InboundSettings : Route

    @Parcelize
    @Serializable
    data object NetworkMatching : Route

    @Parcelize
    @Serializable
    data object RoutingRules : Route

    @Parcelize
    @Serializable
    data object ThemeSettings : Route

    @Parcelize
    @Serializable
    data object KernelSettings : Route

    @Parcelize
    @Serializable
    data object Logs : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data class JsonEdit(val documentId: String) : Route

    /** 链式代理的按应用策略编辑页；id 为空表示新建。 */
    @Parcelize
    @Serializable
    data class ChainPolicy(val id: String) : Route

    /** 智能分组：按地区整理的分组与自建分组。 */
    @Parcelize
    @Serializable
    data object SmartGroups : Route

    /** 智能分组详情：在分组内选择自动选择或某个节点。 */
    @Parcelize
    @Serializable
    data class SmartGroupDetail(val id: String) : Route

    /** 智能分组或自建分组的编辑页；id 为空表示新建自建分组。 */
    @Parcelize
    @Serializable
    data class SmartGroupEdit(val id: String) : Route

}

/**
 * 持有后退栈的简单导航助手。
 */
class Navigator(
    val backStack: NavBackStack,
) {
    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }
}

@Composable
fun rememberNavigator(startRoute: Route): Navigator {
    val backStack = rememberNavBackStack<Route>(startRoute)
    return remember(backStack) { Navigator(backStack) }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
