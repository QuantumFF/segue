package com.quantumff.segue.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.quantumff.segue.core.designsystem.R

@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * Inter variable font (OFL, see third_party/inter). Each weight is one instance of the variable
 * font. Glyphs Inter lacks (CJK and others) fall back to the system sans.
 */
val InterFontFamily = FontFamily(
    interWeight(400),
    interWeight(500),
    interWeight(600),
    interWeight(700),
    interWeight(800),
)

private val ExtraBold = FontWeight.ExtraBold
private val Bold = FontWeight.Bold
private val SemiBold = FontWeight.SemiBold
private val Medium = FontWeight.Medium
private val Regular = FontWeight.Normal

private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.em,
    lineHeightStyle = TrimmedLineHeight,
)

/** DESIGN.md §2.2. */
internal val SegueTypography = Typography(
    displayLarge = style(size = 34, lineHeight = 40, weight = ExtraBold),
    displayMedium = style(size = 34, lineHeight = 40, weight = ExtraBold),
    displaySmall = style(size = 34, lineHeight = 40, weight = ExtraBold),
    headlineLarge = style(size = 28, lineHeight = 34, weight = ExtraBold),
    headlineMedium = style(size = 28, lineHeight = 34, weight = ExtraBold),
    headlineSmall = style(size = 24, lineHeight = 30, weight = ExtraBold),
    titleLarge = style(size = 20, lineHeight = 26, weight = Bold),
    titleMedium = style(size = 16, lineHeight = 22, weight = SemiBold),
    titleSmall = style(size = 14, lineHeight = 20, weight = Medium),
    bodyLarge = style(size = 16, lineHeight = 24, weight = Regular),
    bodyMedium = style(size = 14, lineHeight = 20, weight = Regular),
    bodySmall = style(size = 13, lineHeight = 18, weight = Regular),
    labelLarge = style(size = 14, lineHeight = 20, weight = SemiBold),
    labelMedium = style(size = 12, lineHeight = 16, weight = Medium),
    labelSmall = style(size = 11, lineHeight = 14, weight = SemiBold, letterSpacing = 0.08f),
)

/** Durations, times, track numbers and counts use tabular figures. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Now Playing title: `titleLarge` at 22/28 (DESIGN.md §2.2). */
val Typography.nowPlayingTitle: TextStyle
    get() = titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp)
