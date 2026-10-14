package com.quantumff.segue.feature.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen

// Placeholder. Real UI follows docs/DESIGN.md.
@Composable
fun PlaylistScreen(playlistId: String, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = "Playlist $playlistId", modifier = modifier)
}
