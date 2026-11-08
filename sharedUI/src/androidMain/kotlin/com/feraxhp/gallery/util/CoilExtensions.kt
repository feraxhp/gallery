package com.feraxhp.gallery.util

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Precision
import coil3.toAndroidUri
import coil3.video.videoFrameMillis
import kotlinx.coroutines.CancellationException

fun thumbnailMemoryCacheKey(uri: String, sizePx: Int = 360): String = "thumb_${sizePx}_$uri"

@Composable
actual fun rememberVideoModel(uri: String, duration: Long?): Any? {
    val context = LocalPlatformContext.current
    return remember(uri, duration, context) {
        ImageRequest.Builder(context)
            .data(uri)
            .videoFrameMillis(
                if (duration != null && duration > 0) {
                    minOf(1000L, duration / 2)
                } else {
                    1000L
                }
            )
            .build()
    }
}

@Composable
actual fun rememberThumbnailModel(
    uri: String,
    isVideo: Boolean,
    duration: Long?,
    sizePx: Int
): Any? {
    val context = LocalPlatformContext.current
    return remember(uri, isVideo, duration, sizePx, context) {
        val cacheKey = thumbnailMemoryCacheKey(uri, sizePx)
        ImageRequest.Builder(context)
            .data(uri)
            .size(sizePx, sizePx)
            .precision(Precision.INEXACT)
            .memoryCacheKey(cacheKey)
            .allowRgb565(true)
            .crossfade(false)
            .apply {
                if (isVideo) {
                    videoFrameMillis(
                        if (duration != null && duration > 0) {
                            minOf(1000L, duration / 2)
                        } else {
                            1000L
                        }
                    )
                }
            }
            .build()
    }
}

@Composable
actual fun rememberFullImageModel(
    uri: String,
    isVideo: Boolean,
    duration: Long?,
    thumbnailSizePx: Int
): Any? {
    val context = LocalPlatformContext.current
    return remember(uri, isVideo, duration, thumbnailSizePx, context) {
        val thumbKey = thumbnailMemoryCacheKey(uri, thumbnailSizePx)
        ImageRequest.Builder(context)
            .data(uri)
            .placeholderMemoryCacheKey(thumbKey)
            .crossfade(false)
            .apply {
                if (isVideo) {
                    videoFrameMillis(
                        if (duration != null && duration > 0) {
                            minOf(1000L, duration / 2)
                        } else {
                            1000L
                        }
                    )
                }
            }
            .build()
    }
}

class MediaStoreThumbnailFetcher(
    private val data: coil3.Uri,
    private val options: Options,
    private val widthPx: Int,
    private val heightPx: Int,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val androidUri = data.toAndroidUri()
        return try {
            val bitmap = options.context.contentResolver.loadThumbnail(
                androidUri,
                Size(widthPx, heightPx),
                null
            )
            ImageFetchResult(
                image = bitmap.asImage(shareable = true),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    class Factory(
        private val maxThumbnailDimensionPx: Int = 640
    ) : Fetcher.Factory<coil3.Uri> {
        override fun create(
            data: coil3.Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (data.authority != MediaStore.AUTHORITY) return null

            val width = (options.size.width as? Dimension.Pixels)?.px ?: return null
            val height = (options.size.height as? Dimension.Pixels)?.px ?: return null
            if (width <= 0 || height <= 0) return null
            if (maxOf(width, height) > maxThumbnailDimensionPx) return null

            return MediaStoreThumbnailFetcher(data, options, width, height)
        }
    }
}

actual fun Image.toImageBitmap(): ImageBitmap? {
    return (this as? BitmapImage)?.bitmap?.asImageBitmap()
}
