package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/** How a [SectionHeader] links to the full list. */
enum class SectionHeaderTrailing {
    /** A muted "See all" text button. */
    SeeAll,

    /** A 24 chevron. */
    Chevron,
}

/**
 * Section header (DESIGN.md §5.3): 48 tall, horizontal padding 16, `titleLarge`. [onSeeAllClick]
 * adds the trailing link; [extraIcon] adds one more action before it (the Explore refresh).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
    trailing: SectionHeaderTrailing = SectionHeaderTrailing.SeeAll,
    extraIcon: ImageVector? = null,
    extraIconDescription: String? = null,
    onExtraIconClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.sectionHeader)
            .padding(start = SegueSpacing.lg, end = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (extraIcon != null) {
            IconButton(onClick = onExtraIconClick) {
                Icon(
                    imageVector = extraIcon,
                    contentDescription = extraIconDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
        }
        if (onSeeAllClick != null) {
            when (trailing) {
                SectionHeaderTrailing.SeeAll -> TextButton(
                    onClick = onSeeAllClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(stringResource(R.string.ds_see_all), style = MaterialTheme.typography.labelLarge)
                }

                SectionHeaderTrailing.Chevron -> IconButton(onClick = onSeeAllClick) {
                    Icon(
                        imageVector = SegueIcons.ChevronRight,
                        contentDescription = stringResource(R.string.ds_see_all),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SegueSizes.icon),
                    )
                }
            }
        }
    }
}

@PreviewSegueThemes
@Composable
private fun SectionHeaderPreview() {
    SeguePreview {
        Column {
            SectionHeader(title = "Recently played", onSeeAllClick = {})
            SectionHeader(title = "Recently added", onSeeAllClick = {}, trailing = SectionHeaderTrailing.Chevron)
            SectionHeader(
                title = "Explore",
                onSeeAllClick = {},
                extraIcon = SegueIcons.Refresh,
                extraIconDescription = stringResource(R.string.ds_refresh),
            )
        }
    }
}
