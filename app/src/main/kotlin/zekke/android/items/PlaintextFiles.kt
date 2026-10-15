package zekke.android.items

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class PlaintextFiles(private val context: Context) {
    private val previews = File(context.noBackupFilesDir, "previews")
    private val shared = File(context.noBackupFilesDir, "shared")

    fun newPreview(): File {
        previews.mkdirs()
        return File(previews, UUID.randomUUID().toString() + ".pdf")
    }

    fun newShared(name: String): File {
        val directory = File(shared, UUID.randomUUID().toString())
        directory.mkdirs()
        return File(directory, safeName(name))
    }

    fun uriOf(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".shared", file)

    fun discard(file: File) {
        file.delete()
    }

    fun sweepPreviews() {
        previews.deleteRecursively()
    }

    fun sweepEverything() {
        previews.deleteRecursively()
        shared.deleteRecursively()
    }

    companion object {
        fun safeName(name: String): String {
            val cleaned = name.trim().map { if (it == '/' || it == '\\' || it.isISOControl()) '_' else it }.joinToString("")
            return cleaned.ifEmpty { "file" }.take(120)
        }
    }
}
