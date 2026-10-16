package zekke.android.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

enum class CameraAccess { Unasked, Denied, Blocked, Unavailable }

@Composable
fun PhraseScanSheet(entry: PhraseEntry, scan: PhraseScan, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var access by remember { mutableStateOf(CameraAccess.Unasked) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        scan.systemScreenReturned()
        granted = allowed
        if (!allowed) access = if (context.findActivity()?.let { shouldExplain(it) } == true) CameraAccess.Denied else CameraAccess.Blocked
    }
    val palette = Zekke.palette
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(palette.ink)) {
            if (granted && access != CameraAccess.Unavailable) {
                QrScanner(
                    onText = { text ->
                        entry.acceptScanned(text)
                        onClose()
                    },
                    onUnavailable = { access = CameraAccess.Unavailable },
                )
                QrScannerFrame()
            }
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.scanner_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (!granted || access == CameraAccess.Unavailable) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(accessMessage(granted, access)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        if (access == CameraAccess.Blocked) {
                            ZekkeButton(
                                stringResource(R.string.scanner_open_settings),
                                {
                                    scan.systemScreenWillOpen()
                                    context.startActivity(settingsIntent(context))
                                    onClose()
                                },
                                Modifier.fillMaxWidth(),
                            )
                        } else if (access != CameraAccess.Unavailable) {
                            ZekkeButton(
                                stringResource(R.string.scanner_allow),
                                {
                                    scan.systemScreenWillOpen()
                                    request.launch(Manifest.permission.CAMERA)
                                },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.scanner_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
                ZekkeButton(stringResource(R.string.dialog_cancel), onClose, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
            }
        }
    }
}

private fun accessMessage(granted: Boolean, access: CameraAccess): Int = when {
    access == CameraAccess.Unavailable -> R.string.scanner_unavailable
    granted -> R.string.scanner_hint
    access == CameraAccess.Blocked -> R.string.scanner_blocked
    access == CameraAccess.Denied -> R.string.scanner_denied
    else -> R.string.scanner_why
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun shouldExplain(activity: Activity): Boolean =
    activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

private fun settingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
