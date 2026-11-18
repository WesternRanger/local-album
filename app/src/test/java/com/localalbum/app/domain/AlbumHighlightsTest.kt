package com.localalbum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumHighlightsTest {
    @Test
    fun stripsEmojiAndKeepsTheThought() {
        assertEquals("今天真好", AlbumHighlights.thoughtText("今天真好 😊"))
        assertEquals(listOf("😊"), AlbumHighlights.emojiIn("今天真好 😊"))
        assertEquals(listOf("❤\uFE0F"), AlbumHighlights.emojiIn("喜欢❤\uFE0F"))
    }

    @Test
    fun ranksRepeatedThoughtsAndEmoji() {
        val album = Album(
            id = "month:2026-06",
            title = "2026年6月",
            kind = AlbumKind.MONTH,
            items = listOf(photo("liked"), photo("plain")),
            score = 0,
            sortMillis = 0,
        )
        val comments = mapOf(
            "liked" to listOf(
                Comment(1, "今天真好 😊", 2),
                Comment(2, "今天真好 ❤\uFE0F", 3),
            ),
            "plain" to listOf(
                Comment(3, "今天真好 😊", 1),
                Comment(4, "风很轻 🌿", 4),
            ),
        )
        val highlights = AlbumHighlights.fromAlbum(
            album = album,
            comments = comments,
            likes = setOf("liked"),
            thoughtLimit = 2,
        )
        assertEquals(listOf("今天真好", "风很轻"), highlights.thoughts)
        assertEquals("😊", highlights.emoji.first())
        assertTrue(highlights.emoji.contains("❤\uFE0F"))
        assertTrue(highlights.emoji.contains("🌿"))
    }

    @Test
    fun emojiOnlyCommentsDoNotInventAThought() {
        val album = Album(
            id = "month:2026-06",
            title = "2026年6月",
            kind = AlbumKind.MONTH,
            items = listOf(photo("one")),
            score = 0,
            sortMillis = 0,
        )
        val highlights = AlbumHighlights.fromAlbum(
            album = album,
            comments = mapOf("one" to listOf(Comment(1, "😊😊", 1))),
            likes = emptySet(),
            thoughtLimit = 1,
        )
        assertTrue(highlights.thoughts.isEmpty())
        assertEquals(listOf("😊"), highlights.emoji)
    }

    @Test
    fun albumWithoutCommentsHasNoHighlights() {
        val album = Album(
            id = "month:2026-08",
            title = "2026年8月",
            kind = AlbumKind.MONTH,
            items = listOf(photo("quiet")),
            score = 0,
            sortMillis = 0,
        )
        val highlights = AlbumHighlights.fromAlbum(album, emptyMap(), emptySet(), thoughtLimit = 2)
        assertFalse(highlights.hasContent)
    }

    private fun photo(key: String) = MediaItem(
        key = key,
        uri = "content://$key",
        kind = MediaKind.PHOTO,
        mimeType = "image/jpeg",
        displayName = "$key.jpg",
        dateTakenMillis = 1L,
        width = 8,
        height = 8,
        durationMs = 0,
        bucketId = "cam",
        bucketName = "Camera",
    )
}
