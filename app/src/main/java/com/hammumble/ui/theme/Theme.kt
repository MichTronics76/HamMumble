package com.hammumble.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = HamCyan,
    onPrimary = Color.White,
    primaryContainer = HamCyanDeep,
    onPrimaryContainer = HamCyanLight,
    
    secondary = HamOrange,
    onSecondary = Color.White,
    secondaryContainer = HamOrangeDark,
    onSecondaryContainer = HamOrangeLight,
    
    tertiary = HamGold,
    onTertiary = OnOrangeText,
    tertiaryContainer = HamNavyDark,
    onTertiaryContainer = HamOrangeLight,
    
    background = DarkBackground,
    onBackground = OnDarkText,
    
    surface = DarkSurface,
    onSurface = OnDarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBBBBBB),
    
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFF424242),
    
    error = MutedRed,
    onError = Color.White,
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = HamCyan,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = HamCyanDeep,
    
    secondary = HamOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = HamOrangeDark,
    
    tertiary = HamGold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECB3),
    onTertiaryContainer = OnOrangeText,
    
    background = LightBackground,
    onBackground = OnLightText,
    
    surface = LightSurface,
    onSurface = OnLightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF616161),
    
    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFFE0E0E0),
    
    error = MutedRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun HamMumbleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to use our professional Ham Radio theme
    dynamicColor: Boolean = false,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}