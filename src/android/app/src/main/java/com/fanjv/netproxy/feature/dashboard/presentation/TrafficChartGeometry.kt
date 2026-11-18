package com.fanjv.netproxy.feature.dashboard.presentation

import androidx.compose.ui.geometry.Offset

/** 流量图显示最近一分钟。 */
internal const val TRAFFIC_WINDOW_MILLIS = 60_000L

/**
 * 纵轴上限：窗口内最大速率留 15% 余量，至少按 1 KB/s 计算，
 * 空闲时几十字节的波动不会被放大成满幅曲线。
 */
internal fun trafficChartPeak(samples: List<TrafficSample>): Float = maxOf(
    samples.maxOfOrNull { it.downloadBytesPerSecond } ?: 0L,
    samples.maxOfOrNull { it.uploadBytesPerSecond } ?: 0L,
    1024L,
).toFloat() * 1.15f

/**
 * 显示延迟：比最近一次采样间隔多半个间隔。画面的右边缘比最新采样晚这么多，
 * 最新的点从右边缘外平滑滑入，采样稍有延迟时右端也不会露出空白。
 */
internal fun trafficDisplayDelay(samples: List<TrafficSample>): Long {
    val interval = if (samples.size < 2) 1000L
    else (samples.last().timestampMillis - samples[samples.lastIndex - 1].timestampMillis).coerceIn(250L, 5000L)
    return interval + interval / 2
}

/**
 * 把采样按时间投影到画布比例坐标：x 把 [end - window, end] 映射到 0..1，y 按 [peak] 归一化到 0..1（0 在底部）。
 * 窗口两侧各多保留一个相邻点，折线在左右边缘连续地滑入滑出；绘制时裁剪到画布内。
 */
internal fun trafficPoints(
    samples: List<TrafficSample>,
    end: Long,
    window: Long,
    peak: Float,
    value: (TrafficSample) -> Long,
): List<Offset> {
    val start = end - window
    val first = samples.indexOfFirst { it.timestampMillis >= start }
    val last = samples.indexOfLast { it.timestampMillis <= end }
    val from = if (first < 0) samples.lastIndex else (first - 1).coerceAtLeast(0)
    val to = (last + 1).coerceAtMost(samples.lastIndex)
    if (samples.isEmpty() || to < from) return emptyList()
    return (from..to).map { index ->
        val sample = samples[index]
        Offset((sample.timestampMillis - start).toFloat() / window, (value(sample) / peak).coerceIn(0f, 1f))
    }
}
