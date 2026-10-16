package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

private val KeyShape = RoundedCornerShape(20.dp)
private val PinBoxShapeLarge = RoundedCornerShape(16.dp)
private val KEY_ROWS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", DELETE_KEY))

private const val DELETE_KEY = "del"

fun padPressed(value: String, key: String): String = when {
    key == DELETE_KEY -> value.dropLast(1)
    key.length == 1 && key[0] in '0'..'9' && value.length < PIN_LENGTH -> value + key
    else -> value
}

@Composable
fun PinPad(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onComplete: (String) -> Unit = {},
) {
    val palette = Zekke.palette
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.semantics {
                contentDescription = label
                password()
            },
        ) {
            repeat(PIN_LENGTH) { index ->
                val filled = index < value.length
                val current = index == value.length && enabled
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 48.dp, height = 60.dp)
                        .background(palette.surface, PinBoxShapeLarge)
                        .border(
                            2.dp,
                            when {
                                current -> palette.brand
                                filled -> palette.brandSoft
                                else -> palette.line
                            },
                            PinBoxShapeLarge,
                        ),
                ) {
                    if (filled) Box(Modifier.size(12.dp).background(palette.ink, CircleShape))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            KEY_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        Key(key, enabled) {
                            val next = padPressed(value, key)
                            if (next != value) {
                                onValueChange(next)
                                if (pinCompleted(value, next)) onComplete(next)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(key: String, enabled: Boolean, onPress: () -> Unit) {
    val palette = Zekke.palette
    if (key.isEmpty()) {
        Box(Modifier.weight(1f).height(60.dp))
        return
    }
    val delete = key == DELETE_KEY
    val description = if (delete) stringResource(R.string.pin_pad_delete) else key
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(60.dp)
            .background(if (delete) androidx.compose.ui.graphics.Color.Transparent else palette.surface, KeyShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onPress)
            .semantics { contentDescription = description },
    ) {
        if (delete) {
            Icon(painterResource(R.drawable.ic_pin_delete), contentDescription = null, tint = palette.ink, modifier = Modifier.size(28.dp))
        } else {
            Text(key, style = MaterialTheme.typography.headlineLarge, color = palette.ink)
        }
    }
}
