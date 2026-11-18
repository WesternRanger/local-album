package com.localalbum.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.localalbum.app.domain.Album
import com.localalbum.app.domain.AlbumHighlights
import com.localalbum.app.domain.AlbumKind
import com.localalbum.app.domain.LibraryState
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.MediaKind

@Composable
fun HomeScreen(
    state: LibraryState,
    partial: Boolean,
    imageLoader: ImageLoader,
    onOpen: (String) -> Unit,
    onRequestMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            state.loading && state.albums.isEmpty() -> CenterMessage(
                title = "正在整理相册",
                body = "稍等一下，正在读取手机里的照片和视频。",
                loading = true,
            )
            state.error != null && state.albums.isEmpty() -> CenterMessage(
                title = "没能打开相册",
                body = state.error,
                action = "重试",
                onAction = onRetry,
            )
            state.albums.isEmpty() -> CenterMessage(
                title = "还没有照片或视频",
                body = "拍一张照片，或把图片保存到手机后，回到这里就能看到。",
                action = "重新读取",
                onAction = onRetry,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (partial) {
                    item {
                        PartialBanner(onRequestMore)
                    }
                }
                items(state.albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        highlights = AlbumHighlights.fromAlbum(
                            album = album,
                            comments = state.comments,
                            likes = state.likes,
                            thoughtLimit = 1,
                        ),
                        imageLoader = imageLoader,
                        onClick = { onOpen(album.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PartialBanner(onRequestMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("当前只显示你选择的照片和视频", color = Ink, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "可以再选一些，月份相册和文件夹会跟着变多。",
                color = Quiet,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequestMore, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("选择更多", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    highlights: AlbumHighlights,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(
                    item = album.items.firstOrNull(),
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = albumCount(album),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Quiet,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = albumKindLabel(album.kind),
                        color = Quiet.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
            if (highlights.hasContent) {
                Spacer(Modifier.height(12.dp))
                CoverHighlights(highlights = highlights, large = false)
            }
        }
    }
}

@Composable
fun Cover(
    item: MediaItem?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Field),
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            AsyncImage(
                model = item.uri.toUri(),
                contentDescription = item.displayName,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun CenterMessage(
    title: String,
    body: String,
    loading: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun albumCount(album: Album): String {
    val photos = album.items.count { it.kind == MediaKind.PHOTO }
    val videos = album.items.count { it.kind == MediaKind.VIDEO }
    return buildList {
        if (photos > 0) add("${photos} 张照片")
        if (videos > 0) add("${videos} 个视频")
    }.joinToString(" · ").ifEmpty { "0 项" }
}

fun albumKindLabel(kind: AlbumKind): String = when (kind) {
    AlbumKind.MONTH -> "月份"
    AlbumKind.FOLDER -> "文件夹"
    AlbumKind.SMART -> "智能"
}
