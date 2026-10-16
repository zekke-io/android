package zekke.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import zekke.android.ui.R

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) =
    Font(R.font.nunito_variable, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Nunito = FontFamily(
    nunito(FontWeight.Medium),
    nunito(FontWeight.Normal),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
    nunito(FontWeight.Black),
)

val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Nunito,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

val ZekkeTypography = Typography(
    displaySmall = style(34, 37, FontWeight.Black, -0.03),
    headlineLarge = style(28, 32, FontWeight.Black, -0.025),
    headlineMedium = style(22, 26, FontWeight.Black, -0.02),
    headlineSmall = style(20, 26, FontWeight.ExtraBold, -0.01),
    titleLarge = style(20, 26, FontWeight.ExtraBold, -0.01),
    titleMedium = style(16, 22, FontWeight.ExtraBold),
    titleSmall = style(14, 20, FontWeight.ExtraBold),
    bodyLarge = style(17, 24, FontWeight.SemiBold),
    bodyMedium = style(15, 21, FontWeight.SemiBold),
    bodySmall = style(13, 18, FontWeight.SemiBold),
    labelLarge = style(16, 20, FontWeight.Black),
    labelMedium = style(13, 18, FontWeight.ExtraBold),
    labelSmall = style(11, 14, FontWeight.ExtraBold, 0.08),
)

val MonoStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 18.sp)
