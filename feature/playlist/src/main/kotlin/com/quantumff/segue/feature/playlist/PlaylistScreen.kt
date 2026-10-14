package com.quantumff.segue.feature.playlist

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

/** Placeholder. Real UI follows docs/DESIGN.md §4.5. */
@Composable
fun PlaylistScreen(playlistId: String, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    HeroDetailLayout(
        hero = HeroHeaderState(
            overline = stringResource(R.string.feature_playlist_overline),
            title = stringResource(R.string.feature_playlist_sample_title, playlistId),
            subtitle = null,
            metadata = null,
            coverArtId = null,
        ),
        onBackClick = onBackClick,
        onPlayClick = {},
        onMoreClick = {},
        modifier = modifier,
        heroActions = {
            PlayButton(onClick = {})
            SegueIconButton(
                icon = SegueIcons.Shuffle,
                contentDescription = stringResource(R.string.feature_playlist_shuffle),
                onClick = {},
            )
            SegueIconButton(
                icon = SegueIcons.ShuffleAlbums,
                contentDescription = stringResource(R.string.feature_playlist_shuffle_albums),
                onClick = {},
            )
            SegueIconButton(
                icon = SegueIcons.Download,
                contentDescription = stringResource(R.string.feature_playlist_download),
                onClick = {},
            )
        },
    ) {
        item(contentType = "placeholder") {
            PlaceholderScreen(title = stringResource(R.string.feature_playlist_placeholder))
        }
    }
}

@PreviewSegueThemes
@Composable
private fun PlaylistScreenPreview() {
    SeguePreview { PlaylistScreen(playlistId = "1", onBackClick = {}) }
}
