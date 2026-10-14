package com.quantumff.segue.core.data.sync

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.session.ServerUrl
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncWorkTest {
    private fun info(state: WorkInfo.State, progress: Data = Data.EMPTY, output: Data = Data.EMPTY) =
        WorkInfo(UUID.randomUUID(), state, emptySet(), output, progress)

    @Test
    fun `work states map to sync progress`() {
        assertThat(null.toSyncProgress()).isEqualTo(SyncProgress.Idle)
        assertThat(info(WorkInfo.State.ENQUEUED).toSyncProgress()).isEqualTo(SyncProgress.Pending)
        assertThat(info(WorkInfo.State.SUCCEEDED).toSyncProgress()).isEqualTo(SyncProgress.Succeeded)
        assertThat(info(WorkInfo.State.CANCELLED).toSyncProgress()).isEqualTo(SyncProgress.Idle)
        assertThat(
            info(
                WorkInfo.State.FAILED,
                output = workDataOf(LibrarySyncWorker.KEY_ERROR to "Wrong password"),
            ).toSyncProgress(),
        )
            .isEqualTo(SyncProgress.Failed("Wrong password"))
    }

    @Test
    fun `running progress round-trips through work data`() {
        val running = SyncProgress.Running(SyncPhase.Songs, completed = 120, total = 900)

        assertThat(info(WorkInfo.State.RUNNING, progress = running.toWorkData()).toSyncProgress()).isEqualTo(running)
    }

    @Test
    fun `running without progress yet starts at artists`() {
        assertThat(
            info(WorkInfo.State.RUNNING).toSyncProgress(),
        ).isEqualTo(SyncProgress.Running(SyncPhase.Artists, 0, 0))
    }

    @Test
    fun `server addresses are normalized`() {
        assertThat(ServerUrl.normalize("music.example.com")).isEqualTo("https://music.example.com")
        assertThat(ServerUrl.normalize(" http://192.168.1.2:4533/ ")).isEqualTo("http://192.168.1.2:4533")
        assertThat(
            ServerUrl.normalize("https://example.com/navidrome/rest/"),
        ).isEqualTo("https://example.com/navidrome")
        assertThat(ServerUrl.normalize("https://example.com/navidrome?x=1")).isEqualTo("https://example.com/navidrome")
        assertThrows(IllegalArgumentException::class.java) { ServerUrl.normalize("ftp://example.com") }
        assertThrows(IllegalArgumentException::class.java) { ServerUrl.normalize("") }
    }
}
