package com.d22127059.timekeeperproto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colours ────────────────────────────────────────────────────────────
val Brand       = Color(0xFF10B981)   // Emerald – primary accent
val BrandDim    = Color(0xFF059669)   // Darker emerald for pressed states
val BrandLight  = Color(0xFF6EE7B7)   // Light emerald for highlights

val Amber       = Color(0xFFF59E0B)
val Rose        = Color(0xFFEF4444)

// ── Dark palette ─────────────────────────────────────────────────────────────
val DarkBg          = Color(0xFF0F1923)   // Deep navy-black
val DarkSurface     = Color(0xFF1A2535)   // Slightly lighter surface
val DarkSurface2    = Color(0xFF243044)   // Card surface
val DarkBorder      = Color(0xFF2E3F55)   // Subtle borders
val DarkTextPrimary = Color(0xFFF0F4F8)
val DarkTextSecondary = Color(0xFF8A9BB0)

// ── Light palette ─────────────────────────────────────────────────────────────
val LightBg          = Color(0xFFF0F4F8)
val LightSurface     = Color(0xFFFFFFFF)
val LightSurface2    = Color(0xFFE8EFF7)
val LightBorder      = Color(0xFFCDD5E0)
val LightTextPrimary = Color(0xFF0F1923)
val LightTextSecondary = Color(0xFF4A5568)

// ── Schemes ───────────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary        = Brand,
    onPrimary      = Color.White,
    secondary      = Amber,
    tertiary       = BrandLight,
    background     = DarkBg,
    surface        = DarkSurface,
    surfaceVariant = DarkSurface2,
    onBackground   = DarkTextPrimary,
    onSurface      = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    outline        = DarkBorder,
    error          = Rose
)

private val LightColorScheme = lightColorScheme(
    primary        = BrandDim,
    onPrimary      = Color.White,
    secondary      = Amber,
    tertiary       = Brand,
    background     = LightBg,
    surface        = LightSurface,
    surfaceVariant = LightSurface2,
    onBackground   = LightTextPrimary,
    onSurface      = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    outline        = LightBorder,
    error          = Rose
)

@Composable
fun TimeKeeperTheme(
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}