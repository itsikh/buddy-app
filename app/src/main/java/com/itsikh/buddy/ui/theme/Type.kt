package com.itsikh.buddy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Font Families ─────────────────────────────────────────────────────────
// Design spec: "Plus Jakarta Sans" (display/headline) + "Be Vietnam Pro" (body/label)
//
// To enable the real custom fonts, add downloadable fonts via Android Studio:
//   File → New → More → Downloadable Font, search "Plus Jakarta Sans" and "Be Vietnam Pro"
// Android Studio will generate the res/font/ entries and font_certs.xml automatically.
// Then replace HeadlineFontFamily / BodyFontFamily with the generated FontFamily objects.
//
// Both fonts are on Google Fonts and available via the GMS fonts provider.
val HeadlineFontFamily: FontFamily = FontFamily.SansSerif  // → Plus Jakarta Sans
val BodyFontFamily: FontFamily     = FontFamily.SansSerif  // → Be Vietnam Pro

val BuddyTypography = Typography(
    // ── Display — celebratory moments: "You Won!", level-up screens ────
    displayLarge  = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.Black,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.Black,
        fontSize = 45.sp, lineHeight = 52.sp
    ),
    displaySmall  = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp, lineHeight = 44.sp
    ),

    // ── Headlines — screen titles, card signposts ──────────────────────
    headlineLarge  = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp, lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 36.sp
    ),
    headlineSmall  = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp
    ),

    // ── Titles — card headers, section labels ──────────────────────────
    titleLarge  = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall  = TextStyle(
        fontFamily = HeadlineFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),

    // ── Body — readable text for kids (Be Vietnam Pro: high x-height) ──
    bodyLarge  = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp
    ),
    bodySmall  = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp
    ),

    // ── Labels — buttons, chips, nav items (uppercase tracking) ────────
    labelLarge  = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall  = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
)
