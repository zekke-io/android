package zekke.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import zekke.android.ui.R

private const val SECONDS_PER_MINUTE = 60

@Composable
fun rateLimitedMessage(retryAfterSeconds: Long?): String =
    stringResource(R.string.rate_limited, waitDescription(retryAfterSeconds))

@Composable
fun waitDescription(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return stringResource(R.string.wait_moment)
    if (seconds < SECONDS_PER_MINUTE) {
        val count = seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return pluralStringResource(R.plurals.wait_seconds, count, count)
    }
    val minutes = (seconds / SECONDS_PER_MINUTE + if (seconds % SECONDS_PER_MINUTE == 0L) 0 else 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    if (minutes == 1) return stringResource(R.string.wait_minute)
    return pluralStringResource(R.plurals.wait_minutes, minutes, minutes)
}
