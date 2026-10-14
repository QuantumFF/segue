package com.quantumff.segue.feature.album

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen

// Placeholder. Real UI follows docs/DESIGN.md.
@Composable
fun AlbumScreen(albumId: String, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = "Album $albumId", modifier = modifier)
}
