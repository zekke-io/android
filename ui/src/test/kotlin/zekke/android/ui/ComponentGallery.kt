package zekke.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.AppTile
import zekke.android.ui.components.AppTileStyle
import zekke.android.ui.components.BottomBar
import zekke.android.ui.components.BottomBarItem
import zekke.android.ui.components.Block
import zekke.android.ui.components.CheckboxRow
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FileKind
import zekke.android.ui.components.FileKindIcon
import zekke.android.ui.components.FileTile
import zekke.android.ui.components.ExtendedAddButton
import zekke.android.ui.components.FloatingAddButton
import zekke.android.ui.components.IconButtonTone
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.NoteSurface
import zekke.android.ui.components.SurfaceLine
import zekke.android.ui.components.SurfaceLineKind
import zekke.android.ui.components.SurfaceSpan
import zekke.android.ui.components.PageTile
import zekke.android.ui.components.Segment
import zekke.android.ui.components.SegmentedControl
import zekke.android.ui.components.StatusBadge
import zekke.android.ui.components.StorageMeter
import zekke.android.ui.components.StorageReading
import zekke.android.ui.components.StorageWarning
import zekke.android.ui.components.TabItem
import zekke.android.ui.components.TabStrip
import zekke.android.ui.components.Tone
import zekke.android.ui.components.TopBar
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.components.ZekkeIconToggleButton
import zekke.android.ui.theme.Zekke

private const val GIB = 1024L * 1024 * 1024

@Composable
fun ComponentGallery() {
    val palette = Zekke.palette
    Column(
        modifier = Modifier.fillMaxWidth().background(palette.ground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZekkeButton("Create account", onClick = {}, variant = ButtonVariant.Accent)
            ZekkeIconButton(R.drawable.ic_menu, hint = "Menu", onClick = {})
            ZekkeIconToggleButton(R.drawable.ic_eye, hint = "Show values", checked = false, onCheckedChange = {})
            ZekkeIconToggleButton(R.drawable.ic_eye_off, hint = "Hide values", checked = true, onCheckedChange = {})
            ZekkeIconButton(R.drawable.ic_lock_session, hint = "Lock", onClick = {}, framed = true)
            ZekkeIconButton(R.drawable.ic_trash, hint = "Delete", label = "Delete Bank PIN", onClick = {}, tone = IconButtonTone.Danger)
            ZekkeIconButton(R.drawable.ic_lock_session, hint = "Lock", onClick = {}, tone = IconButtonTone.Ink)
            StatusBadge("Draft", Tone.Neutral)
        }
        BottomBar(
            listOf(
                BottomBarItem(R.drawable.ic_home, "Home", selected = true, onClick = {}),
                BottomBarItem(R.drawable.ic_bell, "Notifications", selected = false, onClick = {}, badge = "3"),
                BottomBarItem(R.drawable.ic_settings, "Settings", selected = false, onClick = {}),
            ),
        )
        TopBar(
            title = "Passwords",
            onBack = {},
            actions = { ZekkeIconButton(R.drawable.ic_lock_session, hint = "Lock", onClick = {}) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageTile("Shopping list", "Shopping\n• fruit\n☐ milk\n☑ bread", "Oct 14, 2026", onClick = {}, modifier = Modifier.width(120.dp))
            PageTile("Untitled note", "plain text of a note that is longer than the sheet can show", "Oct 13, 2026", onClick = {}, modifier = Modifier.width(120.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FileKind.entries.forEach { FileKindIcon(it, height = 64.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileTile("Holidays", "3 files, 1 folder", FileKind.Folder, onClick = {}, modifier = Modifier.width(110.dp))
            FileTile("report-2026.pdf", "2.4 MiB", FileKind.Pdf, onClick = {}, modifier = Modifier.width(110.dp), more = {
                ZekkeIconButton(R.drawable.ic_menu, hint = "More", onClick = {})
            })
        }
        NoteSurface(
            value = TextFieldValue("Shopping\nfruit\nmilk\nbread"),
            onValueChange = {},
            lines = listOf(
                SurfaceLine(SurfaceLineKind.Title, false, 0),
                SurfaceLine(SurfaceLineKind.Topic, false, 9),
                SurfaceLine(SurfaceLineKind.Task, false, 15),
                SurfaceLine(SurfaceLineKind.Task, true, 20),
            ),
            spans = listOf(SurfaceSpan(9, 14, bold = true, italic = false, size = null), SurfaceSpan(20, 25, bold = false, italic = true, size = 18)),
            onToggleTask = {},
        )
        TabStrip(
            tabs = listOf(TabItem("home", "Home", 4, manageable = false), TabItem("w", "Work", 2), TabItem("b", "Bank", 0)),
            selected = "w",
            onSelect = {},
            onAdd = {},
            onManage = {},
        )
        SegmentedControl(
            segments = listOf(Segment(false, "Standard"), Segment(true, "Paranoid")),
            selected = false,
            onSelect = {},
            label = "Protection mode",
        )
        Column {
            CheckboxRow("Added 3 Oct 2026", checked = true, onCheckedChange = {}, detail = "Device ending …d911b424", detailIsValue = true)
            CheckboxRow("Added 9 Oct 2026", checked = false, onCheckedChange = {}, detail = "Device ending …7a3c01fe", detailIsValue = true)
        }
        Notice("A username is permanent. Choose one you are happy to share with your contacts.")
        Notice("Password saved.", tone = NoticeTone.Success, onDismiss = {})
        Notice("Your plan ends in 6 days.", tone = NoticeTone.Warning, onDismiss = {})
        Notice("This contact's key does not match the one you pinned.", tone = NoticeTone.Danger)
        Block(title = "Passwords", subtitle = "Website logins, encrypted here.", actions = {
            ZekkeButton("Recently deleted", onClick = {}, variant = ButtonVariant.Ghost)
        }) {
            Column {
                ItemRow("github.com", meta = "ada@lovelace.dev", icon = R.drawable.ic_passwords, onClick = {}) {
                    ZekkeIconButton(R.drawable.ic_clipboard, hint = "Copy", label = "Copy the password for github.com", onClick = {})
                }
                ItemRow("Bank PIN", meta = "••••", metaIsValue = true, icon = R.drawable.ic_vault, onClick = {}) {
                    ZekkeIconButton(R.drawable.ic_eye, hint = "Show", label = "Show Bank PIN", onClick = {})
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemRow("Amazon", meta = "you@example.com", icon = R.drawable.ic_passwords, avatar = "Amazon", card = true, onClick = {})
            ItemRow("Savings wallet", meta = "••••", metaIsValue = true, icon = R.drawable.ic_vault, card = true, inkIcon = true, onClick = {})
        }
        StorageWarning(StorageReading(storedBytes = 460, usedBytes = 460, quotaBytes = 500))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ZekkeIconButton(R.drawable.ic_action_uploads, hint = "Uploads", onClick = {}, iconSize = 24.dp, dot = true)
            ZekkeIconButton(R.drawable.ic_action_grid, hint = "Grid", onClick = {}, iconSize = 24.dp)
            ZekkeIconButton(R.drawable.ic_action_list, hint = "List", onClick = {}, iconSize = 24.dp)
            ZekkeIconButton(R.drawable.ic_action_trash, hint = "Trash", onClick = {}, iconSize = 24.dp)
        }
        StorageMeter(StorageReading(storedBytes = 3 * GIB + GIB / 5, usedBytes = 3 * GIB + GIB / 3, quotaBytes = 10 * GIB))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTile(R.drawable.ic_app_secrets, "Vault", onClick = {}, style = AppTileStyle.Ink, modifier = Modifier.width(80.dp))
            AppTile(R.drawable.ic_app_passwords, "Passwords", onClick = {}, modifier = Modifier.width(80.dp))
            AppTile(R.drawable.ic_app_notes, "Notes", onClick = {}, style = AppTileStyle.Peach, modifier = Modifier.width(80.dp))
            AppTile(R.drawable.ic_app_drive, "Drive", onClick = {}, modifier = Modifier.width(80.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EmptyState("No passwords yet. Tap + to add the first one.", icon = R.drawable.ic_passwords, modifier = Modifier.weight(1f))
            FloatingAddButton(label = "Add password", onClick = {})
        }
        ExtendedAddButton(label = "Add a password", onClick = {})
    }
}
