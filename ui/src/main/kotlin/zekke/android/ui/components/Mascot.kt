package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import zekke.android.ui.R

enum class MascotPose { Closed, Open, OpenAppIcon }

@DrawableRes
fun mascotDrawable(pose: MascotPose, onOrange: Boolean): Int = when (pose) {
    MascotPose.Closed -> if (onOrange) R.drawable.mascot_ball_cream else R.drawable.mascot_ball_orange
    MascotPose.Open -> R.drawable.mascot_open
    MascotPose.OpenAppIcon -> R.drawable.mascot_app_open
}

@Composable
fun Mascot(pose: MascotPose, modifier: Modifier = Modifier, onOrange: Boolean = false) {
    Image(
        painter = painterResource(mascotDrawable(pose, onOrange)),
        contentDescription = stringResource(if (pose == MascotPose.Closed) R.string.mascot_closed else R.string.mascot_open),
        modifier = modifier,
    )
}
