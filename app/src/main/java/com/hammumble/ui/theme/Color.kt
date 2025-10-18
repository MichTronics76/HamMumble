package com.hammumble.ui.theme

import androidx.compose.ui.graphics.Color

// Ham Radio App Icon Color Palette
// Primary - Cyan/Turquoise (from app icon background)
val HamCyan = Color(0xFF00BCD4)  // Bright cyan from icon
val HamCyanLight = Color(0xFF4DD0E1)  // Lighter cyan
val HamCyanDark = Color(0xFF0097A7)  // Darker cyan
val HamCyanDeep = Color(0xFF006064)  // Deep cyan for dark mode

// Secondary - Orange/Gold (from icon elements)
val HamOrange = Color(0xFFFF9800)  // Vibrant orange from icon
val HamOrangeLight = Color(0xFFFFB74D)  // Light orange
val HamOrangeDark = Color(0xFFF57C00)  // Dark orange
val HamGold = Color(0xFFFFA726)  // Golden orange

// Accent - Navy Blue (from icon outlines)
val HamNavy = Color(0xFF1A237E)  // Deep navy blue
val HamNavyLight = Color(0xFF283593)  // Lighter navy
val HamNavyDark = Color(0xFF0D1642)  // Very dark navy

// Status Colors (using icon palette)
val SpeakingGreen = Color(0xFF26C6DA)  // Cyan-green for speaking
val MutedRed = Color(0xFFEF5350)  // Red for muted
val DeafenedOrange = HamOrange  // Orange for deafened
val PushToTalkBlue = HamCyan  // Cyan for PTT
val ConnectedGreen = Color(0xFF26A69A)  // Teal for connected
val DisconnectedGray = Color(0xFF757575)  // Gray for disconnected

// Background colors with icon theme
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2C2C2C)
val LightBackground = Color(0xFFF5F9FA)  // Light cyan tint
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE0F7FA)  // Very light cyan

// Text colors
val OnDarkText = Color(0xFFE0E0E0)
val OnLightText = Color(0xFF212121)
val OnCyanText = Color(0xFF004D5C)  // Dark text on cyan
val OnOrangeText = Color(0xFF4A2800)  // Dark text on orange