package com.quantumff.segue.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.quantumff.segue.core.designsystem.theme.SegueTheme

/** Every component preview renders in the dark (default) and light themes. */
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class PreviewSegueThemes

/** Wraps preview content in [SegueTheme] on the `background` color. */
@Composable
fun SeguePreview(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SegueTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
            Box { content() }
        }
    }
}
