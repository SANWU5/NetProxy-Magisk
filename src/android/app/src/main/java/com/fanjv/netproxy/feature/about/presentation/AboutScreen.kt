package com.fanjv.netproxy.feature.about.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.fanjv.netproxy.BuildConfig
import com.fanjv.netproxy.R
import com.fanjv.netproxy.navigation.LocalNavigator

/** 关于页入口：组装静态展示状态和页面动作。 */
@Composable
internal fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    // 本分支是非官方 fork：源码与问题反馈指向本仓库，原项目只作为致谢链接，不引导到官方交流群。
    val linksHtml = stringResource(
        id = R.string.about_source_code,
        "<b><a href=\"https://github.com/SANWU5/NetProxy-Magisk\">GitHub</a></b>",
        "<b><a href=\"https://github.com/Fanju6/NetProxy-Magisk\">GitHub</a></b>",
    )

    AboutScreenCoui(
        state = AboutUiState(
            title = stringResource(R.string.about),
            appName = stringResource(R.string.app_name),
            versionName = BuildConfig.VERSION_NAME,
            links = extractLinks(linksHtml),
        ),
        actions = AboutScreenActions(
            onBack = dropUnlessResumed { navigator.pop() },
            onOpenLink = uriHandler::openUri,
        ),
    )
}
