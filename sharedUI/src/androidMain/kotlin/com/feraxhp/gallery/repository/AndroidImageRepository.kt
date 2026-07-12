package com.feraxhp.gallery.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.feraxhp.gallery.model.GalleryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidImageRepository(private val context: Context) : ImageRepository {
    override suspend fun getImages(): List<GalleryImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryImage>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString()

                images.add(GalleryImage(id, contentUri, name))
            }
        }
        images
    }
}
