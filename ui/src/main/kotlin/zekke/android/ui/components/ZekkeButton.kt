package zekke.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

enum class ButtonVariant { Primary, Secondary, Ghost, Danger, Accent }

@Composable
fun ZekkeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val palette = Zekke.palette
    val sized = modifier.defaultMinSize(minHeight = 48.dp)
    val shape = MaterialTheme.shapes.small
    val label: @Composable () -> Unit = { Text(text, style = MaterialTheme.typography.labelLarge) }
    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            colors = ButtonDefaults.buttonColors(containerColor = palette.brand, contentColor = palette.onBrand),
        ) { label() }
        ButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            border = BorderStroke(1.dp, palette.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = palette.surface, contentColor = palette.inkSoft),
        ) { label() }
        ButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            colors = ButtonDefaults.textButtonColors(contentColor = palette.brandText),
        ) { label() }
        ButtonVariant.Danger -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            border = BorderStroke(1.dp, palette.danger.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = palette.surface, contentColor = palette.danger.text),
        ) { label() }
        ButtonVariant.Accent -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized
                .alpha(if (enabled) 1f else 0.5f)
                .background(palette.actionGradient, shape),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = palette.onGradient,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = palette.onGradient,
            ),
        ) { label() }
    }
}
