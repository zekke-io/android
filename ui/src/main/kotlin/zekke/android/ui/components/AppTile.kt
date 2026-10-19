package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

val AppTileShape = RoundedCornerShape(22.dp)

enum class AppTileStyle { Peach, Brand, Amber, Ink }

private val PeachFill = Color(0xFFFFB066)
private val AmberFill = Color(0xFFFF9A4A)
private val OnOrange = Color(0xFF2A1608)

@Composable
fun AppTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppTileStyle = AppTileStyle.Brand,
    description: String? = null,
    size: Dp = 72.dp,
) {
    val palette = Zekke.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when (style) {
        AppTileStyle.Peach -> PeachFill
        AppTileStyle.Brand -> palette.brand
        AppTileStyle.Amber -> AmberFill
        AppTileStyle.Ink -> if (palette.isDark) palette.accent else palette.ink
    }
    val glyph = when (style) {
        AppTileStyle.Ink -> if (palette.isDark) Color.White else palette.ground
        else -> OnOrange
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .scale(if (pressed) 0.95f else 1f)
                .background(fill, AppTileShape),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = glyph, modifier = Modifier.size(size * 0.47f))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = palette.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
