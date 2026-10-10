package zekke.android.feature.onboarding.kit

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJob
import android.print.PrintManager
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.core.recoverykit.RecoveryKitContent
import zekke.core.recoverykit.recoveryKitFileName

const val RECOVERY_KIT_MIME_TYPE = "application/pdf"

fun recoveryKitDocumentName(content: RecoveryKitContent): String = recoveryKitFileName(content.username)

suspend fun saveRecoveryKit(context: Context, content: RecoveryKitContent, destination: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val stream = context.contentResolver.openOutputStream(destination, "wt") ?: return@withContext false
        stream.use { writeRecoveryKitPdf(content, it) }
        true
    } catch (_: Exception) {
        false
    }
}

fun printRecoveryKit(context: Context, content: RecoveryKitContent, onFinished: (printed: Boolean) -> Unit) {
    val manager = context.getSystemService(PrintManager::class.java)
    var job: PrintJob? = null
    val adapter = RecoveryKitPrintAdapter(content) {
        val state = job
        onFinished(state != null && (state.isQueued || state.isStarted || state.isCompleted || state.isBlocked))
    }
    job = manager.print(recoveryKitDocumentName(content), adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}

private class RecoveryKitPrintAdapter(
    private val content: RecoveryKitContent,
    private val onDone: () -> Unit,
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(recoveryKitDocumentName(content))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            FileOutputStream(destination.fileDescriptor).use { writeRecoveryKitPdf(content, it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(null)
        }
    }

    override fun onFinish() = onDone()
}
