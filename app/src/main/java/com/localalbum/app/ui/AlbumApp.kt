package com.localalbum.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.localalbum.app.domain.AlbumKind
import com.localalbum.app.domain.MediaKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localalbum.app.LocalAlbumApp

@Composable
fun AlbumApp() {
    val context = LocalContext.current
    val app = context.applicationContext as LocalAlbumApp
    var access by remember { mutableStateOf(readMediaAccess(context)) }
    var reload by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        access = readMediaAccess(context)
        reload += 1
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = readMediaAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(access) {
        if (access == MediaAccess.None && !prompted) {
            prompted = true
            launcher.launch(mediaPermissionRequest())
        }
    }
    if (access == MediaAccess.None) {
        PermissionScreen(
            onRequest = { launcher.launch(mediaPermissionRequest()) },
            onSettings = { openAppSettings(context) },
        )
        return
    }

    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val similarGroups by viewModel.similarGroups.collectAsStateWithLifecycle()
    val similarScanning by viewModel.similarScanning.collectAsStateWithLifecycle()
    val privatePhotos by viewModel.privatePhotos.collectAsStateWithLifecycle()
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onSystemDeleteResult(result.resultCode == Activity.RESULT_OK)
    }
    val biometricAvailable = remember { deviceBiometricAvailable(context) }
    val libraryPhotos = remember(state.albums) {
        state.albums
            .filter { it.kind == AlbumKind.MONTH }
            .flatMap { it.items }
            .filter { it.kind == MediaKind.PHOTO }
            .distinctBy { it.key }
    }
    LaunchedEffect(reload) { viewModel.refresh() }
    val destination = viewModel.destination
    SystemBars(light = !isSystemInDarkTheme() && destination !is Destination.Viewer)
    val atAlbumRoot = destination == Destination.Home && viewModel.tab == HomeTab.Albums
    BackHandler(enabled = !atAlbumRoot) {
        if (destination == Destination.Home) viewModel.selectTab(HomeTab.Albums) else viewModel.back()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val lockObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.lockPrivate()
        }
        lifecycleOwner.lifecycle.addObserver(lockObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(lockObserver) }
    }

    when (val screen = destination) {
        Destination.Home -> LibraryHome(
            tab = viewModel.tab,
            onTab = viewModel::selectTab,
            state = state,
            partial = access == MediaAccess.Partial,
            imageLoader = app.imageLoader,
            similarGroups = similarGroups,
            similarScanning = similarScanning,
            banner = viewModel.banner,
            onDismissBanner = viewModel::dismissBanner,
            onOpenAlbum = viewModel::openAlbum,
            onRequestMore = { launcher.launch(mediaPermissionRequest()) },
            onRetry = viewModel::refresh,
            onDeleteSimilar = { items ->
                viewModel.deleteSimilar(items) { sender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            },
            privateState = PrivateSpaceState(
                hasPin = viewModel.hasPin,
                unlocked = viewModel.privateUnlocked,
                biometricAvailable = biometricAvailable,
                photos = privatePhotos,
                libraryPhotos = libraryPhotos,
                busy = viewModel.busy,
            ),
            onCreatePin = viewModel::createPin,
            onSubmitPin = viewModel::submitPin,
            onUnlock = viewModel::unlockFromBiometric,
            onLock = viewModel::lockPrivate,
            onImport = { items ->
                viewModel.importPrivate(items) { sender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            },
            onExportPrivate = viewModel::exportPrivate,
            onDeletePrivate = viewModel::deletePrivate,
        )
        is Destination.Album -> AlbumScreen(
            album = state.albums.firstOrNull { it.id == screen.id },
            comments = state.comments,
            likes = state.likes,
            imageLoader = app.imageLoader,
            onBack = viewModel::back,
            onOpen = { index -> viewModel.openViewer(screen.id, index) },
        )
        is Destination.Viewer -> ViewerScreen(
            items = viewModel.viewerItems,
            startIndex = viewModel.viewerIndex,
            likes = state.likes,
            comments = state.comments,
            imageLoader = app.imageLoader,
            onBack = viewModel::back,
            onPageChanged = viewModel::onViewerPage,
            onToggleLike = viewModel::toggleLike,
            onAddComment = viewModel::addComment,
        )
    }
}

@Composable
private fun PermissionScreen(
    onRequest: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("需要访问照片和视频", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "本地相册只在这台手机上查看照片和视频，不会上传。请允许访问后，才能按月份和文件夹整理。",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("允许访问", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("去设置", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SystemBars(light: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
        }
    }
}
