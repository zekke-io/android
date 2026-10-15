package zekke.android.feature.drive

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.AddOption
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.FloatingAddMenu
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FileTile
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.StorageMeter
import zekke.android.ui.components.StorageReading
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.components.ZekkeIconToggleButton
import zekke.android.ui.components.formatBytes
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import zekke.core.files.R2States
import zekke.core.folders.TreeFolder

@Composable
fun DriveScreen(state: DriveState, viewModel: DriveViewModel) {
    val viewing = state.viewing
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.systemScreenReturned()
        viewModel.filesPicked(uris.map { it.toString() })
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.systemScreenReturned()
        viewModel.filesPicked(uris.map { it.toString() })
    }
    val again = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.systemScreenReturned()
        if (uri != null) viewModel.chosenAgain(uri.toString()) else viewModel.cancelChoosing()
    }
    val chooseAgain: (UploadItem) -> Unit = { item ->
        viewModel.askChooseAgain(item)
        viewModel.systemScreenWillOpen()
        again.launch(arrayOf("*/*"))
    }
    Box(Modifier.fillMaxSize()) {
        if (viewing != null) {
            ViewerPage(viewing, viewModel)
        } else if (state.showingTrash) {
            TrashPage(state, viewModel)
        } else {
            DriveBrowser(state, viewModel, chooseAgain)
            FloatingAddMenu(
                label = stringResource(R.string.drive_add),
                options = listOf(
                    AddOption(stringResource(R.string.drive_add_photos), zekke.android.ui.R.drawable.ic_upload) {
                        viewModel.systemScreenWillOpen()
                        photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    AddOption(stringResource(R.string.drive_add_files), zekke.android.ui.R.drawable.ic_documents) {
                        viewModel.systemScreenWillOpen()
                        documents.launch(arrayOf("*/*"))
                    },
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }
    }
    DriveSheets(state, viewModel)
    UploadDialogs(state, viewModel)
}

@Composable
private fun Header(state: DriveState, viewModel: DriveViewModel) {
    val palette = Zekke.palette
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        state.saved?.let { Notice(stringResource(R.string.drive_saved, it), tone = NoticeTone.Warning, onDismiss = viewModel::dismissSaved) }
        state.usage?.let { usage ->
            StorageMeter(StorageReading(usage.storedBytes, usage.usedBytes, usage.quotaBytes))
            val held = usage.usedBytes - usage.storedBytes
            if (held > 0) {
                Text(stringResource(R.string.drive_held_by_uploads, formatBytes(held)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.folderId != null) {
                ZekkeIconButton(zekke.android.ui.R.drawable.ic_arrow_left, stringResource(R.string.drive_up), viewModel::up)
            }
            ZekkeButton(stringResource(R.string.drive_root), { viewModel.openFolder(null) }, variant = ButtonVariant.Ghost)
            state.path.forEach { folder ->
                Text("›", color = palette.inkMuted)
                ZekkeButton(folder.name ?: stringResource(R.string.drive_unreadable_folder), { viewModel.openFolder(folder.id) }, variant = ButtonVariant.Ghost)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ZekkeButton(stringResource(R.string.drive_new_folder), viewModel::startNewFolder, Modifier.weight(1f), ButtonVariant.Secondary, enabled = state.canCreateFolder && !state.busy)
            ZekkeButton(stringResource(R.string.drive_trash), viewModel::showTrash, Modifier.weight(1f), ButtonVariant.Secondary)
            ZekkeIconToggleButton(
                icon = if (state.grid) zekke.android.ui.R.drawable.ic_list else zekke.android.ui.R.drawable.ic_grid,
                hint = stringResource(if (state.grid) R.string.drive_list else R.string.drive_grid),
                checked = !state.grid,
                onCheckedChange = { viewModel.toggleGrid() },
            )
        }
    }
}

@Composable
private fun DriveBrowser(state: DriveState, viewModel: DriveViewModel, chooseAgain: (UploadItem) -> Unit) {
    val folders = state.subfolders
    val files = state.filesHere
    val empty = folders.isEmpty() && files.isEmpty()
    if (state.grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header(state, viewModel) }
            if (!state.loaded || (!state.synced && empty)) {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingIndicator() }
            } else {
                if (state.uploadsHere.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { UploadRows(state, viewModel, chooseAgain) }
                if (empty && state.uploadsHere.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.drive_empty), icon = zekke.android.ui.R.drawable.ic_drive) }
                }
                items(folders, key = { "folder-" + it.id }) { folder -> FolderTile(folder, state, viewModel) }
                items(files, key = { "file-" + it.id }) { file -> FileTileOf(file, state, viewModel) }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Header(state, viewModel) }
            if (!state.loaded || (!state.synced && empty)) {
                item { LoadingIndicator() }
            } else {
                if (state.uploadsHere.isNotEmpty()) item { UploadRows(state, viewModel, chooseAgain) }
                if (empty && state.uploadsHere.isEmpty()) {
                    item { EmptyState(stringResource(R.string.drive_empty), icon = zekke.android.ui.R.drawable.ic_drive) }
                }
                items(folders, key = { "folder-" + it.id }) { folder -> FolderRow(folder, state, viewModel) }
                items(files, key = { "file-" + it.id }) { file -> FileRow(file, state, viewModel) }
            }
        }
    }
}

@Composable
private fun folderCaption(state: DriveState, folder: TreeFolder): String {
    val files = state.itemsInside(folder.id)
    val folders = state.foldersInside(folder.id)
    val first = pluralStringResource(R.plurals.drive_files, files, files)
    return if (folders == 0) first else stringResource(R.string.drive_contents, first, pluralStringResource(R.plurals.drive_folders, folders, folders))
}

@Composable
private fun fileCaption(file: DriveEntry): String = when {
    file.r2State == R2States.MISSING -> stringResource(R.string.drive_missing)
    file.r2State != R2States.OK -> stringResource(R.string.drive_unfinished)
    file.gcsState == "failed" -> stringResource(R.string.drive_second_copy_failed)
    else -> formatBytes(file.sizeBytes)
}

@Composable
private fun fileTitle(file: DriveEntry): String = file.name?.let { shortFileName(it) } ?: stringResource(R.string.drive_unreadable_file)

@Composable
private fun folderTitle(folder: TreeFolder): String = folder.name ?: stringResource(R.string.drive_unreadable_folder)

@Composable
private fun thumbnailOf(file: DriveEntry, state: DriveState, viewModel: DriveViewModel): ImageBitmap? {
    val thumbnailId = file.thumbnailId ?: return null
    LaunchedEffect(thumbnailId) { viewModel.requestThumbnail(file) }
    val bytes = state.thumbnails[thumbnailId] ?: return null
    return remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
}

@Composable
private fun FolderTile(folder: TreeFolder, state: DriveState, viewModel: DriveViewModel) {
    val title = folderTitle(folder)
    FileTile(
        title = title,
        caption = folderCaption(state, folder),
        icon = zekke.android.ui.R.drawable.ic_folder,
        onClick = { viewModel.openFolder(folder.id) },
        more = { MoreButton(title) { viewModel.act(DriveItem.OfFolder(folder)) } },
    )
}

@Composable
private fun FileTileOf(file: DriveEntry, state: DriveState, viewModel: DriveViewModel) {
    val title = fileTitle(file)
    FileTile(
        title = title,
        caption = fileCaption(file),
        icon = zekke.android.ui.R.drawable.ic_drive,
        image = thumbnailOf(file, state, viewModel),
        onClick = { viewModel.act(DriveItem.OfFile(file)) },
        more = { MoreButton(title) { viewModel.act(DriveItem.OfFile(file)) } },
    )
}

@Composable
private fun FolderRow(folder: TreeFolder, state: DriveState, viewModel: DriveViewModel) {
    val title = folderTitle(folder)
    ItemRow(
        title = title,
        meta = folderCaption(state, folder),
        icon = zekke.android.ui.R.drawable.ic_folder,
        onClick = { viewModel.openFolder(folder.id) },
        trailing = { MoreButton(title) { viewModel.act(DriveItem.OfFolder(folder)) } },
    )
}

@Composable
private fun FileRow(file: DriveEntry, state: DriveState, viewModel: DriveViewModel) {
    val title = fileTitle(file)
    ItemRow(
        title = title,
        meta = fileCaption(file),
        icon = zekke.android.ui.R.drawable.ic_drive,
        onClick = { viewModel.act(DriveItem.OfFile(file)) },
        trailing = { MoreButton(title) { viewModel.act(DriveItem.OfFile(file)) } },
    )
}

@Composable
private fun MoreButton(title: String, onClick: () -> Unit) {
    ZekkeIconButton(
        zekke.android.ui.R.drawable.ic_menu,
        hint = stringResource(R.string.drive_more),
        label = stringResource(R.string.drive_more_for, title),
        onClick = onClick,
    )
}
