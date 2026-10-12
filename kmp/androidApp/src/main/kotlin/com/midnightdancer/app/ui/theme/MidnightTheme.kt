package com.midnightdancer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.TypographyTokens

fun Long.toComposeColor(): Color = Color(this)

/** Elite display face for all Material typography slots (C-68). */
private val MidnightDisplayFamily = MdFonts.Display

private val MidnightColorScheme = darkColorScheme(
    primary = ColorTokens.NeonOrange.toComposeColor(),
    onPrimary = ColorTokens.OledTrueBlack.toComposeColor(),
    secondary = ColorTokens.NeonCyan.toComposeColor(),
    onSecondary = ColorTokens.OledTrueBlack.toComposeColor(),
    tertiary = ColorTokens.NeonMagenta.toComposeColor(),
    background = ColorTokens.OledTrueBlack.toComposeColor(),
    onBackground = ColorTokens.TextPrimary.toComposeColor(),
    surface = ColorTokens.OledTrueBlack.toComposeColor(),
    onSurface = ColorTokens.TextPrimary.toComposeColor(),
    surfaceVariant = ColorTokens.SurfaceElevated.toComposeColor(),
    onSurfaceVariant = ColorTokens.TextSecondary.toComposeColor(),
    outline = ColorTokens.SurfaceBorder.toComposeColor(),
    error = ColorTokens.Danger.toComposeColor(),
)

private fun typeStyle(token: com.midnightdancer.core.designsystem.TypeScaleToken): TextStyle {
    val weight = when (token.weight) {
        com.midnightdancer.core.designsystem.TypeWeight.Regular -> FontWeight.Normal
        com.midnightdancer.core.designsystem.TypeWeight.Medium -> FontWeight.Medium
        com.midnightdancer.core.designsystem.TypeWeight.SemiBold -> FontWeight.SemiBold
        com.midnightdancer.core.designsystem.TypeWeight.Bold -> FontWeight.Bold
    }
    return TextStyle(
        fontFamily = MidnightDisplayFamily,
        fontWeight = weight,
        fontSize = token.sizeSp.sp,
        lineHeight = token.lineHeightSp.sp,
        color = ColorTokens.TextPrimary.toComposeColor(),
    )
}

private val MidnightTypography = Typography(
    displayLarge = typeStyle(TypographyTokens.DisplayLarge),
    displayMedium = typeStyle(TypographyTokens.DisplayLarge),
    displaySmall = typeStyle(TypographyTokens.TitleLarge),
    headlineLarge = typeStyle(TypographyTokens.TitleLarge),
    headlineMedium = typeStyle(TypographyTokens.TitleLarge),
    headlineSmall = typeStyle(TypographyTokens.TitleMedium),
    titleLarge = typeStyle(TypographyTokens.TitleLarge),
    titleMedium = typeStyle(TypographyTokens.TitleMedium),
    titleSmall = typeStyle(TypographyTokens.TitleMedium),
    bodyLarge = typeStyle(TypographyTokens.BodyLarge),
    bodyMedium = typeStyle(TypographyTokens.BodyMedium),
    bodySmall = typeStyle(TypographyTokens.BodyMedium),
    labelLarge = typeStyle(TypographyTokens.TabLabel),
    labelMedium = typeStyle(TypographyTokens.TabLabel),
    labelSmall = typeStyle(TypographyTokens.LabelSmall),
)

@Composable
fun MidnightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidnightColorScheme,
        typography = MidnightTypography,
        content = content,
    )
}
