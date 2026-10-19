package zekke.android.feature.sharing

import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import zekke.android.ui.components.FileKind
import zekke.android.ui.components.FileKindIcon
import zekke.android.ui.components.fileKindOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.StatusBadge
import zekke.android.ui.components.Tone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import zekke.core.folders.Folder
import zekke.core.folders.FolderEditProblem

@Composable
fun sharedTypeLabel(type: SharedType?): String = stringResource(
    when (type) {
        SharedType.Secret -> R.string.shared_type_secret
        SharedType.Note -> R.string.shared_type_note
        SharedType.Document -> R.string.shared_type_document
        SharedType.File -> R.string.shared_type_file
        null -> R.string.shared_type_unknown
    },
)

@DrawableRes
private fun iconOf(type: SharedType?): Int = when (type) {
    SharedType.Secret -> zekke.android.ui.R.drawable.ic_vault
    SharedType.Note -> zekke.android.ui.R.drawable.ic_notes
    SharedType.Document -> zekke.android.ui.R.drawable.ic_documents
    SharedType.File -> zekke.android.ui.R.drawable.ic_drive
    null -> zekke.android.ui.R.drawable.ic_info
}

@Composable
private fun titleOf(item: SharedItem): String = when {
    !item.readable -> stringResource(R.string.shared_unreadable)
    item.name != null -> item.name
    else -> sharedTypeLabel(item.type)
}

@Composable
private fun subtitleOf(item: SharedItem, counterparty: String): String = stringResource(
    if (item.inbound) R.string.shared_from else R.string.shared_to,
    sharedTypeLabel(item.type),
    counterparty,
)

@Composable
fun folderProblemText(problem: FolderEditProblem): String = stringResource(
    when (problem) {
        FolderEditProblem.TOO_DEEP -> R.string.shared_folder_problem_too_deep
        FolderEditProblem.INTO_ITSELF -> R.string.shared_folder_problem_into_itself
        FolderEditProblem.UNKNOWN_FOLDER -> R.string.shared_folder_problem_unknown
        FolderEditProblem.BAD_NAME, FolderEditProblem.FOLDER_EXISTS, FolderEditProblem.HOME_IS_FIXED -> R.string.shared_folder_problem_name
    },
)

@Composable
fun SharedScreen(state: SharedState, viewModel: SharedViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { viewModel.opened() }
    BackHandler(enabled = state.space != null) { viewModel.back() }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        val friendship = state.openFriendship
        val space = state.space
        if (friendship == null || space == null) {
            Friendships(state, viewModel)
        } else {
            Space(friendship, space, state, viewModel)
        }
    }
    val friendship = state.openFriendship
    val space = state.space
    if (friendship != null && space != null) {
        ItemSheet(friendship, space, state, viewModel)
        FolderFormSheet(space, viewModel)
        FolderMenuSheet(space, viewModel)
        MoveSheet(space, viewModel)
        space.confirmingFolderDelete?.let { folder ->
            ConfirmDeleteDialog(
                title = stringResource(R.string.shared_folder_delete_title),
                message = stringResource(R.string.shared_folder_delete_message, folder.name),
                onDismiss = viewModel::cancelDeleteFolder,
                onConfirm = viewModel::confirmDeleteFolder,
                confirmLabel = stringResource(R.string.shared_folder_delete_confirm),
                busy = space.busy,
            )
        }
    }
}

@Composable
private fun Friendships(state: SharedState, viewModel: SharedViewModel) {
    if (state.loaded && state.friendships.isEmpty()) {
        EmptyState(stringResource(R.string.shared_friendships_empty), icon = zekke.android.ui.R.drawable.ic_sharing)
        return
    }
    if (!state.loaded) LoadingIndicator()
    Column {
        state.friendships.forEach { item ->
            ItemRow(
                title = item.label,
                meta = if (item.nickname != null) item.username else null,
                metaIsValue = item.nickname != null,
                icon = zekke.android.ui.R.drawable.ic_folder,
                onClick = { viewModel.openFriendship(item) },
            )
        }
    }
}

@Composable
private fun Space(friendship: ConnectionItem, space: SpaceState, state: SharedState, viewModel: SharedViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        ZekkeButton(stringResource(R.string.shared_root), viewModel::closeFriendship, variant = ButtonVariant.Ghost)
        ZekkeButton(friendship.label, viewModel::openTop, variant = ButtonVariant.Ghost)
        space.path.forEach { folder ->
            ZekkeButton(folder.name, { viewModel.openFolder(folder.id) }, variant = ButtonVariant.Ghost)
        }
    }
    if (!space.organised) Notice(stringResource(R.string.shared_flat), tone = NoticeTone.Info)
    if (space.invalid) {
        Notice(stringResource(R.string.shared_folders_invalid), tone = NoticeTone.Warning)
        ZekkeButton(stringResource(R.string.shared_folders_reset), viewModel::resetFolders, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = !space.busy)
    }
    space.notice?.let { notice ->
        Notice(
            when (notice) {
                is SpaceNotice.Copied -> stringResource(R.string.shared_copied)
                is SpaceNotice.Saved -> stringResource(R.string.shared_saved, notice.name)
            },
            tone = NoticeTone.Success,
            onDismiss = viewModel::dismissNotice,
        )
    }
    space.problem?.let { problem ->
        val text = when (problem) {
            is SpaceProblem.Failure -> itemFailureText(problem.failure)
            is SpaceProblem.Folders -> folderProblemText(problem.problem)
            SpaceProblem.TooLarge -> stringResource(R.string.shared_copy_too_large)
            SpaceProblem.NoRoom -> stringResource(R.string.shared_copy_no_room)
        }
        val tone = (problem as? SpaceProblem.Failure)?.let { itemFailureTone(it.failure) } ?: NoticeTone.Danger
        Notice(text, tone = tone, onDismiss = viewModel::dismissProblem)
    }
    if (space.organised && !space.invalid && !space.loading) {
        ZekkeButton(stringResource(R.string.shared_new_folder), viewModel::startCreateFolder, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
    }
    if (space.loading) {
        LoadingIndicator()
        return
    }
    val folders = space.visibleFolders
    val items = space.visibleItems
    if (folders.isEmpty() && items.isEmpty()) {
        EmptyState(stringResource(R.string.shared_friendship_empty), icon = zekke.android.ui.R.drawable.ic_sharing)
        return
    }
    Column {
        folders.forEach { folder ->
            ItemRow(
                title = folder.name,
                leading = { FileKindIcon(FileKind.Folder, height = 36.dp) },
                onClick = { viewModel.openFolder(folder.id) },
                trailing = {
                    ZekkeIconButton(zekke.android.ui.R.drawable.ic_menu, stringResource(R.string.shared_folder_actions), { viewModel.openFolderMenu(folder) })
                },
            )
        }
        items.forEach { item ->
            ItemRow(
                title = titleOf(item),
                meta = if (item.readable) subtitleOf(item, friendship.label) else stringResource(R.string.shared_unreadable_hint),
                icon = iconOf(item.type),
                leading = if (item.type == SharedType.File) ({ FileKindIcon(fileKindOf(item.name), height = 44.dp) }) else null,
                onClick = if (item.readable) ({ viewModel.openItem(item) }) else null,
                trailing = if (item.inbound) null else ({ StatusBadge(stringResource(R.string.shared_sent_by_you), Tone.Neutral) }),
            )
        }
    }
}

@Composable
private fun ItemSheet(friendship: ConnectionItem, space: SpaceState, state: SharedState, viewModel: SharedViewModel) {
    val item = space.opened ?: return
    val context = LocalContext.current
    var saving by remember { mutableStateOf<SharedItem?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        viewModel.systemScreenReturned()
        val target = saving
        saving = null
        if (uri != null && target != null) {
            val resolver = context.contentResolver
            viewModel.save(
                target,
                SaveTarget(
                    open = { resolver.openOutputStream(uri, "w") ?: error("the document cannot be written") },
                    discard = { runCatching { DocumentsContract.deleteDocument(resolver, uri) } },
                ),
            )
        }
    }
    val copyable = canCopy(item, state.copyable)
    FormSheet(
        title = titleOf(item),
        submitLabel = stringResource(if (space.busy) R.string.shared_copying else R.string.shared_copy),
        onSubmit = { viewModel.copy(item) },
        onDismiss = viewModel::closeDetail,
        canSubmit = copyable,
        busy = space.busy,
    ) {
        Text(subtitleOf(item, friendship.label), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
        space.problem?.let { problem ->
            val text = when (problem) {
                is SpaceProblem.Failure -> itemFailureText(problem.failure)
                is SpaceProblem.Folders -> folderProblemText(problem.problem)
                SpaceProblem.TooLarge -> stringResource(R.string.shared_copy_too_large)
                SpaceProblem.NoRoom -> stringResource(R.string.shared_copy_no_room)
            }
            Notice(text, tone = NoticeTone.Danger, onDismiss = viewModel::dismissProblem)
        }
        when {
            item.type == SharedType.Document -> Notice(stringResource(R.string.shared_document_unreadable), tone = NoticeTone.Warning)
            item.type == SharedType.File -> Unit
            item.text != null -> {
                val secret = item.type == SharedType.Secret
                Text(
                    if (secret && !space.revealed) stringResource(R.string.shared_masked) else item.text,
                    style = if (secret) zekke.android.ui.theme.MonoStyle else MaterialTheme.typography.bodyMedium,
                    color = Zekke.palette.ink,
                )
                if (secret) {
                    ZekkeButton(
                        stringResource(if (space.revealed) R.string.shared_hide_value else R.string.shared_show_value),
                        viewModel::toggleRevealed,
                        Modifier.fillMaxWidth(),
                        ButtonVariant.Secondary,
                    )
                }
            }
        }
        if (canDownload(item)) {
            ZekkeButton(
                stringResource(R.string.shared_download),
                {
                    saving = item
                    viewModel.systemScreenWillOpen()
                    launcher.launch(item.name ?: "file")
                },
                Modifier.fillMaxWidth(),
                ButtonVariant.Secondary,
                enabled = !space.busy,
            )
            Text(stringResource(R.string.shared_download_warning), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
        }
        if (space.organised && !space.invalid) {
            ZekkeButton(stringResource(R.string.shared_move_action), { viewModel.startMove(item) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = !space.busy)
        }
        if (item.inbound) {
            Text(stringResource(R.string.shared_copy_explain), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
        }
    }
}

@Composable
private fun FolderFormSheet(space: SpaceState, viewModel: SharedViewModel) {
    val form = space.form ?: return
    val creating = form is FolderForm.Create
    FormSheet(
        title = stringResource(if (creating) R.string.shared_folder_new_title else R.string.shared_folder_rename_title),
        submitLabel = stringResource(
            when {
                space.busy -> R.string.shared_folder_saving
                creating -> R.string.shared_folder_create
                else -> R.string.shared_folder_rename
            },
        ),
        onSubmit = viewModel::submitForm,
        onDismiss = viewModel::cancelForm,
        canSubmit = form.draft.trim().isNotEmpty(),
        busy = space.busy,
    ) {
        space.problem?.let { problem ->
            if (problem is SpaceProblem.Folders) Notice(folderProblemText(problem.problem), tone = NoticeTone.Danger, onDismiss = viewModel::dismissProblem)
            if (problem is SpaceProblem.Failure) Notice(itemFailureText(problem.failure), tone = itemFailureTone(problem.failure), onDismiss = viewModel::dismissProblem)
        }
        PrivateField(
            value = form.draft,
            onValueChange = viewModel::draftChanged,
            label = stringResource(R.string.shared_folder_name),
            singleLine = true,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            onImeAction = viewModel::submitForm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FolderMenuSheet(space: SpaceState, viewModel: SharedViewModel) {
    val folder: Folder = space.menuFolder ?: return
    FormSheet(
        title = folder.name,
        submitLabel = stringResource(R.string.shared_folder_rename),
        onSubmit = { viewModel.startRenameFolder(folder) },
        onDismiss = viewModel::closeFolderMenu,
    ) {
        ZekkeButton(stringResource(R.string.shared_folder_delete), { viewModel.askDeleteFolder(folder) }, Modifier.fillMaxWidth(), ButtonVariant.Danger)
    }
}

@Composable
private fun MoveSheet(space: SpaceState, viewModel: SharedViewModel) {
    val item = space.moving ?: return
    val manifest = space.manifest ?: return
    var chosen by remember(item) { mutableStateOf<String?>(null) }
    var picked by remember(item) { mutableStateOf(false) }
    FormSheet(
        title = stringResource(R.string.shared_move_title),
        submitLabel = stringResource(R.string.shared_move_submit),
        onSubmit = { viewModel.moveTo(chosen) },
        onDismiss = viewModel::cancelMove,
        canSubmit = picked,
        busy = space.busy,
    ) {
        space.problem?.let { problem ->
            if (problem is SpaceProblem.Folders) Notice(folderProblemText(problem.problem), tone = NoticeTone.Danger, onDismiss = viewModel::dismissProblem)
            if (problem is SpaceProblem.Failure) Notice(itemFailureText(problem.failure), tone = itemFailureTone(problem.failure), onDismiss = viewModel::dismissProblem)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val choices = listOf<FolderChoice?>(null) + folderChoices(manifest)
            choices.forEach { choice ->
                val id = choice?.id
                val selected = picked && chosen == id
                ZekkeButton(
                    text = (if (selected) "✓ " else "") + (choice?.name ?: stringResource(R.string.shared_move_top)),
                    onClick = {
                        chosen = id
                        picked = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = ((choice?.depth ?: 0) * 16).dp),
                    variant = if (selected) ButtonVariant.Primary else ButtonVariant.Secondary,
                )
            }
        }
    }
}
