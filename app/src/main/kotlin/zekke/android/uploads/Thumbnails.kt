package zekke.android.uploads

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.core.files.THUMBNAIL_MAX_EDGE
import zekke.core.files.canThumbnail
import zekke.core.files.thumbnailExtent

interface ThumbnailMaker {
    suspend fun make(uri: String, mime: String): ByteArray?
}

class ImageThumbnailMaker(private val resolver: ContentResolver) : ThumbnailMaker {
    override suspend fun make(uri: String, mime: String): ByteArray? {
        if (!canThumbnail(mime)) return null
        return withContext(Dispatchers.Default) {
            try {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri.toUri())) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val extent = thumbnailExtent(info.size.width, info.size.height, THUMBNAIL_MAX_EDGE)
                    decoder.setTargetSize(extent.width, extent.height)
                }
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
                bitmap.recycle()
                out.toByteArray()
            } catch (_: Exception) {
                null
            }
        }
    }
}
