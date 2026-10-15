package zekke.android.feature.settings

import zekke.android.ui.components.ItemFailure
import zekke.android.ui.components.StorageReading

data class PlanInfo(
    val code: String,
    val state: String,
    val paidUntil: String?,
    val renews: Boolean,
    val graceEndsAt: String?,
    val retentionDays: Int,
)

sealed interface PlanLoad {
    data class Loaded(val plan: PlanInfo, val storage: StorageReading?) : PlanLoad

    data class Failed(val failure: ItemFailure) : PlanLoad
}

interface PlanServices {
    suspend fun load(): PlanLoad
}

object PlanStates {
    const val FREE = "free"
    const val ACTIVE = "active"
    const val GRACE = "grace"
}
