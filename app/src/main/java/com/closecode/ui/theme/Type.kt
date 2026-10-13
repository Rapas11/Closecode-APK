package com.closecode.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.closecode.R

/**
 * Code font. JetBrains Mono 12 sp with a line height of exactly 20 dp: the gutter, underlines,
 * pills and the lightbulb are all positioned against that number, so it must not drift.
 */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * UI font. Manrope 14 sp base, line height 1.45, weights 400 / 600 / 700.
 */
val Sans = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/**
 * Fallback for the geometric symbols Fira Code does not cover (chevrons, triangles, the
 * circled cross). Kept separate so code text still renders in Fira Code.
 */
val Symbol = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Spec section 3: code line height is fixed at 20 dp and drives all editor geometry. */
val CodeLineHeight = 20.sp

/** Spec section 3: UI base size, with line height 1.45. */
val UiBaseLineHeight = (14 * 1.45).sp

val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = UiBaseLineHeight,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.02 * 16).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = UiBaseLineHeight,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = UiBaseLineHeight,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    ),
)