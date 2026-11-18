// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.aiingjie.coui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CouiDockContent(
    tabs: List<CouiDockTab>,
    page: Int,
    touch: CouiDockTouch,
    dark: Boolean,
    onPage: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val selectedInk = if (dark) Color.White.copy(alpha = .9f) else Color(0xE6000000)
    val idleInk = if (dark) Color.White.copy(alpha = .68f) else Color.Black.copy(alpha = .68f)
    val labelStyle = TextStyle(fontSize = 10.sp, lineHeight = 13.5.sp)
    val measurer = rememberTextMeasurer()
    val labelWidth = remember(tabs, density, measurer) {
        with(density) { tabs.maxOf { measurer.measure(it.label, style = labelStyle.copy(fontWeight = FontWeight.Medium)).size.width }.toDp() }
    }
    val cell = maxOf((192.dp - 24.dp - 16.dp) / tabs.size, labelWidth + 16.dp)
    val glowRadius = with(density) { 44.dp.toPx() }
    val shown = if (touch.highlight >= 0) touch.highlight else page
    val pillWidth = with(density) { (cell + 12.dp).toPx() }
    val pillHeight = with(density) { 48.dp.toPx() }
    val inset = with(density) { 4.dp.toPx() }
    Box(Modifier.drawWithContent {
        val alpha = touch.light
        if (alpha > 0.01f) {
            val x = touch.lightAt.x.coerceIn(inset + pillWidth / 2f, size.width - inset - pillWidth / 2f)
            drawRoundRect(
                color = (if (dark) Color.White.copy(alpha = .14f) else Color.Black.copy(alpha = .07f)).copy(
                    alpha = (if (dark) .14f else .07f) * alpha),
                topLeft = Offset(x - pillWidth / 2f, (size.height - pillHeight) / 2f),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(pillHeight / 2f),
            )
        }
        drawContent()
        if (alpha > 0.01f) {
            val center = touch.lightAt
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = (if (dark) .2f else .6f) * alpha), Color.Transparent),
                center = center, radius = glowRadius), radius = glowRadius, center = center)
        }
    }) {
        Row(Modifier.height(56.dp).padding(horizontal = 12.dp, vertical = 4.dp).semantics { selectableGroup() },
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { index, tab ->
                val on = shown == index
                val ink by animateColorAsState(if (on) selectedInk else idleInk, tween(160), label = "dock-ink-$index")
                val solid by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "dock-icon-$index")
                val scale by animateFloatAsState(if (touch.pressed == index) .9f else 1f,
                    spring(dampingRatio = .62f, stiffness = 520f), label = "dock-press-$index")
                Column(Modifier.width(cell).height(48.dp)
                    .onGloballyPositioned { c ->
                        val b = c.boundsInRoot()
                        while (touch.cells.size <= index) touch.cells += 0f..0f
                        touch.cells[index] = b.left..b.right
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .semantics {
                        role = Role.Tab
                        selected = page == index
                        onClick(label = tab.label) { if (page != index) onPage(index); true }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(24.dp)) {
                        if (solid < 1f) Image(rememberVectorPainter(tab.idle), null, Modifier.size(24.dp),
                            alpha = 1f - solid, colorFilter = ColorFilter.tint(ink))
                        if (solid > 0f) Image(rememberVectorPainter(tab.selected), tab.label, Modifier.size(24.dp),
                            alpha = solid, colorFilter = ColorFilter.tint(ink))
                    }
                    Spacer(Modifier.height(2.dp))
                    BasicText(tab.label, style = labelStyle.copy(color = ink, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                        softWrap = false)
                }
            }
        }
    }
}
