package com.quantumff.segue.feature.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview

/** Placeholder. Real UI follows docs/DESIGN.md §4.9; it also becomes a Library tab. */
@Composable
fun DownloadsScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = stringResource(R.string.feature_downloads_title), onBackClick = onBackClick)
        EmptyState(
            icon = SegueIcons.Download,
            title = stringResource(R.string.feature_downloads_empty_title),
            message = stringResource(R.string.feature_downloads_empty_message),
            modifier = Modifier.weight(1f),
        )
    }
}

@PreviewSegueThemes
@Composable
private fun DownloadsScreenPreview() {
    SeguePreview { DownloadsScreen(onBackClick = {}) }
}
