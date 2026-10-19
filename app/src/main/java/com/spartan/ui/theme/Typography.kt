package com.spartan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spartan.R

/**
 * One typeface, one scale. Inter (SIL OFL, see docs/licenses) ships as a single variable font, so
 * every weight below is the same file read at a different `wght` axis value.
 *
 * Weights live in the scale, not at call sites: headings are confident and tightly tracked, body is
 * relaxed, labels are compact. A screen should never need its own FontWeight override.
 */
@OptIn(ExperimentalTextApi::class) // variable-font axis settings; stable on every API level we ship
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val SpartanTypography = Typography(
    displayLarge = style(48, 52, 800, -0.03),
    displayMedium = style(40, 44, 800, -0.03),
    displaySmall = style(34, 40, 800, -0.025), // hero numbers
    headlineLarge = style(30, 36, 700, -0.02), // screen titles
    headlineMedium = style(26, 32, 700, -0.02),
    headlineSmall = style(22, 28, 700, -0.015), // section titles on detail pages
    titleLarge = style(20, 26, 600, -0.01),
    titleMedium = style(17, 24, 600, -0.005), // card and row titles
    titleSmall = style(15, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(15, 22, 400),
    bodySmall = style(13, 18, 400),
    labelLarge = style(15, 20, 600), // buttons
    labelMedium = style(13, 18, 600),
    labelSmall = style(11, 14, 700, 0.04), // eyebrows and pills
)
