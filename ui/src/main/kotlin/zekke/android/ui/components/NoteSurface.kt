package zekke.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

enum class SurfaceLineKind { Text, Title, Topic, Task }

data class SurfaceLine(val kind: SurfaceLineKind, val checked: Boolean, val start: Int)

data class SurfaceSpan(val start: Int, val end: Int, val bold: Boolean, val italic: Boolean, val size: Int?)

private const val DEFAULT_SIZE = 14
private const val TITLE_SIZE = 22
private val GUTTER = 32.dp

private class SurfaceTransformation(
    private val lines: List<SurfaceLine>,
    private val spans: List<SurfaceSpan>,
    private val textLength: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text.text)
        for (span in spans) {
            val end = span.end.coerceAtMost(text.length)
            if (span.start >= end) continue
            styled.addStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontSize = (span.size ?: DEFAULT_SIZE).sp,
                ),
                span.start,
                end,
            )
        }
        lines.forEachIndexed { index, line ->
            if (line.kind != SurfaceLineKind.Title) return@forEachIndexed
            val end = (lines.getOrNull(index + 1)?.start?.minus(1) ?: textLength).coerceAtMost(text.length)
            if (line.start < end) styled.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = TITLE_SIZE.sp), line.start, end)
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun NoteSurface(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    lines: List<SurfaceLine>,
    spans: List<SurfaceSpan>,
    onToggleTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "",
    focusRequester: FocusRequester? = null,
) {
    val palette = Zekke.palette
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val transformation = remember(lines, spans, value.text.length) { SurfaceTransformation(lines, spans, value.text.length) }
    val density = LocalDensity.current
    val doneLabel = stringResource(R.string.note_task_done)
    val openLabel = stringResource(R.string.note_task_open)
    Box(modifier.fillMaxWidth().heightIn(min = 240.dp)) {
        PrivateTextInput {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                keyboardOptions = privateKeyboard(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink, fontSize = DEFAULT_SIZE.sp),
                cursorBrush = SolidColor(palette.brand),
                visualTransformation = transformation,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
                    .padding(start = GUTTER)
                    .semantics { contentDescription = label }
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            )
        }
        val result = layout
        if (result != null) {
            lines.forEachIndexed { index, line ->
                if (line.kind != SurfaceLineKind.Topic && line.kind != SurfaceLineKind.Task) return@forEachIndexed
                val offset = line.start.coerceAtMost(result.layoutInput.text.length)
                val top = result.getLineTop(result.getLineForOffset(offset))
                val lineHeight = result.getLineBottom(result.getLineForOffset(offset)) - top
                val modifierAtLine = Modifier
                    .offset(y = with(density) { top.toDp() })
                    .width(GUTTER)
                    .heightIn(min = with(density) { lineHeight.toDp() })
                if (line.kind == SurfaceLineKind.Topic) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = palette.inkSoft, modifier = modifierAtLine.padding(start = 10.dp))
                } else {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = modifierAtLine
                            .clickable(enabled = enabled, role = Role.Checkbox) { onToggleTask(index) }
                            .semantics { contentDescription = if (line.checked) doneLabel else openLabel }
                            .padding(start = 6.dp),
                    ) {
                        Text(
                            if (line.checked) "☑" else "☐",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (line.checked) palette.brandText else palette.inkSoft,
                            modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
                        )
                    }
                }
            }
        }
    }
}

fun surfaceValue(text: String, selection: TextRange = TextRange(text.length)): TextFieldValue = TextFieldValue(text, selection)
