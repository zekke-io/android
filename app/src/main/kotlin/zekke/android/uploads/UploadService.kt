package zekke.android.uploads

import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.feature.drive.UploadItem
import zekke.android.feature.drive.UploadStatus
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.files.SourceMismatchException
import zekke.core.files.UploadSource
import zekke.core.memory.SecretZeroedException
import zekke.core.session.SessionLockedException
import zekke.core.session.SessionState

const val OFFLINE_RETRY_MILLIS = 30_000L
const val DEFAULT_CREATION_PAUSE_SECONDS = 30L

interface UploadRunner {
    suspend fun run(entry: UploadEntry, source: UploadSource, thumbnail: ByteArray?, onProgress: (Int, Boolean) -> Unit)

    suspend fun abandon(id: String)
}

class Classified(val status: UploadStatus, val retryAfterSeconds: Long? = null)

fun classifyUploadFailure(error: Throwable): Classified = when {
    error is SecretZeroedException || error is SessionLockedException -> Classified(UploadStatus.Locked)
    error is NetworkError -> Classified(UploadStatus.Offline)
    error is ApiError && error.isRateLimited -> Classified(UploadStatus.RateLimited, error.retryAfterSeconds ?: DEFAULT_CREATION_PAUSE_SECONDS)
    error is ApiError && error.isQuotaExceeded -> Classified(UploadStatus.QuotaFull)
    error is SourceMismatchException || error is SecurityException || error is FileNotFoundException -> Classified(UploadStatus.NeedsFile)
    else -> Classified(UploadStatus.Failed)
}

class UploadService(
    private val scope: CoroutineScope,
    private val sessionState: StateFlow<SessionState>,
    private val store: UploadStore,
    private val runner: UploadRunner,
    private val sources: UploadSources,
    private val thumbnails: ThumbnailMaker,
    private val transferStarted: () -> Unit,
    private val transferFinished: () -> Unit,
    private val finished: () -> Unit,
    private val newId: () -> String,
    private val offlineRetryMillis: Long = OFFLINE_RETRY_MILLIS,
) {
    private val mutableItems = MutableStateFlow<List<UploadItem>>(emptyList())
    private var worker: Job? = null
    private var currentId: String? = null

    val items: StateFlow<List<UploadItem>> = mutableItems.asStateFlow()

    fun start() {
        scope.launch {
            sessionState.collect { state ->
                if (state == SessionState.UNLOCKED) unlocked() else locked()
            }
        }
    }

    private fun unlocked() {
        mutableItems.value = store.load().map { UploadItem(it.id, it.name, it.size, it.folderId, UploadStatus.Waiting) }
        launchWorker()
    }

    private fun locked() {
        worker?.cancel()
        worker = null
        mutableItems.value = emptyList()
    }

    private fun launchWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch { drain() }
    }

    private suspend fun drain() {
        while (true) {
            val next = mutableItems.value.firstOrNull { it.status == UploadStatus.Waiting } ?: return
            process(next.id)
        }
    }

    private fun update(id: String, status: UploadStatus, percent: Int = 0, retryAfter: Long? = null) {
        mutableItems.update { list -> list.map { if (it.id == id) it.copy(status = status, percent = percent, retryAfterSeconds = retryAfter) else it } }
    }

    private suspend fun process(id: String) {
        val entry = store.load().firstOrNull { it.id == id }
        if (entry == null) {
            mutableItems.update { list -> list.filterNot { it.id == id } }
            return
        }
        update(id, UploadStatus.Uploading)
        currentId = id
        transferStarted()
        var failure: Classified? = null
        try {
            val source = sources.open(entry)
            val thumbnail = thumbnails.make(entry.uri, entry.mime)
            runner.run(entry, source, thumbnail) { percent, finishing ->
                update(id, if (finishing) UploadStatus.Finishing else UploadStatus.Uploading, percent)
            }
            store.remove(id)
            sources.release(entry.uri)
            mutableItems.update { list -> list.filterNot { it.id == id } }
            finished()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = classifyUploadFailure(error)
        } finally {
            currentId = null
            transferFinished()
        }
        val outcome = failure ?: return
        update(id, outcome.status, retryAfter = outcome.retryAfterSeconds)
        when (outcome.status) {
            UploadStatus.Offline -> {
                delay(offlineRetryMillis)
                update(id, UploadStatus.Waiting)
            }
            UploadStatus.RateLimited -> {
                delay((outcome.retryAfterSeconds ?: DEFAULT_CREATION_PAUSE_SECONDS) * 1000)
                update(id, UploadStatus.Waiting)
            }
            else -> Unit
        }
    }

    fun enqueue(uris: List<String>, folderId: String?) {
        for (uri in uris) {
            val info = try {
                sources.describe(uri)
            } catch (_: Exception) {
                continue
            }
            val entry = UploadEntry(newId(), uri, info.name, info.mime, info.size, folderId)
            sources.keepReadable(uri)
            store.add(entry)
            mutableItems.update { it + UploadItem(entry.id, entry.name, entry.size, folderId, UploadStatus.Waiting) }
        }
        launchWorker()
    }

    fun retry(id: String) {
        val item = mutableItems.value.firstOrNull { it.id == id } ?: return
        if (item.status == UploadStatus.Uploading || item.status == UploadStatus.Finishing || item.status == UploadStatus.Waiting) return
        update(id, UploadStatus.Waiting)
        launchWorker()
    }

    fun chooseAgain(id: String, uri: String) {
        val entry = store.load().firstOrNull { it.id == id } ?: return
        val info = try {
            sources.describe(uri)
        } catch (_: Exception) {
            return
        }
        sources.keepReadable(uri)
        if (uri != entry.uri) sources.release(entry.uri)
        store.replace(entry.copy(uri = uri, name = info.name, mime = info.mime, size = info.size))
        update(id, UploadStatus.Waiting)
        launchWorker()
    }

    suspend fun cancel(id: String) {
        val entry = store.load().firstOrNull { it.id == id }
        val running = worker
        val wasRunning = currentId == id && running?.isActive == true
        if (wasRunning) {
            running?.cancelAndJoin()
            worker = null
        }
        mutableItems.update { list -> list.filterNot { it.id == id } }
        store.remove(id)
        entry?.let { sources.release(it.uri) }
        try {
            runner.abandon(id)
        } catch (_: Exception) {
        }
        if (wasRunning) mutableItems.update { list ->
            list.map { if (it.status == UploadStatus.Uploading || it.status == UploadStatus.Finishing) it.copy(status = UploadStatus.Waiting, percent = 0) else it }
        }
        launchWorker()
    }
}
