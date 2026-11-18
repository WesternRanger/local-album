package com.localalbum.app.data

import com.localalbum.app.domain.Comment
import com.localalbum.app.domain.LibraryState
import com.localalbum.app.domain.AlbumCatalog
import com.localalbum.app.domain.MediaKind
import com.localalbum.app.domain.MotionPhoto
import com.localalbum.app.domain.SimilarGroup
import com.localalbum.app.domain.SimilarPhotos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import kotlin.coroutines.coroutineContext

class LibraryRepository(
    private val reader: MediaStoreReader,
    private val probe: MotionFileProbe,
    private val database: AlbumDatabase,
    private val hasher: PhotoHasher,
) {
    private val items = MutableStateFlow<List<com.localalbum.app.domain.MediaItem>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val gate = Mutex()
    private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: kotlinx.coroutines.Job? = null
    private var scanGeneration = 0
    private val similarGroupsFlow = MutableStateFlow<List<SimilarGroup>>(emptyList())
    private val similarScanningFlow = MutableStateFlow(true)
    val similarGroups: StateFlow<List<SimilarGroup>> = similarGroupsFlow
    val similarScanning: StateFlow<Boolean> = similarScanningFlow

    fun observe(): Flow<LibraryState> = combine(
        items,
        database.likes().observeLiked(),
        database.comments().observeAll(),
        loading,
        error,
    ) { media, likeRows, commentRows, isLoading, message ->
        val likes = likeRows.mapTo(mutableSetOf()) { it.mediaKey }
        val usable = commentRows.filter { it.body.isNotBlank() }
        val grouped = usable.groupBy { it.mediaKey }
        LibraryState(
            albums = if (media.isEmpty()) {
                emptyList()
            } else {
                AlbumCatalog.build(
                    items = media,
                    likes = likes,
                    commentCounts = grouped.mapValues { it.value.size },
                    zone = ZoneId.systemDefault(),
                )
            },
            likes = likes,
            comments = grouped.mapValues { (_, rows) ->
                rows.map { Comment(it.id, it.body, it.createdAt) }
            },
            loading = isLoading,
            error = message,
        )
    }.flowOn(Dispatchers.Default)

    suspend fun refresh() {
        similarScanningFlow.value = true
        gate.withLock {
            loading.value = true
            error.value = null
            val loaded = try {
                withContext(Dispatchers.IO) { reader.load() }
            } catch (_: SecurityException) {
                error.value = "没有读取照片和视频的权限"
                loading.value = false
                scanSimilar(emptyList())
                return
            } catch (_: Exception) {
                error.value = "读取相册时出错"
                loading.value = false
                scanSimilar(emptyList())
                return
            }
            items.value = MotionPhoto.assignPairs(loaded)
            loading.value = false
            val probed = try {
                withContext(Dispatchers.IO) { probe.mark(items.value) }
            } catch (_: Exception) {
                scanSimilar(items.value)
                return
            }
            items.value = MotionPhoto.assignPairs(probed)
            scanSimilar(items.value)
        }
    }

    private fun scanSimilar(media: List<com.localalbum.app.domain.MediaItem>) {
        val generation = ++scanGeneration
        scanJob?.cancel()
        val photos = media.filter { it.kind == MediaKind.PHOTO }
        if (photos.isEmpty()) {
            similarGroupsFlow.value = emptyList()
            similarScanningFlow.value = false
            return
        }
        similarScanningFlow.value = true
        scanJob = scanScope.launch {
            val hashes = HashMap<String, Long>()
            for (photo in photos) {
                coroutineContext.ensureActive()
                val hash = withContext(Dispatchers.IO) { hasher.hash(photo) }
                if (hash != null) hashes[photo.key] = hash
            }
            if (generation != scanGeneration) return@launch
            similarGroupsFlow.value = SimilarPhotos.group(photos, hashes)
            similarScanningFlow.value = false
        }
    }

    suspend fun toggleLike(mediaKey: String) {
        val liked = database.likes().get(mediaKey)?.liked == true
        database.likes().upsert(LikeEntity(mediaKey, !liked))
    }

    suspend fun addComment(mediaKey: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        database.comments().insert(
            CommentEntity(
                mediaKey = mediaKey,
                body = body.take(MAX_COMMENT),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val MAX_COMMENT = 200
    }
}
