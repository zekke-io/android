package zekke.android.feature.onboarding.kit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import zekke.core.keys.generateMnemonic
import zekke.core.recoverykit.RECOVERY_KIT_QR_QUIET_ZONE_MODULES
import zekke.core.recoverykit.RecoveryKitInput
import zekke.core.recoverykit.recoveryKitContent

@RunWith(AndroidJUnit4::class)
class RecoveryKitDocumentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun kit(wordCount: Int, username: String): Pair<String, File> {
        val words = generateMnemonic(wordCount)
        val content = recoveryKitContent(RecoveryKitInput(username, words, 1_789_293_600_000L))
        val file = File(context.cacheDir, "kit-$wordCount.pdf")
        file.outputStream().use { writeRecoveryKitPdf(content, it) }
        return content.qrPayload to file
    }

    private fun render(file: File): Bitmap = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                bitmap
            }
        }
    }

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun theQrCodeOnThePageReadsBackAsTheBarePhrase() {
        for (count in listOf(12, 24)) {
            val (phrase, file) = kit(count, "3f1c8a2b9d4e")
            try {
                assertEquals(phrase, decode(render(file)))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun theLongestContentStaysOnOnePage() {
        val (_, file) = kit(24, "a".repeat(64))
        try {
            render(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun theQrMatrixCarriesItsQuietZone() {
        val modules = recoveryKitQrModules("abandon ability able")
        val border = RECOVERY_KIT_QR_QUIET_ZONE_MODULES
        assertTrue(modules.all { it.size == modules.size })
        for (index in modules.indices) {
            for (edge in 0 until border) {
                assertTrue(!modules[edge][index] && !modules[index][edge])
                assertTrue(!modules[modules.size - 1 - edge][index] && !modules[index][modules.size - 1 - edge])
            }
        }
        assertTrue(modules[border][border])
    }
}
