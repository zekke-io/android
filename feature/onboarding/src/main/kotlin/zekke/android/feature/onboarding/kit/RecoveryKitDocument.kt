package zekke.android.feature.onboarding.kit

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.io.OutputStream
import zekke.android.ui.theme.LightPalette
import zekke.core.recoverykit.GLYPH_UNITS_PER_EM
import zekke.core.recoverykit.QrErrorCorrection
import zekke.core.recoverykit.RECOVERY_KIT_PHRASE_COLUMNS
import zekke.core.recoverykit.RECOVERY_KIT_QR_ERROR_CORRECTION
import zekke.core.recoverykit.RECOVERY_KIT_QR_QUIET_ZONE_MODULES
import zekke.core.recoverykit.RecoveryKitContent
import zekke.core.recoverykit.RecoveryKitCopy
import zekke.core.recoverykit.recoveryKitGridCell
import zekke.core.recoverykit.recoveryKitQrModulePath
import zekke.core.recoverykit.recoveryKitWordOutlines

class RecoveryKitOverflowException : IllegalStateException("recovery kit content does not fit on one page")

const val A4_WIDTH = 595
const val A4_HEIGHT = 842
private const val PAGE_MARGIN = 56f
private const val QR_SIZE = 168f
private const val PHRASE_ROW_HEIGHT = 26f
private const val PHRASE_BOX_PADDING = 16f
private const val PHRASE_WORD_SIZE = 13f
private const val BODY_SIZE = 10.5f
private const val BODY_LINE_HEIGHT = 15f
private const val LABEL_SIZE = 9f
private const val NOTE_SIZE = 8f
private const val NOTE_LINE_HEIGHT = 11f
private const val SECTION_GAP = 28f
private const val MINIMUM_FITTED_SIZE = 8f

fun recoveryKitQrModules(payload: String): List<BooleanArray> {
    val level = when (RECOVERY_KIT_QR_ERROR_CORRECTION) {
        QrErrorCorrection.L -> ErrorCorrectionLevel.L
        QrErrorCorrection.M -> ErrorCorrectionLevel.M
        QrErrorCorrection.Q -> ErrorCorrectionLevel.Q
        QrErrorCorrection.H -> ErrorCorrectionLevel.H
    }
    val matrix = Encoder.encode(payload, level).matrix
    val border = RECOVERY_KIT_QR_QUIET_ZONE_MODULES
    val size = matrix.width + 2 * border
    return List(size) { y ->
        BooleanArray(size) { x ->
            val inner = x - border to y - border
            inner.first in 0 until matrix.width && inner.second in 0 until matrix.height && matrix.get(inner.first, inner.second).toInt() == 1
        }
    }
}

fun writeRecoveryKitPdf(content: RecoveryKitContent, destination: OutputStream) {
    val document = PdfDocument()
    try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create())
        val bottom = RecoveryKitPage(page.canvas, content).draw()
        document.finishPage(page)
        if (bottom > A4_HEIGHT - PAGE_MARGIN) throw RecoveryKitOverflowException()
        document.writeTo(destination)
    } finally {
        document.close()
    }
}

private class RecoveryKitPage(private val canvas: Canvas, private val content: RecoveryKitContent) {
    private val ink = LightPalette.ink.toArgb()
    private val inkMuted = LightPalette.inkMuted.toArgb()
    private val brand = LightPalette.brand.toArgb()
    private val rule = LightPalette.line.toArgb()
    private val warning = LightPalette.warning
    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    fun draw(): Float {
        var top = header(PAGE_MARGIN)
        top = identity(top)
        top = phrase(top)
        return warningBox(top)
    }

    private fun text(typeface: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        this.color = color
    }

    private fun wrap(value: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in value.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun paragraph(value: String, x: Float, top: Float, width: Float, paint: Paint): Float {
        var baseline = top + BODY_SIZE
        for (line in wrap(value, paint, width)) {
            canvas.drawText(line, x, baseline, paint)
            baseline += BODY_LINE_HEIGHT
        }
        return baseline - BODY_LINE_HEIGHT + 4
    }

    private fun field(label: String, value: String, valueSize: Float, x: Float, top: Float, width: Float): Float {
        val labelBaseline = top + LABEL_SIZE
        canvas.drawText(label.uppercase(), x, labelBaseline, text(bold, LABEL_SIZE, inkMuted))
        val valuePaint = text(bold, valueSize, ink)
        while (valuePaint.textSize > MINIMUM_FITTED_SIZE && valuePaint.measureText(value) > width) valuePaint.textSize -= 0.5f
        val valueBaseline = labelBaseline + 8 + valuePaint.textSize
        canvas.drawText(value, x, valueBaseline, valuePaint)
        return valueBaseline + 4
    }

    private fun header(top: Float): Float {
        val titleBaseline = top + 28
        canvas.drawText(content.appName, PAGE_MARGIN, titleBaseline, text(bold, 28f, brand))
        val headingBaseline = titleBaseline + 12 + 14
        canvas.drawText(content.heading, PAGE_MARGIN, headingBaseline, text(regular, 14f, inkMuted))
        val ruleY = headingBaseline + 18
        canvas.drawLine(PAGE_MARGIN, ruleY, A4_WIDTH - PAGE_MARGIN, ruleY, stroke(rule))
        return ruleY + SECTION_GAP
    }

    private fun identity(top: Float): Float {
        val qrLeft = A4_WIDTH - PAGE_MARGIN - QR_SIZE
        val columnWidth = qrLeft - PAGE_MARGIN - 24
        var left = field(RecoveryKitCopy.USERNAME_LABEL, content.username, 16f, PAGE_MARGIN, top, columnWidth)
        left = field(RecoveryKitCopy.CREATED_LABEL, content.created, 12f, PAGE_MARGIN, left + 18, columnWidth)
        left = paragraph(content.intro, PAGE_MARGIN, left + 18, columnWidth, text(regular, BODY_SIZE, inkMuted))

        val modules = recoveryKitQrModules(content.qrPayload)
        val qr = PathParser.createPathFromPathData(recoveryKitQrModulePath(modules))
        qr.transform(Matrix().apply {
            setScale(QR_SIZE / modules.size, QR_SIZE / modules.size)
            postTranslate(qrLeft, top)
        })
        canvas.drawPath(qr, fill(android.graphics.Color.BLACK))

        val caption = text(regular, LABEL_SIZE, inkMuted)
        var captionBaseline = top + QR_SIZE + 6 + LABEL_SIZE
        for (line in wrap(content.qrCaption, caption, QR_SIZE + 32)) {
            val lineWidth = caption.measureText(line)
            canvas.drawText(line, minOf(qrLeft + (QR_SIZE - lineWidth) / 2, A4_WIDTH - PAGE_MARGIN - lineWidth), captionBaseline, caption)
            captionBaseline += LABEL_SIZE + 3
        }
        return maxOf(left, captionBaseline) + SECTION_GAP
    }

    private fun outlinedWord(word: String, x: Float, baseline: Float) {
        val scale = PHRASE_WORD_SIZE / GLYPH_UNITS_PER_EM
        val paint = fill(ink)
        val combined = Path()
        for (outline in recoveryKitWordOutlines(word)) {
            val glyph = PathParser.createPathFromPathData(outline.path)
            glyph.transform(Matrix().apply {
                setScale(scale, scale)
                postTranslate(x + outline.offset * scale, baseline)
            })
            combined.addPath(glyph)
        }
        canvas.drawPath(combined, paint)
    }

    private fun phrase(top: Float): Float {
        val boxWidth = A4_WIDTH - 2 * PAGE_MARGIN
        canvas.drawText(RecoveryKitCopy.PHRASE_LABEL.uppercase(), PAGE_MARGIN, top + LABEL_SIZE, text(bold, LABEL_SIZE, inkMuted))
        val boxTop = top + LABEL_SIZE + 12
        val rows = (content.words.size + RECOVERY_KIT_PHRASE_COLUMNS - 1) / RECOVERY_KIT_PHRASE_COLUMNS
        val boxHeight = 2 * PHRASE_BOX_PADDING + (rows - 1) * PHRASE_ROW_HEIGHT + PHRASE_WORD_SIZE
        canvas.drawRect(PAGE_MARGIN, boxTop, PAGE_MARGIN + boxWidth, boxTop + boxHeight, stroke(rule))

        val columnWidth = (boxWidth - 2 * PHRASE_BOX_PADDING) / RECOVERY_KIT_PHRASE_COLUMNS
        val numberWidth = 22f
        val numbers = text(regular, LABEL_SIZE, inkMuted)
        content.words.forEachIndexed { index, entry ->
            val cell = recoveryKitGridCell(index, content.words.size)
            val cellLeft = PAGE_MARGIN + PHRASE_BOX_PADDING + cell.column * columnWidth
            val baseline = boxTop + PHRASE_BOX_PADDING + cell.row * PHRASE_ROW_HEIGHT + PHRASE_WORD_SIZE
            val number = entry.position.toString()
            canvas.drawText(number, cellLeft + numberWidth - numbers.measureText(number), baseline, numbers)
            outlinedWord(entry.word, cellLeft + numberWidth + 8, baseline)
        }

        val note = text(regular, NOTE_SIZE, inkMuted)
        var noteBaseline = boxTop + boxHeight + 8 + NOTE_SIZE
        for (line in wrap(RecoveryKitCopy.PHRASE_NOT_COPYABLE, note, boxWidth)) {
            canvas.drawText(line, PAGE_MARGIN, noteBaseline, note)
            noteBaseline += NOTE_LINE_HEIGHT
        }
        return noteBaseline - NOTE_LINE_HEIGHT + SECTION_GAP
    }

    private fun warningBox(top: Float): Float {
        val boxWidth = A4_WIDTH - 2 * PAGE_MARGIN
        val padding = 14f
        val paint = text(bold, BODY_SIZE, warning.text.toArgb())
        val lines = wrap(content.warning, paint, boxWidth - 2 * padding)
        val boxHeight = 2 * padding + (lines.size - 1) * BODY_LINE_HEIGHT + BODY_SIZE
        canvas.drawRect(PAGE_MARGIN, top, PAGE_MARGIN + boxWidth, top + boxHeight, fill(warning.background.toArgb()))
        canvas.drawRect(PAGE_MARGIN, top, PAGE_MARGIN + boxWidth, top + boxHeight, stroke(warning.line.toArgb()))
        paragraph(content.warning, PAGE_MARGIN + padding, top + padding, boxWidth - 2 * padding, paint)
        return top + boxHeight
    }
}
