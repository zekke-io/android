package zekke.android.feature.drive

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke

private const val MAX_EDGE_PIXELS = 2048

fun decodeImage(bytes: ByteArray, maxEdge: Int = MAX_EDGE_PIXELS): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > maxEdge) {
            val scale = maxEdge.toFloat() / longest
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
    }
} catch (_: Exception) {
    null
}

@Composable
fun ViewerPage(viewing: Viewing, viewModel: DriveViewModel) {
    val palette = Zekke.palette
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZekkeButton(stringResource(R.string.viewer_close), viewModel::closePreview, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        Text(
            viewing.entry.name ?: stringResource(R.string.drive_unreadable_file),
            style = MaterialTheme.typography.titleMedium,
            color = palette.ink,
            maxLines = 1,
        )
        when (viewing) {
            is Viewing.Loading -> LoadingIndicator()
            is Viewing.TooLarge -> Notice(stringResource(R.string.viewer_too_large), tone = NoticeTone.Warning)
            is Viewing.Failed -> Notice(itemFailureText(viewing.failure), tone = itemFailureTone(viewing.failure))
            is Viewing.Image -> ImageView(viewing)
            is Viewing.Pdf -> PdfView(viewing.file)
        }
    }
}

@Composable
private fun ImageView(viewing: Viewing.Image) {
    val bitmap = remember(viewing.bytes) { decodeImage(viewing.bytes) }
    if (bitmap == null) {
        Notice(stringResource(R.string.viewer_unreadable), tone = NoticeTone.Warning)
    } else {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = viewing.entry.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PdfView(file: File) {
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) {
        Notice(stringResource(R.string.viewer_pdf_unreadable), tone = NoticeTone.Warning)
        return
    }
    val lock = remember(renderer) { Mutex() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            items(renderer.pageCount) { index ->
                val page = produceState<Bitmap?>(null, renderer, index, width) {
                    value = withContext(Dispatchers.Default) {
                        lock.withLock {
                            runCatching {
                                renderer.openPage(index).use { source ->
                                    val height = (width * source.height.toFloat() / source.width).toInt().coerceAtLeast(1)
                                    createBitmap(width, height).also { target ->
                                        target.eraseColor(android.graphics.Color.WHITE)
                                        source.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }.getOrNull()
                        }
                    }
                }.value
                val label = stringResource(R.string.viewer_page, index + 1, renderer.pageCount)
                if (page != null) {
                    Image(page.asImageBitmap(), contentDescription = label, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
                } else {
                    LoadingIndicator()
                }
            }
        }
    }
}
