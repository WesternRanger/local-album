package com.localalbum.app.domain

enum class MediaKind {
    PHOTO,
    VIDEO,
}

data class MediaItem(
    val key: String,
    val uri: String,
    val kind: MediaKind,
    val mimeType: String,
    val displayName: String,
    val dateTakenMillis: Long?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: String,
    val bucketName: String,
    val embeddedMotion: Boolean = false,
    val embeddedMotionBytes: Long? = null,
    val pairedMotionUri: String? = null,
) {
    val isLive: Boolean
        get() = embeddedMotion || !pairedMotionUri.isNullOrBlank()
}

enum class AlbumKind {
    MONTH,
    FOLDER,
    SMART,
}

data class Album(
    val id: String,
    val title: String,
    val kind: AlbumKind,
    val items: List<MediaItem>,
    val score: Int,
    val sortMillis: Long,
)

data class Comment(
    val id: Long,
    val body: String,
    val createdAt: Long,
)

data class LibraryState(
    val albums: List<Album> = emptyList(),
    val likes: Set<String> = emptySet(),
    val comments: Map<String, List<Comment>> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
)
