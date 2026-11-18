package com.localalbum.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.PrivatePhoto
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Entity(tableName = "private_photos")
data class PrivatePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val dateTakenMillis: Long?,
    val width: Int,
    val height: Int,
    val addedAt: Long,
)

@Dao
interface PrivatePhotoDao {
    @Query("SELECT * FROM private_photos ORDER BY addedAt DESC")
    fun observe(): Flow<List<PrivatePhotoEntity>>

    @Query("SELECT * FROM private_photos WHERE id = :id")
    suspend fun get(id: Long): PrivatePhotoEntity?

    @Insert
    suspend fun insert(entity: PrivatePhotoEntity): Long

    @Query("DELETE FROM private_photos WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [PrivatePhotoEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PrivateDatabase : RoomDatabase() {
    abstract fun photos(): PrivatePhotoDao
}

/**
 * Copies photos into app-private storage. They stay out of the normal album list
 * only after the original MediaStore item is deleted.
 */
class PrivateLibrary(
    private val context: Context,
    private val dao: PrivatePhotoDao,
) {
    fun observe(): Flow<List<PrivatePhoto>> = dao.observe().map { rows ->
        rows.map { row ->
            PrivatePhoto(
                id = row.id,
                uri = Uri.fromFile(File(directory(), row.fileName)).toString(),
                displayName = row.displayName,
                mimeType = row.mimeType,
                dateTakenMillis = row.dateTakenMillis,
            )
        }
    }

    suspend fun copyIn(item: MediaItem): Long = withContext(Dispatchers.IO) {
        val folder = directory()
        val file = File(folder, "${UUID.randomUUID()}.${extension(item)}")
        try {
            context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("empty")
            dao.insert(
                PrivatePhotoEntity(
                    fileName = file.name,
                    displayName = item.displayName.ifBlank { "照片" },
                    mimeType = item.mimeType.ifBlank { "image/jpeg" },
                    dateTakenMillis = item.dateTakenMillis,
                    width = item.width,
                    height = item.height,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    suspend fun discard(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            val row = dao.get(id) ?: return@forEach
            File(directory(), row.fileName).delete()
            dao.delete(id)
        }
    }

    suspend fun exportToGallery(ids: List<Long>): Boolean = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext false
        var exported = 0
        ids.forEach { id ->
            val row = dao.get(id) ?: return@forEach
            val file = File(directory(), row.fileName)
            if (writeToGallery(row, file)) {
                file.delete()
                dao.delete(id)
                exported += 1
            }
        }
        exported == ids.size
    }

    private fun writeToGallery(row: PrivatePhotoEntity, file: File): Boolean {
        if (!file.exists()) return false
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, uniqueName(row.displayName))
            put(MediaStore.Images.Media.MIME_TYPE, row.mimeType.ifBlank { "image/jpeg" })
            if (row.dateTakenMillis != null) {
                put(MediaStore.Images.Media.DATE_TAKEN, row.dateTakenMillis)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/本地相册")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun directory(): File = File(context.filesDir, "private").apply { mkdirs() }

    private fun extension(item: MediaItem): String = when (item.mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }

    private fun uniqueName(displayName: String): String {
        val safe = displayName.replace(Regex("[\\\\/\\u0000]"), "_").ifBlank { "照片.jpg" }
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ".jpg"
        return "$base-${System.currentTimeMillis()}$ext"
    }
}
