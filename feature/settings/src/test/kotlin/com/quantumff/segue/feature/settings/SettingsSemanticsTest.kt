package com.quantumff.segue.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.model.UserPreferences
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What TalkBack hears in Settings: switches with their state, and the server list as one choice. */
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class SettingsSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        uiState: SettingsUiState = SettingsUiState(),
        actions: SettingsActions = SettingsActions.None,
        section: SettingsSection? = null,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SegueTheme {
                    SettingsContent(
                        uiState = uiState,
                        versionName = "1.0.0",
                        actions = actions,
                        onBackClick = {},
                        section = section,
                    )
                }
            }
        }
    }

    private fun offlineModeRow() = compose.onNode(hasText("Offline mode") and isSwitch)

    @Test
    fun `a switch row is announced as a switch that is off`() {
        show(section = SettingsSection.DownloadsAndStorage)

        offlineModeRow().assertIsToggleable().assertIsOff()
    }

    @Test
    fun `a switch row that is on is announced as on`() {
        show(
            uiState = SettingsUiState(downloads = DownloadPreferences(forceOffline = true)),
            section = SettingsSection.DownloadsAndStorage,
        )

        offlineModeRow().assertIsOn()
    }

    @Test
    fun `toggling a switch row calls its callback with the new value`() {
        val toggled = mutableListOf<Boolean>()
        show(
            actions = SettingsActions(
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onOfflineMode = { toggled += it },
            ),
            section = SettingsSection.DownloadsAndStorage,
        )

        offlineModeRow().performClick()

        assertEquals(listOf(true), toggled)
    }

    @Test
    fun `a switch row that does not apply is announced as disabled`() {
        show(uiState = SettingsUiState(preferences = UserPreferences(replayGainMode = ReplayGainMode.Off)))
        val boost = hasText("Boost quiet tracks") and isSwitch
        compose.onNode(hasScrollAction()).performScrollToNode(boost)

        compose.onNode(boost).assertIsToggleable().assertIsNotEnabled()
    }

    @Test
    fun `server rows are one choice with the active server selected`() {
        show(uiState = twoServers)

        compose.onNode(hasText("Home")).assertIsSelectable().assertIsSelected()
            .assert(isRadioButton).assert(hasParent(serverGroup))
        compose.onNode(hasText("Work")).assertIsSelectable().assertIsNotSelected()
            .assert(isRadioButton).assert(hasParent(serverGroup))
        // Its own group holding just the servers: the list's own collection would hide it from TalkBack.
        compose.onNode(serverGroup and hasAnyChild(hasText("Home"))).onChildren().assertCountEquals(2)
    }

    @Test
    fun `the active server says active once`() {
        show(uiState = twoServers)

        compose.onAllNodesWithContentDescription("Active", substring = true).assertCountEquals(0)
        compose.onNode(hasText("Home")).assert(hasText("Active", substring = true))
    }

    @Test
    fun `choosing a server switches to it`() {
        val switched = mutableListOf<String>()
        show(
            uiState = twoServers,
            actions = SettingsActions(
                onSwitchServer = { switched += it },
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            ),
        )

        compose.onNode(hasText("Work")).performClick()

        assertEquals(listOf("2"), switched)
    }

    @Test
    fun `at large font sizes a row's labels wrap inside the row rather than clip`() {
        show(section = SettingsSection.DownloadsAndStorage, fontScale = 2f)
        val summary = "Use only downloaded music, even with a connection"
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Offline mode") and isSwitch)

        val layout = compose.onNode(hasText(summary), useUnmergedTree = true).textLayout()
        assertTrue("wraps", layout.lineCount > 1)
        assertFalse("clipped", layout.hasVisualOverflow)
        val row = offlineModeRow().getBoundsInRoot()
        val text = compose.onNode(hasText(summary), useUnmergedTree = true).getBoundsInRoot()
        assertTrue("summary inside its row", text.bottom <= row.bottom)
    }

    @Test
    fun `at large font sizes the active server's summary wraps rather than clips`() {
        show(uiState = twoServers, fontScale = 2f)

        val summary = compose.onNode(hasText("Active", substring = true), useUnmergedTree = true)
        val layout = summary.textLayout()
        assertTrue("wraps", layout.lineCount > 1)
        assertFalse("clipped", layout.hasVisualOverflow)
        summary.assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    private companion object {
        val twoServers = SettingsUiState(
            servers = persistentListOf(
                Server("1", "Home", "https://home.example.com", "me"),
                Server("2", "Work", "https://work.example.com", "me"),
            ),
            activeServerId = "1",
        )
        val isRadioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val serverGroup = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.CollectionInfo)
        val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    }
}
