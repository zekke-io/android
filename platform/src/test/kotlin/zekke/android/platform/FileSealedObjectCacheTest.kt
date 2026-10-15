package zekke.android.platform

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSealedObjectCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val a = "0b8e3c1a-5d7f-4a62-9c1e-3f4a5b6c7d8e"
    private val b = "1c9f4d2b-6e80-4b73-8d2f-4a5b6c7d8e9f"

    private fun cache() = FileSealedObjectCache(File(folder.root, "sealed"))

    @Test
    fun whatIsWrittenIsReadBackByteForByte() = runTest {
        val cache = cache()
        assertNull(cache.read(a))
        cache.write(a, byteArrayOf(1, 2, 3, 4))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), cache.read(a))
    }

    @Test
    fun anIdThatIsNotACanonicalUuidNeverTouchesTheDisk() = runTest {
        val cache = cache()
        cache.write("../escape", byteArrayOf(9))
        cache.write("not-an-id", byteArrayOf(9))
        assertNull(cache.read("../escape"))
        assertFalse(File(folder.root, "escape").exists())
        assertEquals(null, File(folder.root, "sealed").listFiles()?.size?.takeIf { it > 0 })
    }

    @Test
    fun pruningKeepsOnlyWhatTheListingStillHolds() = runTest {
        val cache = cache()
        cache.write(a, byteArrayOf(1))
        cache.write(b, byteArrayOf(2))
        cache.prune(setOf(a))
        assertArrayEquals(byteArrayOf(1), cache.read(a))
        assertNull(cache.read(b))
    }

    @Test
    fun aHalfWrittenFileIsSweptAndNeverRead() = runTest {
        val cache = cache()
        File(folder.root, "sealed").mkdirs()
        File(folder.root, "sealed/$a.part").writeBytes(byteArrayOf(5))
        assertNull(cache.read(a))
        cache.prune(setOf(a))
        assertFalse(File(folder.root, "sealed/$a.part").exists())
    }

    @Test
    fun clearingEmptiesTheCache() = runTest {
        val cache = cache()
        cache.write(a, byteArrayOf(1))
        cache.clear()
        assertNull(cache.read(a))
        assertTrue(File(folder.root, "sealed").listFiles()?.isEmpty() != false)
    }
}
