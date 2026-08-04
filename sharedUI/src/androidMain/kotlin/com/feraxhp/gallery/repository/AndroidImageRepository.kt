package com.feraxhp.gallery.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidImageRepository(private val context: Context) : ImageRepository {
    override suspend fun getImages(): List<GalleryImage> = getImagesInternal(null, null)

    override suspend fun getImagesByAlbum(albumId: String): List<GalleryImage> =
        getImagesInternal(
            "${MediaStore.MediaColumns.BUCKET_ID} = ?",
            arrayOf(albumId)
        )

    private suspend fun getImagesInternal(
        selection: String?,
        selectionArgs: Array<String>?
    ): List<GalleryImage> = withContext(Dispatchers.IO) {
        val media = mutableListOf<GalleryImage>()
        
        val images = queryMedia(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            selection,
            selectionArgs,
            MediaType.IMAGE
        )
        
        val videos = queryMedia(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            selection,
            selectionArgs,
            MediaType.VIDEO
        )
        
        media.addAll(images)
        media.addAll(videos)
        media.sortByDescending { it.dateAdded }
        
        Log.d("GalleryRepo", "Returning ${media.size} items total (${images.size} images, ${videos.size} videos)")
        media
    }

    private fun queryMedia(
        uri: android.net.Uri,
        selection: String?,
        selectionArgs: Array<String>?,
        type: MediaType
    ): List<GalleryImage> {
        val list = mutableListOf<GalleryImage>()
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        )

        if (type == MediaType.VIDEO) {
            projection.add(MediaStore.Video.VideoColumns.DURATION)
        }

        // We wrap the motion photo check in a try-catch to handle cases where the column might not be available
        // despite the SDK version check, which can happen on some devices or older MediaStore providers.
        val isMotionPhotoColumnName = "is_motion_photo"
        val includeMotionPhoto = type == MediaType.IMAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        
        if (includeMotionPhoto) {
            projection.add(isMotionPhotoColumnName)
        }

        try {
            context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val durationColumn = if (type == MediaType.VIDEO) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                val motionPhotoColumn = if (includeMotionPhoto) cursor.getColumnIndex(isMotionPhotoColumnName) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else null
                    val isMotionPhoto = if (motionPhotoColumn != -1) cursor.getInt(motionPhotoColumn) == 1 else false

                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    list.add(GalleryImage(id, contentUri, name, dateAdded, type, isMotionPhoto, duration))
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error querying $type with projection ${projection.joinToString()}: ${e.message}")
            // If it failed with the full projection, try again with a basic one
            if (projection.size > 3) {
                return queryMediaBasic(uri, selection, selectionArgs, type)
            }
        }
        return list
    }

    private fun queryMediaBasic(
        uri: android.net.Uri,
        selection: String?,
        selectionArgs: Array<String>?,
        type: MediaType
    ): List<GalleryImage> {
        val list = mutableListOf<GalleryImage>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        )
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val contentUri = ContentUris.withAppendedId(uri, id).toString()
                list.add(GalleryImage(id, contentUri, name, dateAdded, type, false, null))
            }
        }
        return list
    }

    override suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableMapOf<String, Album>()
        
        fun processUri(uri: android.net.Uri) {
            val projection = arrayOf(
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns._ID
            )

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)

                    while (cursor.moveToNext()) {
                        val bucketId = cursor.getString(bucketIdColumn)
                        val bucketName = cursor.getString(bucketNameColumn) ?: "Desconocido"
                        val mediaId = cursor.getLong(idColumn)
                        val contentUri = ContentUris.withAppendedId(uri, mediaId).toString()

                        val album = albums[bucketId]
                        if (album == null) {
                            albums[bucketId] = Album(bucketId, bucketName, contentUri, 1)
                        } else {
                            albums[bucketId] = album.copy(imageCount = album.imageCount + 1)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryRepo", "Error processing albums for $uri: ${e.message}")
            }
        }

        processUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        processUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        
        albums.values.toList()
    }
}
