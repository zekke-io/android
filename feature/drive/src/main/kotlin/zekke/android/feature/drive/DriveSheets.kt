package zekke.android.feature.drive

import android.content.ClipData
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.Notice
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke

@Composable
fun DriveSheets(state: DriveState, viewModel: DriveViewModel) {
    ActionSheet(state, viewModel)
    ShareEffect(state, viewModel)
    NamingSheet(state, viewModel)
    MoveSheet(state, viewModel)
    DeleteDialog(state, viewModel)
}

@Composable
private fun ActionSheet(state: DriveState, viewModel: DriveViewModel) {
    val item = state.acting ?: return
    val context = LocalContext.current
    var saving by remember { mutableStateOf<DriveEntry?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        viewModel.systemScreenReturned()
        val entry = saving
        saving = null
        if (uri != null && entry != null) {
            val resolver = context.contentResolver
            viewModel.saveTo(
                entry,
                ExportTarget(
                    open = { resolver.openOutputStream(uri, "w") ?: error("the document cannot be written") },
                    discard = { runCatching { DocumentsContract.deleteDocument(resolver, uri) } },
                ),
            )
        } else {
            viewModel.stopActing()
        }
    }
    val title = when (item) {
        is DriveItem.OfFile -> item.file.name ?: stringResource(R.string.drive_unreadable_file)
        is DriveItem.OfFolder -> item.folder.name ?: stringResource(R.string.drive_unreadable_folder)
    }
    val openable = item is DriveItem.OfFile && item.file.readable && item.file.r2State == "ok"
    val previewable = item is DriveItem.OfFile && openable && previewKindOf(item.file.mime) != PreviewKind.None
    FormSheet(
        title = title,
        submitLabel = stringResource(
            when {
                item is DriveItem.OfFolder -> R.string.drive_action_open
                previewable -> R.string.drive_action_preview
                else -> R.string.drive_action_open_with
            },
        ),
        onSubmit = {
            when (item) {
                is DriveItem.OfFolder -> viewModel.openFolder(item.folder.id)
                is DriveItem.OfFile -> if (previewable) viewModel.preview(item.file) else viewModel.shareFile(item.file)
            }
        },
        onDismiss = viewModel::stopActing,
        canSubmit = item is DriveItem.OfFolder || openable,
    ) {
        if (item is DriveItem.OfFile && openable) {
            ZekkeButton(
                stringResource(R.string.drive_action_save),
                {
                    saving = item.file
                    viewModel.systemScreenWillOpen()
                    launcher.launch(item.file.name ?: "file")
                },
                Modifier.fillMaxWidth(),
                ButtonVariant.Secondary,
            )
            if (previewable) {
                ZekkeButton(stringResource(R.string.drive_action_open_with), { viewModel.shareFile(item.file) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
            }
            Text(stringResource(R.string.drive_export_warning), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
        }
        ZekkeButton(stringResource(R.string.drive_action_rename), { viewModel.startRename(item) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = item !is DriveItem.OfFile || item.file.readable)
        ZekkeButton(stringResource(R.string.drive_action_move), { viewModel.askMove(item) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        if (viewModel.canDeleteItem(item) || (state.canDelete && state.retentionDays == null)) {
            ZekkeButton(
                stringResource(if (item is DriveItem.OfFile && item.file.isUnfinished) R.string.drive_action_discard else R.string.drive_action_delete),
                { viewModel.askDelete(item) },
                Modifier.fillMaxWidth(),
                ButtonVariant.Danger,
                enabled = viewModel.canDeleteItem(item),
            )
            if (state.canDelete && state.retentionDays == null && !(item is DriveItem.OfFile && item.file.isUnfinished)) {
                Text(stringResource(R.string.drive_retention_unknown), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
            }
        }
    }
}

@Composable
fun ShareEffect(state: DriveState, viewModel: DriveViewModel) {
    val ready = state.share ?: return
    val context = LocalContext.current
    LaunchedEffect(ready) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = ready.uri.toUri()
            setDataAndType(uri, ready.mime)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            clipData = intent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.systemScreenWillOpen()
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            viewModel.systemScreenReturned()
        }
        viewModel.shareHandled()
    }
}

@Composable
private fun NamingSheet(state: DriveState, viewModel: DriveViewModel) {
    val naming = state.naming ?: return
    val problem = viewModel.namingProblem()
    val isFolder = naming !is DriveNaming.RenameFile
    FormSheet(
        title = stringResource(
            when (naming) {
                is DriveNaming.NewFolder -> R.string.drive_name_new_folder
                is DriveNaming.RenameFolder -> R.string.drive_name_rename_folder
                is DriveNaming.RenameFile -> R.string.drive_name_rename_file
            },
        ),
        submitLabel = stringResource(
            when {
                state.busy -> R.string.drive_name_working
                naming is DriveNaming.NewFolder -> R.string.drive_name_create
                else -> R.string.drive_name_save
            },
        ),
        onSubmit = viewModel::commitNaming,
        onDismiss = viewModel::cancelNaming,
        canSubmit = problem == null,
        busy = state.busy,
    ) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        PrivateField(
            value = naming.name,
            onValueChange = viewModel::nameChanged,
            label = stringResource(if (isFolder) R.string.drive_name_field_folder else R.string.drive_name_field_file),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (problem != null && naming.name.isNotEmpty()) {
            Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.danger.text)
        }
        if (naming is DriveNaming.RenameFile) {
            extensionChanged(naming.original, naming.name)?.let {
                Text(stringResource(R.string.drive_extension_note, it), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
            }
        }
    }
}

@Composable
private fun problemText(problem: NamingProblem): String = when (problem) {
    is NamingProblem.File -> stringResource(
        when (problem.problem) {
            FileNameProblem.Empty -> R.string.drive_name_empty_file
            FileNameProblem.TooLong -> R.string.drive_name_too_long_file
            FileNameProblem.Separator -> R.string.drive_name_separator
            FileNameProblem.Control -> R.string.drive_name_control
        },
    )
    NamingProblem.FolderEmpty -> stringResource(R.string.drive_name_empty_folder)
    is NamingProblem.FolderTooLong -> pluralStringResource(R.plurals.drive_name_too_long_folder, problem.limit, problem.limit)
    is NamingProblem.FolderClash -> stringResource(R.string.drive_name_clash, problem.existing)
}

@Composable
private fun MoveSheet(state: DriveState, viewModel: DriveViewModel) {
    if (state.moving == null) return
    var chosen by remember(state.moving) { mutableStateOf<String?>(null) }
    var picked by remember(state.moving) { mutableStateOf(false) }
    val destinations = viewModel.destinations()
    FormSheet(
        title = stringResource(R.string.drive_move_title),
        submitLabel = stringResource(R.string.drive_move_submit),
        onSubmit = { viewModel.moveTo(chosen) },
        onDismiss = viewModel::cancelMove,
        canSubmit = picked && viewModel.canMoveTo(chosen),
        busy = state.busy,
    ) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            destinations.forEach { (folder, depth) ->
                val id = folder?.id
                val selected = picked && chosen == id
                ZekkeButton(
                    text = (if (selected) "✓ " else "") + (folder?.name ?: stringResource(R.string.drive_move_top)),
                    onClick = {
                        chosen = id
                        picked = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp),
                    variant = if (selected) ButtonVariant.Primary else ButtonVariant.Secondary,
                    enabled = viewModel.canMoveTo(id),
                )
            }
        }
    }
}

@Composable
private fun DeleteDialog(state: DriveState, viewModel: DriveViewModel) {
    val item = state.deleting ?: return
    val days = state.retentionDays ?: 0
    val unfinished = item is DriveItem.OfFile && item.file.isUnfinished
    val message = when (item) {
        is DriveItem.OfFile -> if (unfinished) {
            stringResource(R.string.drive_discard_message, item.file.name?.let { shortFileName(it) } ?: stringResource(R.string.drive_unreadable_file))
        } else {
            val name = item.file.name?.let { shortFileName(it) } ?: stringResource(R.string.drive_unreadable_file)
            if (days > 0) stringResource(R.string.drive_delete_file_trash, name, pluralStringResource(R.plurals.drive_days, days, days))
            else stringResource(R.string.drive_delete_file_permanent, name)
        }
        is DriveItem.OfFolder -> {
            val name = item.folder.name ?: stringResource(R.string.drive_unreadable_folder)
            val inside = stringResource(if (state.foldersInside(item.folder.id) == 0) R.string.drive_inside_files else R.string.drive_inside_folders)
            if (days > 0) stringResource(R.string.drive_delete_folder_trash, name, inside, pluralStringResource(R.plurals.drive_days, days, days))
            else stringResource(R.string.drive_delete_folder_permanent, name, inside)
        }
    }
    ConfirmDeleteDialog(
        title = stringResource(
            when {
                unfinished -> R.string.drive_discard_title
                item is DriveItem.OfFile -> R.string.drive_delete_file_title
                else -> R.string.drive_delete_folder_title
            },
        ),
        message = message,
        onDismiss = viewModel::cancelDelete,
        onConfirm = viewModel::confirmDelete,
        confirmLabel = stringResource(
            when {
                unfinished -> R.string.drive_action_discard
                days > 0 -> R.string.drive_delete_confirm_trash
                else -> R.string.drive_delete_confirm_permanent
            },
        ),
        busy = state.busy,
    )
}
