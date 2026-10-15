package zekke.android.feature.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.core.folders.createFolder
import zekke.core.folders.emptyFolderManifest
import zekke.core.folders.placeItem
import zekke.core.sharing.SHARED_FOLDER_RULES

class SharedPolicyTest {
    private val copyable = setOf(SharedType.Secret, SharedType.Note, SharedType.File)

    @Test
    fun onlyWhatArrivedAndCanBeOpenedAndKeptCanBeCopied() {
        assertTrue(canCopy(sharedItem("1", "n"), copyable))
        assertFalse(canCopy(sharedItem("1", "n", inbound = false), copyable))
        assertFalse(canCopy(sharedItem("1", null, readable = false), copyable))
        assertFalse(canCopy(sharedItem("1", "d", type = SharedType.Document), copyable + SharedType.Document))
        assertFalse(canCopy(sharedItem("1", "s", type = SharedType.Secret), setOf(SharedType.Note)))
        assertFalse(canCopy(sharedItem("1", "x", type = null), copyable))
    }

    @Test
    fun onlyAReadableFileCanBeDownloaded() {
        assertTrue(canDownload(sharedItem("1", "f", type = SharedType.File)))
        assertTrue(canDownload(sharedItem("1", "f", type = SharedType.File, inbound = false)))
        assertFalse(canDownload(sharedItem("1", "f", type = SharedType.File, readable = false)))
        assertFalse(canDownload(sharedItem("1", "n", type = SharedType.Note)))
    }

    @Test
    fun theWireNamesMapToTheTypesAndAnUnknownOneToNone() {
        assertEquals(SharedType.Secret, sharedTypeOf("secret"))
        assertEquals(SharedType.Document, sharedTypeOf("document"))
        assertNull(sharedTypeOf("spreadsheet"))
    }

    @Test
    fun everythingIsAtTheTopWithoutAManifestAndNothingInsideAFolder() {
        val items = listOf(sharedItem("1", "a"), sharedItem("2", "b"))
        assertEquals(items, itemsInFolder(items, null, null))
        assertTrue(itemsInFolder(items, null, "f").isEmpty())
    }

    @Test
    fun anItemIsWhereTheManifestPutsItAndAtTheTopOtherwise() {
        val items = listOf(sharedItem("1", "a"), sharedItem("2", "b"))
        var manifest = emptyFolderManifest(SHARED_FOLDER_RULES, AT)
        manifest = createFolder("Trips", null, "f1", AT).apply(manifest, SHARED_FOLDER_RULES)
        manifest = placeItem("1", "f1", AT).apply(manifest, SHARED_FOLDER_RULES)
        assertEquals(listOf("1"), itemsInFolder(items, manifest, "f1").map { it.shareId })
        assertEquals(listOf("2"), itemsInFolder(items, manifest, null).map { it.shareId })
    }

    @Test
    fun anItemFiledInADeletedFolderFallsToTheTop() {
        val items = listOf(sharedItem("1", "a"))
        var manifest = emptyFolderManifest(SHARED_FOLDER_RULES, AT)
        manifest = createFolder("Trips", null, "f1", AT).apply(manifest, SHARED_FOLDER_RULES)
        manifest = createFolder("Inner", "f1", "f2", AT).apply(manifest, SHARED_FOLDER_RULES)
        manifest = placeItem("1", "f2", AT).apply(manifest, SHARED_FOLDER_RULES)
        manifest = zekke.core.folders.deleteFolder("f1", AT).apply(manifest, SHARED_FOLDER_RULES)
        assertEquals(listOf("1"), itemsInFolder(items, manifest, null).map { it.shareId })
        assertTrue(foldersIn(manifest, null).isEmpty())
    }

    @Test
    fun theFolderChoicesWalkTheTreeWithTheirDepth() {
        var manifest = emptyFolderManifest(SHARED_FOLDER_RULES, AT)
        manifest = createFolder("B", null, "b", AT).apply(manifest, SHARED_FOLDER_RULES)
        manifest = createFolder("A", null, "a", AT).apply(manifest, SHARED_FOLDER_RULES)
        manifest = createFolder("Inner", "b", "i", AT).apply(manifest, SHARED_FOLDER_RULES)
        val choices = folderChoices(manifest)
        assertEquals(listOf("b" to 0, "i" to 1, "a" to 0), choices.map { it.id to it.depth })
        assertEquals(listOf("B", "Inner", "A"), choices.map { it.name })
    }

    @Test
    fun friendshipsAreTheAcceptedConnectionsByName() {
        val list = listOf(
            connection("1", "zed"),
            connection("2", "amy", ConnectionStanding.WaitingForMe),
            connection("3", "bob", nickname = "Al"),
            connection("4", "cat", ConnectionStanding.WaitingForThem),
        )
        assertEquals(listOf("3", "1"), friendships(list).map { it.id })
    }
}
