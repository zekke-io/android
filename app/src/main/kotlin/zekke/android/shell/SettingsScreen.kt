package zekke.android.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.R
import zekke.android.feature.settings.PlanInfo
import zekke.android.feature.settings.planName
import zekke.android.sync.SyncStatus
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PlanStorageCard
import zekke.android.ui.components.SectionLabel
import zekke.android.ui.components.SettingsGroup
import zekke.android.ui.components.SettingsProfileCard
import zekke.android.ui.components.SettingsRow
import zekke.android.ui.components.StorageReading
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.theme.Zekke

class SettingsProfile(val username: String?, val paranoid: Boolean?)

@Composable
fun SettingsContent(
    status: SyncStatus,
    profile: SettingsProfile,
    plan: PlanInfo?,
    storage: StorageReading?,
    idleMinutes: Int,
    onOpened: () -> Unit,
    onOpenNotifications: () -> Unit,
    onDiscard: (Long) -> Unit,
    onOpen: (Destination) -> Unit,
    onLock: () -> Unit,
    notices: @Composable () -> Unit,
) {
    val palette = Zekke.palette
    val resources = LocalResources.current
    LaunchedEffect(Unit) { onOpened() }
    val planLabel = plan?.let { planName(it.code, resources) }
    val modeLabel = profile.paranoid?.let { stringResource(if (it) R.string.settings_mode_paranoid else R.string.settings_mode_standard) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        notices()
        if (storage != null && planLabel != null) {
            PlanStorageCard(storage, planLabel, stringResource(R.string.settings_plan_details), { onOpen(Destination.SettingsPlan) })
        }
        if (profile.username != null) {
            SettingsProfileCard(
                initial = profile.username,
                name = "@" + profile.username,
                tags = listOfNotNull(planLabel, modeLabel),
                onClick = { onOpen(Destination.SettingsAccount) },
            )
        }
        if (status.outboxRefused.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.outbox_refused_title), style = MaterialTheme.typography.titleMedium, color = palette.ink)
                Notice(stringResource(R.string.outbox_refused_summary), tone = NoticeTone.Danger)
                status.outboxRefused.forEach { entry ->
                    ZekkeButton(
                        stringResource(R.string.outbox_discard) + " · " + entry.path,
                        { onDiscard(entry.position) },
                        Modifier.fillMaxWidth(),
                        ButtonVariant.Secondary,
                    )
                }
            }
        }
        SectionLabel(stringResource(R.string.settings_section_account))
        SettingsGroup {
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_user,
                stringResource(R.string.settings_username),
                value = profile.username?.let { "@$it" },
                onClick = { onOpen(Destination.SettingsAccount) },
                divider = false,
            )
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_card,
                stringResource(R.string.settings_plan_storage),
                value = planLabel,
                onClick = { onOpen(Destination.SettingsPlan) },
            )
        }
        SectionLabel(stringResource(R.string.settings_section_security))
        SettingsGroup {
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_shield,
                stringResource(R.string.settings_protection_mode),
                value = modeLabel,
                onClick = { onOpen(Destination.SettingsAccount) },
                divider = false,
            )
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_key,
                stringResource(R.string.settings_account_pin),
                value = if (profile.paranoid == false) stringResource(R.string.settings_account_pin_paranoid_only) else null,
                onClick = { onOpen(Destination.SettingsAccount) },
                enabled = profile.paranoid == true,
            )
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_phone,
                stringResource(R.string.settings_devices),
                onClick = { onOpen(Destination.SettingsDevices) },
            )
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_lock,
                stringResource(R.string.settings_idle),
                value = pluralStringResource(R.plurals.settings_idle_minutes, idleMinutes, idleMinutes),
                onClick = { onOpen(Destination.SettingsSecurity) },
            )
        }
        SectionLabel(stringResource(R.string.settings_section_phone))
        SettingsGroup {
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_bell,
                stringResource(R.string.notifications_open),
                value = if (status.unreadNotifications > 0) {
                    pluralStringResource(R.plurals.notifications_unread, status.unreadNotifications, status.unreadNotifications)
                } else {
                    null
                },
                onClick = onOpenNotifications,
                divider = false,
            )
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_info,
                stringResource(R.string.settings_about),
                onClick = { onOpen(Destination.SettingsAbout) },
            )
            SettingsRow(
                zekke.android.ui.R.drawable.ic_set_lock,
                stringResource(R.string.settings_lock_now),
                onClick = onLock,
            )
        }
        SettingsGroup(Modifier.padding(top = 10.dp)) {
            SettingsRow(
                zekke.android.ui.R.drawable.ic_action_trash,
                stringResource(R.string.settings_delete_account),
                onClick = { onOpen(Destination.SettingsAccount) },
                danger = true,
                divider = false,
            )
        }
    }
}
