package zekke.android.feature.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton

@Composable
fun UploadRows(state: DriveState, viewModel: DriveViewModel, onChooseAgain: (UploadItem) -> Unit) {
    if (state.uploadsHere.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        state.uploadsHere.forEach { item ->
            Column {
                ItemRow(
                    title = item.name,
                    meta = uploadLabel(item),
                    icon = zekke.android.ui.R.drawable.ic_upload,
                    divider = false,
                    trailing = {
                        ZekkeIconButton(
                            zekke.android.ui.R.drawable.ic_close,
                            hint = stringResource(R.string.upload_discard),
                            label = stringResource(R.string.upload_discard) + " · " + item.name,
                            onClick = { viewModel.askCancelUpload(item) },
                        )
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (item.status) {
                        UploadStatus.NeedsFile -> ZekkeButton(stringResource(R.string.upload_choose), { onChooseAgain(item) }, variant = ButtonVariant.Secondary)
                        UploadStatus.QuotaFull, UploadStatus.Failed -> ZekkeButton(stringResource(R.string.upload_retry), { viewModel.retryUpload(item.id) }, variant = ButtonVariant.Secondary)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun uploadLabel(item: UploadItem): String = when (item.status) {
    UploadStatus.Waiting -> stringResource(R.string.upload_waiting)
    UploadStatus.Uploading -> stringResource(R.string.upload_uploading, item.percent)
    UploadStatus.Finishing -> stringResource(R.string.upload_finishing)
    UploadStatus.Offline -> stringResource(R.string.upload_offline)
    UploadStatus.RateLimited -> stringResource(R.string.upload_paused)
    UploadStatus.Locked -> stringResource(R.string.upload_locked)
    UploadStatus.NeedsFile -> stringResource(R.string.upload_needs_file)
    UploadStatus.QuotaFull -> stringResource(R.string.upload_quota_full)
    UploadStatus.Failed -> stringResource(R.string.upload_failed)
}

@Composable
fun UploadDialogs(state: DriveState, viewModel: DriveViewModel) {
    val item = state.cancelling ?: return
    ConfirmDeleteDialog(
        title = stringResource(R.string.upload_discard_title),
        message = stringResource(R.string.upload_discard_message, item.name),
        onDismiss = viewModel::keepUpload,
        onConfirm = viewModel::confirmCancelUpload,
        confirmLabel = stringResource(R.string.upload_discard),
    )
}
