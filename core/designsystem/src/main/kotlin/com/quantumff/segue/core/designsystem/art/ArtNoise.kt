package com.quantumff.segue.core.designsystem.art

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.theme.SegueAlpha

/**
 * A tiled 64px noise texture at 4% alpha. Every art backdrop gets one on top to stop gradient
 * banding (DESIGN.md §2.4). Size it with [modifier], usually `matchParentSize()`.
 */
@Composable
fun ArtNoise(modifier: Modifier = Modifier) {
    val noise = ImageBitmap.imageResource(R.drawable.art_noise)
    val brush = remember(noise) { ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)) }
    Spacer(modifier.drawBehind { drawRect(brush = brush, alpha = SegueAlpha.ART_NOISE) })
}
