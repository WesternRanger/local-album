package com.localalbum.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.MediaKind
import java.io.File

class MediaStoreReader(
    private val context: Context,
) {
    fun load(): List<MediaItem> = query(images = true) + query(images = false)

    private fun query(images: Boolean): List<MediaItem> {
        val collection = if (images) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
        )
        if (!images) projection += MediaStore.MediaColumns.DURATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.MediaColumns.BUCKET_ID
            projection += MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        } else {
            projection += MediaStore.MediaColumns.DATA
        }

        val selection = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
            }
        }.joinToString(" AND ").ifBlank { null }

        val sort = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        val found = ArrayList<MediaItem>()
        context.contentResolver.query(collection, projection.toTypedArray(), selection, null, sort)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val taken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                    val added = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                    val (bucketId, bucketName) = bucketOf(cursor, bucketIdCol, bucketNameCol, dataCol)
                    found += MediaItem(
                        key = if (images) "image:$id" else "video:$id",
                        uri = uri,
                        kind = if (images) MediaKind.PHOTO else MediaKind.VIDEO,
                        mimeType = cursor.string(mimeCol)
                            ?: if (images) "image/jpeg" else "video/mp4",
                        displayName = cursor.string(nameCol) ?: "未命名",
                        dateTakenMillis = when {
                            taken > 0L -> taken
                            added > 0L -> added * 1000L
                            else -> null
                        },
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                        durationMs = if (!images && durationCol >= 0) cursor.getLong(durationCol) else 0L,
                        bucketId = bucketId,
                        bucketName = bucketName,
                    )
                }
            }
        return found
    }

    private fun bucketOf(
        cursor: android.database.Cursor,
        bucketIdCol: Int,
        bucketNameCol: Int,
        dataCol: Int,
    ): Pair<String, String> {
        if (bucketIdCol >= 0) {
            val id = cursor.string(bucketIdCol)?.ifBlank { null } ?: "unknown"
            val name = if (bucketNameCol >= 0) cursor.string(bucketNameCol).orEmpty() else ""
            return id to name
        }
        val path = if (dataCol >= 0) cursor.string(dataCol).orEmpty() else ""
        val parent = File(path).parentFile
        val name = parent?.name.orEmpty()
        val id = parent?.absolutePath?.ifBlank { null } ?: name.ifBlank { "unknown" }
        return id to name
    }

    private fun android.database.Cursor.string(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }
}
