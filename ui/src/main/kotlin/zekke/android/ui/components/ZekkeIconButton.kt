package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

enum class IconButtonTone { Neutral, Danger }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Hinted(hint: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
fun ZekkeIconButton(
    @DrawableRes icon: Int,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = hint,
    framed: Boolean = false,
    tone: IconButtonTone = IconButtonTone.Neutral,
    enabled: Boolean = true,
) {
    val palette = Zekke.palette
    val glyph = if (tone == IconButtonTone.Danger) palette.danger.text else palette.inkSoft
    val border = when {
        tone == IconButtonTone.Danger -> BorderStroke(1.dp, palette.danger.line)
        framed -> BorderStroke(1.dp, palette.line)
        else -> BorderStroke(0.dp, Color.Transparent)
    }
    Hinted(hint) {
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            border = border,
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = if (framed || tone == IconButtonTone.Danger) palette.surface else Color.Transparent,
                contentColor = glyph,
            ),
            modifier = modifier.size(48.dp).semantics { contentDescription = label },
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ZekkeIconToggleButton(
    @DrawableRes icon: Int,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    enabled: Boolean = true,
) {
    val palette = Zekke.palette
    Hinted(hint) {
        OutlinedIconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(if (framed) 1.dp else 0.dp, if (framed) palette.line else Color.Transparent),
            colors = IconButtonDefaults.outlinedIconToggleButtonColors(
                containerColor = if (framed) palette.surface else Color.Transparent,
                contentColor = palette.inkSoft,
                checkedContainerColor = palette.brandTint,
                checkedContentColor = palette.brandText,
            ),
            modifier = modifier.size(48.dp).semantics { contentDescription = hint },
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
