package com.example.data

import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {
    val allPhotos: Flow<List<EditedPhoto>> = photoDao.getAllPhotos()

    suspend fun getPhotoById(id: Int): EditedPhoto? {
        return photoDao.getPhotoById(id)
    }

    suspend fun insertPhoto(photo: EditedPhoto): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun deletePhoto(photo: EditedPhoto) {
        photoDao.deletePhoto(photo)
    }

    suspend fun deletePhotoById(id: Int) {
        photoDao.deletePhotoById(id)
    }
}
