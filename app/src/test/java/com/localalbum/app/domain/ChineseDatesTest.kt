package com.localalbum.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseDatesTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun formatsYearMonthWithoutPadding() {
        val millis = Instant.parse("2026-06-15T02:00:00Z").toEpochMilli()
        assertEquals("2026年6月", ChineseDates.yearMonth(millis, zone))
        assertEquals("2026-06", ChineseDates.yearMonthKey(millis, zone))
    }

    @Test
    fun formatsCaptureDateAndOmitsMissingDates() {
        val millis = Instant.parse("2026-06-15T02:30:00Z").toEpochMilli()
        assertEquals("2026年6月15日", ChineseDates.captureDate(millis, zone))
        assertEquals("日期未知", ChineseDates.captureDate(null, zone))
        assertEquals("日期未知", ChineseDates.captureDate(0L, zone))
    }

    @Test
    fun formatsVideoDuration() {
        assertEquals("", Durations.format(0))
        assertEquals("0:03", Durations.format(3_200))
        assertEquals("1:05", Durations.format(65_000))
        assertEquals("1:02:03", Durations.format(3_723_000))
    }
}
