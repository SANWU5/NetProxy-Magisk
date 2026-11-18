package dev.aiingjie.coui

import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

fun <T> LazyListScope.couiCardItems(
    items: List<T>,
    key: (T) -> Any,
    title: String? = null,
    first: Boolean = false,
    dividerStart: Dp = CouiDimens.rowInnerPadding,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header:" + (title ?: key(items[0]).toString())) {
        Column(Modifier.fillMaxWidth()) {
            if (!first) Spacer(Modifier.height(CouiDimens.categoryGap))
            if (title != null) CouiCategory(title)
        }
    }
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        val top = index == 0
        val bottom = index == items.lastIndex
        val r = CouiDimens.cardRadius
        val shape = RoundedCornerShape(
            topStart = if (top) r else 0.dp, topEnd = if (top) r else 0.dp,
            bottomStart = if (bottom) r else 0.dp, bottomEnd = if (bottom) r else 0.dp,
        )
        val colors = Coui.colors
        val bounds = remember { CouiCardBounds() }
        Box(
            Modifier.padding(horizontal = CouiDimens.cardMargin).fillMaxWidth().clip(shape).background(colors.card)
                .onPlaced { bounds.coordinates = it }
                .padding(top = if (top) CouiDimens.cardEdgeExtra else 0.dp, bottom = if (bottom) CouiDimens.cardEdgeExtra else 0.dp)
                .drawWithContent {
                    drawContent()
                    if (!bottom) {
                        val t = max(1f, CouiDimens.dividerThickness.toPx())
                        val s = dividerStart.toPx()
                        val end = size.width - CouiDimens.rowInnerPadding.toPx()
                        if (end > s) drawRect(colors.divider, Offset(s, size.height - t), Size(end - s, t))
                    }
                },
        ) {
            CompositionLocalProvider(LocalCouiCardEdge provides CouiDimens.cardEdgeExtra, LocalCouiCardBounds provides bounds) { row(item) }
        }
    }
}
