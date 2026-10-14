package zekke.android.session

import androidx.compose.runtime.Composable
import zekke.android.feature.unlock.ClientVersionStatus
import zekke.android.feature.unlock.UpgradeRequiredScreen

@Composable
fun ClientGate(status: ClientVersionStatus, keepContent: Boolean, content: @Composable () -> Unit) {
    if (status is ClientVersionStatus.UpgradeRequired && !keepContent) {
        UpgradeRequiredScreen(status.latest)
    } else {
        content()
    }
}
