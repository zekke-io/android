package zekke.android.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveRulesTest {
    @Test
    fun aMimeTypeIsAKindAsTheWebAppSaysIt() {
        assertEquals(FileKind.Pdf, fileKindOf("application/pdf"))
        assertEquals(FileKind.Image, fileKindOf("image/heic"))
        assertEquals(FileKind.Video, fileKindOf("video/mp4"))
        assertEquals(FileKind.Audio, fileKindOf("audio/ogg"))
        assertEquals(FileKind.Document, fileKindOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(FileKind.Sheet, fileKindOf("text/csv"))
        assertEquals(FileKind.Slides, fileKindOf("application/vnd.ms-powerpoint"))
        assertEquals(FileKind.Archive, fileKindOf("application/zip"))
        assertEquals(FileKind.Code, fileKindOf("application/json"))
        assertEquals(FileKind.Text, fileKindOf("text/plain"))
        assertEquals(FileKind.Other, fileKindOf("application/octet-stream"))
    }

    @Test
    fun anExtensionIsShortAlphanumericAndUppercase() {
        assertEquals("JPG", fileExtensionOf("photo.jpg"))
        assertEquals("", fileExtensionOf("archive.tar.longext"))
        assertEquals("", fileExtensionOf(".hidden"))
        assertEquals("", fileExtensionOf("trailing."))
        assertEquals("", fileExtensionOf("no-extension"))
        assertEquals("PDF", fileExtensionOf("dir/report.pdf"))
    }

    @Test
    fun aLongNameIsCutAtEightyCharactersWithAnEllipsis() {
        assertNull(shortFileName("   "))
        assertEquals("short.txt", shortFileName(" short.txt "))
        val cut = shortFileName("a".repeat(100))!!
        assertEquals(81, cut.length)
        assertEquals('…', cut.last())
    }

    @Test
    fun aFileNameIsCheckedBeforeItIsSent() {
        assertEquals(FileNameProblem.Empty, fileNameProblem("  "))
        assertEquals(FileNameProblem.TooLong, fileNameProblem("x".repeat(256)))
        assertEquals(FileNameProblem.Separator, fileNameProblem("a/b"))
        assertEquals(FileNameProblem.Separator, fileNameProblem("a\\b"))
        assertEquals(FileNameProblem.Control, fileNameProblem("a\nb"))
        assertNull(fileNameProblem("holiday photo (1).jpg"))
        assertNull(fileNameProblem("x".repeat(255)))
    }

    @Test
    fun changingTheExtensionIsNotedButKeepingItIsNot() {
        assertEquals("JPG", extensionChanged("photo.jpg", "photo.png"))
        assertNull(extensionChanged("photo.jpg", "holiday.jpg"))
        assertNull(extensionChanged("notes", "notes.txt"))
    }

    @Test
    fun theTrashCountsWholeDaysUpAndStopsAtZero() {
        val now = java.time.Instant.parse("2026-10-14T12:00:00Z")
        assertEquals(30, daysLeft("2026-10-14T11:00:00Z", 30, now))
        assertEquals(1, daysLeft("2026-09-14T13:00:00Z", 30, now))
        assertEquals(0, daysLeft("2026-09-14T12:00:00Z", 30, now))
        assertEquals(0, daysLeft("not a date", 30, now))
    }

    @Test
    fun onlyImagesAndPdfsPreviewInTheApp() {
        assertEquals(PreviewKind.Pdf, previewKindOf("application/pdf"))
        assertEquals(PreviewKind.Image, previewKindOf("image/jpeg"))
        assertEquals(PreviewKind.Image, previewKindOf("IMAGE/HEIC; charset=x"))
        assertEquals(PreviewKind.None, previewKindOf("image/svg+xml"))
        assertEquals(PreviewKind.None, previewKindOf("video/mp4"))
        assertEquals(PreviewKind.None, previewKindOf("text/plain"))
        assertEquals(PreviewKind.None, previewKindOf(null))
    }
}
