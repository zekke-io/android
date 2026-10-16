package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

@Composable
fun StepBar(step: Int, total: Int, modifier: Modifier = Modifier) {
    val palette = Zekke.palette
    val label = stringResource(R.string.step_bar_label, step, total)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.widthIn(max = 200.dp).semantics { contentDescription = label },
    ) {
        repeat(total) { index ->
            Segment(done = index < step, palette.brand, palette.line)
        }
    }
}

@Composable
private fun RowScope.Segment(done: Boolean, doneColor: androidx.compose.ui.graphics.Color, todoColor: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .weight(1f)
            .height(6.dp)
            .background(if (done) doneColor else todoColor, RoundedCornerShape(3.dp)),
    )
}
