package com.localalbum.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "likes")
data class LikeEntity(
    @PrimaryKey val mediaKey: String,
    val liked: Boolean,
)

@Entity(
    tableName = "comments",
    indices = [Index("mediaKey")],
)
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaKey: String,
    val body: String,
    val createdAt: Long,
)

@Dao
interface LikeDao {
    @Query("SELECT * FROM likes WHERE liked = 1")
    fun observeLiked(): Flow<List<LikeEntity>>

    @Query("SELECT * FROM likes WHERE mediaKey = :mediaKey LIMIT 1")
    suspend fun get(mediaKey: String): LikeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LikeEntity)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CommentEntity>>

    @Insert
    suspend fun insert(entity: CommentEntity)
}

@Database(
    entities = [LikeEntity::class, CommentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AlbumDatabase : RoomDatabase() {
    abstract fun likes(): LikeDao
    abstract fun comments(): CommentDao
}
