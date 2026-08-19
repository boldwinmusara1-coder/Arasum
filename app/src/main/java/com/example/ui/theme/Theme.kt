package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TradingViewColorScheme = darkColorScheme(
    primary = TvBlue,
    onPrimary = Color.White,
    primaryContainer = TvBlueContainer,
    onPrimaryContainer = TvBlueText,
    secondary = TvBlue,
    onSecondary = Color.White,
    secondaryContainer = TvSurfaceElevated,
    onSecondaryContainer = TvBlueText,
    tertiary = TvAmber,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF422800),
    onTertiaryContainer = Color(0xFFFFD180),
    error = TvRed,
    onError = Color.White,
    errorContainer = Color(0xFF5C1019),
    onErrorContainer = Color(0xFFFFCDD2),
    background = TvBackground,
    onBackground = TvTextPrimary,
    surface = TvSurface,
    onSurface = TvTextPrimary,
    surfaceVariant = TvSurfaceElevated,
    onSurfaceVariant = TvTextSecondary,
    outline = TvBorder
)

val TradingViewShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TradingViewColorScheme,
        typography = Typography,
        shapes = TradingViewShapes,
        content = content
    )
}
