package com.fanjv.netproxy.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiBarButton
import dev.aiingjie.coui.CouiButton
import dev.aiingjie.coui.CouiButtonStyle
import dev.aiingjie.coui.CouiGlyph
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiOverlayState
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiPressable

/** 底部操作面板中的一项；danger 用红字标出删除等不可撤销的操作。 */
internal data class SheetAction(val text: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * 底部操作面板：标题与说明在上，操作放在一张卡片里（每项都有卡片底色与整行按压态），「取消」单独成块。
 * 用于列表项的更多操作，替代只有文字、没有底色的对话框按钮。
 */
internal fun CouiOverlayState.showActionSheet(title: String?, message: String? = null, actions: List<SheetAction>, cancel: String) {
    showPanel(title) {
        if (message != null) {
            BasicText(
                message, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                style = Coui.type.rowSummary.copy(color = Coui.colors.labelSecondary, textAlign = TextAlign.Center),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        CouiGroup(first = message == null) {
            actions.forEach { action ->
                SheetRow(action.text, if (action.danger) Coui.colors.error else Coui.colors.label) {
                    dismissPanel()
                    action.onClick()
                }
            }
        }
        CouiButton(
            cancel, ::dismissPanel,
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            style = CouiButtonStyle.Secondary,
        )
    }
}

@Composable
private fun SheetRow(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).couiPressable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Coui.type.rowTitle.copy(color = color, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 顶栏「更多」菜单中的一项；不可用的项不显示。 */
internal data class MenuAction(val text: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** 顶栏「更多」按钮：点击后在按钮下方弹出带卡片底色与阴影的浮动菜单。 */
@Composable
internal fun CouiBarMenuButton(description: String, actions: List<MenuAction>, enabled: Boolean = true) {
    val overlay = LocalCouiOverlay.current
    val bounds = remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.onGloballyPositioned { bounds.value = it.boundsInRoot() }) {
        CouiBarButton(CouiGlyph.More, description, {
            val visible = actions.filter { it.enabled }
            if (visible.isNotEmpty()) overlay.showMenu(bounds.value, visible.map { it.text }, -1) { index -> visible[index].onClick() }
        }, enabled)
    }
}
