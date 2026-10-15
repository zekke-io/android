package zekke.android.feature.drive

const val FILE_NAME_MAX_CHARACTERS = 80
const val FILE_NAME_LIMIT = 255
const val FILE_EXTENSION_MAX_CHARACTERS = 4

enum class FileKind { Image, Video, Audio, Pdf, Archive, Document, Sheet, Slides, Code, Text, Other }

private val DOCUMENT_TYPES = Regex("msword|wordprocessingml|opendocument\\.text|rtf|epub")
private val SHEET_TYPES = Regex("ms-excel|spreadsheetml|opendocument\\.spreadsheet|csv")
private val SLIDES_TYPES = Regex("ms-powerpoint|presentationml|opendocument\\.presentation")
private val ARCHIVE_TYPES = Regex("zip|tar|gzip|bzip|rar|7z-compressed|x-xz")
private val CODE_TYPES = Regex("javascript|typescript|json|xml|x-sh|x-python|x-c|x-java|yaml|sql|wasm")

fun fileKindOf(mime: String): FileKind {
    val type = mime.lowercase()
    return when {
        type == "application/pdf" -> FileKind.Pdf
        DOCUMENT_TYPES.containsMatchIn(type) -> FileKind.Document
        SHEET_TYPES.containsMatchIn(type) -> FileKind.Sheet
        SLIDES_TYPES.containsMatchIn(type) -> FileKind.Slides
        type.startsWith("image/") -> FileKind.Image
        type.startsWith("video/") -> FileKind.Video
        type.startsWith("audio/") -> FileKind.Audio
        ARCHIVE_TYPES.containsMatchIn(type) -> FileKind.Archive
        CODE_TYPES.containsMatchIn(type) -> FileKind.Code
        type.startsWith("text/") -> FileKind.Text
        else -> FileKind.Other
    }
}

fun fileExtensionOf(name: String): String {
    val base = name.trim().substringAfterLast('/')
    val cut = base.lastIndexOf('.')
    if (cut <= 0 || cut == base.length - 1) return ""
    val extension = base.substring(cut + 1)
    return if (extension.all { it.isLetterOrDigit() && it.code < 128 } && extension.length <= FILE_EXTENSION_MAX_CHARACTERS) extension.uppercase() else ""
}

fun shortFileName(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val points = trimmed.codePointCount(0, trimmed.length)
    if (points <= FILE_NAME_MAX_CHARACTERS) return trimmed
    val end = trimmed.offsetByCodePoints(0, FILE_NAME_MAX_CHARACTERS)
    return trimmed.substring(0, end).trimEnd() + "…"
}

enum class FileNameProblem { Empty, TooLong, Separator, Control }

fun fileNameProblem(name: String): FileNameProblem? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> FileNameProblem.Empty
        trimmed.codePointCount(0, trimmed.length) > FILE_NAME_LIMIT -> FileNameProblem.TooLong
        trimmed.any { it == '/' || it == '\\' } -> FileNameProblem.Separator
        trimmed.any { it.isISOControl() } -> FileNameProblem.Control
        else -> null
    }
}

fun extensionChanged(before: String, after: String): String? {
    val was = fileExtensionOf(before)
    val now = fileExtensionOf(after.trim())
    return if (was.isEmpty() || was == now) null else was
}

enum class PreviewKind { Image, Pdf, None }

private val VIEWABLE_IMAGES = setOf(
    "image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/bmp", "image/heic", "image/heif", "image/x-icon", "image/vnd.microsoft.icon",
)

fun previewKindOf(mime: String?): PreviewKind {
    val type = mime?.lowercase()?.substringBefore(';')?.trim() ?: return PreviewKind.None
    return when {
        type == "application/pdf" -> PreviewKind.Pdf
        type in VIEWABLE_IMAGES -> PreviewKind.Image
        else -> PreviewKind.None
    }
}

const val MAX_IMAGE_PREVIEW_BYTES = 48L * 1024 * 1024
