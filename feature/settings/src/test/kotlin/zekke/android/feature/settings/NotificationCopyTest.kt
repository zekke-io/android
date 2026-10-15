package zekke.android.feature.settings

import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.components.NoticeTone
import zekke.core.notifications.NOTIFICATION_KINDS
import zekke.core.notifications.NotificationRecord

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NotificationCopyTest {
    private val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val clock = NotificationClock({ now }, ZoneId.of("UTC"), Locale.US)

    private fun view(kind: String, createdAt: String = "2026-10-14T11:59:30Z", read: Boolean = false, vararg params: Pair<String, Any>): NotificationView {
        val json = JsonObject(params.associate { (key, value) -> key to if (value is Number) JsonPrimitive(value) else JsonPrimitive(value.toString()) })
        return notificationView(NotificationRecord("id", kind, json, createdAt, if (read) createdAt else null), resources, clock)
    }

    @Test
    fun aPurchaseNamesThePlanAndTheDate() {
        val view = view("purchase_succeeded", params = arrayOf("plan" to "premium_1", "paid_until" to "2026-11-14T00:00:00Z"))
        assertEquals("Premium 1 is active", view.title)
        assertEquals("Thank you. Your payment went through and Premium 1 is yours until Nov 14, 2026.", view.body)
        assertEquals(NoticeTone.Success, view.tone)
        assertTrue(view.unread)
    }

    @Test
    fun aPurchaseWithoutADateOmitsIt() {
        val view = view("purchase_succeeded", params = arrayOf("plan" to "free"))
        assertEquals("Thank you. Your payment went through and Free is yours.", view.body)
    }

    @Test
    fun anUnknownPlanCodeIsShownAsIs() {
        assertEquals("gold is active", view("purchase_succeeded", params = arrayOf("plan" to "gold")).title)
        assertEquals("your plan is active", view("purchase_succeeded").title)
    }

    @Test
    fun renewalFailureRefundExpiryAndGraceHaveTheirOwnWords() {
        assertEquals("Premium 2 renewed", view("subscription_renewed", params = arrayOf("plan" to "premium_2")).title)
        assertEquals(NoticeTone.Warning, view("payment_failed").tone)
        assertEquals("A payment was refunded", view("refunded").title)
        assertEquals("Premium 1 ends in 1 day", view("expiring", params = arrayOf("plan" to "premium_1", "days" to 1)).title)
        assertEquals("Premium 1 ends in 7 days", view("expiring", params = arrayOf("plan" to "premium_1", "days" to 7)).title)
        assertEquals("Premium 1 ends soon", view("expiring", params = arrayOf("plan" to "premium_1")).title)
        assertEquals("Premium 1 has ended", view("grace_started", params = arrayOf("plan" to "premium_1")).title)
        assertEquals("Your plan has ended", view("grace_started").title)
    }

    @Test
    fun aSmallerPlanGraceNamesTheDeadline() {
        val view = view("grace_started", params = arrayOf("plan" to "free", "reason" to "smaller_plan", "grace_ends_at" to "2026-10-28T00:00:00Z"))
        assertEquals("Your Drive is larger than Free allows", view.title)
        assertTrue(view.body.contains("and on Oct 28, 2026 the most recent uploads"))
        assertEquals(NoticeTone.Danger, view.tone)
        assertTrue(view("grace_started", params = arrayOf("reason" to "smaller_plan")).body.contains("and in 14 days the most recent uploads"))
    }

    @Test
    fun theCountdownAndTheDeletionFormatSizesAndCounts() {
        val countdown = view("data_loss_countdown", params = arrayOf("days_left" to 3, "over_bytes" to 1_572_864))
        assertEquals("Files will be deleted in 3 days", countdown.title)
        assertTrue(countdown.body.startsWith("Your files and the images in your documents take 1.5 MiB more than your plan allows. Renew,"))
        assertEquals("Files will be deleted soon", view("data_loss_countdown").title)
        assertEquals("Files were deleted", view("data_deleted").title)
        assertEquals(
            "2 files were deleted (512 B) so your Drive fits your plan. Everything else is untouched.",
            view("data_deleted", params = arrayOf("files" to 2, "deleted_bytes" to 512)).body,
        )
        assertEquals("1 file was deleted so your Drive fits your plan. Everything else is untouched.", view("data_deleted", params = arrayOf("files" to 1)).body)
        assertEquals("Files were deleted so your Drive fits your plan. Everything else is untouched.", view("data_deleted").body)
    }

    @Test
    fun anUnknownKindIsAGenericNoticeAndNeverDropped() {
        val view = view("something_new_from_the_server")
        assertEquals("Account notice", view.title)
        assertEquals("Something changed on your account. Update the app to read the details.", view.body)
        assertEquals(NoticeTone.Info, view.tone)
    }

    @Test
    fun everyKnownKindIsWordedAndNoneFallsToTheGenericNotice() {
        NOTIFICATION_KINDS.forEach { kind -> assertTrue(kind, view(kind).title != "Account notice") }
    }

    @Test
    fun wrongTypedParamsAreIgnoredNotShown() {
        val view = view("expiring", params = arrayOf("plan" to "premium_1", "days" to "soon"))
        assertEquals("Premium 1 ends soon", view.title)
        assertEquals("Files were deleted so your Drive fits your plan. Everything else is untouched.", view("data_deleted", params = arrayOf("files" to -4)).body)
    }

    @Test
    fun theAgeIsRelativeThenADate() {
        assertEquals("Just now", view("refunded", "2026-10-14T11:59:30Z").age)
        assertEquals("5 min ago", view("refunded", "2026-10-14T11:55:00Z").age)
        assertEquals("3 h ago", view("refunded", "2026-10-14T09:00:00Z").age)
        assertEquals("Yesterday", view("refunded", "2026-10-13T09:00:00Z").age)
        assertEquals("3 days ago", view("refunded", "2026-10-11T09:00:00Z").age)
        assertEquals("Sep 1, 2026", view("refunded", "2026-09-01T09:00:00Z").age)
        assertEquals("", view("refunded", "not a date").age)
    }

    @Test
    fun aReadNoticeIsNotUnread() {
        assertEquals(false, view("refunded", read = true).unread)
    }

    @Test
    fun byteSizesAndTheBadgeFollowTheWebApp() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("2.5 GiB", formatBytes(2_684_354_560))
        assertEquals("5", notificationBadge(5))
        assertEquals("99", notificationBadge(99))
        assertEquals("99+", notificationBadge(100))
    }
}
