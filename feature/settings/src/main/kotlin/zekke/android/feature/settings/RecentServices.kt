package zekke.android.feature.settings

enum class RecentKind { Note, Password, Secret, File }

data class RecentEntry(val kind: RecentKind, val id: String, val title: String, val updatedAt: String)

interface RecentServices {
    suspend fun load(): List<RecentEntry>
}
