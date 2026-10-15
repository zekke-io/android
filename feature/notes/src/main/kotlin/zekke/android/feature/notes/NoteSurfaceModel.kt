package zekke.android.feature.notes

import zekke.android.ui.components.SurfaceLine
import zekke.android.ui.components.SurfaceLineKind
import zekke.android.ui.components.SurfaceSpan
import zekke.core.notes.CharStyle
import zekke.core.notes.NoteDocument
import zekke.core.notes.NoteLineType

class SurfaceModel(val lines: List<SurfaceLine>, val spans: List<SurfaceSpan>)

fun surfaceModelOf(document: NoteDocument): SurfaceModel {
    val lines = ArrayList<SurfaceLine>()
    val spans = ArrayList<SurfaceSpan>()
    var start = 0
    for (line in document.lines) {
        val kind = when (line.type) {
            NoteLineType.TITLE -> SurfaceLineKind.Title
            NoteLineType.TOPIC -> SurfaceLineKind.Topic
            NoteLineType.TASK -> SurfaceLineKind.Task
            NoteLineType.TEXT -> SurfaceLineKind.Text
        }
        lines += SurfaceLine(kind, line.checked, start)
        var from = 0
        while (from < line.text.length) {
            var to = from + 1
            while (to < line.text.length && line.styles[to] == line.styles[from]) to++
            val style: CharStyle = line.styles[from]
            if (style.bold || style.italic || style.size != null) spans += SurfaceSpan(start + from, start + to, style.bold, style.italic, style.size)
            from = to
        }
        start += line.text.length + 1
    }
    return SurfaceModel(lines, spans)
}
