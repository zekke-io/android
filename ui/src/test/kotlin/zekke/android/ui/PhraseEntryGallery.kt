package zekke.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.PhraseEntry
import zekke.android.ui.components.PhraseEntryFields
import zekke.android.ui.components.PhraseScan
import zekke.android.ui.components.QrScannerFrame
import zekke.android.ui.theme.Zekke

@Composable
fun PhraseEntryGallery() {
    val entry = remember {
        PhraseEntry(
            suggestionsFor = { prefix -> listOf("abandon", "ability", "able", "about").filter { it.startsWith(String(prefix)) } },
            isValid = { false },
        ).also { holder ->
            holder.start(12)
            listOf("abandon", "ability", "able", "about", "ab").forEachIndexed { index, word -> holder.wordChanged(index, word) }
        }
    }
    Column(Modifier.fillMaxWidth().background(Zekke.palette.ground).padding(16.dp)) {
        PhraseEntryFields(entry, scan = PhraseScan({}, {}))
    }
}

@Composable
fun ScannerGallery() {
    Box(Modifier.fillMaxWidth().height(520.dp).background(Zekke.palette.ink)) {
        QrScannerFrame()
        Text(
            "Point the camera at the QR code of your recovery kit. It is read on this phone and nothing is saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = Zekke.palette.onGradient,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        )
    }
}
