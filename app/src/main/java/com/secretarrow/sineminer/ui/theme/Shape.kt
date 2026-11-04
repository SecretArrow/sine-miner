package com.secretarrow.sineminer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SineMiner shape system. Modern, slightly rounded corners for a friendly feel
 * without looking like a toy.
 *
 * Extra small: chips, small buttons (4dp)
 * Small: text fields, cards (8dp)
 * Medium: dialogs, bottom sheets (16dp)
 * Large: hero cards (24dp)
 * Extra large: full-screen sheets (32dp)
 */
val SineMinerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
