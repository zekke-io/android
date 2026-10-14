package zekke.android.feature.unlock

sealed interface ClientVersionStatus {
    data object Current : ClientVersionStatus
    data class UpdateAvailable(val latest: String) : ClientVersionStatus
    data class Deprecated(val latest: String, val endsAtEpochMillis: Long) : ClientVersionStatus
    data class UpgradeRequired(val latest: String?) : ClientVersionStatus
}
