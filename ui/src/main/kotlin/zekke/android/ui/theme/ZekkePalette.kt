package zekke.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class ToneColors(val text: Color, val background: Color, val line: Color)

@Immutable
data class FileKindColors(
    val pdf: Color,
    val document: Color,
    val sheet: Color,
    val slides: Color,
    val archive: Color,
    val image: Color,
    val video: Color,
    val audio: Color,
    val code: Color,
)

@Immutable
data class ZekkePalette(
    val isDark: Boolean,
    val brand: Color,
    val brandStrong: Color,
    val brandText: Color,
    val brandTint: Color,
    val brandTintLine: Color,
    val brandMark: Color,
    val brandSoft: Color,
    val accent: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val onGradient: Color,
    val focusRing: Color,
    val scrim: Color,
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val onBrand: Color,
    val success: ToneColors,
    val warning: ToneColors,
    val danger: ToneColors,
    val folderBack: Color,
    val folderFront: Color,
    val folderShine: Color,
    val folderEdge: Color,
    val files: FileKindColors,
) {
    val actionGradient: Brush
        get() = Brush.linearGradient(listOf(gradientStart, gradientEnd))

    val strongBorder: Color
        get() = if (isDark) accent else ink
}

val LightPalette = ZekkePalette(
    isDark = false,
    brand = Color(0xFFFF7A1A),
    brandStrong = Color(0xFFE8590C),
    brandText = Color(0xFF9A3412),
    brandTint = Color(0xFFFFE3C7),
    brandTintLine = Color(0xFFF2D2AE),
    brandMark = Color(0xFFC2410C),
    brandSoft = Color(0xFFFFE3C7),
    accent = Color(0xFF6B4CF0),
    gradientStart = Color(0xFFFF7A1A),
    gradientEnd = Color(0xFFFF9A4A),
    onGradient = Color(0xFF2A1608),
    focusRing = Color(0xFF6B4CF0),
    scrim = Color(0x662A1608),
    ground = Color(0xFFFFF4E8),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFFFE3C7),
    line = Color(0xFFF2DCC6),
    lineStrong = Color(0xFFF2D2AE),
    ink = Color(0xFF2A1608),
    inkSoft = Color(0xFF4A2E1A),
    inkMuted = Color(0xFF6B4A32),
    inkFaint = Color(0xFF8A6A52),
    onBrand = Color(0xFF2A1608),
    success = ToneColors(Color(0xFF14683F), Color(0xFFE6F5EC), Color(0xFFB7E2C8)),
    warning = ToneColors(Color(0xFFFFF4E8), Color(0xFF2A1608), Color(0xFF2A1608)),
    danger = ToneColors(Color(0xFFB42318), Color(0xFFFDECEA), Color(0xFFF6C3BD)),
    folderBack = Color(0xFFE8590C),
    folderFront = Color(0xFFFF7A1A),
    folderShine = Color(0xFFFFB066),
    folderEdge = Color(0xFFFFB066),
    files = FileKindColors(
        pdf = Color(0xFFD93036),
        document = Color(0xFF2F63D6),
        sheet = Color(0xFF1F8A4C),
        slides = Color(0xFFE8590C),
        archive = Color(0xFFC2410C),
        image = Color(0xFF6B4CF0),
        video = Color(0xFF4A3C9A),
        audio = Color(0xFF0E7C6B),
        code = Color(0xFF6B5444),
    ),
)

val DarkPalette = ZekkePalette(
    isDark = true,
    brand = Color(0xFFFF7A1A),
    brandStrong = Color(0xFFFF9A4A),
    brandText = Color(0xFFFFB066),
    brandTint = Color(0xFF3A2E54),
    brandTintLine = Color(0xFF4A3C66),
    brandMark = Color(0xFFFF7A1A),
    brandSoft = Color(0xFF4A3C66),
    accent = Color(0xFF8C73FF),
    gradientStart = Color(0xFFFF7A1A),
    gradientEnd = Color(0xFFFF9A4A),
    onGradient = Color(0xFF2A1608),
    focusRing = Color(0xFFA99BFF),
    scrim = Color(0x990A0612),
    ground = Color(0xFF1C1428),
    surface = Color(0xFF2B2140),
    raised = Color(0xFF3A2E54),
    line = Color(0xFF4A3C66),
    lineStrong = Color(0xFF5B4B7E),
    ink = Color(0xFFFFF4E8),
    inkSoft = Color(0xFFDCCFE6),
    inkMuted = Color(0xFFB9AACB),
    inkFaint = Color(0xFF8F7FA6),
    onBrand = Color(0xFF2A1608),
    success = ToneColors(Color(0xFF6EE7B7), Color(0xFF0F2A22), Color(0xFF1F5A44)),
    warning = ToneColors(Color(0xFF2A1608), Color(0xFFFFB066), Color(0xFFFFB066)),
    danger = ToneColors(Color(0xFFFF9C8F), Color(0xFF3A1A26), Color(0xFF6B2C3A)),
    folderBack = Color(0xFFC2410C),
    folderFront = Color(0xFFFF7A1A),
    folderShine = Color(0xFFFFB066),
    folderEdge = Color(0xFFFFB066),
    files = FileKindColors(
        pdf = Color(0xFFFF8A8F),
        document = Color(0xFF7AA2F7),
        sheet = Color(0xFF5FD18A),
        slides = Color(0xFFFF9A4A),
        archive = Color(0xFFFFB066),
        image = Color(0xFFA99BFF),
        video = Color(0xFFB3A6F0),
        audio = Color(0xFF4FD1BD),
        code = Color(0xFFC9B8A8),
    ),
)

fun ZekkePalette.colorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = brand,
        onPrimary = onBrand,
        primaryContainer = brandTint,
        onPrimaryContainer = brandText,
        inversePrimary = if (isDark) LightPalette.brand else DarkPalette.brand,
        secondary = accent,
        onSecondary = onBrand,
        secondaryContainer = brandTint,
        onSecondaryContainer = brandText,
        tertiary = accent,
        onTertiary = onBrand,
        background = ground,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = raised,
        onSurfaceVariant = inkSoft,
        surfaceTint = brand,
        surfaceBright = surface,
        surfaceDim = ground,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = line,
        inverseSurface = if (isDark) LightPalette.surface else DarkPalette.surface,
        inverseOnSurface = if (isDark) LightPalette.ink else DarkPalette.ink,
        outline = lineStrong,
        outlineVariant = line,
        error = danger.text,
        onError = if (isDark) DarkPalette.danger.background else Color.White,
        errorContainer = danger.background,
        onErrorContainer = danger.text,
        scrim = scrim.copy(alpha = 1f),
    )
}
