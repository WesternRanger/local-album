package com.localalbum.app

import android.app.Application
import androidx.room.Room
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import com.localalbum.app.data.AlbumDatabase
import com.localalbum.app.data.LibraryRepository
import com.localalbum.app.data.MediaRemoval
import com.localalbum.app.data.MediaStoreReader
import com.localalbum.app.data.MotionFileProbe
import com.localalbum.app.data.MotionVideoStore
import com.localalbum.app.data.PhotoHasher
import com.localalbum.app.data.PinStore
import com.localalbum.app.data.PrivateDatabase
import com.localalbum.app.data.PrivateLibrary

class LocalAlbumApp : Application() {
    lateinit var repository: LibraryRepository
        private set

    lateinit var imageLoader: ImageLoader
        private set

    lateinit var motionVideos: MotionVideoStore
        private set

    lateinit var pinStore: PinStore
        private set

    lateinit var privateLibrary: PrivateLibrary
        private set

    lateinit var mediaRemoval: MediaRemoval
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, AlbumDatabase::class.java, "local-album.db").build()
        val privateDatabase = Room.databaseBuilder(this, PrivateDatabase::class.java, "private-album.db").build()
        pinStore = PinStore(this)
        privateLibrary = PrivateLibrary(this, privateDatabase.photos())
        mediaRemoval = MediaRemoval(this)
        repository = LibraryRepository(
            reader = MediaStoreReader(this),
            probe = MotionFileProbe(this),
            database = database,
            hasher = PhotoHasher(this),
        )
        motionVideos = MotionVideoStore(this)
        imageLoader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}
