package zekke.android.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.android.ui.components.PhraseEntry
import zekke.android.ui.components.QrDecoder

class QrDecoderTest {
    private val decoder = QrDecoder()
    private val phrase12 = List(12) { "abandon" }.joinToString(" ")
    private val phrase24 = List(24) { "zoo" }.joinToString(" ")

    private fun qr(text: String, size: Int = 640): BitMatrix =
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4),
        )

    private fun luminance(matrix: BitMatrix, rowStride: Int = matrix.width): ByteArray {
        val data = ByteArray(rowStride * matrix.height) { 127 }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) data[y * rowStride + x] = if (matrix.get(x, y)) 0 else 255.toByte()
        }
        return data
    }

    private fun rotated(matrix: BitMatrix): BitMatrix {
        val turned = BitMatrix(matrix.height, matrix.width)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix.get(x, y)) turned.set(matrix.height - 1 - y, x)
        return turned
    }

    @Test
    fun readsTheBarePhraseOfATwelveWordKit() {
        val matrix = qr(phrase12)
        assertEquals(phrase12, decoder.decode(luminance(matrix), matrix.width, matrix.width, matrix.height))
    }

    @Test
    fun readsTheBarePhraseOfATwentyFourWordKit() {
        val matrix = qr(phrase24)
        assertEquals(phrase24, decoder.decode(luminance(matrix), matrix.width, matrix.width, matrix.height))
    }

    @Test
    fun readsARowWithPaddingAsACameraFrameHasIt() {
        val matrix = qr(phrase12)
        assertEquals(phrase12, decoder.decode(luminance(matrix, rowStride = matrix.width + 48), matrix.width + 48, matrix.width, matrix.height))
    }

    @Test
    fun readsACodeTurnedOnItsSide() {
        val turned = rotated(qr(phrase12))
        assertEquals(phrase12, decoder.decode(luminance(turned), turned.width, turned.width, turned.height))
    }

    @Test
    fun readsASmallCodeInALargeFrame() {
        val small = qr(phrase12, size = 260)
        val frame = BitMatrix(1280, 720)
        for (y in 0 until small.height) for (x in 0 until small.width) if (small.get(x, y)) frame.set(x + 500, y + 200)
        assertEquals(phrase12, decoder.decode(luminance(frame), frame.width, frame.width, frame.height))
    }

    @Test
    fun readsNothingFromABlankFrameOrAnotherKindOfCode() {
        val blank = ByteArray(640 * 480) { 200.toByte() }
        assertNull(decoder.decode(blank, 640, 640, 480))
        val bars = MultiFormatWriter().encode("123456789012", BarcodeFormat.CODE_128, 640, 200)
        assertNull(decoder.decode(luminance(bars), bars.width, bars.width, bars.height))
    }

    @Test
    fun theTextOfAKitFillsTheFormWhateverItsCaseAndSpacing() {
        val matrix = qr(phrase12)
        val text = decoder.decode(luminance(matrix), matrix.width, matrix.width, matrix.height)
        val entry = PhraseEntry({ emptyList() }, { true }).also { it.start(12) }
        assertEquals(phrase12, text)
        assertTrue(entry.acceptScanned("  " + text.orEmpty().uppercase().replace(" ", "\t ") + "\n"))
        assertEquals("abandon", entry.word(0))
        assertEquals("abandon", entry.word(11))
    }

    @Test
    fun aCodeOfSomethingElseFillsNothing() {
        val matrix = qr("https://zekke.io")
        val text = decoder.decode(luminance(matrix), matrix.width, matrix.width, matrix.height)
        val entry = PhraseEntry({ emptyList() }, { true }).also { it.start(12) }
        assertFalse(entry.acceptScanned(text.orEmpty()))
        assertTrue(entry.state.scanRefused)
        assertEquals("", entry.word(0))
    }
}
