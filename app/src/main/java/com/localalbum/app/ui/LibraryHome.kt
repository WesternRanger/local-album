package com.localalbum.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.localalbum.app.domain.LibraryState
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.SimilarGroup

enum class HomeTab {
    Albums,
    Similar,
    Private,
}

private sealed interface PrivateAsk {
    data class Import(val items: List<MediaItem>) : PrivateAsk
    data class Export(val ids: List<Long>) : PrivateAsk
    data class Delete(val ids: List<Long>) : PrivateAsk
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHome(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    state: LibraryState,
    partial: Boolean,
    imageLoader: ImageLoader,
    similarGroups: List<SimilarGroup>,
    similarScanning: Boolean,
    banner: String?,
    onDismissBanner: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onRequestMore: () -> Unit,
    onRetry: () -> Unit,
    onDeleteSimilar: (List<MediaItem>) -> Unit,
    privateState: PrivateSpaceState,
    onCreatePin: (String) -> Unit,
    onSubmitPin: (String) -> Boolean,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onImport: (List<MediaItem>) -> Unit,
    onExportPrivate: (List<Long>) -> Unit,
    onDeletePrivate: (List<Long>) -> Unit,
    initialSimilarSelection: Set<String> = emptySet(),
    initialSimilarConfirm: List<MediaItem>? = null,
) {
    var similarConfirm by remember { mutableStateOf(initialSimilarConfirm) }
    var privateAsk by remember { mutableStateOf<PrivateAsk?>(null) }
    val title = when (tab) {
        HomeTab.Albums -> "本地相册"
        HomeTab.Similar -> "相似图片"
        HomeTab.Private -> "私密空间"
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            bottomBar = { HomeTabs(tab, onTab) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (banner != null) {
                    NoticeBanner(banner, onDismissBanner)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (tab) {
                        HomeTab.Albums -> HomeScreen(
                            state = state,
                            partial = partial,
                            imageLoader = imageLoader,
                            onOpen = onOpenAlbum,
                            onRequestMore = onRequestMore,
                            onRetry = onRetry,
                        )
                        HomeTab.Similar -> SimilarScreen(
                            groups = similarGroups,
                            scanning = similarScanning,
                            libraryLoading = state.loading && state.albums.isEmpty(),
                            imageLoader = imageLoader,
                            busy = privateState.busy,
                            onAskDelete = { similarConfirm = it },
                            initialSelected = initialSimilarSelection,
                        )
                        HomeTab.Private -> PrivateSpaceScreen(
                            state = privateState,
                            imageLoader = imageLoader,
                            onCreatePin = onCreatePin,
                            onSubmitPin = onSubmitPin,
                            onUnlock = onUnlock,
                            onLock = onLock,
                            onImport = { items -> privateAsk = PrivateAsk.Import(items) },
                            onExport = { ids -> privateAsk = PrivateAsk.Export(ids) },
                            onDelete = { ids -> privateAsk = PrivateAsk.Delete(ids) },
                        )
                    }
                }
            }
        }
        val pendingSimilar = similarConfirm
        if (pendingSimilar != null) {
            ConfirmOverlay(
                title = "确定删除这些照片？",
                body = "将删除 ${pendingSimilar.size} 张。\n手机还会再让你确认一次。\n确认后才会从手机里移除。\n不会自动删除。",
                confirmLabel = "删除",
                destructive = true,
                onConfirm = {
                    similarConfirm = null
                    onDeleteSimilar(pendingSimilar)
                },
                onDismiss = { similarConfirm = null },
            )
        }
        val pendingPrivate = privateAsk
        if (pendingPrivate != null && tab == HomeTab.Private) {
            val count = when (pendingPrivate) {
                is PrivateAsk.Import -> pendingPrivate.items.size
                is PrivateAsk.Export -> pendingPrivate.ids.size
                is PrivateAsk.Delete -> pendingPrivate.ids.size
            }
            PrivateConfirm(
                ask = pendingPrivate,
                count = count,
                onConfirm = {
                    privateAsk = null
                    when (pendingPrivate) {
                        is PrivateAsk.Import -> onImport(pendingPrivate.items)
                        is PrivateAsk.Export -> onExportPrivate(pendingPrivate.ids)
                        is PrivateAsk.Delete -> onDeletePrivate(pendingPrivate.ids)
                    }
                },
                onDismiss = { privateAsk = null },
            )
        }
    }
}

@Composable
private fun PrivateConfirm(
    ask: PrivateAsk,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (ask) {
        is PrivateAsk.Import -> "移入私密空间？"
        is PrivateAsk.Export -> "移回相册？"
        is PrivateAsk.Delete -> "删除这些私密照片？"
    }
    val body = when (ask) {
        is PrivateAsk.Import -> "将把 $count 张照片移入私密空间。\n手机会再让你确认一次。\n确认后，这些照片只在这里查看。"
        is PrivateAsk.Export -> "将把 $count 张照片移回手机相册。\n私密空间里不再保留。"
        is PrivateAsk.Delete -> "将从这台手机删除 $count 张照片。\n相册里也不会出现。\n删除后无法恢复。"
    }
    val label = when (ask) {
        is PrivateAsk.Import -> "移入"
        is PrivateAsk.Export -> "移出"
        is PrivateAsk.Delete -> "删除"
    }
    ConfirmOverlay(
        title = title,
        body = body,
        confirmLabel = label,
        destructive = ask is PrivateAsk.Delete,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun NoticeBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(message, color = Ink, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("知道了", color = Blue, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HomeTabs(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 0.dp) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Line),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeTabButton(HomeTab.Albums, "相册", Icons.Filled.Home, tab, onTab)
                HomeTabButton(HomeTab.Similar, "相似图片", Icons.AutoMirrored.Filled.List, tab, onTab)
                HomeTabButton(HomeTab.Private, "私密空间", Icons.Filled.Lock, tab, onTab)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HomeTabButton(
    value: HomeTab,
    label: String,
    icon: ImageVector,
    current: HomeTab,
    onTab: (HomeTab) -> Unit,
) {
    val selected = value == current
    val tint = if (selected) Blue else Quiet
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFE8F2FF) else Color.Transparent)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable { onTab(value) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}
