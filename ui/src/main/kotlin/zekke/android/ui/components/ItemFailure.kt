package zekke.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import zekke.android.ui.R

sealed interface ItemFailure {
    data object Offline : ItemFailure

    data class RateLimited(val retryAfterSeconds: Long?) : ItemFailure

    data object SessionOver : ItemFailure

    data object SessionRenewed : ItemFailure

    data object StaleKeys : ItemFailure

    data object TooLarge : ItemFailure

    data object ScopeMissing : ItemFailure

    data object NotFound : ItemFailure

    data object Conflict : ItemFailure

    data object ChainUnverified : ItemFailure

    data object Failed : ItemFailure
}

@Composable
fun itemFailureText(failure: ItemFailure): String = when (failure) {
    ItemFailure.Offline -> stringResource(R.string.item_failure_offline)
    is ItemFailure.RateLimited -> rateLimitedMessage(failure.retryAfterSeconds)
    ItemFailure.SessionOver -> stringResource(R.string.item_failure_session_over)
    ItemFailure.SessionRenewed -> stringResource(R.string.item_failure_session_renewed)
    ItemFailure.StaleKeys -> stringResource(R.string.item_failure_stale_keys)
    ItemFailure.TooLarge -> stringResource(R.string.item_failure_too_large)
    ItemFailure.ScopeMissing -> stringResource(R.string.item_failure_scope_missing)
    ItemFailure.NotFound -> stringResource(R.string.item_failure_not_found)
    ItemFailure.Conflict -> stringResource(R.string.item_failure_conflict)
    ItemFailure.ChainUnverified -> stringResource(R.string.item_failure_chain_unverified)
    ItemFailure.Failed -> stringResource(R.string.item_failure_failed)
}

fun itemFailureTone(failure: ItemFailure): NoticeTone = when (failure) {
    ItemFailure.Offline, is ItemFailure.RateLimited, ItemFailure.SessionRenewed, ItemFailure.StaleKeys, ItemFailure.Conflict -> NoticeTone.Warning
    else -> NoticeTone.Danger
}
