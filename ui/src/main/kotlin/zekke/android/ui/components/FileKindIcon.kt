package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.Nunito

private const val PAGE_WIDTH = 96f
private const val PAGE_HEIGHT = 120f
private const val BADGE_LEFT = 2f
private const val BADGE_TOP = 80f
private const val BADGE_HEIGHT = 26f
private const val BADGE_FONT = 15f
private const val LABEL_MIN_WIDTH_DP = 28f
private val OnLight = Color(0xFF2A1608)

enum class FileKind(
    @param:DrawableRes val drawable: Int,
    val width: Float,
    val height: Float,
    val label: String? = null,
    val badgeWidth: Float = 45f,
    val labelColor: Color = Color.White,
) {
    Folder(R.drawable.file_folder, 128f, 106f),
    FolderEmpty(R.drawable.file_folder_empty, 128f, 106f),
    Docx(R.drawable.file_docx, PAGE_WIDTH, PAGE_HEIGHT, "DOCX", 56f),
    Doc(R.drawable.file_doc, PAGE_WIDTH, PAGE_HEIGHT, "DOC"),
    Xlsx(R.drawable.file_xlsx, PAGE_WIDTH, PAGE_HEIGHT, "XLSX", 56f),
    Xls(R.drawable.file_xlsx, PAGE_WIDTH, PAGE_HEIGHT, "XLS", 56f),
    Csv(R.drawable.file_csv, PAGE_WIDTH, PAGE_HEIGHT, "CSV"),
    Txt(R.drawable.file_txt, PAGE_WIDTH, PAGE_HEIGHT, "TXT"),
    Pdf(R.drawable.file_pdf, PAGE_WIDTH, PAGE_HEIGHT, "PDF"),
    Zip(R.drawable.file_zip, PAGE_WIDTH, PAGE_HEIGHT, "ZIP", labelColor = OnLight),
    Photo(R.drawable.file_photo, 128f, 96f),
    Video(R.drawable.file_video, 128f, 96f),
    Other(R.drawable.file_other, PAGE_WIDTH, PAGE_HEIGHT),
}

private val EXTENSIONS = mapOf(
    "docx" to FileKind.Docx,
    "doc" to FileKind.Doc,
    "xlsx" to FileKind.Xlsx,
    "xls" to FileKind.Xls,
    "csv" to FileKind.Csv,
    "txt" to FileKind.Txt,
    "pdf" to FileKind.Pdf,
    "zip" to FileKind.Zip,
    "jpg" to FileKind.Photo,
    "jpeg" to FileKind.Photo,
    "png" to FileKind.Photo,
    "gif" to FileKind.Photo,
    "webp" to FileKind.Photo,
    "heic" to FileKind.Photo,
    "heif" to FileKind.Photo,
    "avif" to FileKind.Photo,
    "mp4" to FileKind.Video,
    "mov" to FileKind.Video,
    "mkv" to FileKind.Video,
    "webm" to FileKind.Video,
    "m4v" to FileKind.Video,
)

fun fileKindOf(name: String?): FileKind {
    val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return EXTENSIONS[extension] ?: FileKind.Other
}

@Composable
fun FileKindIcon(kind: FileKind, height: Dp, modifier: Modifier = Modifier) {
    val unit = height.value / kind.height
    val width = (kind.width * unit).dp
    Box(modifier.size(width, height)) {
        Image(painterResource(kind.drawable), contentDescription = null, modifier = Modifier.size(width, height))
        val label = kind.label
        if (label != null && width.value >= LABEL_MIN_WIDTH_DP) {
            val fontSize = with(LocalDensity.current) { (BADGE_FONT * unit).dp.toSp() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.offset((BADGE_LEFT * unit).dp, (BADGE_TOP * unit).dp).size((kind.badgeWidth * unit).dp, (BADGE_HEIGHT * unit).dp),
            ) {
                Text(label, style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = fontSize, color = kind.labelColor), maxLines = 1, softWrap = false)
            }
        }
    }
}
