package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = TerracottaExpense,
    onError = Color.White,
    errorContainer = TerracottaContainer,
    onErrorContainer = OnTerracottaContainer,
    background = WarmAlabaster,
    onBackground = InkPrimary,
    surface = PureCream,
    onSurface = InkPrimary,
    surfaceVariant = WarmOat,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = PureCream,
    surfaceContainerLow = WarmAlabaster,
    surfaceContainer = WarmOat,
    surfaceContainerHigh = WarmSurfaceHigh,
    outline = WarmOutline,
    outlineVariant = WarmOutlineSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = OnEmeraldDarkPrimary,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = OnEmeraldDarkContainer,
    secondary = Color(0xFF9BB4A9),
    onSecondary = Color(0xFF172C24),
    secondaryContainer = Color(0xFF233830),
    onSecondaryContainer = Color(0xFFD5E8DF),
    tertiary = AmberDark,
    onTertiary = Color(0xFF3E2904),
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = OnAmberDarkContainer,
    error = TerracottaDark,
    onError = Color(0xFF471509),
    errorContainer = TerracottaDarkContainer,
    onErrorContainer = OnTerracottaDarkContainer,
    background = DarkForestBg,
    onBackground = DarkInkPrimary,
    surface = DarkForestSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkForestSurfaceHigh,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainerLowest = DarkForestBg,
    surfaceContainerLow = DarkForestSurface,
    surfaceContainer = DarkForestSurfaceHigh,
    surfaceContainerHigh = Color(0xFF2A3631),
    outline = DarkOutline,
    outlineVariant = Color(0xFF26312D)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SakuHariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
