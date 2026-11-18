package com.localalbum.app.domain

import java.text.Collator
import java.time.ZoneId
import java.util.Locale

object AlbumCatalog {
    const val LIVE = "smart:live"
    const val VIDEO = "smart:video"
    const val LIKED = "smart:liked"
    const val COMMENTED = "smart:commented"

    private val smartOrder = listOf(LIVE, VIDEO, LIKED, COMMENTED)
    private val nameOrder: Collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }

    fun scoreOf(
        items: List<MediaItem>,
        likes: Set<String>,
        commentCounts: Map<String, Int>,
    ): Int = items.sumOf { item ->
        (if (item.key in likes) 1 else 0) + (commentCounts[item.key] ?: 0)
    }

    fun folderTitle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "未命名文件夹"
        return when (trimmed.lowercase(Locale.US)) {
            "camera" -> "相机"
            "screenshots", "screenshot" -> "截屏"
            "screen recordings", "screenrecord" -> "录屏"
            "download", "downloads" -> "下载"
            "pictures" -> "图片"
            "movies" -> "影片"
            "wechat", "weixin" -> "微信"
            "qq" -> "QQ"
            else -> trimmed
        }
    }

    /**
     * Every month that contains media becomes its own album. System folders and the
     * four smart albums are added beside those months. Albums with likes or comments
     * float to the top; everything else keeps the usual month, folder, then smart order.
     */
    fun build(
        items: List<MediaItem>,
        likes: Set<String>,
        commentCounts: Map<String, Int>,
        zone: ZoneId,
    ): List<Album> {
        if (items.isEmpty()) return emptyList()
        val albums = buildMonths(items, likes, commentCounts, zone) +
            buildFolders(items, likes, commentCounts) +
            buildSmart(items, likes, commentCounts)
        return rank(albums)
    }

    private fun buildMonths(
        items: List<MediaItem>,
        likes: Set<String>,
        commentCounts: Map<String, Int>,
        zone: ZoneId,
    ): List<Album> {
        return items.groupBy { monthKey(it, zone) }.map { (key, group) ->
            val ordered = group.newestFirst()
            val sample = ordered.first()
            album(
                id = "month:$key",
                title = if (key == "unknown") "日期未知" else ChineseDates.yearMonth(
                    sample.dateTakenMillis ?: 0L,
                    zone,
                ),
                kind = AlbumKind.MONTH,
                items = ordered,
                likes = likes,
                commentCounts = commentCounts,
            )
        }
    }

    private fun buildFolders(
        items: List<MediaItem>,
        likes: Set<String>,
        commentCounts: Map<String, Int>,
    ): List<Album> {
        return items.groupBy { it.bucketId.ifBlank { "unknown" } }.map { (bucketId, group) ->
            val ordered = group.newestFirst()
            album(
                id = "folder:$bucketId",
                title = folderTitle(ordered.first().bucketName),
                kind = AlbumKind.FOLDER,
                items = ordered,
                likes = likes,
                commentCounts = commentCounts,
            )
        }
    }

    private fun buildSmart(
        items: List<MediaItem>,
        likes: Set<String>,
        commentCounts: Map<String, Int>,
    ): List<Album> {
        val specs = listOf(
            LIVE to ("实况" to items.filter { it.kind == MediaKind.PHOTO && it.isLive }),
            VIDEO to ("视频" to items.filter { it.kind == MediaKind.VIDEO }),
            LIKED to ("我喜欢的" to items.filter { it.key in likes }),
            COMMENTED to ("有留言" to items.filter { (commentCounts[it.key] ?: 0) > 0 }),
        )
        return specs.mapNotNull { (id, titled) ->
            val (title, group) = titled
            if (group.isEmpty()) return@mapNotNull null
            album(
                id = id,
                title = title,
                kind = AlbumKind.SMART,
                items = group.newestFirst(),
                likes = likes,
                commentCounts = commentCounts,
            )
        }
    }

    private fun album(
        id: String,
        title: String,
        kind: AlbumKind,
        items: List<MediaItem>,
        likes: Set<String>,
        commentCounts: Map<String, Int>,
    ): Album = Album(
        id = id,
        title = title,
        kind = kind,
        items = items,
        score = scoreOf(items, likes, commentCounts),
        sortMillis = items.maxOfOrNull { it.dateTakenMillis ?: 0L } ?: 0L,
    )

    private fun rank(albums: List<Album>): List<Album> {
        val hot = albums.filter { it.score > 0 }.sortedWith(
            compareByDescending<Album> { it.score }
                .thenByDescending { it.sortMillis }
                .thenBy { it.title },
        )
        val rest = albums.filter { it.score == 0 }.sortedWith { left, right ->
            conventional(left, right)
        }
        return hot + rest
    }

    private fun conventional(left: Album, right: Album): Int {
        val byKind = left.kind.ordinal - right.kind.ordinal
        if (byKind != 0) return byKind
        return when (left.kind) {
            AlbumKind.MONTH -> compareValuesBy(right, left) { it.sortMillis }
                .let { dateOrder -> if (dateOrder != 0) dateOrder else left.id.compareTo(right.id) }
            AlbumKind.FOLDER -> {
                val byName = nameOrder.compare(left.title, right.title)
                if (byName != 0) byName else left.id.compareTo(right.id)
            }
            AlbumKind.SMART -> smartOrder.indexOf(left.id).compareTo(smartOrder.indexOf(right.id))
        }
    }

    private fun monthKey(item: MediaItem, zone: ZoneId): String {
        val millis = item.dateTakenMillis
        if (millis == null || millis <= 0L) return "unknown"
        return ChineseDates.yearMonthKey(millis, zone)
    }

    private fun List<MediaItem>.newestFirst(): List<MediaItem> =
        sortedWith(
            compareByDescending<MediaItem> { it.dateTakenMillis ?: Long.MIN_VALUE }
                .thenBy { it.key },
        )
}
