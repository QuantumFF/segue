package com.quantumff.segue.feature.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.art.ArtNoise
import com.quantumff.segue.core.designsystem.art.BlurredArt
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.component.NowPlayingTopBar
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueShadows
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme

/**
 * Placeholder. Real UI follows docs/DESIGN.md §4.7. Background is the default "Blurred art":
 * the pre-blurred bitmap plus a black 30% to 85% gradient and noise.
 */
@Composable
fun NowPlayingScreen(onCollapseClick: () -> Unit, onQueueClick: () -> Unit, modifier: Modifier = Modifier) {
    val coverArtId: String? = null
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BlurredArt(coverArtId = coverArtId, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SegueTheme.colors.artShade.copy(alpha = SegueAlpha.NOW_PLAYING_SHADE_TOP),
                            SegueTheme.colors.artShade.copy(alpha = SegueAlpha.NOW_PLAYING_SHADE_BOTTOM),
                        ),
                    ),
                ),
        )
        ArtNoise(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            NowPlayingTopBar(
                sourceLabel = stringResource(R.string.feature_nowplaying_source_label),
                sourceName = stringResource(R.string.feature_nowplaying_placeholder_source),
                onCollapseClick = onCollapseClick,
                onMoreClick = {},
            )
            val shadowColor = SegueTheme.colors.shadow.copy(alpha = SegueShadows.NOW_PLAYING_ART_ALPHA)
            CoverArt(
                coverArtId = coverArtId,
                displaySize = SegueSizes.nowPlayingArtMax,
                shape = SegueShapes.lg,
                crossfade = false,
                modifier = Modifier
                    .padding(horizontal = SegueSpacing.xl)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(
                        SegueShadows.nowPlayingArt,
                        SegueShapes.lg,
                        ambientColor = shadowColor,
                        spotColor = shadowColor,
                    ),
            )
            Spacer(Modifier.height(SegueSpacing.xl))
            TextButton(
                onClick = onQueueClick,
                colors = ButtonDefaults.textButtonColors(contentColor = SegueTheme.colors.onArt),
            ) {
                Text(stringResource(R.string.feature_nowplaying_open_queue))
            }
        }
    }
}

@PreviewSegueThemes
@Composable
private fun NowPlayingScreenPreview() {
    SeguePreview { NowPlayingScreen(onCollapseClick = {}, onQueueClick = {}) }
}
