package com.localalbum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhotoTest {
    @Test
    fun detectsGoogleMotionPhotoAndReadsVideoLength() {
        val header = """
            Camera:MotionPhoto="1"
            Camera:MotionPhotoVersion="1"
            Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0"
            Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="4823911"
        """.trimIndent()

        val motion = MotionPhoto.parseEmbedded(header)
        assertEquals(4_823_911L, motion?.bytesFromEnd)
    }

    @Test
    fun detectsLegacyMicroVideoOffset() {
        val header = """GCamera:MicroVideo="1" GCamera:MicroVideoOffset="321000""""
        assertEquals(321_000L, MotionPhoto.parseEmbedded(header)?.bytesFromEnd)
    }

    @Test
    fun ignoresOrdinaryStillsAndDisabledFlags() {
        assertNull(MotionPhoto.parseEmbedded("""DateTimeOriginal 2026:06:01 photo video"""))
        assertNull(MotionPhoto.parseEmbedded("""Camera:MotionPhoto="0" Camera:MotionPhotoVersion="1""""))
        assertNull(MotionPhoto.parseEmbedded(null))
        assertNull(MotionPhoto.parseEmbedded(""))
    }

    @Test
    fun pairsOnlyAClearStillAndShortVideo() {
        val still = still("IMG_100.jpg", time = 1_000_000L)
        val close = clip("IMG_100.MOV", time = 1_001_200L, duration = 1_500)
        val otherName = clip("IMG_200.MOV", time = 1_000_100L, duration = 1_500)
        val far = clip("IMG_100.mp4", time = 1_080_000L, duration = 1_500, key = "far")
        val longClip = clip("IMG_100.mp4", time = 1_000_500L, duration = 30_000, key = "long")
        val otherFolder = clip(
            name = "IMG_100.mp4",
            time = 1_000_200L,
            duration = 1_500,
            bucketId = "other",
            key = "other",
        )

        val paired = MotionPhoto.assignPairs(listOf(still, close, otherName, far, longClip, otherFolder))
        val result = paired.first { it.key == "still" }
        assertEquals(close.uri, result.pairedMotionUri)
        assertTrue(result.isLive)
        assertFalse(otherName.isLive)
        assertTrue(paired.filter { it.kind == MediaKind.VIDEO }.none { it.isLive })
    }

    @Test
    fun doesNotInventAPairWhenTimingAndDurationAreBothUnknown() {
        val still = still("IMG_9.jpg", time = null)
        val clip = clip("IMG_9.mp4", time = null, duration = 0)
        val result = MotionPhoto.assignPairs(listOf(still, clip)).first()
        assertNull(result.pairedMotionUri)
        assertFalse(result.isLive)
    }

    @Test
    fun acceptsAShortClipWhenCaptureTimeIsMissing() {
        assertTrue(MotionPhoto.isClearPair(null, null, videoDurationMs = 1_800))
        assertTrue(MotionPhoto.isClearPair(5_000, 8_000, videoDurationMs = 0))
        assertFalse(MotionPhoto.isClearPair(0, 20_000, videoDurationMs = 40_000))
    }

    private fun still(name: String, time: Long?) = MediaItem(
        key = "still",
        uri = "content://still",
        kind = MediaKind.PHOTO,
        mimeType = "image/jpeg",
        displayName = name,
        dateTakenMillis = time,
        width = 1,
        height = 1,
        durationMs = 0,
        bucketId = "camera",
        bucketName = "Camera",
    )

    private fun clip(
        name: String,
        time: Long?,
        duration: Long,
        bucketId: String = "camera",
        key: String = name,
    ) = MediaItem(
        key = key,
        uri = "content://$key",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
        displayName = name,
        dateTakenMillis = time,
        width = 1,
        height = 1,
        durationMs = duration,
        bucketId = bucketId,
        bucketName = "Camera",
    )
}
