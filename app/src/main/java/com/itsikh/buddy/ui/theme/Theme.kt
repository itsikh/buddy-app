package com.itsikh.buddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// "The Tactile Playground" — The Digital Sandbox design system.
// All color tokens sourced from the Google Stitch "Warm Morning" design spec.
// See design/design.md for the full creative direction and usage rules.
private val BuddyColorScheme = lightColorScheme(
    primary                 = Primary,
    onPrimary               = OnPrimary,
    primaryContainer        = PrimaryContainer,
    onPrimaryContainer      = OnPrimaryContainer,
    secondary               = Secondary,
    onSecondary             = OnSecondary,
    secondaryContainer      = SecondaryContainer,
    onSecondaryContainer    = OnSecondaryContainer,
    tertiary                = Tertiary,
    onTertiary              = OnTertiary,
    tertiaryContainer       = TertiaryContainer,
    onTertiaryContainer     = OnTertiaryContainer,
    error                   = Error,
    onError                 = OnError,
    errorContainer          = ErrorContainer,
    onErrorContainer        = OnErrorContainer,
    background              = Background,
    onBackground            = OnBackground,
    surface                 = Surface,
    onSurface               = OnSurface,
    surfaceVariant          = SurfaceVariant,
    onSurfaceVariant        = OnSurfaceVariant,
    outline                 = Outline,
    outlineVariant          = OutlineVariant,
    inverseSurface          = InverseSurface,
    inverseOnSurface        = InverseOnSurface,
    inversePrimary          = InversePrimary,
    surfaceBright           = SurfaceBright,
    surfaceDim              = SurfaceDim,
    surfaceContainerLowest  = SurfaceContainerLowest,
    surfaceContainerLow     = SurfaceContainerLow,
    surfaceContainer        = SurfaceContainer,
    surfaceContainerHigh    = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuddyColorScheme,
        typography  = BuddyTypography,
        shapes      = BuddyShapes,
        content     = content,
    )
}
