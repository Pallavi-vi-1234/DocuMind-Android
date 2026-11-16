package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = DocuMindBluePrimary,
    onPrimary = PureWhite,
    primaryContainer = DocuMindBlueContainer,
    onPrimaryContainer = DocuMindOnBlueContainer,
    secondary = DocuMindCyanSecondary,
    onSecondary = PureWhite,
    secondaryContainer = DocuMindCyanContainer,
    onSecondaryContainer = DocuMindOnCyanContainer,
    tertiary = DocuMindBlueLight,
    onTertiary = PureWhite,
    background = Slate50,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    outlineVariant = Slate200
)

private val DarkColorScheme = darkColorScheme(
    primary = DocuMindBlueLight,
    onPrimary = Slate950,
    primaryContainer = DocuMindBlueDark,
    onPrimaryContainer = DocuMindBlueContainer,
    secondary = DocuMindCyanSecondary,
    onSecondary = Slate950,
    secondaryContainer = Color(0xFF034A75),
    onSecondaryContainer = DocuMindCyanContainer,
    tertiary = Color(0xFF93C5FD),
    onTertiary = Slate950,
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111C33),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep clean brand identity by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
