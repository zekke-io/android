package zekke.android.uploads

import java.io.FileNotFoundException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.android.feature.drive.UploadStatus
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.files.SourceMismatch
import zekke.core.files.SourceMismatchException
import zekke.core.files.UploadSource
import zekke.core.memory.SecretZeroedException
import zekke.core.session.SessionState

@OptIn(ExperimentalCoroutinesApi::class)
class UploadServiceTest {
    private class MemoryStore(var entries: List<UploadEntry> = emptyList()) : UploadStore {
        override fun load() = entries
        override fun add(entry: UploadEntry) { entries = entries.filterNot { it.id == entry.id } + entry }
        override fun remove(id: String) { entries = entries.filterNot { it.id == id } }
        override fun replace(entry: UploadEntry) { entries = entries.map { if (it.id == entry.id) entry else it } }
        override fun clear() { entries = emptyList() }
    }

    private class FakeSources : UploadSources {
        val infos = mutableMapOf<String, SourceInfo>()
        val kept = mutableListOf<String>()
        val released = mutableListOf<String>()
        var openFails: Exception? = null
        override fun describe(uri: String) = infos[uri] ?: throw UnreadableSourceException(uri)
        override fun open(entry: UploadEntry) = object : UploadSource {
            override val name = entry.name
            override val mime = entry.mime
            override val size = entry.size
            override suspend fun open() = throw UnsupportedOperationException()
        }.also { openFails?.let { throw it } }
        override fun keepReadable(uri: String): Boolean { kept += uri; return true }
        override fun release(uri: String) { released += uri }
    }

    private class FakeRunner : UploadRunner {
        val ran = mutableListOf<String>()
        val thumbnailsSeen = mutableListOf<ByteArray?>()
        val abandoned = mutableListOf<String>()
        var existing = false
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var steps = listOf(30 to false, 100 to false)
        var progressSeen = mutableListOf<Int>()
        override suspend fun exists(id: String) = existing
        override suspend fun run(entry: UploadEntry, source: UploadSource, thumbnail: ByteArray?, onProgress: (Int, Boolean) -> Unit) {
            ran += entry.id
            thumbnailsSeen += thumbnail
            for ((percent, finishing) in steps) {
                onProgress(percent, finishing)
                progressSeen += percent
            }
            gate?.await()
            failure?.let { failure = null; throw it }
        }
        override suspend fun abandon(id: String) { abandoned += id }
    }

    private class FakeThumbnails(var bytes: ByteArray? = byteArrayOf(1, 2)) : ThumbnailMaker {
        val asked = mutableListOf<String>()
        override suspend fun make(uri: String, mime: String): ByteArray? { asked += uri; return bytes }
    }

    private val scope = TestScope()
    private val session = MutableStateFlow(SessionState.UNLOCKED)
    private val store = MemoryStore()
    private val sources = FakeSources()
    private val runner = FakeRunner()
    private val thumbnails = FakeThumbnails()
    private var starts = 0
    private var finishes = 0
    private var syncs = 0
    private var ids = 0
    private val service = UploadService(
        scope = scope,
        sessionState = session,
        store = store,
        runner = runner,
        sources = sources,
        thumbnails = thumbnails,
        transferStarted = { starts++ },
        transferFinished = { finishes++ },
        finished = { syncs++ },
        newId = { "up-${++ids}" },
        offlineRetryMillis = 1_000,
    )

    private fun begin() {
        service.start()
        scope.runCurrent()
    }

    private fun pick(uri: String = "content://a", name: String = "a.bin", mime: String = "image/jpeg", size: Long = 100, folder: String? = null) {
        sources.infos[uri] = SourceInfo(name, mime, size)
        service.enqueue(listOf(uri), folder)
        scope.runCurrent()
    }

    private val statuses get() = service.items.value.map { it.status }

    @Test
    fun aPickedFileIsPersistedKeptReadableUploadedAndForgotten() {
        begin()
        pick(folder = "f1")
        assertEquals(listOf("up-1"), runner.ran)
        assertEquals(listOf<ByteArray?>(thumbnails.bytes).size, runner.thumbnailsSeen.size)
        assertTrue(service.items.value.isEmpty())
        assertTrue(store.entries.isEmpty())
        assertEquals(listOf("content://a"), sources.kept)
        assertEquals(listOf("content://a"), sources.released)
        assertEquals(1, syncs)
        assertEquals(starts, finishes)
    }

    @Test
    fun progressIsReportedAndTheFinishingPhaseIsNamed() {
        runner.steps = listOf(10 to false, 100 to true)
        runner.gate = CompletableDeferred()
        begin()
        pick()
        assertEquals(UploadStatus.Finishing, service.items.value.single().status)
        assertEquals(100, service.items.value.single().percent)
        runner.gate!!.complete(Unit)
        scope.runCurrent()
        assertTrue(service.items.value.isEmpty())
    }

    @Test
    fun anUnreadableUriIsSkippedNotQueued() {
        begin()
        service.enqueue(listOf("content://missing"), null)
        scope.runCurrent()
        assertTrue(store.entries.isEmpty())
        assertTrue(runner.ran.isEmpty())
    }

    @Test
    fun uploadsRunOneAtATimeInTheOrderPicked() {
        runner.gate = CompletableDeferred()
        begin()
        sources.infos["content://a"] = SourceInfo("a", "text/plain", 1)
        sources.infos["content://b"] = SourceInfo("b", "text/plain", 1)
        service.enqueue(listOf("content://a", "content://b"), null)
        scope.runCurrent()
        assertEquals(listOf("up-1"), runner.ran)
        assertEquals(listOf(UploadStatus.Uploading, UploadStatus.Waiting), statuses)
        runner.gate!!.complete(Unit)
        scope.runCurrent()
        assertEquals(listOf("up-1", "up-2"), runner.ran)
    }

    @Test
    fun aResumedUploadSendsNoNewThumbnail() {
        runner.existing = true
        begin()
        pick()
        assertEquals(listOf<ByteArray?>(null), runner.thumbnailsSeen)
        assertTrue(thumbnails.asked.isEmpty())
    }

    @Test
    fun aLockStopsTheUploadKeepsItOnDiskAndTheNextUnlockResumesIt() {
        runner.gate = CompletableDeferred()
        begin()
        pick()
        assertEquals(1, runner.ran.size)
        session.value = SessionState.LOCKED
        scope.runCurrent()
        assertTrue(service.items.value.isEmpty())
        assertEquals(1, store.entries.size)
        assertEquals(starts, finishes)
        runner.gate = null
        session.value = SessionState.UNLOCKED
        scope.runCurrent()
        assertEquals(listOf("up-1", "up-1"), runner.ran)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun theQueueSavedBeforeIsReloadedAtUnlock() {
        store.entries = listOf(UploadEntry("old", "content://old", "old.bin", "text/plain", 5, null))
        begin()
        assertEquals(listOf("old"), runner.ran)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun theOfflineUploadWaitsThenTriesAgain() {
        runner.failure = NetworkError("/files", RuntimeException("down"))
        begin()
        pick()
        assertEquals(listOf(UploadStatus.Offline), statuses)
        scope.advanceTimeBy(1_100)
        scope.runCurrent()
        assertEquals(listOf("up-1", "up-1"), runner.ran)
        assertTrue(service.items.value.isEmpty())
    }

    @Test
    fun aRateLimitPausesForTheServersWaitAndNothingFails() {
        runner.failure = ApiError("RATE_LIMITED", 429, "/files", retryAfterSeconds = 20)
        begin()
        pick()
        assertEquals(UploadStatus.RateLimited, service.items.value.single().status)
        assertEquals(20L, service.items.value.single().retryAfterSeconds)
        scope.advanceTimeBy(19_000)
        assertEquals(1, runner.ran.size)
        scope.advanceTimeBy(1_500)
        scope.runCurrent()
        assertEquals(2, runner.ran.size)
        assertTrue(service.items.value.isEmpty())
    }

    @Test
    fun aFullQuotaIsNeverRetriedUntilThePersonAsks() {
        runner.failure = ApiError("QUOTA_EXCEEDED", 507, "/files")
        begin()
        pick()
        scope.advanceTimeBy(600_000)
        assertEquals(listOf(UploadStatus.QuotaFull), statuses)
        assertEquals(1, runner.ran.size)
        service.retry("up-1")
        scope.runCurrent()
        assertEquals(2, runner.ran.size)
    }

    @Test
    fun aFileThatCannotBeReadOrIsNotTheSameAsksForTheFileAgain() {
        sources.openFails = FileNotFoundException("gone")
        begin()
        pick()
        assertEquals(listOf(UploadStatus.NeedsFile), statuses)
        sources.openFails = null
        sources.infos["content://new"] = SourceInfo("a.bin", "image/jpeg", 100)
        service.chooseAgain("up-1", "content://new")
        scope.runCurrent()
        assertEquals(listOf("content://a", "content://new"), sources.released)
        assertEquals(listOf("content://new"), sources.kept.drop(1))
        assertEquals(listOf("up-1"), runner.ran)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun aDifferentFileIsReportedAsNeedingTheRightOne() {
        runner.failure = SourceMismatchException(SourceMismatch.CONTENT)
        begin()
        pick()
        assertEquals(listOf(UploadStatus.NeedsFile), statuses)
    }

    @Test
    fun anUnknownFailureCanBeRetriedByHand() {
        runner.failure = IllegalStateException("boom")
        begin()
        pick()
        assertEquals(listOf(UploadStatus.Failed), statuses)
        service.retry("up-1")
        scope.runCurrent()
        assertTrue(service.items.value.isEmpty())
    }

    @Test
    fun cancellingForgetsTheEntryReleasesTheFileAndGivesTheReservationBack() {
        runner.gate = CompletableDeferred()
        begin()
        pick()
        scope.runCurrent()
        var done = false
        scope.launchCancel { done = true }
        scope.runCurrent()
        assertTrue(done)
        assertTrue(store.entries.isEmpty())
        assertEquals(listOf("up-1"), runner.abandoned)
        assertEquals(listOf("content://a"), sources.released)
        assertTrue(service.items.value.isEmpty())
        assertEquals(starts, finishes)
    }

    @Test
    fun theFailureClassesAreKeptApart() {
        assertEquals(UploadStatus.Locked, classifyUploadFailure(SecretZeroedException()).status)
        assertEquals(UploadStatus.Offline, classifyUploadFailure(NetworkError("/files")).status)
        assertEquals(UploadStatus.QuotaFull, classifyUploadFailure(ApiError("X", 507, "/files")).status)
        assertEquals(DEFAULT_CREATION_PAUSE_SECONDS, classifyUploadFailure(ApiError("X", 429, "/files")).retryAfterSeconds)
        assertEquals(UploadStatus.NeedsFile, classifyUploadFailure(SecurityException()).status)
        assertEquals(UploadStatus.Failed, classifyUploadFailure(IllegalStateException()).status)
    }

    private fun TestScope.launchCancel(after: () -> Unit) {
        launch {
            service.cancel("up-1")
            after()
        }
    }
}
