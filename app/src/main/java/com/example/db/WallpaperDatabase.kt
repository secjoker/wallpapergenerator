package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallpapers")
data class WallpaperItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,             // Relative or absolute internal storage file path
    val prompt: String,               // Prompt vibe used to generate
    val generationDate: Long = System.currentTimeMillis(),
    val isSavedToGallery: Boolean = false, // Explicitly downloaded by the user
    val isFavorite: Boolean = false    // Favorited by the user
)

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers ORDER BY generationDate DESC")
    fun getAllWallpapers(): Flow<List<WallpaperItem>>

    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    suspend fun getWallpaperById(id: Int): WallpaperItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(item: WallpaperItem): Long

    @Update
    suspend fun updateWallpaper(item: WallpaperItem)

    @Query("DELETE FROM wallpapers WHERE id = :id")
    suspend fun deleteWallpaperById(id: Int)

    @Query("DELETE FROM wallpapers")
    suspend fun deleteAllWallpapers()
}

@Database(entities = [WallpaperItem::class], version = 1, exportSchema = false)
abstract class WallpaperDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
}
