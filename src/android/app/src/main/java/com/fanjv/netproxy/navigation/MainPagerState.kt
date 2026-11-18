package com.fanjv.netproxy.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.ui.graphics.vector.ImageVector
import com.fanjv.netproxy.R

internal fun mainDestinations(moduleAvailable: Boolean): List<AppDestination> =
    if (moduleAvailable) AppDestination.entries
    else listOf(AppDestination.Dashboard, AppDestination.Settings)

internal fun AppDestination.availableIn(destinations: List<AppDestination>): AppDestination =
    takeIf { it in destinations } ?: AppDestination.Dashboard

/** 底栏的五个主分页；selected/idle 分别是悬浮底栏选中与未选中的图标。 */
internal enum class AppDestination(
    val labelRes: Int,
    val selected: ImageVector,
    val idle: ImageVector,
) {
    Dashboard(R.string.dashboard, Icons.Rounded.SpaceDashboard, Icons.Outlined.SpaceDashboard),
    Nodes(R.string.nodes, Icons.Rounded.Router, Icons.Outlined.Router),
    Chain(R.string.chain, Icons.Rounded.Link, Icons.Outlined.Link),
    Subscriptions(R.string.subscriptions, Icons.Rounded.CloudSync, Icons.Outlined.CloudSync),
    Settings(R.string.settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}
