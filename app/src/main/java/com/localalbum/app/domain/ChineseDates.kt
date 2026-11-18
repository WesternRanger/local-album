package com.localalbum.app.domain

import java.time.Instant
import java.time.ZoneId

object ChineseDates {
    fun yearMonth(millis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        return "${date.year}年${date.monthValue}月"
    }

    fun yearMonthKey(millis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        return "%04d-%02d".format(date.year, date.monthValue)
    }

    fun captureDate(millis: Long?, zone: ZoneId): String {
        if (millis == null || millis <= 0L) return "日期未知"
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}

object Durations {
    fun format(ms: Long): String {
        if (ms <= 0L) return ""
        val total = ms / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
