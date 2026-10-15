package zekke.android.uploads

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class UploadEntry(
    val id: String,
    val uri: String,
    val name: String,
    val mime: String,
    val size: Long,
    val folderId: String?,
)

interface UploadStore {
    fun load(): List<UploadEntry>

    fun add(entry: UploadEntry)

    fun remove(id: String)

    fun replace(entry: UploadEntry)

    fun clear()
}

class FileUploadStore(private val file: File) : UploadStore {
    @Synchronized
    override fun load(): List<UploadEntry> {
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                UploadEntry(
                    id = item.getString("id"),
                    uri = item.getString("uri"),
                    name = item.getString("name"),
                    mime = item.getString("mime"),
                    size = item.getLong("size"),
                    folderId = if (item.isNull("folder")) null else item.getString("folder"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun save(entries: List<UploadEntry>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("uri", entry.uri)
                    .put("name", entry.name)
                    .put("mime", entry.mime)
                    .put("size", entry.size)
                    .put("folder", entry.folderId ?: JSONObject.NULL),
            )
        }
        val partial = File(file.parentFile, file.name + ".part")
        partial.writeText(array.toString())
        if (!partial.renameTo(file)) partial.delete()
    }

    @Synchronized
    override fun add(entry: UploadEntry) = save(load().filterNot { it.id == entry.id } + entry)

    @Synchronized
    override fun remove(id: String) = save(load().filterNot { it.id == id })

    @Synchronized
    override fun replace(entry: UploadEntry) = save(load().map { if (it.id == entry.id) entry else it })

    @Synchronized
    override fun clear() {
        file.delete()
    }
}
