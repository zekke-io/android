package zekke.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

const val PIN_LENGTH = 6

private val PinBoxShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

fun acceptPinInput(typed: String): String = typed.filter { it in '0'..'9' }.take(PIN_LENGTH)

fun pinCompleted(previous: String, next: String): Boolean = previous.length < PIN_LENGTH && next.length == PIN_LENGTH

@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onComplete: (String) -> Unit = {},
) {
    val palette = Zekke.palette
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.inkSoft)
        PrivateTextInput {
            BasicTextField(
                value = value,
                onValueChange = { typed ->
                    val next = acceptPinInput(typed)
                    onValueChange(next)
                    if (pinCompleted(value, next)) onComplete(next)
                },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = privateKeyboard(KeyboardType.NumberPassword),
                modifier = Modifier.semantics {
                    contentDescription = label
                    password()
                },
                decorationBox = { inner ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(PIN_LENGTH) { index ->
                            val filled = index < value.length
                            val current = index == value.length && enabled
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 60.dp)
                                    .background(palette.surface, PinBoxShape)
                                    .border(
                                        width = 2.dp,
                                        color = when {
                                            current -> palette.brand
                                            filled -> palette.brandSoft
                                            else -> palette.line
                                        },
                                        shape = PinBoxShape,
                                    ),
                            ) {
                                if (filled) Box(Modifier.size(10.dp).background(palette.ink, CircleShape))
                            }
                        }
                    }
                    Box(Modifier.alpha(0f).size(1.dp)) { inner() }
                },
            )
        }
    }
}
