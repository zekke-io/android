package zekke.android.feature.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.ItemFailure
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.TabItem
import zekke.android.ui.components.TabStrip
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import zekke.core.folders.FolderManifestProblem
import zekke.core.folders.FolderTab
import zekke.core.folders.HOME_FOLDER_NAME

class TabNouns(val one: String, val many: String) {
    fun countOf(count: Int): String = "$count ${if (count == 1) one else many}"
}

@Composable
fun TabsBar(
    controller: FolderTabsController,
    itemIds: List<String>,
    nouns: TabNouns,
    deleteItems: suspend (List<String>) -> ItemFailure?,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.invalid?.let { problem ->
            Notice(stringResource(R.string.tabs_invalid, invalidReason(problem)), tone = NoticeTone.Warning)
            ZekkeButton(stringResource(R.string.tabs_reset), controller::reset, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = !state.busy)
        }
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = controller::dismissFailure) }
        if (state.manifest != null) {
            val tabs = controller.tabs(itemIds)
            val active = controller.active(itemIds)
            val homeLabel = stringResource(R.string.tabs_home)
            TabStrip(
                tabs = tabs.map { TabItem(it.id, tabLabel(it, homeLabel), it.count) },
                selected = active,
                onSelect = controller::select,
                onAdd = controller::startNew,
                onManage = controller::manage,
                enabled = !state.busy,
            )
            TabsDialogs(controller, tabs, itemIds, nouns, deleteItems, homeLabel)
        }
    }
}

private fun tabLabel(tab: FolderTab, homeLabel: String): String = if (tab.home && tab.name == HOME_FOLDER_NAME) homeLabel else tab.name

@Composable
private fun TabsDialogs(
    controller: FolderTabsController,
    tabs: List<FolderTab>,
    itemIds: List<String>,
    nouns: TabNouns,
    deleteItems: suspend (List<String>) -> ItemFailure?,
    homeLabel: String,
) {
    val state by controller.state.collectAsState()
    state.managing?.let { id ->
        val tab = tabs.firstOrNull { it.id == id }
        if (tab != null) {
            FormSheet(
                title = tabLabel(tab, homeLabel),
                submitLabel = stringResource(R.string.tabs_rename),
                onSubmit = { controller.startRename(tab) },
                onDismiss = controller::stopManaging,
            ) {
                if (!tab.home) {
                    ZekkeButton(stringResource(R.string.tabs_delete), { controller.startDelete(tab) }, Modifier.fillMaxWidth(), ButtonVariant.Danger)
                }
            }
        }
    }
    state.naming?.let { naming ->
        val problem = controller.nameProblem(tabs)
        FormSheet(
            title = stringResource(if (naming.renaming == null) R.string.tabs_new_title else R.string.tabs_rename_title),
            submitLabel = stringResource(if (state.busy) R.string.tabs_working else if (naming.renaming == null) R.string.tabs_create else R.string.tabs_rename),
            onSubmit = { controller.commitName(tabs) },
            onDismiss = controller::cancelNaming,
            canSubmit = problem == null,
            busy = state.busy,
        ) {
            state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = controller::dismissFailure) }
            PrivateField(
                value = naming.name,
                onValueChange = controller::nameChanged,
                label = stringResource(R.string.tabs_field_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (problem != null && naming.name.isNotEmpty()) {
                Text(nameProblemText(problem), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.danger.text)
            }
        }
    }
    state.deleting?.let { id ->
        val tab = tabs.firstOrNull { it.id == id }
        if (tab != null) {
            val refusal = controller.deleteRefusal(tab)
            ConfirmDeleteDialog(
                title = stringResource(R.string.tabs_delete_title, tabLabel(tab, homeLabel)),
                message = refusal?.let { refusalText(it, nouns) } ?: deleteMessage(tab, nouns),
                onDismiss = controller::cancelDelete,
                onConfirm = if (refusal == null) ({ controller.confirmDelete(tabs, itemIds, deleteItems) }) else null,
                confirmLabel = if (tab.count > 0) stringResource(R.string.tabs_delete_with, nouns.countOf(tab.count)) else stringResource(R.string.tabs_delete),
                busy = state.busy,
            )
        }
    }
}

@Composable
private fun nameProblemText(problem: TabNameProblem): String = when (problem) {
    TabNameProblem.Empty -> stringResource(R.string.tabs_name_empty)
    is TabNameProblem.TooLong -> pluralStringResource(R.plurals.tabs_name_long, problem.limit, problem.limit)
    is TabNameProblem.Clash -> stringResource(R.string.tabs_name_clash, problem.existing)
}

@Composable
private fun refusalText(refusal: TabDeleteRefusal, nouns: TabNouns): String = when (refusal) {
    TabDeleteRefusal.Home -> stringResource(R.string.tabs_refusal_home)
    is TabDeleteRefusal.NeedsFullDevice -> stringResource(R.string.tabs_refusal_full_device, nouns.many, refusal.name)
}

@Composable
private fun deleteMessage(tab: FolderTab, nouns: TabNouns): String =
    if (tab.count == 0) stringResource(R.string.tabs_delete_empty, tab.name) else stringResource(R.string.tabs_delete_full, tab.name, nouns.countOf(tab.count))

@Composable
private fun invalidReason(problem: FolderManifestProblem): String = stringResource(
    when (problem) {
        FolderManifestProblem.CYCLE -> R.string.tabs_invalid_cycle
        FolderManifestProblem.TOO_DEEP -> R.string.tabs_invalid_too_deep
        FolderManifestProblem.UNKNOWN_PARENT -> R.string.tabs_invalid_unknown_parent
        FolderManifestProblem.DELETED_PARENT -> R.string.tabs_invalid_deleted_parent
        FolderManifestProblem.MISSING_HOME -> R.string.tabs_invalid_missing_home
        FolderManifestProblem.UNKNOWN_FOLDER -> R.string.tabs_invalid_unknown_folder
        FolderManifestProblem.MALFORMED -> R.string.tabs_invalid_malformed
        FolderManifestProblem.BAD_NAME -> R.string.tabs_invalid_bad_name
        FolderManifestProblem.UNKNOWN_VERSION -> R.string.tabs_invalid_unknown_version
    },
)
