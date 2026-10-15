package zekke.android.platform

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.core.files.SealedObjectCache

class FileSealedObjectCache(private val directory: File) : SealedObjectCache {
    private fun fileOf(id: String): File? = id.takeIf { SAFE_ID.matches(it) }?.let { File(directory, it) }

    override suspend fun read(id: String): ByteArray? = withContext(Dispatchers.IO) {
        fileOf(id)?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    override suspend fun write(id: String, sealed: ByteArray) {
        withContext(Dispatchers.IO) {
            val target = fileOf(id) ?: return@withContext
            directory.mkdirs()
            val partial = File(directory, "$id.part")
            runCatching {
                partial.writeBytes(sealed)
                if (!partial.renameTo(target)) partial.delete()
            }.onFailure { partial.delete() }
        }
    }

    override suspend fun prune(keep: Set<String>) {
        withContext(Dispatchers.IO) {
            directory.listFiles()?.forEach { file ->
                val id = file.name.removeSuffix(PARTIAL_SUFFIX)
                if (file.name.endsWith(PARTIAL_SUFFIX) || id !in keep) file.delete()
            }
        }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val SAFE_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        const val PARTIAL_SUFFIX = ".part"
    }
}
