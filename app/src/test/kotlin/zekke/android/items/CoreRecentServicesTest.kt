package zekke.android.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.android.feature.drive.DriveEntry
import zekke.android.feature.drive.DriveLoad
import zekke.android.feature.notes.NotesLoad
import zekke.android.feature.passwords.PasswordsLoad
import zekke.android.feature.settings.RecentKind
import zekke.android.feature.vault.VaultLoad
import zekke.android.ui.components.ItemFailure
import zekke.core.credentials.PasswordRow
import zekke.core.credentials.PasswordsView
import zekke.core.notes.NoteTile
import zekke.core.secrets.VaultRow
import zekke.core.secrets.VaultView

class CoreRecentServicesTest {
    private fun password(id: String, site: String, readable: Boolean = true) =
        PasswordRow(id, "r$id", 1, site, "user", "pw", "", "2026-10-18T10:00:00Z", readable, null)

    @Test
    fun passwordsBecomeEntriesLabelledByTheirSite() {
        val load = PasswordsLoad.Loaded(PasswordsView(listOf(password("a", "https://www.nubank.com.br/login"), password("b", "x", readable = false)), emptyList()), true)
        val entries = load.toRecent()
        assertEquals(1, entries.size)
        assertEquals(RecentKind.Password, entries.single().kind)
        assertEquals("2026-10-18T10:00:00Z", entries.single().updatedAt)
    }

    @Test
    fun secretsBecomeEntriesNamedAsTheyAre() {
        val rows = listOf(
            VaultRow("s1", "Savings wallet", "v", 1, "1", "2026-10-17T10:00:00Z", true),
            VaultRow("s2", "", "", 0, "1", "2026-10-17T10:00:00Z", false),
        )
        val entries = VaultLoad.Loaded(VaultView(rows, emptyList()), true).toRecent()
        assertEquals(listOf("Savings wallet"), entries.map { it.title })
        assertEquals(RecentKind.Secret, entries.single().kind)
    }

    @Test
    fun anUntitledNoteGetsTheFallbackTitle() {
        val tiles = listOf(
            NoteTile("n1", "Trip ideas", "t", 1, "1", "2026-10-01T10:00:00Z", "2026-10-18T10:00:00Z", true),
            NoteTile("n2", null, "t", 1, "1", "2026-10-01T10:00:00Z", "2026-10-17T10:00:00Z", true),
            NoteTile("n3", null, "t", 1, "1", "2026-10-01T10:00:00Z", "2026-10-16T10:00:00Z", false),
        )
        val entries = NotesLoad.Loaded(tiles, true).toRecent("Untitled note")
        assertEquals(listOf("Trip ideas", "Untitled note"), entries.map { it.title })
    }

    @Test
    fun finishedReadableFilesAreListedAndUnfinishedOnesAreNot() {
        val files = listOf(
            DriveEntry("f1", "Passport.pdf", "application/pdf", 1, null, null, "ok", "ok", "2026-10-10T10:00:00Z"),
            DriveEntry("f2", "half.bin", null, 1, null, null, "pending", "ok", "2026-10-10T10:00:00Z"),
            DriveEntry("f3", null, null, 1, null, null, "ok", "ok", "2026-10-10T10:00:00Z"),
        )
        val entries = DriveLoad.Loaded(files, emptyList(), true).toRecent()
        assertEquals(listOf("Passport.pdf"), entries.map { it.title })
        assertEquals(RecentKind.File, entries.single().kind)
    }

    @Test
    fun aFailedLoadContributesNothing() {
        assertTrue(PasswordsLoad.Failed(ItemFailure.Failed).toRecent().isEmpty())
        assertTrue(VaultLoad.Failed(ItemFailure.Failed).toRecent().isEmpty())
        assertTrue(NotesLoad.Failed(ItemFailure.Failed).toRecent("x").isEmpty())
        assertTrue(DriveLoad.Failed(ItemFailure.Failed).toRecent().isEmpty())
    }
}
