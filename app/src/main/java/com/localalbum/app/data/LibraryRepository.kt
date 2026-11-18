package com.localalbum.app.data

import com.localalbum.app.domain.Comment
import com.localalbum.app.domain.LibraryState
import com.localalbum.app.domain.AlbumCatalog
import com.localalbum.app.domain.MotionPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

class LibraryRepository(
    private val reader: MediaStoreReader,
    private val probe: MotionFileProbe,
    private val database: AlbumDatabase,
) {
    private val items = MutableStateFlow<List<com.localalbum.app.domain.MediaItem>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val gate = Mutex()

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
        gate.withLock {
            loading.value = true
            error.value = null
            val loaded = try {
                withContext(Dispatchers.IO) { reader.load() }
            } catch (_: SecurityException) {
                error.value = "没有读取照片和视频的权限"
                loading.value = false
                return
            } catch (_: Exception) {
                error.value = "读取相册时出错"
                loading.value = false
                return
            }
            items.value = MotionPhoto.assignPairs(loaded)
            loading.value = false
            val probed = try {
                withContext(Dispatchers.IO) { probe.mark(items.value) }
            } catch (_: Exception) {
                return
            }
            items.value = MotionPhoto.assignPairs(probed)
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
