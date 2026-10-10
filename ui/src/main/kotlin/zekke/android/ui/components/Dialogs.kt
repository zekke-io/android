package zekke.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?,
    confirmLabel: String = stringResource(R.string.dialog_delete),
    busy: Boolean = false,
) {
    val palette = Zekke.palette
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        shape = MaterialTheme.shapes.large,
        containerColor = palette.surface,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = palette.ink) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft) },
        dismissButton = {
            ZekkeButton(stringResource(R.string.dialog_keep), onClick = onDismiss, variant = ButtonVariant.Secondary, enabled = !busy)
        },
        confirmButton = {
            if (onConfirm != null) {
                ZekkeButton(confirmLabel, onClick = onConfirm, variant = ButtonVariant.Danger, enabled = !busy)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    submitLabel: String,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    canSubmit: Boolean = true,
    busy: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = Zekke.palette
    val currentBusy = rememberUpdatedState(busy)
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy.value },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = state,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = palette.surface,
        scrimColor = palette.scrim,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = palette.ink,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
            )
            HorizontalDivider(color = palette.line)
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                content()
            }
            HorizontalDivider(color = palette.line)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                ZekkeButton(stringResource(R.string.dialog_cancel), onClick = onDismiss, variant = ButtonVariant.Secondary, enabled = !busy)
                ZekkeButton(submitLabel, onClick = onSubmit, enabled = canSubmit && !busy)
            }
        }
    }
}
