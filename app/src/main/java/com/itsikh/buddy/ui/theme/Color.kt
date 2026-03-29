package com.itsikh.buddy.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary: Warm Amber-Brown ─────────────────────────────────────────────
// Used for: primary actions, app title, progress indicators
val Primary             = Color(0xFF7B5400)
val OnPrimary           = Color(0xFFFFF1DF)
val PrimaryContainer    = Color(0xFFFEB300)  // golden yellow — main CTA buttons
val OnPrimaryContainer  = Color(0xFF523700)
val PrimaryFixed        = Color(0xFFFEB300)
val PrimaryFixedDim     = Color(0xFFEDA600)

// ── Secondary: Ocean Blue ─────────────────────────────────────────────────
// Used for: exploration, stories, informational elements
val Secondary             = Color(0xFF006384)
val OnSecondary           = Color(0xFFE6F5FF)
val SecondaryContainer    = Color(0xFF97DAFF)
val OnSecondaryContainer  = Color(0xFF004D68)

// ── Tertiary: Coral-Orange ────────────────────────────────────────────────
// Used for: games, energy, call-to-action variation
val Tertiary             = Color(0xFFA83206)
val OnTertiary           = Color(0xFFFFEFEB)
val TertiaryContainer    = Color(0xFFFF9473)
val OnTertiaryContainer  = Color(0xFF5F1600)

// ── Background & Surface ──────────────────────────────────────────────────
// "Sun-Drenched Morning" — warm cream base, no cold greys
val Background              = Color(0xFFFFF6E1)
val OnBackground            = Color(0xFF392E00)
val Surface                 = Color(0xFFFFF6E1)
val OnSurface               = Color(0xFF392E00)
val SurfaceBright           = Color(0xFFFFF6E1)
val SurfaceDim              = Color(0xFFEDD374)
val SurfaceVariant          = Color(0xFFF5DC81)
val OnSurfaceVariant        = Color(0xFF695B23)

// ── Surface Containers (Tonal Sculpting) ──────────────────────────────────
// Layered from lightest → darkest to create depth without shadows
val SurfaceContainerLowest  = Color(0xFFFFFFFF)  // elevated cards ("lifted")
val SurfaceContainerLow     = Color(0xFFFFF0C4)  // secondary content areas
val SurfaceContainer        = Color(0xFFFFE796)  // interactive containers
val SurfaceContainerHigh    = Color(0xFFFAE18C)  // pressed/active state
val SurfaceContainerHighest = Color(0xFFF5DC81)  // high-priority modals

// ── Outline ───────────────────────────────────────────────────────────────
// The "No-Line Rule": outlines only used for focus states, never decoration
val Outline        = Color(0xFF86763B)
val OutlineVariant = Color(0xFFBFAC6C)  // ghost border for inputs (40% opacity)

// ── Error ─────────────────────────────────────────────────────────────────
val Error             = Color(0xFFB02500)
val OnError           = Color(0xFFFFEFEC)
val ErrorContainer    = Color(0xFFF95630)
val OnErrorContainer  = Color(0xFF520C00)

// ── Inverse ───────────────────────────────────────────────────────────────
val InverseSurface    = Color(0xFF130E00)
val InverseOnSurface  = Color(0xFFAE9C5E)
val InversePrimary    = Color(0xFFFEB300)
