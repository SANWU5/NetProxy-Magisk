package com.fanjv.netproxy.feature.dashboard.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanjv.netproxy.R
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiSwitchRow

/** 服务开关、实时速率与最近流量趋势。 */
@Composable
internal fun SpeedChartCard(
    downloadSpeed: String,
    uploadSpeed: String,
    trafficSamples: List<TrafficSample>,
    statusSummary: String,
    isRunning: Boolean,
    serviceControlEnabled: Boolean,
    /** 仪表盘可见时图表按帧连续滚动；不可见时停止，不占用绘制。 */
    animateChart: Boolean = true,
    onToggleService: () -> Unit = {}
) {
    val colors = Coui.colors
    CouiGroup(first = true) {
        Column {
            CouiSwitchRow(
                title = stringResource(R.string.service_status),
                checked = isRunning,
                onCheckedChange = { onToggleService() },
                summary = statusSummary,
                enabled = serviceControlEnabled,
            )
            AnimatedVisibility(
                visible = isRunning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpeedLabel(Icons.Rounded.Download, downloadSpeed, colors.accent)
                        SpeedLabel(Icons.Rounded.Upload, uploadSpeed, colors.success)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SpeedChart(trafficSamples, colors.accent, colors.success, colors.divider, animateChart, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedLabel(icon: ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(icon, null, modifier = Modifier.size(14.dp), colorFilter = ColorFilter.tint(color))
        Spacer(Modifier.width(4.dp))
        // 等宽数字：速率每秒变化时文字宽度不变，图标不会左右抖动。
        BasicText(value, style = Coui.type.rowSummary.copy(color = Coui.colors.labelSecondary, fontFeatureSettings = "tnum"))
    }
}

/**
 * 将最近一分钟的流量采样绘制为双折线：横轴按时间连续向左滚动，最新的点从右边缘滑入，
 * 纵轴上限随窗口内的峰值平滑变化，不会在新数据到来时整体跳动或变形。
 */
@Composable
private fun SpeedChart(
    trafficSamples: List<TrafficSample>,
    downloadColor: Color,
    uploadColor: Color,
    gridColor: Color,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val peak by animateFloatAsState(
        targetValue = trafficChartPeak(trafficSamples),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "traffic-peak",
    )
    val delay = remember(trafficSamples) { trafficDisplayDelay(trafficSamples) }
    val latest = trafficSamples.lastOrNull()?.timestampMillis ?: 0L
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(animate) {
        // 只在绘制阶段读取时间，每帧只重画图表，不触发重组。
        while (animate) withFrameMillis { now = System.currentTimeMillis() }
    }
    Canvas(modifier = modifier) {
        for (line in 1..3) {
            val y = size.height * line / 4f
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val end = if (animate) now - delay else latest

        fun drawSeries(points: List<Offset>, color: Color, alpha: Float) {
            if (points.size < 2) return
            val line = Path().apply {
                val first = points.first()
                moveTo(first.x * size.width, (1f - first.y) * size.height)
                for (index in 1 until points.lastIndex) {
                    val current = points[index]
                    val next = points[index + 1]
                    val midpointX = (current.x + next.x) / 2f
                    val midpointY = (current.y + next.y) / 2f
                    quadraticTo(
                        current.x * size.width,
                        (1f - current.y) * size.height,
                        midpointX * size.width,
                        (1f - midpointY) * size.height
                    )
                }
                val last = points.last()
                lineTo(last.x * size.width, (1f - last.y) * size.height)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(points.last().x * size.width, size.height)
                lineTo(points.first().x * size.width, size.height)
                close()
            }
            clipRect {
                drawPath(path = fill, brush = Brush.verticalGradient(listOf(color.copy(alpha = alpha), Color.Transparent)))
                drawPath(path = line, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        drawSeries(trafficPoints(trafficSamples, end, TRAFFIC_WINDOW_MILLIS, peak) { it.downloadBytesPerSecond }, downloadColor, 0.25f)
        drawSeries(trafficPoints(trafficSamples, end, TRAFFIC_WINDOW_MILLIS, peak) { it.uploadBytesPerSecond }, uploadColor, 0.15f)
    }
}
