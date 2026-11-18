package dev.aiingjie.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
                        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .couiPressable(role = Role.Button, onClick = action.onClick)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = type.buttonSmall.copy(color = if (action.secondary) colors.labelSecondary else colors.accentText),
                    )
                }
            }
        }
    }
}
