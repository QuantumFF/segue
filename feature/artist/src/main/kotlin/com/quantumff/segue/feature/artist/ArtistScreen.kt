package com.quantumff.segue.feature.artist

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Placeholder. Real UI follows docs/DESIGN.md §4.4. */
@Composable
fun ArtistScreen(
    artistId: String,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroDetailLayout(
        hero = HeroHeaderState(
            overline = stringResource(R.string.feature_artist_overline),
            title = stringResource(R.string.feature_artist_sample_title, artistId),
            subtitle = null,
            metadata = null,
            coverArtId = null,
            circularArt = true,
        ),
        onBackClick = onBackClick,
        onPlayClick = {},
        onMoreClick = {},
        modifier = modifier,
        heroActions = {
            PlayButton(onClick = {})
            SegueIconButton(
                icon = SegueIcons.Shuffle,
                contentDescription = stringResource(R.string.feature_artist_shuffle),
                onClick = {},
            )
            SegueIconButton(
                icon = SegueIcons.ShuffleAlbums,
                contentDescription = stringResource(R.string.feature_artist_shuffle_albums),
                onClick = {},
            )
            SegueIconButton(
                icon = SegueIcons.Related,
                contentDescription = stringResource(R.string.feature_artist_instant_mix),
                onClick = {},
            )
        },
    ) {
        item(contentType = "placeholder") {
            PlaceholderScreen(title = stringResource(R.string.feature_artist_placeholder)) {
                TextButton(onClick = { onAlbumClick(SAMPLE_ALBUM_ID) }) {
                    Text(stringResource(R.string.feature_artist_open_sample_album))
                }
            }
        }
    }
}

/** Lets the shell's back stacks be exercised before real data exists. */
private const val SAMPLE_ALBUM_ID = "sample-album"

@PreviewSegueThemes
@Composable
private fun ArtistScreenPreview() {
    SeguePreview { ArtistScreen(artistId = "1", onBackClick = {}, onAlbumClick = {}) }
}
