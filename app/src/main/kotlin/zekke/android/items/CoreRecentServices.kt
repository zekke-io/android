package zekke.android.items

import zekke.android.feature.drive.DriveLoad
import zekke.android.feature.drive.DriveServices
import zekke.android.feature.notes.NotesLoad
import zekke.android.feature.notes.NotesServices
import zekke.android.feature.passwords.PasswordsLoad
import zekke.android.feature.passwords.PasswordsServices
import zekke.android.feature.settings.RecentEntry
import zekke.android.feature.settings.RecentKind
import zekke.android.feature.settings.RecentServices
import zekke.android.feature.vault.VaultLoad
import zekke.android.feature.vault.VaultServices
import zekke.core.credentials.siteLabel

fun PasswordsLoad.toRecent(): List<RecentEntry> =
    (this as? PasswordsLoad.Loaded)?.view?.rows.orEmpty().filter { it.readable }
        .map { RecentEntry(RecentKind.Password, it.id, siteLabel(it.site), it.changedAt) }

fun VaultLoad.toRecent(): List<RecentEntry> =
    (this as? VaultLoad.Loaded)?.view?.rows.orEmpty().filter { it.readable }
        .map { RecentEntry(RecentKind.Secret, it.id, it.name, it.updatedAt) }

fun NotesLoad.toRecent(untitledNote: String): List<RecentEntry> =
    (this as? NotesLoad.Loaded)?.tiles.orEmpty().filter { it.readable }
        .map { RecentEntry(RecentKind.Note, it.id, it.title ?: untitledNote, it.updatedAt) }

fun DriveLoad.toRecent(): List<RecentEntry> =
    (this as? DriveLoad.Loaded)?.files.orEmpty().filter { it.readable && !it.isUnfinished }
        .map { RecentEntry(RecentKind.File, it.id, it.name.orEmpty(), it.updatedAt) }

class CoreRecentServices(
    private val passwords: PasswordsServices,
    private val vault: VaultServices,
    private val notes: NotesServices,
    private val drive: DriveServices,
    private val untitledNote: () -> String,
) : RecentServices {
    override suspend fun load(): List<RecentEntry> =
        passwords.load().toRecent() + vault.load().toRecent() + notes.load().toRecent(untitledNote()) + drive.load().toRecent()
}
