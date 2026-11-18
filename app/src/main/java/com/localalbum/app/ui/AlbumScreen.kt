package com.localalbum.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.localalbum.app.domain.Durations
import com.localalbum.app.domain.MediaKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    album: Album?,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = album?.title ?: "相册",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("返回", style = MaterialTheme.typography.titleMedium, color = Blue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        when {
            album == null -> CenterMessage(
                title = "正在打开",
                body = "相册内容马上就到。",
                loading = true,
            )
            album.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                CenterMessage(
                    title = "这个相册是空的",
                    body = "返回后可以看看其他相册。",
                    action = "返回",
                    onAction = onBack,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(album.items, key = { _, item -> item.key }) { index, item ->
                    Thumb(item, imageLoader, onClick = { onOpen(index) })
                }
            }
        }
    }
}

@Composable
private fun Thumb(
    item: com.localalbum.app.domain.MediaItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Field,
        modifier = Modifier.aspectRatio(1f),
    ) {
        Box(Modifier.clip(RoundedCornerShape(12.dp))) {
            AsyncImage(
                model = item.uri.toUri(),
                contentDescription = if (item.kind == MediaKind.VIDEO) "视频 ${item.displayName}" else item.displayName,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.kind == MediaKind.VIDEO) {
                val duration = Durations.format(item.durationMs)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    if (duration.isNotEmpty()) {
                        Text(
                            duration,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
