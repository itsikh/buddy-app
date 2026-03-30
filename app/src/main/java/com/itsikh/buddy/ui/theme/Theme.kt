package com.itsikh.buddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class AppTheme(val label: String, val emoji: String) {
    PURPLE("סגול",  "🟣"),
    AMBER ("ענבר",  "🟡"),
    OCEAN ("ים",    "🔵"),
    FOREST("יער",   "🌿"),
    ROSE  ("ורוד",  "🌸"),
}

fun colorSchemeFor(theme: AppTheme) = when (theme) {
    AppTheme.PURPLE -> PurpleScheme
    AppTheme.AMBER  -> AmberScheme
    AppTheme.OCEAN  -> OceanScheme
    AppTheme.FOREST -> ForestScheme
    AppTheme.ROSE   -> RoseScheme
}

@Composable
fun BuddyTheme(theme: AppTheme = AppTheme.PURPLE, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorSchemeFor(theme),
        typography  = BuddyTypography,
        shapes      = BuddyShapes,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
