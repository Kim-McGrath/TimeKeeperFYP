package com.d22127059.timekeeperproto.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentOrange,
    tertiary = SuccessGreen,
    background = BackgroundLight,
    surface = NeutralLight,
    error = ErrorRed,
    onPrimary = TextOnPrimary,
    onSecondary = TextOnPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = TextOnPrimary
)

@Composable
fun TimeKeeperTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}