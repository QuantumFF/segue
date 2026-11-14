package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sync status bar's progress text under Home's top bar (DESIGN.md §4.11). */
@RunWith(RobolectricTestRunner::class)
class SyncStatusBarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(progress: SyncProgress) {
        compose.setContent { SegueTheme { SyncStatusBar(progress, onRetryClick = {}) } }
    }

    @Test
    fun `a known total shows synced albums against it`() {
        show(SyncProgress.Running(SyncPhase.Albums, completed = 1240, total = 5600))

        compose.onNodeWithText("Syncing library · 1,240 / 5,600 albums").assertExists()
    }

    @Test
    fun `an unknown total shows only the count`() {
        show(SyncProgress.Running(SyncPhase.Albums, completed = 1500, total = 0))

        compose.onNodeWithText("Syncing library · 1,500 albums").assertExists()
        compose.onNodeWithText("/", substring = true).assertDoesNotExist()
    }

    @Test
    fun `one album reads in the singular`() {
        show(SyncProgress.Running(SyncPhase.Albums, completed = 1, total = 0))

        compose.onNodeWithText("Syncing library · 1 album").assertExists()
    }

    @Test
    fun `nothing counted yet shows no numbers`() {
        show(SyncProgress.Running(SyncPhase.Artists, completed = 0, total = 0))

        compose.onNodeWithText("Syncing library").assertExists()
    }
}
