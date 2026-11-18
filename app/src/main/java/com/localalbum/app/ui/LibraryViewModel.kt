package com.localalbum.app.ui

import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localalbum.app.LocalAlbumApp
import com.localalbum.app.data.DirectDelete
import com.localalbum.app.data.LibraryRepository
import com.localalbum.app.data.MediaRemoval
import com.localalbum.app.data.PinStore
import com.localalbum.app.data.PrivateLibrary
import com.localalbum.app.domain.LibraryState
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.PrivatePhoto
import com.localalbum.app.domain.SimilarGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Destination {
    data object Home : Destination
    data class Album(val id: String) : Destination
    data class Viewer(val albumId: String) : Destination
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val pinStore: PinStore,
    private val privateLibrary: PrivateLibrary,
    private val removal: MediaRemoval,
) : ViewModel() {
    val state = repository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibraryState(),
    )
    val similarGroups: StateFlow<List<SimilarGroup>> = repository.similarGroups
    val similarScanning: StateFlow<Boolean> = repository.similarScanning
    val privatePhotos: StateFlow<List<PrivatePhoto>> = privateLibrary.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    var destination by mutableStateOf<Destination>(Destination.Home)
        private set

    var viewerItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    var viewerIndex by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    var tab by mutableStateOf(HomeTab.Albums)
        private set

    var hasPin by mutableStateOf(pinStore.hasPin())
        private set

    var privateUnlocked by mutableStateOf(false)
        private set

    var banner by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    private var session: DeleteSession? = null

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun selectTab(next: HomeTab) {
        tab = next
    }

    fun dismissBanner() {
        banner = null
    }

    fun openAlbum(id: String) {
        destination = Destination.Album(id)
    }

    fun openViewer(albumId: String, index: Int) {
        val album = state.value.albums.firstOrNull { it.id == albumId } ?: return
        if (album.items.isEmpty()) return
        viewerItems = album.items
        viewerIndex = index.coerceIn(0, album.items.lastIndex)
        destination = Destination.Viewer(albumId)
    }

    fun onViewerPage(index: Int) {
        viewerIndex = index
    }

    fun back() {
        destination = when (val current = destination) {
            is Destination.Viewer -> Destination.Album(current.albumId)
            is Destination.Album -> Destination.Home
            Destination.Home -> Destination.Home
        }
    }

    fun toggleLike(mediaKey: String) {
        viewModelScope.launch { repository.toggleLike(mediaKey) }
    }

    fun addComment(mediaKey: String, text: String) {
        viewModelScope.launch { repository.addComment(mediaKey, text) }
    }

    fun submitPin(pin: String): Boolean {
        val ok = pinStore.check(pin)
        if (ok) privateUnlocked = true
        return ok
    }

    fun createPin(pin: String) {
        pinStore.setPin(pin)
        hasPin = pinStore.hasPin()
        privateUnlocked = hasPin
    }

    fun unlockFromBiometric() {
        if (hasPin) privateUnlocked = true
    }

    fun lockPrivate() {
        privateUnlocked = false
    }

    fun deleteSimilar(items: List<MediaItem>, launch: (IntentSender) -> Unit) {
        if (busy || items.isEmpty()) return
        busy = true
        beginDelete(Purpose.Similar, items.map { it.uri.toUri() }, emptyList(), launch)
    }

    fun importPrivate(items: List<MediaItem>, launch: (IntentSender) -> Unit) {
        if (busy || items.isEmpty()) return
        busy = true
        viewModelScope.launch {
            val ids = try {
                items.map { privateLibrary.copyIn(it) }
            } catch (_: Exception) {
                busy = false
                banner = "没能读到这些照片，它们还在相册里"
                return@launch
            }
            beginDelete(Purpose.Import, items.map { it.uri.toUri() }, ids, launch)
        }
    }

    fun exportPrivate(ids: List<Long>) {
        if (busy || ids.isEmpty()) return
        busy = true
        viewModelScope.launch {
            val ok = privateLibrary.exportToGallery(ids)
            banner = if (ok) "已移回相册" else "没能移回相册，照片还在私密空间里"
            if (ok) repository.refresh()
            busy = false
        }
    }

    fun deletePrivate(ids: List<Long>) {
        if (busy || ids.isEmpty()) return
        busy = true
        viewModelScope.launch {
            privateLibrary.discard(ids)
            banner = "已从私密空间删除"
            busy = false
        }
    }

    fun onSystemDeleteResult(ok: Boolean) {
        val current = session
        if (current == null) {
            busy = false
            return
        }
        if (!ok) {
            finishDelete(success = false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || current.index >= current.uris.size) {
            finishDelete(success = true)
            return
        }
        current.index += 1
        continueDirect()
    }

    private fun beginDelete(
        purpose: Purpose,
        uris: List<Uri>,
        importIds: List<Long>,
        launch: (IntentSender) -> Unit,
    ) {
        val sender = removal.batchRequest(uris)
        if (sender != null) {
            session = DeleteSession(purpose, uris, uris.size, importIds, launch)
            launch(sender)
            return
        }
        session = DeleteSession(purpose, uris, 0, importIds, launch)
        continueDirect()
    }

    private fun continueDirect() {
        val current = session ?: return
        viewModelScope.launch {
            while (current.index < current.uris.size) {
                when (val result = withContext(Dispatchers.IO) { removal.deleteDirect(current.uris[current.index]) }) {
                    DirectDelete.Deleted -> current.index += 1
                    is DirectDelete.NeedsUser -> {
                        current.launch(result.intentSender)
                        return@launch
                    }
                    DirectDelete.Failed -> {
                        finishDelete(success = false)
                        return@launch
                    }
                }
            }
            finishDelete(success = true)
        }
    }

    private fun finishDelete(success: Boolean) {
        val current = session ?: return
        session = null
        viewModelScope.launch {
            when (current.purpose) {
                Purpose.Similar -> {
                    banner = if (success) "已删除所选照片" else "没有删除，照片还在手机里"
                    if (success) repository.refresh()
                }
                Purpose.Import -> {
                    if (success) {
                        banner = "已移入私密空间"
                        repository.refresh()
                    } else {
                        privateLibrary.discard(current.importIds)
                        banner = "没有移入，照片还在相册里"
                    }
                }
            }
            busy = false
        }
    }

    private enum class Purpose { Similar, Import }

    private class DeleteSession(
        val purpose: Purpose,
        val uris: List<Uri>,
        var index: Int,
        val importIds: List<Long>,
        val launch: (IntentSender) -> Unit,
    )

    companion object {
        fun factory(app: LocalAlbumApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(
                        repository = app.repository,
                        pinStore = app.pinStore,
                        privateLibrary = app.privateLibrary,
                        removal = app.mediaRemoval,
                    ) as T
                }
            }
    }
}
