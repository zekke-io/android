package zekke.android.uploads

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.core.files.ByteSource
import zekke.core.files.UploadSource

class SourceInfo(val name: String, val mime: String, val size: Long)

class UnreadableSourceException(message: String) : FileNotFoundException(message)

interface UploadSources {
    fun describe(uri: String): SourceInfo

    fun open(entry: UploadEntry): UploadSource

    fun keepReadable(uri: String): Boolean

    fun release(uri: String)
}

class ContentUploadSources(private val resolver: ContentResolver) : UploadSources {
    override fun describe(uri: String): SourceInfo {
        val parsed = uri.toUri()
        var name: String? = null
        var size = -1L
        resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameAt = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeAt = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameAt >= 0 && !cursor.isNull(nameAt)) name = cursor.getString(nameAt)
                if (sizeAt >= 0 && !cursor.isNull(sizeAt)) size = cursor.getLong(sizeAt)
            }
        }
        if (size < 0) size = resolver.openAssetFileDescriptor(parsed, "r")?.use { it.length } ?: -1L
        if (size < 0) throw UnreadableSourceException("the size of $uri is not known")
        val mime = resolver.getType(parsed)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        return SourceInfo(name?.takeIf { it.isNotBlank() } ?: parsed.lastPathSegment ?: "file", mime, size)
    }

    override fun open(entry: UploadEntry): UploadSource {
        val parsed = entry.uri.toUri()
        return object : UploadSource {
            override val name: String = entry.name
            override val mime: String = entry.mime
            override val size: Long = entry.size

            override suspend fun open(): ByteSource {
                val stream = resolver.openInputStream(parsed) ?: throw UnreadableSourceException("${entry.uri} cannot be opened")
                return StreamSource(stream)
            }
        }
    }

    override fun keepReadable(uri: String): Boolean = try {
        resolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: Exception) {
        false
    }

    override fun release(uri: String) {
        try {
            resolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }
}

class StreamSource(private val stream: InputStream) : ByteSource {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withContext(Dispatchers.IO) { stream.read(buffer, offset, length) }

    override fun close() = stream.close()
}
