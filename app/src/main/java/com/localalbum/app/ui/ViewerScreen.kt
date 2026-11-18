package com.localalbum.app.ui

import android.net.Uri
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.localalbum.app.LocalAlbumApp
import com.localalbum.app.domain.ChineseDates
import com.localalbum.app.domain.Comment
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.MediaKind
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    likes: Set<String>,
    comments: Map<String, List<Comment>>,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddComment: (String, String) -> Unit,
    initialScale: Float = 1f,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            TextButton(onClick = onBack) {
                Text("返回", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    var motionPlaying by remember { mutableStateOf(false) }
    var motionError by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        zoomed = false
        motionPlaying = false
        motionError = false
    }
    val current = items[pagerState.currentPage]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhotoWell)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed && !motionPlaying,
                beyondViewportPageCount = 1,
                key = { items[it].key },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                val active = page == pagerState.currentPage
                when {
                    item.kind == MediaKind.VIDEO && active -> MediaPlayer(
                        uri = item.uri.toUri(),
                        onFinished = null,
                        onFailed = null,
                        onSwipe = { direction ->
                            val target = page + direction
                            if (target in items.indices) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        },
                    )
                    item.kind == MediaKind.PHOTO && active && motionPlaying -> MotionPlayback(
                        item = item,
                        onFinished = { motionPlaying = false },
                        onFailed = {
                            motionPlaying = false
                            motionError = true
                        },
                        onSwipe = { direction ->
                            motionPlaying = false
                            val target = page + direction
                            if (target in items.indices) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        },
                    )
                    item.kind == MediaKind.PHOTO -> ZoomablePhoto(
                        item = item,
                        imageLoader = imageLoader,
                        initialScale = if (active) initialScale else 1f,
                        onZoomed = { if (active) zoomed = it },
                    )
                    else -> Cover(
                        item = item,
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Surface(
                onClick = onBack,
                color = Color(0xCC101318),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    "返回",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            Surface(
                color = Color(0xCC101318),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        ViewerPanel(
            item = current,
            liked = current.key in likes,
            comments = comments[current.key].orEmpty(),
            motionError = motionError,
            motionPlaying = motionPlaying,
            onToggleLike = { onToggleLike(current.key) },
            onPlayMotion = { motionError = false; motionPlaying = true },
            onAddComment = { text -> onAddComment(current.key, text) },
        )
    }
}

@Composable
private fun ZoomablePhoto(
    item: MediaItem,
    imageLoader: ImageLoader,
    initialScale: Float,
    onZoomed: (Boolean) -> Unit,
) {
    var scale by remember(item.key) { mutableFloatStateOf(initialScale.coerceIn(1f, 4f)) }
    var offset by remember(item.key) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var box by remember(item.key) { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { _, zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 4f)
        scale = if (next <= 1.01f) 1f else next
        offset = if (scale <= 1f) {
            androidx.compose.ui.geometry.Offset.Zero
        } else {
            clampOffset(offset + panChange, scale, box)
        }
    }
    LaunchedEffect(scale) { onZoomed(scale > 1.01f) }
    val context = LocalContext.current
    var failed by remember(item.key) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { box = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(
                state = transform,
                lockRotationOnZoomPan = true,
                canPan = { scale > 1.01f },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri.toUri())
                .size(4096)
                .build(),
            contentDescription = item.displayName,
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onError = { failed = true },
        )
        if (failed) {
            Text(
                "这张图片无法打开",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun clampOffset(
    offset: androidx.compose.ui.geometry.Offset,
    scale: Float,
    size: IntSize,
): androidx.compose.ui.geometry.Offset {
    if (scale <= 1f || size.width == 0 || size.height == 0) {
        return androidx.compose.ui.geometry.Offset.Zero
    }
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return androidx.compose.ui.geometry.Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

@Composable
private fun MotionPlayback(
    item: MediaItem,
    onFinished: () -> Unit,
    onFailed: () -> Unit,
    onSwipe: (Int) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LocalAlbumApp
    val uri = remember(item.key, item.pairedMotionUri, item.embeddedMotionBytes) {
        app.motionVideos.playable(item)
    }
    if (uri == null) {
        LaunchedEffect(item.key) { onFailed() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    MediaPlayer(uri = uri, onFinished = onFinished, onFailed = onFailed, onSwipe = onSwipe)
}

@Composable
private fun MediaPlayer(
    uri: Uri,
    onFinished: (() -> Unit)?,
    onFailed: (() -> Unit)?,
    onSwipe: (Int) -> Unit,
) {
    val context = LocalContext.current
    val finished = rememberUpdatedState(onFinished)
    val failed = rememberUpdatedState(onFailed)
    val swipe = rememberUpdatedState(onSwipe)
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finished.value?.invoke()
            }

            override fun onPlayerError(error: PlaybackException) {
                failed.value?.invoke()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            GalleryPlayerView(viewContext).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                controllerShowTimeoutMs = 3_000
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view ->
            view.player = player
            view.onHorizontalSwipe = { direction -> swipe.value(direction) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ViewerPanel(
    item: MediaItem,
    liked: Boolean,
    comments: List<Comment>,
    motionError: Boolean,
    motionPlaying: Boolean,
    onToggleLike: () -> Unit,
    onPlayMotion: () -> Unit,
    onAddComment: (String) -> Unit,
) {
    var draft by remember(item.key) { mutableStateOf("") }
    val zone = remember { ZoneId.systemDefault() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Sheet, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = ChineseDates.captureDate(item.dateTakenMillis, zone),
            color = Ink,
            style = MaterialTheme.typography.titleMedium,
        )
        if (item.kind == MediaKind.PHOTO && item.isLive) {
            Button(
                onClick = onPlayMotion,
                enabled = !motionPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue,
                    contentColor = Color.White,
                    disabledContainerColor = Blue.copy(alpha = 0.4f),
                    disabledContentColor = Color.White,
                ),
            ) {
                Text(
                    if (motionPlaying) "正在播放实况" else "播放实况",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (motionError) {
            Text("这段实况暂时无法播放", color = LikeRed, style = MaterialTheme.typography.bodyLarge)
        }
        Surface(
            onClick = onToggleLike,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
            color = Field,
            border = BorderStroke(1.dp, Line),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (liked) LikeRed else Quiet,
                )
                Box(Modifier.width(8.dp))
                Text(
                    if (liked) "已喜欢" else "喜欢",
                    color = if (liked) LikeRed else Ink,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Text("感想", color = Ink, style = MaterialTheme.typography.titleMedium)
        if (comments.isEmpty()) {
            Text("还没有感想", color = Quiet, style = MaterialTheme.typography.bodyLarge)
        } else {
            comments.forEach { comment ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Field,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = comment.body,
                        color = Ink,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { if (it.length <= 200) draft = it },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                placeholder = {
                    Text("写一点感想", color = Quiet, style = MaterialTheme.typography.bodyLarge)
                },
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Field,
                    unfocusedContainerColor = Field,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Blue,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                ),
                singleLine = true,
            )
            Box(Modifier.width(10.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        onAddComment(text)
                        draft = ""
                    }
                },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue,
                    contentColor = Color.White,
                ),
            ) {
                Text("发送", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private class GalleryPlayerView(context: android.content.Context) : PlayerView(context) {
    var onHorizontalSwipe: ((Int) -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val controllerPx = 96f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var swipe = false
    private var inController = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swipe = false
                inController = event.y >= height - controllerPx
            }
            MotionEvent.ACTION_MOVE -> {
                if (!inController) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (!swipe && abs(dx) > slop && abs(dx) > abs(dy)) swipe = true
                    if (swipe) return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipe) {
                    val dx = event.x - downX
                    if (abs(dx) > slop * 3) {
                        onHorizontalSwipe?.invoke(if (dx < 0) 1 else -1)
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
