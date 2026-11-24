package com.fanjv.netproxy.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.res.stringResource
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.module.ModuleAvailability
import com.fanjv.netproxy.core.module.ModuleConnectionState
import dev.aiingjie.coui.CouiEmptyState
import dev.aiingjie.coui.CouiStatus
import dev.aiingjie.coui.CouiSubPage

internal val LocalModuleAvailability = staticCompositionLocalOf<ModuleAvailability?> { null }

/** 打开“重新连接模块”对话框，由应用根部提供。 */
internal val LocalModuleReconnect = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun ModulePage(
    @StringRes title: Int,
    @StringRes unavailable: Int,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val availability = LocalModuleAvailability.current
    // 暂时失去访问权限时仍保留编辑器的文本、revision 和导航条目的 ViewModel。
    val holder = rememberSaveableStateHolder()
    if (availability?.available == true) {
        holder.SaveableStateProvider("content", content)
    } else {
        val reconnect = LocalModuleReconnect.current
        CouiSubPage(title = stringResource(title), onBack = onBack) {
            item {
                if (availability == null) CouiEmptyState(stringResource(R.string.loading), loading = true)
                else CouiEmptyState(
                    stringResource(unavailable), status = CouiStatus.Error,
                    body = stringResource(
                        if (availability.state == ModuleConnectionState.UNTRUSTED) R.string.module_identity_failed
                        else R.string.no_module_summary,
                    ),
                    actionText = stringResource(R.string.module_reconnect), onAction = reconnect,
                )
            }
        }
    }
}
