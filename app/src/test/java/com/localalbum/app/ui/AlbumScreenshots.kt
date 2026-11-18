package com.localalbum.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import coil3.Image
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import com.localalbum.app.domain.AlbumCatalog
import com.localalbum.app.domain.Comment
import com.localalbum.app.domain.LibraryState
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.MediaKind
import com.localalbum.app.domain.PrivatePhoto
import com.localalbum.app.domain.SimilarGroup
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real Compose screens with solid-color sample images.
 * Nothing here is a photograph of a person.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class AlbumScreenshots {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val images = LinkedHashMap<String, Image>()

    @Before
    fun useShanghaiCalendar() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @Test
    fun homeAlbumList() {
        capture(fileName = "home_albums_photos.png", widthDp = 393, heightDp = 2400) {
            home(HomeTab.Albums)
        }
    }

    @Test
    fun albumDetailCover() {
        val state = library()
        val june = state.albums.first { it.id == "month:2026-06" }
        capture(fileName = "album_detail_cover.png", widthDp = 393, heightDp = 1200) {
            AlbumScreen(
                album = june,
                comments = state.comments,
                likes = state.likes,
                imageLoader = loader(),
                onBack = {},
                onOpen = {},
            )
        }
    }

    @Test
    fun similarPhotosTab() {
        capture(fileName = "similar_photos.png", widthDp = 393, heightDp = 1400) {
            home(HomeTab.Similar, groups = similarGroups())
        }
    }

    @Test
    fun similarDeleteConfirm() {
        val chosen = listOf(junePhoto, juneStill)
        capture(fileName = "similar_delete_confirm.png", widthDp = 393, heightDp = 1000) {
            home(
                tab = HomeTab.Similar,
                groups = similarGroups(),
                selected = chosen.map { it.key }.toSet(),
                confirm = chosen,
            )
        }
    }

    @Test
    fun privateSpaceLocked() {
        capture(fileName = "private_locked.png", widthDp = 393, heightDp = 1000) {
            home(
                tab = HomeTab.Private,
                privateState = PrivateSpaceState(
                    hasPin = true,
                    unlocked = false,
                    biometricAvailable = true,
                    photos = emptyList(),
                    libraryPhotos = emptyList(),
                    busy = false,
                ),
            )
        }
    }

    @Test
    fun privateSpaceUnlocked() {
        capture(fileName = "private_unlocked.png", widthDp = 393, heightDp = 1100) {
            home(
                tab = HomeTab.Private,
                privateState = PrivateSpaceState(
                    hasPin = true,
                    unlocked = true,
                    biometricAvailable = true,
                    photos = listOf(
                        PrivatePhoto(
                            id = 1,
                            uri = "placeholder://private-one",
                            displayName = "示例-私密.jpg",
                            mimeType = "image/jpeg",
                            dateTakenMillis = java.time.Instant.parse("2026-06-15T04:00:00Z").toEpochMilli(),
                        ),
                    ),
                    libraryPhotos = emptyList(),
                    busy = false,
                ),
            )
        }
    }

    @Test
    fun photoViewer() {
        capture(fileName = "viewer_photo_photos.png", widthDp = 393, heightDp = 900) {
            viewer(initialScale = 1f)
        }
    }

    @Test
    fun photoViewerZoomed() {
        capture(fileName = "viewer_zoomed_photos.png", widthDp = 393, heightDp = 900) {
            viewer(initialScale = 2.6f)
        }
    }

    @Test
    fun livePhotoViewer() {
        capture(fileName = "viewer_live_photos.png", widthDp = 393, heightDp = 1000) {
            ViewerScreen(
                items = listOf(livePhoto),
                startIndex = 0,
                likes = emptySet(),
                comments = mapOf(livePhoto.key to listOf(Comment(1, "示例感想，不是真实照片", 1L))),
                imageLoader = loader(),
                onBack = {},
                onPageChanged = {},
                onToggleLike = {},
                onAddComment = { _, _ -> },
            )
        }
    }

    @Composable
    private fun loader(): ImageLoader {
        val context = LocalContext.current
        return remember {
            ensureImages()
            ImageLoader.Builder(context)
                .components { add(PlaceholderFetcher.Factory(images)) }
                .fetcherCoroutineContext(Dispatchers.Unconfined)
                .decoderCoroutineContext(Dispatchers.Unconfined)
                .diskCache(null)
                .build()
        }
    }

    @Composable
    private fun viewer(initialScale: Float) {
        ViewerScreen(
            items = listOf(junePhoto),
            startIndex = 0,
            likes = setOf(junePhoto.key),
            comments = mapOf(junePhoto.key to listOf(Comment(1, "示例感想：色块，没有真人", 1L))),
            imageLoader = loader(),
            onBack = {},
            onPageChanged = {},
            onToggleLike = {},
            onAddComment = { _, _ -> },
            initialScale = initialScale,
        )
    }

    private fun capture(
        fileName: String,
        widthDp: Int,
        heightDp: Int,
        content: @Composable () -> Unit,
    ) {
        val directory = File("/opt/cursor/artifacts").apply { mkdirs() }
        captureRoboImage(
            file = File(directory, fileName),
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = widthDp, heightDp = heightDp)
                addOption(IdleMainLooper)
            },
        ) {
            ScreenshotTheme { content() }
        }
    }

    @Composable
    private fun home(
        tab: HomeTab,
        groups: List<SimilarGroup> = emptyList(),
        selected: Set<String> = emptySet(),
        confirm: List<MediaItem>? = null,
        privateState: PrivateSpaceState = PrivateSpaceState(
            hasPin = true,
            unlocked = false,
            biometricAvailable = false,
            photos = emptyList(),
            libraryPhotos = emptyList(),
            busy = false,
        ),
    ) {
        val state = library()
        LibraryHome(
            tab = tab,
            onTab = {},
            state = state,
            partial = false,
            imageLoader = loader(),
            similarGroups = groups,
            similarScanning = false,
            banner = null,
            onDismissBanner = {},
            onOpenAlbum = {},
            onRequestMore = {},
            onRetry = {},
            onDeleteSimilar = {},
            privateState = privateState,
            onCreatePin = {},
            onSubmitPin = { false },
            onUnlock = {},
            onLock = {},
            onImport = {},
            onExportPrivate = {},
            onDeletePrivate = {},
            initialSimilarSelection = selected,
            initialSimilarConfirm = confirm,
        )
    }

    private fun similarGroups(): List<SimilarGroup> = listOf(
        SimilarGroup("burst", listOf(junePhoto, juneStill, augustPhoto)),
        SimilarGroup("copy", listOf(livePhoto, copyPhoto)),
    )

    private fun library(): LibraryState {
        val items = listOf(junePhoto, juneStill, juneVideo, augustPhoto, livePhoto)
        val comments = mapOf(
            junePhoto.key to listOf(
                Comment(1, "今天真好 😊", 3),
                Comment(2, "今天真好 ❤\uFE0F", 2),
            ),
            juneStill.key to listOf(
                Comment(3, "今天真好 😊", 1),
                Comment(4, "风很轻 🌿", 4),
            ),
            livePhoto.key to listOf(Comment(5, "慢慢看 🌸", 1)),
        )
        return LibraryState(
            albums = AlbumCatalog.build(
                items = items,
                likes = setOf(junePhoto.key),
                commentCounts = comments.mapValues { it.value.size },
                zone = zone,
            ),
            likes = setOf(junePhoto.key),
            comments = comments,
            loading = false,
            error = null,
        )
    }

    private fun ensureImages() {
        if (images.isNotEmpty()) return
        images[junePhoto.uri] = picture(0xFFE5E5EA.toInt(), motif = 0)
        images[juneStill.uri] = picture(0xFFD1D1D6.toInt(), motif = 1)
        images[juneVideo.uri] = picture(0xFFC7C7CC.toInt(), motif = 2)
        images[augustPhoto.uri] = picture(0xFFAEAEB2.toInt(), motif = 0)
        images[livePhoto.uri] = picture(0xFFD6E4F0.toInt(), motif = 1)
        images["placeholder://private-one"] = picture(0xFFE8F2FF.toInt(), motif = 0)
        images[copyPhoto.uri] = picture(0xFFC7C7CC.toInt(), motif = 1)
    }

    private fun picture(color: Int, motif: Int): Image {
        val size = 1080
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFF1C1C1E.toInt()
            style = Paint.Style.FILL
        }
        val center = size / 2f
        when (motif % 3) {
            0 -> canvas.drawCircle(center, center - 70f, 150f, paint)
            1 -> canvas.drawRect(center - 150f, center - 230f, center + 150f, center + 70f, paint)
            else -> {
                var y = 180f
                while (y < size - 360f) {
                    canvas.drawRect(180f, y, size - 180f, y + 28f, paint)
                    y += 72f
                }
            }
        }
        paint.textSize = 72f
        paint.typeface = Typeface.createFromFile(File(CJK_FONT))
        val label = "示例"
        val textWidth = paint.measureText(label)
        val baseline = size * 0.72f
        canvas.drawText(label, (size - textWidth) / 2f, baseline, paint)
        return bitmap.asImage()
    }

    private val junePhoto = item(
        key = "june-photo",
        name = "示例-圆.jpg",
        at = "2026-06-15T04:00:00Z",
        bucketId = "camera",
        bucketName = "Camera",
    )
    private val juneStill = item(
        key = "june-still",
        name = "示例-方块.jpg",
        at = "2026-06-16T04:00:00Z",
        bucketId = "camera",
        bucketName = "Camera",
    )
    private val juneVideo = item(
        key = "june-video",
        name = "示例-条纹.mp4",
        at = "2026-06-10T04:00:00Z",
        bucketId = "camera",
        bucketName = "Camera",
        kind = MediaKind.VIDEO,
        mime = "video/mp4",
        durationMs = 12_500,
    )
    private val augustPhoto = item(
        key = "august-photo",
        name = "示例-八月.jpg",
        at = "2026-08-03T04:00:00Z",
        bucketId = "camera",
        bucketName = "Camera",
    )
    private val copyPhoto = item(
        key = "copy-photo",
        name = "示例-副本.jpg",
        at = "2026-05-21T04:00:00Z",
        bucketId = "camera",
        bucketName = "Camera",
    )
    private val livePhoto = item(
        key = "live-photo",
        name = "示例-实况.jpg",
        at = "2026-05-20T04:00:00Z",
        bucketId = "screenshots",
        bucketName = "Screenshots",
        embedded = true,
    )

    private fun item(
        key: String,
        name: String,
        at: String,
        bucketId: String,
        bucketName: String,
        kind: MediaKind = MediaKind.PHOTO,
        mime: String = "image/jpeg",
        durationMs: Long = 0,
        embedded: Boolean = false,
    ) = MediaItem(
        key = key,
        uri = "placeholder://$key",
        kind = kind,
        mimeType = mime,
        displayName = name,
        dateTakenMillis = Instant.parse(at).toEpochMilli(),
        width = 1080,
        height = 1080,
        durationMs = durationMs,
        bucketId = bucketId,
        bucketName = bucketName,
        embeddedMotion = embedded,
        embeddedMotionBytes = if (embedded) 4_096L else null,
    )

    private class PlaceholderFetcher(
        private val image: Image,
    ) : Fetcher {
        override suspend fun fetch(): ImageFetchResult {
            return ImageFetchResult(image = image, isSampled = false, dataSource = DataSource.MEMORY)
        }

        class Factory(
            private val images: Map<String, Image>,
        ) : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                val text = data.toString()
                val image = images.entries.firstOrNull { (stored, _) ->
                    val id = stored.substringAfterLast('/')
                    text == stored || text.contains(id)
                }?.value ?: return null
                return PlaceholderFetcher(image)
            }
        }
    }

    private object IdleMainLooper : com.github.takahirom.roborazzi.RoborazziComposeCaptureOption {
        override fun beforeCapture() {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        override fun afterCapture() = Unit
    }

    companion object {
        private const val CJK_FONT = "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"
    }
}

@Composable
private fun ScreenshotTheme(content: @Composable () -> Unit) {
    LocalAlbumTheme {
        val font = remember { screenshotFontFamily() }
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MaterialTheme.typography.withFont(font),
            content = content,
        )
    }
}

private fun screenshotFontFamily(): FontFamily {
    val cjk = File("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc")
    val droid = File("/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf")
    val emoji = File("/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf")
    val baseFile = if (cjk.exists()) cjk else droid
    return try {
        val typeface = if (android.os.Build.VERSION.SDK_INT >= 29 && emoji.exists()) {
            android.graphics.Typeface.CustomFallbackBuilder(platformFamily(baseFile))
                .addCustomFallback(platformFamily(emoji))
                .build()
        } else {
            loadTypeface(baseFile)
        }
        FontFamily(typeface)
    } catch (_: RuntimeException) {
        FontFamily(loadTypeface(droid))
    }
}

private fun platformFamily(file: File): android.graphics.fonts.FontFamily {
    val font = android.graphics.fonts.Font.Builder(file).apply {
        if (file.extension.equals("ttc", ignoreCase = true)) setTtcIndex(0)
    }.build()
    return android.graphics.fonts.FontFamily.Builder(font).build()
}

private fun loadTypeface(file: File): android.graphics.Typeface {
    return if (file.extension.equals("ttc", ignoreCase = true)) {
        android.graphics.Typeface.Builder(file).setTtcIndex(0).build()
    } else {
        android.graphics.Typeface.createFromFile(file)
    }
}

private fun Typography.withFont(font: FontFamily): Typography {
    fun TextStyle.useFont() = copy(fontFamily = font)
    return copy(
        displayLarge = displayLarge.useFont(),
        displayMedium = displayMedium.useFont(),
        displaySmall = displaySmall.useFont(),
        headlineLarge = headlineLarge.useFont(),
        headlineMedium = headlineMedium.useFont(),
        headlineSmall = headlineSmall.useFont(),
        titleLarge = titleLarge.useFont(),
        titleMedium = titleMedium.useFont(),
        titleSmall = titleSmall.useFont(),
        bodyLarge = bodyLarge.useFont(),
        bodyMedium = bodyMedium.useFont(),
        bodySmall = bodySmall.useFont(),
        labelLarge = labelLarge.useFont(),
        labelMedium = labelMedium.useFont(),
        labelSmall = labelSmall.useFont(),
    )
}

