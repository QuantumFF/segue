package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes

/**
 * Five-star rating. Tapping the current rating clears it (Subsonic rating 0). Filled stars use
 * `primary`, the accent's "selected state" role; empty ones are muted.
 */
@Composable
fun RatingBar(rating: Int, onRatingChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.ds_rating_stars, rating)
    Row(modifier.selectableGroup().semantics { contentDescription = label }) {
        for (star in 1..MAX_STARS) {
            val filled = star <= rating
            IconButton(
                onClick = { onRatingChange(if (star == rating) 0 else star) },
                modifier = Modifier.size(SegueSizes.overflowButton),
            ) {
                Icon(
                    imageVector = if (filled) SegueIcons.StarFilled else SegueIcons.Star,
                    contentDescription = stringResource(R.string.ds_rating_stars, star),
                    tint = if (filled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
        }
    }
}

private const val MAX_STARS = 5

@PreviewSegueThemes
@Composable
private fun RatingBarPreview() {
    SeguePreview { RatingBar(rating = 3, onRatingChange = {}) }
}
