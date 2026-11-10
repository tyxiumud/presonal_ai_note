package com.example.test0.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.test0.ai.settings.AppSkin

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = PaperHigh,
    primaryContainer = PineLight,
    onPrimaryContainer = PineDeep,
    secondary = Clay,
    onSecondary = PaperHigh,
    secondaryContainer = ClayLight,
    onSecondaryContainer = ClayDeep,
    tertiary = Plum,
    onTertiary = PaperHigh,
    tertiaryContainer = PlumLight,
    onTertiaryContainer = ClayDeep,
    background = Paper,
    onBackground = Ink,
    surface = PaperHigh,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PaperHigh,
    surfaceContainerLow = PaperHigh,
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperDim,
    surfaceContainerHighest = PaperDim,
    outline = Linen,
    outlineVariant = LinenSoft,
    error = Color(0xFFB3261E),
    onError = PaperHigh,
    errorContainer = Color(0xFFFCE8E5),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Ink,
)

/** 薄荷：冷一点的青绿，背景偏通透的白。 */
private val MintColors = LightColors.copy(
    primary = Color(0xFF166B5B),
    primaryContainer = Color(0xFFC4EDE1),
    onPrimaryContainer = Color(0xFF062A22),
    secondary = Color(0xFFB26A3D),
    secondaryContainer = Color(0xFFF7E4D2),
    tertiary = Color(0xFF4C7FA8),
    tertiaryContainer = Color(0xFFD9EBF8),
    background = Color(0xFFF1F8F5),
    onBackground = Color(0xFF172420),
    surface = Color(0xFFFAFEFD),
    onSurface = Color(0xFF172420),
    surfaceVariant = Color(0xFFE1F0EA),
    onSurfaceVariant = Color(0xFF4C615A),
    surfaceContainerLowest = Color(0xFFFAFEFD),
    surfaceContainerLow = Color(0xFFF6FCFA),
    surfaceContainer = Color(0xFFF1F8F5),
    surfaceContainerHigh = Color(0xFFE8F4EF),
    surfaceContainerHighest = Color(0xFFE1F0EA),
    outline = Color(0xFFB9D3C9),
    outlineVariant = Color(0xFFCFE4DB),
    scrim = Color(0xFF172420),
)

/** 藕粉：偏暖的玫瑰色，纸面带一点粉。 */
private val RoseColors = LightColors.copy(
    primary = Color(0xFF8A4A5B),
    primaryContainer = Color(0xFFF6DCE3),
    onPrimaryContainer = Color(0xFF3A1824),
    secondary = Color(0xFFB0683C),
    secondaryContainer = Color(0xFFF7E4D2),
    tertiary = Color(0xFF7A6A9E),
    tertiaryContainer = Color(0xFFEAE2F6),
    background = Color(0xFFFBF5F4),
    onBackground = Color(0xFF2A1F22),
    surface = Color(0xFFFFFBFB),
    onSurface = Color(0xFF2A1F22),
    surfaceVariant = Color(0xFFF3E6E7),
    onSurfaceVariant = Color(0xFF6C565B),
    surfaceContainerLowest = Color(0xFFFFFBFB),
    surfaceContainerLow = Color(0xFFFEF7F7),
    surfaceContainer = Color(0xFFFBF5F4),
    surfaceContainerHigh = Color(0xFFF7EBEC),
    surfaceContainerHighest = Color(0xFFF3E6E7),
    outline = Color(0xFFDCC3C8),
    outlineVariant = Color(0xFFE7D3D6),
    scrim = Color(0xFF2A1F22),
)

private val DarkColors = darkColorScheme(
    primary = PineNight,
    onPrimary = PineNightDeep,
    primaryContainer = PineNightContainer,
    onPrimaryContainer = PineLight,
    secondary = ClayNight,
    onSecondary = ClayNightDeep,
    secondaryContainer = ClayNightContainer,
    onSecondaryContainer = ClayLight,
    tertiary = PlumNight,
    onTertiary = PineNightDeep,
    tertiaryContainer = PlumNightContainer,
    onTertiaryContainer = PlumLight,
    background = Night,
    onBackground = NightInk,
    surface = NightHigh,
    onSurface = NightInk,
    surfaceVariant = NightDim,
    onSurfaceVariant = NightMuted,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightHigh,
    surfaceContainer = NightHigh,
    surfaceContainerHigh = NightDim,
    surfaceContainerHighest = NightDim,
    outline = NightOutline,
    outlineVariant = NightOutlineSoft,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFCE8E5),
    scrim = Night,
)

private val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun Test0Theme(
    skin: AppSkin = AppSkin.PAPER,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (skin) {
        AppSkin.PAPER -> LightColors
        AppSkin.MINT -> MintColors
        AppSkin.ROSE -> RoseColors
        AppSkin.NIGHT -> DarkColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VaultShapes,
        content = content,
    )
}
