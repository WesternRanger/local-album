package com.localalbum.app.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.MediaKind
import com.localalbum.app.domain.MotionPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

class MotionFileProbe(
    private val context: Context,
) {
    suspend fun mark(items: List<MediaItem>): List<MediaItem> = coroutineScope {
        val gate = Semaphore(4)
        items.map { item ->
            async(Dispatchers.IO) {
                gate.withPermit { probe(item) }
            }
        }.awaitAll()
    }

    private fun probe(item: MediaItem): MediaItem {
        if (item.kind != MediaKind.PHOTO || !mayContainMotion(item.mimeType)) return item
        val maxBytes = if (isHeif(item.mimeType)) 1_500_000 else 262_144
        val header = readHeader(Uri.parse(item.uri), maxBytes) ?: return item
        val embedded = MotionPhoto.parseEmbedded(header) ?: return item
        return item.copy(embeddedMotion = true, embeddedMotionBytes = embedded.bytesFromEnd)
    }

    private fun readHeader(uri: Uri, maxBytes: Int): String? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                val buffer = ByteArray(maxBytes)
                var read = 0
                while (read < maxBytes) {
                    val count = input.read(buffer, read, maxBytes - read)
                    if (count < 0) break
                    read += count
                }
                if (read <= 0) null else String(buffer, 0, read, Charsets.ISO_8859_1)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun mayContainMotion(mime: String): Boolean {
        val value = mime.lowercase(Locale.US)
        return value.contains("jpeg") || value.contains("jpg") || isHeif(value)
    }

    private fun isHeif(mime: String): Boolean {
        val value = mime.lowercase(Locale.US)
        return value.contains("heic") || value.contains("heif")
    }
}

class MotionVideoStore(
    private val context: Context,
) {
    fun playable(item: MediaItem): Uri? {
        val paired = item.pairedMotionUri
        if (!paired.isNullOrBlank()) return Uri.parse(paired)
        if (!item.embeddedMotion) return null
        return extract(Uri.parse(item.uri), item.embeddedMotionBytes)
    }

    private fun extract(uri: Uri, bytesFromEnd: Long?): Uri? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val size = pfd.statSize
            if (size <= 32L) return null
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val starts = ArrayList<Long>()
                if (bytesFromEnd != null && bytesFromEnd > 32L) {
                    val tail = size - bytesFromEnd
                    if (tail >= 0L) starts += tail
                    if (bytesFromEnd < size - 32L) starts += bytesFromEnd
                }
                for (start in starts) {
                    if (looksLikeMp4(channel, start)) {
                        val file = writeRange(pfd, start, size - start) ?: continue
                        return Uri.fromFile(file)
                    }
                }
                val found = findFtyp(channel, size) ?: return null
                val file = writeRange(pfd, found, size - found) ?: return null
                Uri.fromFile(file)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun looksLikeMp4(channel: FileChannel, start: Long): Boolean {
        if (start < 0L) return false
        val buffer = ByteBuffer.allocate(12)
        val read = channel.read(buffer, start)
        if (read < 8) return false
        buffer.flip()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (bytes.size < 8) return false
        return String(bytes, 4, 4, Charsets.ISO_8859_1) == "ftyp"
    }

    private fun findFtyp(channel: FileChannel, size: Long): Long? {
        val window = minOf(size, 8L * 1024L * 1024L).toInt()
        val start = size - window
        val buffer = ByteBuffer.allocate(window)
        var position = start
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, position)
            if (count < 0) break
            position += count
            if (count == 0) break
        }
        buffer.flip()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var index = 4
        while (index + 4 <= bytes.size) {
            if (bytes[index] == 'f'.code.toByte() &&
                bytes[index + 1] == 't'.code.toByte() &&
                bytes[index + 2] == 'y'.code.toByte() &&
                bytes[index + 3] == 'p'.code.toByte()
            ) {
                val boxStart = start + index - 4
                if (looksLikeMp4(channel, boxStart)) return boxStart
            }
            index++
        }
        return null
    }

    private fun writeRange(pfd: ParcelFileDescriptor, start: Long, length: Long): File? {
        if (length <= 32L || length > 80L * 1024L * 1024L) return null
        val dir = File(context.cacheDir, "motion").apply { mkdirs() }
        val out = File(dir, "clip-${start}-${length}.mp4")
        FileInputStream(pfd.fileDescriptor).channel.use { source ->
            FileOutputStream(out).channel.use { dest ->
                var position = start
                val end = start + length
                while (position < end) {
                    val count = source.transferTo(position, end - position, dest)
                    if (count <= 0L) break
                    position += count
                }
            }
        }
        return out.takeIf { it.length() > 32L }
    }
}
