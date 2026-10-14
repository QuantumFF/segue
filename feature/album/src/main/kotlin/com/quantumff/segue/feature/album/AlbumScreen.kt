package com.quantumff.segue.feature.album

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.HeroDetailLayout
import com.quantumff.segue.core.designsystem.component.HeroHeaderState
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen
import com.quantumff.segue.core.designsystem.component.PlayButton
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview

/** Placeholder. Real UI follows docs/DESIGN.md §4.3. */
@Composable
fun AlbumScreen(
    albumId: String,
    onBackClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroDetailLayout(
        hero = HeroHeaderState(
            overline = stringResource(R.string.feature_album_overline),
            title = stringResource(R.string.feature_album_sample_title, albumId),
            subtitle = stringResource(R.string.feature_album_sample_artist),
            metadata = null,
            coverArtId = null,
        ),
        onBackClick = onBackClick,
        onPlayClick = {},
        onMoreClick = {},
        onSubtitleClick = { onArtistClick(SAMPLE_ARTIST_ID) },
        modifier = modifier,
        heroActions = {
            PlayButton(onClick = {})
            SegueIconButton(
                icon = SegueIcons.Shuffle,
                contentDescription = stringResource(R.string.feature_album_shuffle),
                onClick = {},
            )
            SegueIconButton(
                icon = SegueIcons.Heart,
                contentDescription = stringResource(R.string.feature_album_favorite),
                onClick = {},
            )
            SegueIconButton(
                icon = SegueIcons.Download,
                contentDescription = stringResource(R.string.feature_album_download),
                onClick = {},
            )
        },
    ) {
        item(contentType = "placeholder") {
            PlaceholderScreen(title = stringResource(R.string.feature_album_placeholder))
        }
    }
}

/** Lets the shell's back stacks be exercised before real data exists. */
private const val SAMPLE_ARTIST_ID = "sample-artist"

@PreviewSegueThemes
@Composable
private fun AlbumScreenPreview() {
    SeguePreview { AlbumScreen(albumId = "1", onBackClick = {}, onArtistClick = {}) }
}
