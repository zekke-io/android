package zekke.android.shell

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import zekke.android.R
import zekke.android.sync.SyncStatus
import zekke.android.ui.components.AppTile
import zekke.android.feature.settings.notificationBadge
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.StatusBadge
import zekke.android.ui.components.TopBar
import zekke.android.ui.components.Tone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.theme.Zekke
import zekke.core.feed.FeedScope

enum class Destination { Home, Passwords, Vault, Notes, Drive, Shared, Settings, SettingsDevices, SettingsAccount, SettingsSecurity, SettingsPlan, SettingsAbout, Notifications }

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
    @DrawableRes val mark: Int,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @PluralsRes val count: Int?,
)

private val SECTIONS = listOf(
    Section(Destination.Passwords, setOf(FeedScope.PASSWORDS), zekke.android.ui.R.drawable.mark_password_manager, R.string.section_passwords, R.string.section_passwords_description, R.plurals.count_passwords),
    Section(Destination.Vault, setOf(FeedScope.SECRETS), zekke.android.ui.R.drawable.mark_secrets, R.string.section_vault, R.string.section_vault_description, R.plurals.count_vault),
    Section(Destination.Notes, setOf(FeedScope.NOTES), zekke.android.ui.R.drawable.mark_notes, R.string.section_notes, R.string.section_notes_description, R.plurals.count_notes),
    Section(Destination.Drive, setOf(FeedScope.FILES), zekke.android.ui.R.drawable.mark_drive, R.string.section_drive, R.string.section_drive_description, null),
    Section(Destination.Shared, setOf(FeedScope.SECRETS, FeedScope.NOTES, FeedScope.FILES), zekke.android.ui.R.drawable.mark_safe_sharing, R.string.section_shared, R.string.section_shared_description, null),
)

@Composable
fun Shell(
    status: SyncStatus,
    onLock: () -> Unit,
    notices: @Composable () -> Unit,
    settings: @Composable (openNotifications: () -> Unit, open: (Destination) -> Unit) -> Unit,
    settingsPages: Map<Destination, @Composable () -> Unit> = emptyMap(),
    notifications: @Composable () -> Unit,
    listPages: Map<Destination, @Composable () -> Unit> = emptyMap(),
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var notificationsFrom by rememberSaveable { mutableStateOf(Destination.Home) }
    val openNotifications = {
        notificationsFrom = destination
        destination = Destination.Notifications
    }
    BackHandler(enabled = destination != Destination.Home) {
        destination = when {
            destination == Destination.Notifications -> notificationsFrom
            destination in SETTINGS_PAGES -> Destination.Settings
            else -> Destination.Home
        }
    }
    val palette = Zekke.palette
    Column(Modifier.fillMaxSize().background(palette.ground).safeDrawingPadding()) {
        when (destination) {
            Destination.Home -> HomeScreen(
                status = status,
                onOpen = { destination = it },
                onOpenNotifications = openNotifications,
                onLock = onLock,
                notices = notices,
            )
            Destination.Settings -> ShellPage(
                title = stringResource(R.string.shell_settings),
                onBack = { destination = Destination.Home },
                onLock = onLock,
            ) { settings(openNotifications) { destination = it } }
            Destination.SettingsDevices, Destination.SettingsAccount, Destination.SettingsSecurity, Destination.SettingsPlan, Destination.SettingsAbout -> ShellPage(
                title = stringResource(SETTINGS_PAGES.getValue(destination)),
                onBack = { destination = Destination.Settings },
                onLock = onLock,
            ) { settingsPages[destination]?.invoke() }
            Destination.Notifications -> ShellPage(
                title = stringResource(R.string.shell_notifications),
                onBack = { destination = notificationsFrom },
                onLock = onLock,
            ) { notifications() }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    status: SyncStatus,
    onOpen: (Destination) -> Unit,
    onOpenNotifications: () -> Unit,
    onLock: () -> Unit,
    notices: @Composable () -> Unit,
) {
    val palette = Zekke.palette
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            ZekkeIconButton(
                zekke.android.ui.R.drawable.ic_bell,
                hint = stringResource(R.string.shell_notifications),
                label = if (status.unreadNotifications > 0) {
                    pluralStringResource(R.plurals.notifications_bell, status.unreadNotifications, status.unreadNotifications)
                } else {
                    stringResource(R.string.shell_notifications)
                },
                onClick = onOpenNotifications,
            )
            if (status.unreadNotifications > 0) {
                StatusBadge(notificationBadge(status.unreadNotifications), Tone.Brand)
            }
            ZekkeIconButton(
                zekke.android.ui.R.drawable.ic_settings,
                hint = stringResource(R.string.shell_settings),
                onClick = { onOpen(Destination.Settings) },
            )
        }
        Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge, color = palette.inkSoft)
        notices()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            SECTIONS.filter { section -> section.scopes.any { it in status.heldScopes } }.forEach { section ->
                AppTile(
                    mark = section.mark,
                    label = stringResource(section.title),
                    description = stringResource(section.description),
                    onClick = { onOpen(section.destination) },
                    modifier = Modifier.width(88.dp),
                )
            }
        }
        ZekkeButton(stringResource(R.string.home_lock), onLock, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
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
    TopBar(
        title = title,
        onBack = onBack,
        actions = {
            ZekkeIconButton(
                zekke.android.ui.R.drawable.ic_lock_session,
                hint = stringResource(R.string.shell_lock_hint),
                label = stringResource(R.string.home_lock),
                onClick = onLock,
            )
        },
    )
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
