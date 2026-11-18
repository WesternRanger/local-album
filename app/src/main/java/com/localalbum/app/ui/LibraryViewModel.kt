package com.localalbum.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localalbum.app.LocalAlbumApp
import com.localalbum.app.data.LibraryRepository
import com.localalbum.app.domain.LibraryState
import com.localalbum.app.domain.MediaItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Destination {
    data object Home : Destination
    data class Album(val id: String) : Destination
    data class Viewer(val albumId: String) : Destination
}

class LibraryViewModel(
    private val repository: LibraryRepository,
) : ViewModel() {
    val state = repository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibraryState(),
    )

    var destination by mutableStateOf<Destination>(Destination.Home)
        private set

    var viewerItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    var viewerIndex by mutableIntStateOf(0)
        private set

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
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

    companion object {
        fun factory(app: LocalAlbumApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(app.repository) as T
                }
            }
    }
}
