package zekke.android.uploads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.android.sync.SyncService
import zekke.core.files.ObjectStore
import zekke.core.files.UploadOptions
import zekke.core.files.UploadPhase
import zekke.core.files.UploadSource
import zekke.core.files.abandonUpload
import zekke.core.files.uploadToDrive
import zekke.core.items.ItemContext

class CoreUploadRunner(
    private val sync: SyncService,
    private val context: ItemContext,
    private val store: ObjectStore,
) : UploadRunner {
    override suspend fun run(entry: UploadEntry, source: UploadSource, thumbnail: ByteArray?, onProgress: (Int, Boolean) -> Unit) {
        withContext(Dispatchers.Default) {
            uploadToDrive(
                context,
                source,
                store,
                entry.folderId,
                thumbnail,
                entry.id,
                UploadOptions(
                    onProgress = { progress ->
                        val percent = if (progress.totalBytes <= 0) 0 else (progress.doneBytes * 100 / progress.totalBytes).toInt().coerceIn(0, 100)
                        onProgress(percent, progress.phase == UploadPhase.COMPLETING)
                    },
                ),
            )
        }
    }

    override suspend fun abandon(id: String) {
        abandonUpload(context, id)
    }
}
