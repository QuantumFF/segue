package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import kotlin.math.roundToInt

/**
 * Five-star rating. Tapping the current rating clears it (Subsonic rating 0). Filled stars use
 * `primary`, the accent's "selected state" role; empty ones are muted.
 *
 * TalkBack reads it as one adjustable control, "Rating, 3 of 5 stars", which a swipe up or down
 * changes (0 clears it); the stars themselves are only for touch, each with a 48dp target.
 */
@Composable
fun RatingBar(rating: Int, onRatingChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.ds_rating)
    val state = stringResource(R.string.ds_rating_stars, rating)
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = label
            stateDescription = state
            progressBarRangeInfo = ProgressBarRangeInfo(rating.toFloat(), 0f..MAX_STARS.toFloat(), MAX_STARS - 1)
            setProgress { value ->
                onRatingChange(value.roundToInt().coerceIn(0, MAX_STARS))
                true
            }
        },
    ) {
        for (star in 1..MAX_STARS) {
            Star(
                filled = star <= rating,
                onClick = { onRatingChange(if (star == rating) 0 else star) },
            )
        }
    }
}

/** One star: drawn in a 40dp circle (its ripple too), touched in a 48dp slot. */
@Composable
private fun Star(filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .touchTargetClickable(interactions, onClick = onClick)
            .size(SegueSizes.overflowButton)
            .clip(CircleShape)
            .indication(interactions, ripple()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (filled) SegueIcons.StarFilled else SegueIcons.Star,
            contentDescription = null,
            tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SegueSizes.iconSmall),
        )
    }
}

private const val MAX_STARS = 5

@PreviewSegueThemes
@Composable
private fun RatingBarPreview() {
    SeguePreview { RatingBar(rating = 3, onRatingChange = {}) }
}
