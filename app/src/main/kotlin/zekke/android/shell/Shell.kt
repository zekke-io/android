package zekke.android.shell

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import zekke.android.ui.components.TopBarActionsSlot
import zekke.android.ui.components.LocalTopBarActions
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zekke.android.R
import zekke.android.sync.SyncStatus
import zekke.android.ui.components.AppTile
import zekke.android.ui.components.AppTileStyle
import zekke.android.ui.components.Mascot
import zekke.android.ui.components.MascotPose
import zekke.android.feature.settings.notificationBadge
import zekke.android.ui.components.BottomBar
import zekke.android.ui.components.BottomBarItem
import zekke.android.ui.components.IconButtonTone
import zekke.android.ui.components.TopBar
import zekke.android.ui.components.StorageWarning
import zekke.android.ui.components.StorageReading
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.theme.Zekke
import zekke.core.feed.FeedScope

enum class Destination { Home, Passwords, Vault, Notes, Drive, Shared, Settings, SettingsDevices, SettingsAccount, SettingsSecurity, SettingsPlan, SettingsAbout, Activity }

private val SETTINGS_PAGES = mapOf(
    Destination.SettingsDevices to R.string.settings_devices,
    Destination.SettingsAccount to R.string.settings_account,
    Destination.SettingsSecurity to R.string.settings_security,
    Destination.SettingsPlan to R.string.settings_plan,
    Destination.SettingsAbout to R.string.settings_about,
)

private class Section(
    val destination: Destination,
    val scopes: Set<FeedScope>,
    @DrawableRes val icon: Int,
    val style: AppTileStyle,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @PluralsRes val count: Int?,
)

private val SECTIONS = listOf(
    Section(Destination.Passwords, setOf(FeedScope.PASSWORDS), zekke.android.ui.R.drawable.ic_app_passwords, AppTileStyle.Brand, R.string.section_passwords, R.string.section_passwords_description, R.plurals.count_passwords),
    Section(Destination.Vault, setOf(FeedScope.SECRETS), zekke.android.ui.R.drawable.ic_app_secrets, AppTileStyle.Ink, R.string.section_vault, R.string.section_vault_description, R.plurals.count_vault),
    Section(Destination.Notes, setOf(FeedScope.NOTES), zekke.android.ui.R.drawable.ic_app_notes, AppTileStyle.Peach, R.string.section_notes, R.string.section_notes_description, R.plurals.count_notes),
    Section(Destination.Drive, setOf(FeedScope.FILES), zekke.android.ui.R.drawable.ic_app_drive, AppTileStyle.Brand, R.string.section_drive, R.string.section_drive_description, null),
    Section(Destination.Shared, setOf(FeedScope.SECRETS, FeedScope.NOTES, FeedScope.FILES), zekke.android.ui.R.drawable.ic_sharing, AppTileStyle.Amber, R.string.section_shared, R.string.section_shared_description, null),
)

@Composable
fun Shell(
    status: SyncStatus,
    storage: StorageReading? = null,
    onLock: () -> Unit,
    notices: @Composable () -> Unit,
    settings: @Composable (openNotifications: () -> Unit, open: (Destination) -> Unit) -> Unit,
    settingsPages: Map<Destination, @Composable () -> Unit> = emptyMap(),
    activity: @Composable (open: (Destination) -> Unit) -> Unit,
    listPages: Map<Destination, @Composable () -> Unit> = emptyMap(),
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    val openNotifications = { destination = Destination.Activity }
    BackHandler(enabled = destination != Destination.Home) {
        destination = when {
            destination in SETTINGS_PAGES -> Destination.Settings
            else -> Destination.Home
        }
    }
    val palette = Zekke.palette
    Column(Modifier.fillMaxSize().background(palette.ground).safeDrawingPadding()) {
        when (destination) {
            Destination.Home -> HomeScreen(
                status = status,
                storage = storage,
                onOpen = { destination = it },
                onOpenNotifications = openNotifications,
                onLock = onLock,
                notices = notices,
            )
            Destination.Settings -> MainTabPage(
                title = stringResource(R.string.shell_settings),
                selected = Destination.Settings,
                status = status,
                onOpen = { destination = it },
                onOpenNotifications = openNotifications,
            ) { settings(openNotifications) { destination = it } }
            Destination.SettingsDevices, Destination.SettingsAccount, Destination.SettingsSecurity, Destination.SettingsPlan, Destination.SettingsAbout -> ShellPage(
                title = stringResource(SETTINGS_PAGES.getValue(destination)),
                onBack = { destination = Destination.Settings },
                onLock = onLock,
            ) { settingsPages[destination]?.invoke() }
            Destination.Activity -> MainTabPage(
                title = stringResource(R.string.shell_activity),
                selected = Destination.Activity,
                status = status,
                onOpen = { destination = it },
                onOpenNotifications = openNotifications,
            ) { activity { destination = it } }
            else -> {
                val section = SECTIONS.first { it.destination == destination }
                val listPage = listPages[destination]
                if (listPage != null) {
                    ShellPage(
                        title = stringResource(section.title),
                        onBack = { destination = Destination.Home },
                        onLock = onLock,
                        scrollable = false,
                        header = { if (status.failure != null || status.outboxPending > 0 || status.outboxWaiting > 0) SyncStatusView(status) },
                    ) { listPage() }
                } else {
                    ShellPage(stringResource(section.title), { destination = Destination.Home }, onLock) {
                        SectionBody(section, status)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    status: SyncStatus,
    storage: StorageReading?,
    onOpen: (Destination) -> Unit,
    onOpenNotifications: () -> Unit,
    onLock: () -> Unit,
    notices: @Composable () -> Unit,
) {
    val palette = Zekke.palette
    val sections = SECTIONS.filter { section -> section.scopes.any { it in status.heldScopes } }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mascot(MascotPose.OpenAppIcon, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_greeting),
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                ZekkeIconButton(
                    zekke.android.ui.R.drawable.ic_lock_session,
                    hint = stringResource(R.string.shell_lock_hint),
                    label = stringResource(R.string.home_lock),
                    tone = IconButtonTone.Ink,
                    onClick = onLock,
                )
            }
            notices()
            if (storage != null && storage.isAlmostFull) StorageWarning(storage)
            Text(
                stringResource(R.string.home_apps),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.ink,
                modifier = Modifier.semantics { heading() },
            )
            sections.chunked(3).forEach { rowSections ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSections.forEach { section ->
                        AppTile(
                            icon = section.icon,
                            style = section.style,
                            label = stringResource(section.title),
                            description = stringResource(section.description),
                            onClick = { onOpen(section.destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowSections.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        MainBottomBar(Destination.Home, status, onOpen, onOpenNotifications)
    }
}

@Composable
private fun MainBottomBar(
    selected: Destination,
    status: SyncStatus,
    onOpen: (Destination) -> Unit,
    onOpenNotifications: () -> Unit,
) {
    BottomBar(
        listOf(
            BottomBarItem(
                zekke.android.ui.R.drawable.ic_home,
                stringResource(R.string.shell_home),
                selected = selected == Destination.Home,
                onClick = { onOpen(Destination.Home) },
            ),
            BottomBarItem(
                zekke.android.ui.R.drawable.ic_activity,
                stringResource(R.string.shell_activity),
                selected = selected == Destination.Activity,
                onClick = onOpenNotifications,
                description = if (status.unreadNotifications > 0) {
                    pluralStringResource(R.plurals.activity_unread, status.unreadNotifications, status.unreadNotifications)
                } else {
                    stringResource(R.string.shell_activity)
                },
                badge = if (status.unreadNotifications > 0) notificationBadge(status.unreadNotifications) else null,
            ),
            BottomBarItem(
                zekke.android.ui.R.drawable.ic_settings,
                stringResource(R.string.shell_settings),
                selected = selected == Destination.Settings,
                onClick = { onOpen(Destination.Settings) },
            ),
        ),
    )
}

@Composable
private fun MainTabPage(
    title: String,
    selected: Destination,
    status: SyncStatus,
    onOpen: (Destination) -> Unit,
    onOpenNotifications: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
                color = Zekke.palette.ink,
                modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
            )
            content()
        }
        MainBottomBar(selected, status, onOpen, onOpenNotifications)
    }
}

@Composable
private fun ShellPage(
    title: String,
    onBack: () -> Unit,
    onLock: () -> Unit,
    scrollable: Boolean = true,
    header: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val slot = remember { TopBarActionsSlot() }
    TopBar(
        title = title,
        onBack = onBack,
        actions = {
            slot.content?.invoke(this)
            ZekkeIconButton(
                zekke.android.ui.R.drawable.ic_lock_session,
                hint = stringResource(R.string.shell_lock_hint),
                label = stringResource(R.string.home_lock),
                onClick = onLock,
            )
        },
    )
    CompositionLocalProvider(LocalTopBarActions provides slot) {
        if (scrollable) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { header() }
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun SectionBody(section: Section, status: SyncStatus) {
    val palette = Zekke.palette
    val count = when (section.destination) {
        Destination.Passwords -> status.counts.passwords
        Destination.Vault -> status.counts.vault
        Destination.Notes -> status.counts.notes
        else -> null
    }
    val text = when {
        section.count != null && count != null && count > 0 -> pluralStringResource(section.count, count, count)
        section.count != null -> stringResource(R.string.section_empty)
        else -> stringResource(R.string.section_drive_summary)
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
    SyncStatusView(status)
}
