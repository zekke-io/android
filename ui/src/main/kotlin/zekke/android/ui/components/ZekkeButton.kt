package zekke.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zekke.android.ui.theme.Zekke

enum class ButtonVariant { Primary, Secondary, Ghost, Danger, Accent }

enum class ButtonSize { Regular, Large }

@Composable
fun ZekkeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Regular,
) {
    val palette = Zekke.palette
    val large = size == ButtonSize.Large
    val sized = modifier.defaultMinSize(minHeight = if (large) 58.dp else 48.dp)
    val shape = CircleShape
    val padding = if (large) PaddingValues(horizontal = 28.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding
    val label: @Composable () -> Unit = {
        Text(text, style = MaterialTheme.typography.labelLarge.let { if (large) it.copy(fontSize = 18.sp, lineHeight = 22.sp) else it })
    }
    when (variant) {
        ButtonVariant.Primary, ButtonVariant.Accent -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.brand,
                contentColor = palette.onBrand,
                disabledContainerColor = palette.raised,
                disabledContentColor = palette.inkMuted,
            ),
        ) { label() }
        ButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            contentPadding = padding,
            border = BorderStroke(3.dp, if (enabled) palette.strongBorder else palette.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = palette.ground, contentColor = palette.ink),
        ) { label() }
        ButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = palette.brandText),
        ) { label() }
        ButtonVariant.Danger -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = sized,
            contentPadding = padding,
            border = BorderStroke(3.dp, palette.danger.text),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = palette.ground, contentColor = palette.danger.text),
        ) { label() }
    }
}
