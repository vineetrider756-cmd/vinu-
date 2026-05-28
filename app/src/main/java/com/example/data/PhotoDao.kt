package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM edited_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<EditedPhoto>>

    @Query("SELECT * FROM edited_photos WHERE id = :id")
    suspend fun getPhotoById(id: Int): EditedPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: EditedPhoto): Long

    @Delete
    suspend fun deletePhoto(photo: EditedPhoto)

    @Query("DELETE FROM edited_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Int)
}
