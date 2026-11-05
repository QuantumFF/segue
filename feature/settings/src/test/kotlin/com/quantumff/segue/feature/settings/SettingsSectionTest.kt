package com.quantumff.segue.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.model.UserPreferences
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings opened at a group, as the offline chip opens it at Downloads & storage. */
@Config(qualifiers = "w360dp-h640dp")
@RunWith(RobolectricTestRunner::class)
class SettingsSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(section: SettingsSection?, uiState: SettingsUiState = SettingsUiState()) {
        compose.setContent {
            SegueTheme {
                SettingsContent(
                    uiState = uiState,
                    versionName = "1.0.0",
                    actions = SettingsActions.None,
                    onBackClick = {},
                    section = section,
                )
            }
        }
    }

    @Test
    fun `settings open at the top by default`() {
        show(section = null)

        compose.onNodeWithText("Servers").assertIsDisplayed()
        compose.onNodeWithText("Offline mode").assertIsNotDisplayed()
    }

    @Test
    fun `opened at downloads and storage, that group heads the screen with offline mode in it`() {
        show(
            section = SettingsSection.DownloadsAndStorage,
            uiState = SettingsUiState(
                servers = persistentListOf(Server("1", "Home", "https://music.example.com", "me")),
                preferences = UserPreferences(accentSource = AccentSource.Fixed),
            ),
        )

        compose.onNodeWithText("Downloads & storage").assertIsDisplayed()
        compose.onNodeWithText("Offline mode").assertIsDisplayed()
        // The row just above the group has scrolled away.
        compose.onNodeWithText("Mobile data quality").assertIsNotDisplayed()
    }
}
