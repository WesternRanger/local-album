package com.localalbum.app.domain

import java.util.Locale
import kotlin.math.abs

data class EmbeddedMotion(
    val bytesFromEnd: Long?,
)

object MotionPhoto {
    const val MAX_MOTION_MS = 5_000L

    private val motionFlag = Regex("""MotionPhoto\s*=\s*"1"""")
    private val microFlag = Regex("""MicroVideo\s*=\s*"1"""")
    private val semantic = Regex("""Semantic\s*=\s*"MotionPhoto"""")
    private val microOffset = Regex("""MicroVideoOffset\s*=\s*"(\d+)"""")
    private val lengthAttr = Regex("""Length\s*=\s*"(\d+)"""")
    private val itemLength = Regex("""Item:Length\s*=\s*"(\d+)"""")

    fun parseEmbedded(header: String?): EmbeddedMotion? {
        if (header.isNullOrEmpty()) return null
        val flagged = motionFlag.containsMatchIn(header) ||
            microFlag.containsMatchIn(header) ||
            semantic.containsMatchIn(header)
        if (!flagged) return null
        val offset = microOffset.find(header)?.groupValues?.get(1)?.toLongOrNull()
        if (offset != null && offset > 0L) return EmbeddedMotion(offset)
        return EmbeddedMotion(lengthNearMotion(header))
    }

    fun stem(displayName: String): String {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return ""
        val dot = trimmed.lastIndexOf('.')
        val base = if (dot > 0) trimmed.substring(0, dot) else trimmed
        return base.lowercase(Locale.US)
    }

    /**
     * A still and a video are a real pair only when they share a folder and file stem,
     * and the clip is short or was captured within a few seconds. Ordinary stills stay still.
     */
    fun isClearPair(stillTime: Long?, videoTime: Long?, videoDurationMs: Long): Boolean {
        val durationKnown = videoDurationMs > 0L
        val short = durationKnown && videoDurationMs <= MAX_MOTION_MS
        val timesKnown = stillTime != null && videoTime != null && stillTime > 0L && videoTime > 0L
        val close = timesKnown && abs(stillTime - videoTime) <= MAX_MOTION_MS
        return (short && close) || (short && !timesKnown) || (close && !durationKnown)
    }

    fun assignPairs(items: List<MediaItem>): List<MediaItem> {
        val videos = items.filter { it.kind == MediaKind.VIDEO }
        if (videos.isEmpty()) {
            return items.map { item ->
                if (item.kind == MediaKind.PHOTO && item.pairedMotionUri != null) {
                    item.copy(pairedMotionUri = null)
                } else {
                    item
                }
            }
        }
        return items.map { item ->
            if (item.kind != MediaKind.PHOTO) return@map item
            val name = stem(item.displayName)
            if (name.isEmpty()) return@map item.copy(pairedMotionUri = null)
            val match = videos
                .asSequence()
                .filter { video ->
                    video.bucketId == item.bucketId &&
                        stem(video.displayName) == name &&
                        isClearPair(item.dateTakenMillis, video.dateTakenMillis, video.durationMs)
                }
                .minWithOrNull(
                    compareBy<MediaItem>(
                        { video -> timeGap(item.dateTakenMillis, video.dateTakenMillis) },
                        { video -> video.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE },
                    ),
                )
            item.copy(pairedMotionUri = match?.uri)
        }
    }

    private fun timeGap(still: Long?, video: Long?): Long {
        if (still == null || video == null || still <= 0L || video <= 0L) return Long.MAX_VALUE
        return abs(still - video)
    }

    private fun lengthNearMotion(header: String): Long? {
        val matches = semantic.findAll(header).toList()
        for (match in matches.asReversed()) {
            val start = (match.range.first - 500).coerceAtLeast(0)
            val end = (match.range.last + 500).coerceAtMost(header.length)
            val window = header.substring(start, end)
            val value = lengthAttr.findAll(window)
                .mapNotNull { it.groupValues[1].toLongOrNull() }
                .lastOrNull { it > 0L }
            if (value != null) return value
        }
        return itemLength.findAll(header)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .lastOrNull { it > 0L }
    }
}
