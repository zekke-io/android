package zekke.android.session

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import zekke.android.feature.settings.IDLE_CHOICES_MINUTES
import zekke.core.session.DEFAULT_IDLE_TIMEOUT

interface IdleTimeoutStore {
    fun minutes(): Int

    fun save(minutes: Int)
}

fun idleDurationOf(minutes: Int): Duration = if (minutes in IDLE_CHOICES_MINUTES) minutes.minutes else DEFAULT_IDLE_TIMEOUT

class FileIdleTimeoutStore(private val file: File) : IdleTimeoutStore {
    override fun minutes(): Int = try {
        file.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()?.takeIf { it in IDLE_CHOICES_MINUTES } ?: DEFAULT_MINUTES
    } catch (_: Exception) {
        DEFAULT_MINUTES
    }

    override fun save(minutes: Int) {
        if (minutes !in IDLE_CHOICES_MINUTES) return
        try {
            file.parentFile?.mkdirs()
            file.writeText(minutes.toString())
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val DEFAULT_MINUTES = 15
    }
}
