package com.localalbum.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCatalogTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun eachMonthIsItsOwnAlbum() {
        val june = photo("june", at("2026-06-15T02:00:00Z"))
        val july = photo("july", at("2026-07-02T02:00:00Z"))
        val albums = AlbumCatalog.build(listOf(june, july), emptySet(), emptyMap(), zone)
        val months = albums.filter { it.kind == AlbumKind.MONTH }

        assertEquals(listOf("2026年7月", "2026年6月"), months.map { it.title })
        assertEquals(listOf("july"), months[0].items.map { it.key })
        assertEquals(listOf("june"), months[1].items.map { it.key })
        assertTrue(months[1].items.none { it.key == "july" })
    }

    @Test
    fun monthBoundaryUsesTheGivenZone() {
        val instant = at("2026-06-30T16:30:00Z")
        val albums = AlbumCatalog.build(listOf(photo("edge", instant)), emptySet(), emptyMap(), zone)
        val months = albums.filter { it.kind == AlbumKind.MONTH }

        assertEquals(listOf("2026年7月"), months.map { it.title })
        assertEquals("month:2026-07", months.single().id)
    }

    @Test
    fun undatedMediaGetsItsOwnAlbum() {
        val albums = AlbumCatalog.build(listOf(photo("none", null)), emptySet(), emptyMap(), zone)
        val month = albums.single { it.kind == AlbumKind.MONTH }
        assertEquals("日期未知", month.title)
        assertEquals("month:unknown", month.id)
    }

    @Test
    fun foldersUseMediaStoreBucketsAndFriendlyNames() {
        val camera = photo("a", at("2026-06-01T02:00:00Z"), bucketId = "cam", bucketName = "Camera")
        val shot = photo("b", at("2026-06-02T02:00:00Z"), bucketId = "shots", bucketName = "Screenshots")
        val albums = AlbumCatalog.build(listOf(camera, shot), emptySet(), emptyMap(), zone)
        val folders = albums.filter { it.kind == AlbumKind.FOLDER }

        assertEquals(setOf("相机", "截屏"), folders.map { it.title }.toSet())
        assertEquals(listOf("a"), folders.first { it.title == "相机" }.items.map { it.key })
        assertEquals(listOf("b"), folders.first { it.title == "截屏" }.items.map { it.key })
    }

    @Test
    fun smartAlbumsAppearOnlyWhenTheyHaveItems() {
        val still = photo("still", at("2026-06-01T02:00:00Z"))
        val live = photo("live", at("2026-06-02T02:00:00Z"), embedded = true)
        val clip = video("clip", at("2026-06-03T02:00:00Z"), name = "clip.mp4", durationMs = 12_000)
        val emptySmart = AlbumCatalog.build(listOf(still), emptySet(), emptyMap(), zone)
        assertTrue(emptySmart.none { it.kind == AlbumKind.SMART })

        val albums = AlbumCatalog.build(
            items = listOf(still, live, clip),
            likes = setOf("still"),
            commentCounts = mapOf("live" to 2),
            zone = zone,
        )
        val smart = albums.filter { it.kind == AlbumKind.SMART }.associateBy { it.id }
        assertEquals(listOf("live"), smart.getValue(AlbumCatalog.LIVE).items.map { it.key })
        assertEquals(listOf("clip"), smart.getValue(AlbumCatalog.VIDEO).items.map { it.key })
        assertEquals(listOf("still"), smart.getValue(AlbumCatalog.LIKED).items.map { it.key })
        assertEquals(listOf("live"), smart.getValue(AlbumCatalog.COMMENTED).items.map { it.key })
        assertFalse(smart.getValue(AlbumCatalog.LIVE).items.any { it.key == "still" })
    }

    @Test
    fun engagedAlbumsRankAheadOfTheUsualOrder() {
        val june = photo("june", at("2026-06-15T02:00:00Z"), bucketId = "cam", bucketName = "Camera")
        val july = photo("july", at("2026-07-02T02:00:00Z"), bucketId = "shots", bucketName = "Screenshots")
        val august = photo("august", at("2026-08-01T02:00:00Z"), bucketId = "cam", bucketName = "Camera")
        val clip = video("clip", at("2026-08-02T02:00:00Z"), bucketId = "cam", bucketName = "Camera")
        val albums = AlbumCatalog.build(
            items = listOf(june, july, august, clip),
            likes = setOf("july"),
            commentCounts = mapOf("july" to 2),
            zone = zone,
        )

        assertEquals(3, albums.first { it.id == "month:2026-07" }.score)
        assertEquals(
            listOf(
                "month:2026-07",
                AlbumCatalog.LIKED,
                "folder:shots",
                AlbumCatalog.COMMENTED,
                "month:2026-08",
                "month:2026-06",
                "folder:cam",
                AlbumCatalog.VIDEO,
            ),
            albums.map { it.id },
        )
        assertTrue(albums.take(4).all { it.score > 0 })
        assertTrue(albums.drop(4).all { it.score == 0 })
        assertEquals(AlbumKind.MONTH, albums[4].kind)
        assertEquals(AlbumKind.FOLDER, albums[6].kind)
        assertEquals(AlbumKind.SMART, albums[7].kind)
    }

    @Test
    fun scoreAddsEveryLikeAndEveryComment() {
        val items = listOf(photo("a", 1L), photo("b", 2L))
        assertEquals(4, AlbumCatalog.scoreOf(items, setOf("a", "b"), mapOf("a" to 2)))
    }

    @Test
    fun emptyLibraryProducesNoAlbums() {
        assertTrue(AlbumCatalog.build(emptyList(), emptySet(), emptyMap(), zone).isEmpty())
    }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun photo(
        key: String,
        at: Long?,
        bucketId: String = "cam",
        bucketName: String = "Camera",
        embedded: Boolean = false,
    ) = MediaItem(
        key = key,
        uri = "content://media/$key",
        kind = MediaKind.PHOTO,
        mimeType = "image/jpeg",
        displayName = "$key.jpg",
        dateTakenMillis = at,
        width = 10,
        height = 10,
        durationMs = 0,
        bucketId = bucketId,
        bucketName = bucketName,
        embeddedMotion = embedded,
        embeddedMotionBytes = if (embedded) 1000L else null,
    )

    private fun video(
        key: String,
        at: Long?,
        name: String = "$key.mp4",
        bucketId: String = "cam",
        bucketName: String = "Camera",
        durationMs: Long = 1_500,
    ) = MediaItem(
        key = key,
        uri = "content://media/$key",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
        displayName = name,
        dateTakenMillis = at,
        width = 10,
        height = 10,
        durationMs = durationMs,
        bucketId = bucketId,
        bucketName = bucketName,
    )
}
