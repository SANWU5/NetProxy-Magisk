package dev.aiingjie.coui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 展开行（COUI 的可展开分组）：点击后在同一张卡片里展开或收起下方的行，右侧箭头随之转向。
 * 展开的每一行用 [CouiExpandContent] 包住并直接放在卡片里，这样卡片仍会在行与行之间画分割线。
 */
@Composable
fun CouiExpandRow(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = Coui.colors
    val turn by animateFloatAsState(if (expanded) 180f else 0f, CouiMotion.move(), label = "expand")
    CouiRow(
        title,
        modifier.semantics {
            if (enabled) {
                if (expanded) collapse { onClick(); true } else expand { onClick(); true }
            }
        },
        summary, enabled, leading = leading, role = Role.Button, onClick = onClick,
        minHeight = if (leading != null) 60.dp else CouiDimens.rowMinHeight, wrapTrailing = true,
    ) {
        if (!value.isNullOrEmpty()) {
            BasicText(
                value,
                Modifier.padding(start = CouiDimens.titleToValue),
                style = Coui.type.jumpValue.copy(color = if (enabled) colors.labelSecondary else colors.labelTertiary, textAlign = TextAlign.End),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(CouiDimens.arrowGap))
        CouiIcon(CouiGlyph.Expand, if (enabled) colors.labelTertiary else colors.labelQuaternary, Modifier.rotate(turn))
    }
}

/** [CouiExpandRow] 下方的一行：展开时自上而下长出并淡入，收起时收拢并淡出。 */
@Composable
fun CouiExpandContent(expanded: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(CouiMotion.move(CouiMotion.SLOW), expandFrom = Alignment.Top) +
            fadeIn(CouiMotion.ease(CouiMotion.FAST)),
        exit = shrinkVertically(CouiMotion.move(), shrinkTowards = Alignment.Top) + fadeOut(CouiMotion.out()),
    ) { content() }
}
