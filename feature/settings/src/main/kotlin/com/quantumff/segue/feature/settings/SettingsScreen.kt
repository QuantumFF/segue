package com.quantumff.segue.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview

/** Placeholder. Real UI follows docs/DESIGN.md §4.10. */
@Composable
fun SettingsScreen(onBackClick: () -> Unit, onDownloadsClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = stringResource(R.string.feature_settings_title), onBackClick = onBackClick)
        PlaceholderScreen(
            title = stringResource(R.string.feature_settings_placeholder),
            modifier = Modifier.weight(1f),
        ) {
            TextButton(onClick = onDownloadsClick) { Text(stringResource(R.string.feature_settings_open_downloads)) }
        }
    }
}

@PreviewSegueThemes
@Composable
private fun SettingsScreenPreview() {
    SeguePreview { SettingsScreen(onBackClick = {}, onDownloadsClick = {}) }
}
