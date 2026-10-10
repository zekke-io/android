package zekke.android.ui.clipboard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.os.SystemClock

const val CLIPBOARD_CLEAR_AFTER_MILLIS = 30_000L
const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

class SensitiveClipboard(private val context: Context, private val clearAfterMillis: Long = CLIPBOARD_CLEAR_AFTER_MILLIS) {
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    fun copy(label: String, value: CharSequence) {
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        clipboard.setPrimaryClip(clip)
        scheduleClear()
    }

    private fun scheduleClear() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + clearAfterMillis, clearIntent(context))
    }

    companion object {
        fun clearIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ClipboardClearReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(ClipboardManager::class.java).clearPrimaryClip()
    }
}
