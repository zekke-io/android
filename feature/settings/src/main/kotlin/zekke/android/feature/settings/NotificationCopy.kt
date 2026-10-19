package zekke.android.feature.settings

import android.content.res.Resources
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import zekke.android.ui.components.NoticeTone
import zekke.core.notifications.NotificationRecord

const val NOTIFICATION_BADGE_CAP = 99

data class NotificationView(
    val id: String,
    val title: String,
    val body: String,
    val tone: NoticeTone,
    val age: String,
    val unread: Boolean,
)

class NotificationClock(
    val now: () -> Instant = Instant::now,
    val zone: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault(),
)

private val PLAN_NAMES = mapOf("free" to "Free", "premium_1" to "Premium 1", "premium_2" to "Premium 2")
private val BYTE_UNITS = listOf("KiB", "MiB", "GiB", "TiB")

fun planName(code: String?, resources: Resources): String = when (code) {
    null -> resources.getString(R.string.notif_your_plan)
    else -> PLAN_NAMES[code] ?: code
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, BYTE_UNITS[unit])
}

fun notificationBadge(count: Int): String = if (count > NOTIFICATION_BADGE_CAP) "$NOTIFICATION_BADGE_CAP+" else count.toString()

fun notificationView(record: NotificationRecord, resources: Resources, clock: NotificationClock = NotificationClock()): NotificationView {
    val copy = NotificationCopyBuilder(record.params, resources, clock).build(record.kind)
    return NotificationView(record.id, copy.title, copy.body, copy.tone, age(record.createdAt, resources, clock), unread = !record.isRead)
}

private class NotificationText(val title: String, val body: String, val tone: NoticeTone)

private class NotificationCopyBuilder(val params: JsonObject, val resources: Resources, val clock: NotificationClock) {
    private val plan = planName(text("plan"), resources)

    fun build(kind: String): NotificationText = when (kind) {
        "purchase_succeeded" -> NotificationText(
            resources.getString(R.string.notif_purchase_title, plan),
            resources.getString(R.string.notif_purchase_body, plan, until(text("paid_until"))),
            NoticeTone.Success,
        )
        "subscription_renewed" -> NotificationText(
            resources.getString(R.string.notif_renewed_title, plan),
            resources.getString(R.string.notif_renewed_body, until(text("paid_until"))),
            NoticeTone.Success,
        )
        "payment_failed" -> NotificationText(
            resources.getString(R.string.notif_failed_title),
            resources.getString(R.string.notif_failed_body, plan),
            NoticeTone.Warning,
        )
        "refunded" -> NotificationText(
            resources.getString(R.string.notif_refunded_title),
            resources.getString(R.string.notif_refunded_body, plan),
            NoticeTone.Info,
        )
        "expiring" -> NotificationText(
            count("days")?.let { resources.getQuantityString(R.plurals.notif_expiring_title, it, plan, it) }
                ?: resources.getString(R.string.notif_expiring_title_soon, plan),
            resources.getString(R.string.notif_expiring_body),
            NoticeTone.Warning,
        )
        "grace_started" -> graceStarted()
        "data_loss_countdown" -> dataLossCountdown()
        "data_deleted" -> dataDeleted()
        else -> NotificationText(
            resources.getString(R.string.notif_unknown_title),
            resources.getString(R.string.notif_unknown_body),
            NoticeTone.Info,
        )
    }

    private fun graceStarted(): NotificationText {
        val date = date(text("grace_ends_at"))
        val deadline = if (date == null) resources.getString(R.string.notif_deadline_default) else resources.getString(R.string.notif_deadline_on, date)
        return if (text("reason") == "smaller_plan") {
            NotificationText(
                resources.getString(R.string.notif_grace_smaller_title, plan),
                resources.getString(R.string.notif_grace_smaller_body, deadline, plan),
                NoticeTone.Danger,
            )
        } else {
            NotificationText(
                if (text("plan") != null) resources.getString(R.string.notif_grace_ended_title, plan) else resources.getString(R.string.notif_grace_ended_title_unknown),
                resources.getString(R.string.notif_grace_ended_body, deadline),
                NoticeTone.Danger,
            )
        }
    }

    private fun dataLossCountdown(): NotificationText {
        val left = count("days_left")
        val over = count("over_bytes")
        val first = if (over == null) {
            resources.getString(R.string.notif_countdown_body_unknown)
        } else {
            resources.getString(R.string.notif_countdown_body_over, formatBytes(over.toLong()))
        }
        return NotificationText(
            left?.let { resources.getQuantityString(R.plurals.notif_countdown_title, it, it) } ?: resources.getString(R.string.notif_countdown_title_soon),
            first + " " + resources.getString(R.string.notif_countdown_body_tail),
            NoticeTone.Danger,
        )
    }

    private fun dataDeleted(): NotificationText {
        val files = count("files")
        val bytes = count("deleted_bytes")
        val what = files?.let { resources.getQuantityString(R.plurals.notif_deleted_files, it, it) } ?: resources.getString(R.string.notif_deleted_files_unknown)
        val size = bytes?.let { " " + resources.getString(R.string.notif_deleted_size, formatBytes(it.toLong())) }.orEmpty()
        return NotificationText(
            resources.getString(R.string.notif_deleted_title),
            resources.getString(R.string.notif_deleted_body, what, size),
            NoticeTone.Danger,
        )
    }

    private fun text(key: String): String? = (params[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun count(key: String): Int? {
        val primitive = params[key] as? JsonPrimitive ?: return null
        if (primitive.isString || primitive.booleanOrNull != null) return null
        val value = primitive.doubleOrNull ?: return null
        return if (value.isFinite() && value >= 0 && value <= Int.MAX_VALUE) value.toInt() else null
    }

    private fun until(at: String?): String = date(at)?.let { " " + resources.getString(R.string.notif_until, it) }.orEmpty()

    private fun date(at: String?): String? = at?.let { formatDate(it, clock) }
}

private fun parseInstant(at: String): Instant? = runCatching { Instant.parse(at) }.getOrNull()

private fun formatDate(at: String, clock: NotificationClock): String? =
    parseInstant(at)?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(clock.locale).withZone(clock.zone).format(it) }

fun activityAge(at: String, resources: Resources, clock: NotificationClock = NotificationClock()): String = age(at, resources, clock)

private fun age(createdAt: String, resources: Resources, clock: NotificationClock): String {
    val moment = parseInstant(createdAt) ?: return ""
    val elapsed = Duration.between(moment, clock.now()).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed < Duration.ofMinutes(1) -> resources.getString(R.string.notif_just_now)
        elapsed < Duration.ofHours(1) -> resources.getString(R.string.notif_minutes_ago, elapsed.toMinutes().toInt())
        elapsed < Duration.ofDays(1) -> resources.getString(R.string.notif_hours_ago, elapsed.toHours().toInt())
        elapsed < Duration.ofDays(7) -> {
            val days = elapsed.toDays().toInt()
            if (days == 1) resources.getString(R.string.notif_yesterday) else resources.getQuantityString(R.plurals.notif_days_ago, days, days)
        }
        else -> formatDate(createdAt, clock).orEmpty()
    }
}
