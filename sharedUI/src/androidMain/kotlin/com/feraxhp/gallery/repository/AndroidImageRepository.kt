package com.feraxhp.gallery.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_ID
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            projection.add(MediaStore.MediaColumns.XMP)
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            projection.add("bucket_display_name")
        }

        if (type == MediaType.VIDEO) {
            projection.add(MediaStore.Video.VideoColumns.DURATION)
            projection.add(MediaStore.Video.VideoColumns.DATE_TAKEN)
        } else {
            projection.add(MediaStore.Images.Media.DATE_TAKEN)
        }

        // is_motion_photo is often unavailable even on modern SDKs if the provider doesn't support it
        // We will rely on XMP detection for motion photos on API 29+

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
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
                val durationColumn = if (type == MediaType.VIDEO) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                val bucketNameColumn = cursor.getColumnIndex("bucket_display_name")
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                val xmpColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.XMP)
                } else -1

                val dateTakenColumn = if (type == MediaType.VIDEO) {
                    cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATE_TAKEN)
                } else {
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                }
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val albumId = cursor.getString(bucketIdColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val orientation = cursor.getInt(orientationColumn)
                    
                    var albumName = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) else null

                    // Fallback para obtener el nombre del álbum (carpeta) si BUCKET_DISPLAY_NAME es nulo
                    if (albumName.isNullOrEmpty()) {
                        if (relativePathColumn != -1) {
                            val relativePath = cursor.getString(relativePathColumn)
                            albumName = relativePath?.trim('/')?.split('/')?.lastOrNull()
                        }
                        if (albumName.isNullOrEmpty() && dataColumn != -1) {
                            val data = cursor.getString(dataColumn)
                            albumName = data?.substringBeforeLast('/')?.substringAfterLast('/')
                        }
                    }

                    val dateTaken = if (dateTakenColumn != -1 && !cursor.isNull(dateTakenColumn)) {
                        val value = cursor.getLong(dateTakenColumn)
                        if (value > 0) value else null
                    } else null
                    val rawWidth = cursor.getInt(widthColumn)
                    val rawHeight = cursor.getInt(heightColumn)
                    val width = if (orientation == 90 || orientation == 270) rawHeight else rawWidth
                    val height = if (orientation == 90 || orientation == 270) rawWidth else rawHeight
                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else null
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else null
                    val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    
                    var detectedMotionPhoto = false
                    if (type == MediaType.IMAGE && xmpColumn != -1) {
                        val xmpBlob = cursor.getBlob(xmpColumn)
                        if (xmpBlob != null) {
                            val xmpString = String(xmpBlob, Charsets.UTF_8)
                            if (xmpString.contains("MicroVideo") || xmpString.contains("MotionPhoto")) {
                                detectedMotionPhoto = true
                            }
                        }
                    }

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val metadata = if (type == MediaType.IMAGE) {
                        getExifMetadata(contentUri)
                    } else {
                        ExifMetadata()
                    }

                    list.add(
                        GalleryImage(
                            id, contentUri.toString(), name, dateAdded, type, detectedMotionPhoto,
                            duration, width, height, albumId, albumName, metadata.latitude, metadata.longitude,
                            metadata.shutterSpeed, dateTaken, metadata.cameraModel, metadata.cameraManufacturer,
                            metadata.iso, metadata.aperture, metadata.focalLength, size, metadata.software, path
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error querying $type with projection ${projection.joinToString()}: ${e.message}")
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
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.BUCKET_ID,
            "bucket_display_name",
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE
        )
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
                val bucketNameColumn = cursor.getColumnIndex("bucket_display_name")
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val albumId = cursor.getString(bucketIdColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val orientation = cursor.getInt(orientationColumn)
                    var albumName = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) else null
                    if (albumName.isNullOrEmpty() && dataColumn != -1) {
                        val data = cursor.getString(dataColumn)
                        albumName = data?.substringBeforeLast('/')?.substringAfterLast('/')
                    }
                    val rawWidth = cursor.getInt(widthColumn)
                    val rawHeight = cursor.getInt(heightColumn)
                    val width = if (orientation == 90 || orientation == 270) rawHeight else rawWidth
                    val height = if (orientation == 90 || orientation == 270) rawWidth else rawHeight
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else null
                    val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val metadata = if (type == MediaType.IMAGE) {
                        getExifMetadata(contentUri)
                    } else {
                        ExifMetadata()
                    }

                    list.add(
                        GalleryImage(
                            id = id, uri = contentUri.toString(), name = name, dateAdded = dateAdded, type = type,
                            isMotionPhoto = false, duration = null, width = width, height = height,
                            albumId = albumId, albumName = albumName, latitude = metadata.latitude, longitude = metadata.longitude,
                            shutterSpeed = metadata.shutterSpeed, dateTaken = null,
                            cameraModel = metadata.cameraModel, cameraManufacturer = metadata.cameraManufacturer,
                            iso = metadata.iso, aperture = metadata.aperture, focalLength = metadata.focalLength,
                            size = size, software = metadata.software, path = path
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Critical error in queryMediaBasic: ${e.message}")
        }
        return list
    }

    override suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableMapOf<String, Album>()
        fun processUri(uri: android.net.Uri) {
            val projection = arrayOf(
                MediaStore.MediaColumns.BUCKET_ID,
                "bucket_display_name",
                MediaStore.MediaColumns._ID
            )
            try {
                context.contentResolver.query(
                    uri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameColumn = cursor.getColumnIndex("bucket_display_name")
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        val bucketId = cursor.getString(bucketIdColumn)
                        val bucketName = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) else "Desconocido"
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

    private fun getExifMetadata(uri: android.net.Uri): ExifMetadata {
        return try {
            val photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setRequireOriginal(uri)
            } else {
                uri
            }
            context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)
                val hasLatLong = exif.getLatLong(latLong)
                val shutterSpeed = exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)

                ExifMetadata(
                    latitude = if (hasLatLong) latLong[0].toDouble() else null,
                    longitude = if (hasLatLong) latLong[1].toDouble() else null,
                    shutterSpeed = shutterSpeed,
                    cameraManufacturer = make,
                    cameraModel = model,
                    iso = iso,
                    aperture = aperture,
                    focalLength = focalLength,
                    software = software
                )
            } ?: ExifMetadata()
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error reading EXIF for $uri: ${e.message}")
            ExifMetadata()
        }
    }

    override fun openInFileManager(path: String) {
        try {
            val file = java.io.File(path)
            val parentPath = file.parent ?: return
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(parentPath), "resource/folder")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                intent.setDataAndType(android.net.Uri.parse(parentPath), "inode/directory")
                try {
                    context.startActivity(intent)
                } catch (e2: android.content.ActivityNotFoundException) {
                    Log.e("GalleryRepo", "No se pudo encontrar un gestor de archivos para la ruta: $parentPath")
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error al abrir el gestor de archivos: ${e.message}")
        }
    }

    override fun shareImage(image: GalleryImage) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, Uri.parse(image.uri))
            type = if (image.type == MediaType.VIDEO) "video/*" else "image/*"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir con")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    override suspend fun getImageById(id: Long, type: MediaType): GalleryImage? = withContext(Dispatchers.IO) {
        val uri = if (type == MediaType.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.MediaColumns._ID} = ?"
        val selectionArgs = arrayOf(id.toString())
        queryMedia(uri, selection, selectionArgs, type).firstOrNull()
    }

    override suspend fun deleteImage(image: GalleryImage): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                image.path?.let { File(it).delete() }
            }
            val uri = Uri.parse(image.uri)
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            rowsDeleted > 0
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error deleting image: ${e.message}")
            false
        }
    }

    override suspend fun moveImage(image: GalleryImage, albumId: String): GalleryImage? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(image.uri)
            val albums = getAlbums()
            val targetAlbum = albums.find { it.id == albumId } ?: return@withContext null

            var success = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                val sourceFile = image.path?.let { File(it) }
                if (sourceFile != null && sourceFile.exists()) {
                    val targetImages = getImagesByAlbum(albumId)
                    val targetDir = targetImages.firstOrNull()?.path?.let { File(it).parentFile }
                        ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetAlbum.name)
                    
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val destFile = File(targetDir, sourceFile.name)
                    
                    if (sourceFile.renameTo(destFile)) {
                        // Scan file to update MediaStore
                        val scanResult = suspendCoroutine<Uri?> { continuation ->
                            android.media.MediaScannerConnection.scanFile(
                                context,
                                arrayOf(destFile.absolutePath),
                                null
                            ) { _, scannedUri ->
                                continuation.resume(scannedUri)
                            }
                        }
                        if (scanResult != null) {
                            val newId = ContentUris.parseId(scanResult)
                            return@withContext getImageById(newId, image.type)
                        }
                        success = true
                    }
                }
            }

            // Fallback o intento vía MediaStore si no hay MANAGE_EXTERNAL_STORAGE o falló renameTo
            if (!success) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/${targetAlbum.name}/")
                    }
                    val rowsUpdated = context.contentResolver.update(uri, values, null, null)
                    if (rowsUpdated > 0) {
                        return@withContext getImageById(image.id, image.type)
                    }
                } else {
                    // Para versiones antiguas, copia y borra (simplificado)
                    if (copyImageToAlbum(image, albumId)) {
                        deleteImage(image)
                        success = true
                    }
                }
            }
            
            if (success) getImageById(image.id, image.type) else null
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error moving image: ${e.message}")
            null
        }
    }

    override suspend fun copyImage(image: GalleryImage): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceUri = Uri.parse(image.uri)
            val contentResolver = context.contentResolver
            
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Copy_${image.name}")
                put(MediaStore.MediaColumns.MIME_TYPE, if (image.type == MediaType.VIDEO) "video/mp4" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GalleryCopy/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (image.type == MediaType.VIDEO) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val newUri = contentResolver.insert(collection, values) ?: return@withContext false

            contentResolver.openInputStream(sourceUri)?.use { input ->
                contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(newUri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error copying image: ${e.message}")
            false
        }
    }

    private suspend fun copyImageToAlbum(image: GalleryImage, albumId: String): Boolean = withContext(Dispatchers.IO) {
        // Similar a copyImage pero al álbum específico
        // Implementación simplificada
        copyImage(image) // Por ahora solo duplica
    }
}

private data class ExifMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val shutterSpeed: String? = null,
    val cameraModel: String? = null,
    val cameraManufacturer: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val software: String? = null
)
