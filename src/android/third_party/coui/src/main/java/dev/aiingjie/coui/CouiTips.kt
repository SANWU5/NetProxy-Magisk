package dev.aiingjie.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class CouiTipsAction(val text: String, val secondary: Boolean = false, val onClick: () -> Unit)

@Composable
fun CouiTopTips(
    status: CouiStatus,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actions: List<CouiTipsAction> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val colors = Coui.colors
    val type = Coui.type
    val source = remember { MutableInteractionSource() }
    Column(
        modifier.padding(horizontal = CouiDimens.cardMargin).fillMaxWidth()
            .couiScaleOnPress(source)
            .clip(CouiShapes.tips).background(colors.card)
            .then(if (onClick != null) Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (actions.isEmpty()) 16.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CouiStatusIcon(status, Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(title, style = type.tipsTitle.copy(color = colors.label))
                if (!body.isNullOrEmpty()) BasicText(body, Modifier.padding(top = 4.dp), style = type.tipsBody.copy(color = colors.labelSecondary))
            }
        }
        if (actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                actions.forEach { action ->
                    BasicText(
                        action.text,
                        Modifier.clip(RoundedCornerShape(50))
                            .couiPressable(role = Role.Button, onClick = action.onClick)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = type.buttonSmall.copy(color = if (action.secondary) colors.labelSecondary else colors.accentText),
                    )
                }
            }
        }
    }
}

/**
 * 空状态：列表为空、读取失败或暂时不可用时代替列表，直接放在页面上而不是卡片里。
 * 对应 COUIEmptyStateView 的小尺寸：圆形插图、标题、说明，下方可选一个文字按钮。
 * [loading] 为真时插图换成加载圈；[status] 为空时是空托盘，否则是对应颜色的提示符号。
 */
@Composable
fun CouiEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    status: CouiStatus? = null,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Coui.colors
    val type = Coui.type
    val tint = when {
        loading -> colors.labelSecondary
        status == null -> colors.labelTertiary
        status == CouiStatus.Error -> colors.error
        status == CouiStatus.Warning -> colors.orange
        status == CouiStatus.Success -> colors.success
        else -> colors.accent
    }
    val circle = if (loading || status == null) colors.container8 else tint.copy(alpha = if (colors.isDark) .18f else .12f)
    Column(
        modifier.fillMaxWidth().heightIn(min = 220.dp).padding(horizontal = CouiDimens.textInset, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            if (loading) CouiLoading(sizeDp = 28, color = tint)
            else CouiEmptyGlyph(status, tint, Modifier.size(36.dp))
        }
        BasicText(title, Modifier.padding(top = 16.dp), style = type.tipsTitle.copy(color = colors.label, textAlign = TextAlign.Center))
        if (!body.isNullOrEmpty()) {
            BasicText(body, Modifier.padding(top = 6.dp),
                style = type.tipsBody.copy(color = colors.labelSecondary, textAlign = TextAlign.Center))
        }
        if (actionText != null && onAction != null) {
            BasicText(
                actionText,
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50))
                    .couiPressable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = type.buttonSmall.copy(color = colors.accentText),
            )
        }
    }
}
