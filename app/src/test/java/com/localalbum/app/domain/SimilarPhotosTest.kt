package com.localalbum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarPhotosTest {
    @Test
    fun nearDuplicatesFormOneGroupNewestFirst() {
        val older = photo("older", 1L)
        val newer = photo("newer", 5L)
        val groups = SimilarPhotos.group(
            items = listOf(older, newer),
            hashes = mapOf("older" to 0L, "newer" to 1L),
        )
        assertEquals(listOf("newer", "older"), groups.single().items.map { it.key })
    }

    @Test
    fun distantPhotosStaySeparate() {
        val groups = SimilarPhotos.group(
            items = listOf(photo("a", 1L), photo("b", 2L)),
            hashes = mapOf("a" to 0L, "b" to bits(SimilarPhotos.MAX_DISTANCE + 1)),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun transitiveNearDuplicatesStayTogether() {
        val first = 0L
        val middle = bits(SimilarPhotos.MAX_DISTANCE)
        val last = middle or (1L shl SimilarPhotos.MAX_DISTANCE)
        val groups = SimilarPhotos.group(
            items = listOf(photo("a", 1L), photo("b", 2L), photo("c", 3L)),
            hashes = mapOf("a" to first, "b" to middle, "c" to last),
        )
        assertEquals(setOf("a", "b", "c"), groups.single().items.map { it.key }.toSet())
    }

    @Test
    fun videosAndSinglesAreNotGroups() {
        val groups = SimilarPhotos.group(
            items = listOf(
                photo("only", 1L),
                photo("clip", 2L, kind = MediaKind.VIDEO),
                photo("other-clip", 3L, kind = MediaKind.VIDEO),
            ),
            hashes = mapOf("only" to 0L, "clip" to 0L, "other-clip" to 0L),
        )
        assertTrue(groups.isEmpty())
    }

    private fun bits(count: Int): Long {
        var value = 0L
        repeat(count) { index -> value = value or (1L shl index) }
        return value
    }

    private fun photo(
        key: String,
        at: Long,
        kind: MediaKind = MediaKind.PHOTO,
    ) = MediaItem(
        key = key,
        uri = "content://$key",
        kind = kind,
        mimeType = if (kind == MediaKind.PHOTO) "image/jpeg" else "video/mp4",
        displayName = "$key.jpg",
        dateTakenMillis = at,
        width = 8,
        height = 8,
        durationMs = 0,
        bucketId = "cam",
        bucketName = "Camera",
    )
}
