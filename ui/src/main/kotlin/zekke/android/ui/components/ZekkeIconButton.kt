package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

enum class IconButtonTone { Neutral, Danger, Ink, Soft }

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
    iconSize: Dp? = null,
    dot: Boolean = false,
) {
    val palette = Zekke.palette
    val glyph = when (tone) {
        IconButtonTone.Danger -> palette.danger.text
        IconButtonTone.Ink -> if (palette.isDark) Color.White else palette.ground
        IconButtonTone.Soft -> palette.ink
        IconButtonTone.Neutral -> palette.inkSoft
    }
    val filled = when (tone) {
        IconButtonTone.Ink -> if (palette.isDark) palette.accent else palette.ink
        IconButtonTone.Soft -> palette.raised
        IconButtonTone.Danger -> palette.surface
        IconButtonTone.Neutral -> if (framed) palette.surface else Color.Transparent
    }
    val border = when {
        tone == IconButtonTone.Danger -> BorderStroke(1.dp, palette.danger.line)
        framed -> BorderStroke(1.dp, palette.line)
        else -> BorderStroke(0.dp, Color.Transparent)
    }
    Hinted(hint) {
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            shape = if (tone == IconButtonTone.Ink || tone == IconButtonTone.Soft) CircleShape else MaterialTheme.shapes.small,
            border = border,
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = filled,
                contentColor = glyph,
            ),
            modifier = modifier.size(48.dp).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize ?: if (tone == IconButtonTone.Soft) 24.dp else 20.dp))
                if (dot) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(10.dp).background(palette.brand, CircleShape))
                }
            }
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
