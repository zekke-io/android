package zekke.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonSize
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.Mascot
import zekke.android.ui.components.MascotPose
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.OptionCard
import zekke.android.ui.components.PinPad
import zekke.android.ui.components.StepBar
import zekke.android.ui.components.Tone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.theme.Zekke

@Composable
fun BrandGallery() {
    val palette = Zekke.palette
    Column(
        modifier = Modifier.fillMaxWidth().background(palette.ground).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(MascotPose.Closed, Modifier.size(120.dp))
            Mascot(MascotPose.Open, Modifier.size(width = 150.dp, height = 100.dp))
            Mascot(MascotPose.OpenAppIcon, Modifier.size(64.dp))
        }
        StepBar(step = 2, total = 4)
        OptionCard("Standard", "The PIN stays on this device. If you forget it, sign in again with your phrase.", selected = true, onSelect = {}, tag = "Recommended")
        OptionCard("Paranoid", "The server also checks your PIN, so signing in takes the phrase and the PIN together.", selected = false, onSelect = {}, tag = "Two factors", tagTone = Tone.Brand)
        Notice("If you forget the PIN, the account ends. Nobody can reset it, not even us.", tone = NoticeTone.Warning)
        ZekkeButton("Create account", {}, Modifier.fillMaxWidth(), size = ButtonSize.Large)
        ZekkeButton("Sign in", {}, Modifier.fillMaxWidth(), ButtonVariant.Secondary, size = ButtonSize.Large)
        PinPad(value = "48", onValueChange = {}, label = "PIN")
    }
}
