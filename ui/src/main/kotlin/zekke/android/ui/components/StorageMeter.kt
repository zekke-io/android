package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

private val BYTE_UNITS = listOf("KiB", "MiB", "GiB", "TiB")

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit += 1
    }
    return String.format(Locale.ROOT, "%.1f %s", value, BYTE_UNITS[unit])
}

data class StorageReading(val storedBytes: Long, val usedBytes: Long, val quotaBytes: Long) {
    val storedShare: Float
        get() = share(storedBytes)

    val reservedShare: Float
        get() = max(0f, share(usedBytes) - storedShare)

    val freeBytes: Long
        get() = max(0L, quotaBytes - usedBytes)

    val unfinishedBytes: Long
        get() = max(0L, usedBytes - storedBytes)

    val percent: Double
        get() = (storedShare * 1000.0).roundToLong() / 10.0

    private fun share(bytes: Long): Float = if (quotaBytes <= 0) 0f else min(1f, bytes.toFloat() / quotaBytes)
}

@Composable
fun StorageMeter(reading: StorageReading, modifier: Modifier = Modifier) {
    val palette = Zekke.palette
    val percent = if (reading.percent % 1.0 == 0.0) reading.percent.toLong().toString() else reading.percent.toString()
    val used = stringResource(R.string.storage_used, formatBytes(reading.storedBytes), percent)
    val free = stringResource(R.string.storage_free, formatBytes(reading.freeBytes))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(used, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = palette.ink)
            Text(free, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, textAlign = TextAlign.End)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(palette.line)
                .clearAndSetSemantics { contentDescription = "$used, $free" },
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(min(1f, reading.storedShare + reading.reservedShare)).background(palette.brandSoft))
            Box(Modifier.fillMaxHeight().fillMaxWidth(reading.storedShare).clip(CircleShape).background(palette.brandMark))
        }
        Text(
            if (reading.unfinishedBytes > 0) {
                stringResource(R.string.storage_unfinished, formatBytes(reading.unfinishedBytes))
            } else {
                stringResource(R.string.storage_ready)
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
        )
    }
}
