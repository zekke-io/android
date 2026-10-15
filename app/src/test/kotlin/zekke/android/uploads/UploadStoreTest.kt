package zekke.android.uploads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UploadStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = FileUploadStore(File(folder.root, "state/uploads.json"))

    private fun entry(id: String, folderId: String? = null) = UploadEntry(id, "content://x/$id", "name $id.bin", "image/jpeg", 123_456_789_012L, folderId)

    @Test
    fun nothingIsStoredAtFirst() {
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun entriesSurviveAReopenWithTheirFolderAndAHugeSize() {
        store().add(entry("a", "f1"))
        store().add(entry("b"))
        val loaded = store().load()
        assertEquals(listOf("a", "b"), loaded.map { it.id })
        assertEquals("f1", loaded[0].folderId)
        assertNull(loaded[1].folderId)
        assertEquals(123_456_789_012L, loaded[0].size)
    }

    @Test
    fun addingTheSameIdReplacesItAndRemovingForgetsIt() {
        val store = store()
        store.add(entry("a"))
        store.add(entry("a").copy(name = "renamed"))
        assertEquals(listOf("renamed"), store.load().map { it.name })
        store.replace(entry("a").copy(uri = "content://new"))
        assertEquals("content://new", store.load().single().uri)
        store.remove("a")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun aDamagedFileIsAnEmptyQueueNotACrash() {
        val file = File(folder.root, "state/uploads.json")
        file.parentFile!!.mkdirs()
        file.writeText("{ not json")
        assertTrue(FileUploadStore(file).load().isEmpty())
    }

    @Test
    fun clearingDeletesTheFile() {
        val store = store()
        store.add(entry("a"))
        store.clear()
        assertTrue(store.load().isEmpty())
    }
}
