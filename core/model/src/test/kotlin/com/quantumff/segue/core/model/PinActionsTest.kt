package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinActionsTest {
    @Test
    fun `a failed pin offers retry and remove`() {
        assertThat(DownloadStatus.Failed.pinActions).containsExactly(PinAction.Retry, PinAction.Remove).inOrder()
    }

    @Test
    fun `a paused pin offers resume and remove`() {
        assertThat(DownloadStatus.Paused.pinActions).containsExactly(PinAction.Resume, PinAction.Remove).inOrder()
    }

    @Test
    fun `a queued pin offers pause and cancel`() {
        assertThat(DownloadStatus.Queued.pinActions).containsExactly(PinAction.Pause, PinAction.Cancel).inOrder()
    }

    @Test
    fun `a downloading pin offers pause and cancel`() {
        assertThat(DownloadStatus.Downloading(0.4f).pinActions)
            .containsExactly(PinAction.Pause, PinAction.Cancel)
            .inOrder()
    }

    @Test
    fun `a downloaded pin offers only remove`() {
        assertThat(DownloadStatus.Downloaded.pinActions).containsExactly(PinAction.Remove)
    }
}
