package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

data class AddOption(val label: String, @param:DrawableRes val icon: Int?, val onSelect: () -> Unit)

@Composable
fun FloatingAddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, open: Boolean = false, enabled: Boolean = true) {
    val palette = Zekke.palette
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        containerColor = Color.Transparent,
        contentColor = palette.onGradient,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 3.dp),
        modifier = modifier
            .size(56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(palette.actionGradient, CircleShape)
            .semantics { contentDescription = label },
    ) {
        Icon(
            painterResource(R.drawable.ic_plus),
            contentDescription = null,
            modifier = Modifier.size(24.dp).rotate(if (open) 45f else 0f),
        )
    }
}

@Composable
fun ExtendedAddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = Zekke.palette
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        containerColor = palette.brand,
        contentColor = palette.onBrand,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 3.dp),
        modifier = modifier.height(56.dp).alpha(if (enabled) 1f else 0.5f).semantics { contentDescription = label },
    ) {
        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
fun FloatingAddMenu(label: String, options: List<AddOption>, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = Zekke.palette
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        FloatingAddButton(label = label, onClick = { open = !open }, open = open, enabled = enabled)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = palette.surface,
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider(color = palette.line)
                DropdownMenuItem(
                    text = { Text(option.label, style = MaterialTheme.typography.titleSmall, color = palette.inkSoft) },
                    leadingIcon = option.icon?.let { icon ->
                        { Icon(painterResource(icon), contentDescription = null, tint = palette.inkSoft, modifier = Modifier.size(20.dp)) }
                    },
                    onClick = {
                        open = false
                        option.onSelect()
                    },
                )
            }
        }
    }
}
