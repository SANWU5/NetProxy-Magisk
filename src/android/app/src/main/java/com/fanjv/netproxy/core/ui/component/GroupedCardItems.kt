package com.fanjv.netproxy.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aiingjie.coui.Coui

class CardItem(
    val key: String,
    val content: @Composable ColumnScope.() -> Unit,
)

@Composable
private fun CardSegment(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Coui.colors.card,
    cornerRadius: Dp = 16.dp,
    outerHorizontalPadding: Dp = 0.dp,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val topCornerRadius = if (isFirst) cornerRadius else 0.dp
    val bottomCornerRadius = if (isLast) cornerRadius else 0.dp
    val background = if (topCornerRadius == 0.dp && bottomCornerRadius == 0.dp) {
        Modifier.background(color)
    } else {
        Modifier
            .clip(RoundedCornerShape(topCornerRadius, topCornerRadius, bottomCornerRadius, bottomCornerRadius))
            .background(color)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = outerHorizontalPadding,
                end = outerHorizontalPadding,
                top = outerTopPadding,
                bottom = outerBottomPadding,
            )
            .then(background)
            .padding(insidePadding),
        content = content,
    )
}

fun LazyListScope.groupedCardItems(
    keyPrefix: String,
    items: List<CardItem>,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    outerHorizontalPadding: Dp = 0.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
) {
    if (items.isEmpty()) return
    val lastIndex = items.lastIndex

    items.forEachIndexed { index, cardItem ->
        item(key = "$keyPrefix:${cardItem.key}") {
            CardSegment(
                isFirst = index == 0,
                isLast = index == lastIndex,
                outerHorizontalPadding = outerHorizontalPadding,
                outerTopPadding = if (index == 0) outerTopPadding else 0.dp,
                outerBottomPadding = if (index == lastIndex) outerBottomPadding else 0.dp,
                insidePadding = insidePadding,
                content = cardItem.content,
            )
        }
    }
}
