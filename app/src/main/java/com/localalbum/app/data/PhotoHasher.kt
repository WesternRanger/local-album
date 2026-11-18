package com.localalbum.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.localalbum.app.domain.DHash
import com.localalbum.app.domain.MediaItem

/**
 * Downsamples a still photo on this phone and turns it into a [DHash].
 * Nothing leaves the device.
 */
class PhotoHasher(
    private val context: Context,
) {
    private val cache = HashMap<String, Long>()

    fun hash(item: MediaItem): Long? = synchronized(cache) {
        cache[item.key]?.let { return it }
        val value = compute(item) ?: return null
        cache[item.key] = value
        value
    }

    private fun compute(item: MediaItem): Long? {
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val uri = Uri.parse(item.uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null
            scaled = Bitmap.createScaledBitmap(decoded, 9, 8, true)
            val luma = IntArray(9 * 8)
            var index = 0
            for (y in 0 until 8) {
                for (x in 0 until 9) {
                    luma[index++] = lumaOf(scaled.getPixel(x, y))
                }
            }
            DHash.fromLuma(width = 9, height = 8, luma = luma)
        } catch (_: Exception) {
            null
        } finally {
            if (scaled != null && scaled !== decoded) scaled.recycle()
            decoded?.recycle()
        }
    }

    private fun lumaOf(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > 72 && currentHeight > 72) {
            sample *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sample
    }
}
