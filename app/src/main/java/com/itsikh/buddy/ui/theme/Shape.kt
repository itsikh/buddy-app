package com.itsikh.buddy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// "The Tactile Playground" — no sharp corners in the sandbox.
//
// Stitch spec mapping:
//   rounded-DEFAULT = 1rem  → 16dp  (inputs, small surfaces)
//   rounded-lg      = 2rem  → 28dp  (cards, bottom sheets)
//   rounded-xl      = 3rem  → 40dp  (large containers)
//   rounded-full    = pill  → 999dp (chips, nav pills, FAB)
val BuddyShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),   // chips, badges, small tags
    small      = RoundedCornerShape(16.dp),   // text fields, snackbars
    medium     = RoundedCornerShape(20.dp),   // cards, dialogs
    large      = RoundedCornerShape(28.dp),   // bottom sheets, large cards
    extraLarge = RoundedCornerShape(40.dp),   // full-screen modals
)
