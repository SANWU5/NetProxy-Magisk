package com.fanjv.netproxy.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.res.stringResource
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.module.ModuleAvailability
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage

internal val LocalModuleAvailability = staticCompositionLocalOf<ModuleAvailability?> { null }

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
        CouiSubPage(title = stringResource(title), onBack = onBack) {
            item {
                CouiGroup(first = true) {
                    if (availability == null) CouiRow(stringResource(R.string.loading), leading = { CouiLoading() })
                    else CouiRow(stringResource(unavailable))
                }
            }
        }
    }
}
