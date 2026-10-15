package zekke.android.items

import kotlinx.coroutines.CancellationException
import zekke.android.feature.settings.PlanInfo
import zekke.android.feature.settings.PlanLoad
import zekke.android.feature.settings.PlanServices
import zekke.android.feature.settings.PlanStates
import zekke.android.ui.components.StorageReading
import zekke.core.files.getStorageUsage
import zekke.core.items.ItemContext
import zekke.core.users.AccountRecord
import zekke.core.users.getMe

fun planInfoOf(account: AccountRecord): PlanInfo {
    val plan = account.plan
    return PlanInfo(
        code = plan?.code ?: PlanStates.FREE,
        state = plan?.state ?: PlanStates.FREE,
        paidUntil = plan?.paidUntil,
        renews = plan?.renews ?: false,
        graceEndsAt = plan?.graceEndsAt,
        retentionDays = plan?.retentionDays ?: account.retentionDays,
    )
}

class CorePlanServices(
    private val calls: ItemCalls,
    private val context: ItemContext,
) : PlanServices {
    override suspend fun load(): PlanLoad = calls.read {
        val account = getMe(context.api)
        val storage = try {
            getStorageUsage(context).let { StorageReading(it.storedBytes, it.usedBytes, it.quotaBytes) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        PlanLoad.Loaded(planInfoOf(account), storage)
    }.fold({ it }, { PlanLoad.Failed(it) })
}
