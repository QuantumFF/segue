package com.quantumff.segue.feature.artist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen

// Placeholder. Real UI follows docs/DESIGN.md.
@Composable
fun ArtistScreen(artistId: String, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = "Artist $artistId", modifier = modifier)
}
