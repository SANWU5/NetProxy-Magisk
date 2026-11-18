package com.fanjv.netproxy.feature.dashboard.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficChartGeometryTest {
    private val samples = (0..60).map { second ->
        TrafficSample(timestampMillis = 1_000L + second * 1_000L, downloadBytesPerSecond = second * 100L, uploadBytesPerSecond = 0)
    }

    @Test
    fun `points are placed by time across the window`() {
        val points = trafficPoints(samples, end = 61_000, window = 60_000, peak = 6_000f) { it.downloadBytesPerSecond }

        assertEquals(61, points.size)
        assertEquals(0f, points.first().x, 1e-6f)
        assertEquals(1f, points.last().x, 1e-6f)
        assertEquals(1f, points.last().y, 1e-6f)
    }

    @Test
    fun `each edge keeps one neighbour so the line slides in and out continuously`() {
        val points = trafficPoints(samples, end = 30_500, window = 60_000, peak = 6_000f) { it.downloadBytesPerSecond }

        assertTrue(points.last().x > 1f)
        assertTrue(points.dropLast(1).all { it.x <= 1f })
    }

    @Test
    fun `advancing time only shifts points left without reshaping the curve`() {
        val before = trafficPoints(samples, end = 40_000, window = 60_000, peak = 6_000f) { it.downloadBytesPerSecond }
        val after = trafficPoints(samples, end = 40_250, window = 60_000, peak = 6_000f) { it.downloadBytesPerSecond }

        val shared = before.zip(after)
        assertTrue(shared.isNotEmpty())
        shared.forEach { (old, new) ->
            assertEquals(old.y, new.y, 1e-6f)
            assertEquals(old.x - 250f / 60_000f, new.x, 1e-6f)
        }
    }

    @Test
    fun `peak keeps headroom and ignores idle noise`() {
        assertEquals(1024f * 1.15f, trafficChartPeak(emptyList()), 1e-3f)
        assertEquals(1024f * 1.15f, trafficChartPeak(listOf(TrafficSample(0, 40, 12))), 1e-3f)
        assertEquals(6_000f * 1.15f, trafficChartPeak(samples), 1e-3f)
    }

    @Test
    fun `display delay is one and a half sampling intervals`() {
        assertEquals(1_500L, trafficDisplayDelay(samples))
        assertEquals(1_500L, trafficDisplayDelay(emptyList()))
        assertEquals(7_500L, trafficDisplayDelay(listOf(TrafficSample(0, 0, 0), TrafficSample(60_000, 0, 0))))
    }
}
