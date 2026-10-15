package zekke.android.session

import java.io.File
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import zekke.core.session.DEFAULT_IDLE_TIMEOUT

class IdleTimeoutStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = FileIdleTimeoutStore(File(folder.root, "idle/idle-timeout.txt"))

    @Test
    fun withNothingSavedTheDefaultOfFifteenMinutesApplies() {
        assertEquals(15, store().minutes())
        assertEquals(DEFAULT_IDLE_TIMEOUT, idleDurationOf(store().minutes()))
    }

    @Test
    fun aChoiceIsKeptAcrossInstances() {
        store().save(5)
        assertEquals(5, store().minutes())
        assertEquals(5.minutes, idleDurationOf(store().minutes()))
    }

    @Test
    fun aValueThatIsNotOfferedIsNeverSavedAndAGarbledFileFallsBack() {
        store().save(7)
        assertEquals(15, store().minutes())
        File(folder.root, "idle").mkdirs()
        File(folder.root, "idle/idle-timeout.txt").writeText("not a number")
        assertEquals(15, store().minutes())
        File(folder.root, "idle/idle-timeout.txt").writeText("999")
        assertEquals(15, store().minutes())
    }

    @Test
    fun anUnknownWindowMapsToTheDefaultDuration() {
        assertEquals(DEFAULT_IDLE_TIMEOUT, idleDurationOf(3))
    }
}
