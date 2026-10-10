package zekke.android.ui.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import zekke.android.ui.R

@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val masked = !revealed
    PrivateTextInput {
        key(masked) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = privateKeyboard(if (masked) KeyboardType.Password else KeyboardType.Text),
                trailingIcon = {
                    TextButton(onClick = { revealed = !revealed }) {
                        Text(stringResource(if (masked) R.string.secret_show else R.string.secret_hide))
                    }
                },
                modifier = if (masked) modifier.semantics { password() } else modifier,
            )
        }
    }
}

@Composable
fun PrivateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
) {
    PrivateTextInput {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = privateKeyboard(),
            modifier = modifier,
        )
    }
}
