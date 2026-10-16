package com.quantumff.segue.core.data.download

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PinEntity
import com.quantumff.segue.core.database.model.PinSongRow
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import org.junit.Test

class DownloadPlannerTest {
    private fun row(songId: String, state: DownloadState, bytes: Long = 0, total: Long? = null) =
        DownloadEntity("s1", songId, state, bytesDownloaded = bytes, totalBytes = total, updatedAt = 0)

    private fun pin(kind: PinKind, id: String) = PinEntity("s1", kind, id, name = id, createdAt = 0)

    @Test
    fun `songs covered by a pin and not yet tracked are queued`() {
        val plan = DownloadPlanner.plan(desired = setOf("a", "b"), existing = setOf("a"))

        assertThat(plan.toQueue).containsExactly("b")
        assertThat(plan.toRemove).isEmpty()
    }

    @Test
    fun `songs no pin covers any more are removed`() {
        val plan = DownloadPlanner.plan(desired = setOf("a"), existing = setOf("a", "b", "c"))

        assertThat(plan.toQueue).isEmpty()
        assertThat(plan.toRemove).containsExactly("b", "c")
    }

    @Test
    fun `a song shared by two pins stays while either pin references it`() {
        val album = listOf(PinSongRow(PinKind.Album, "al", "shared"), PinSongRow(PinKind.Album, "al", "a"))
        val playlist = listOf(PinSongRow(PinKind.Playlist, "p", "shared"))

        val afterUnpinningAlbum = DownloadPlanner.plan(
            desired = DownloadPlanner.desiredSongs(playlist),
            existing = setOf("shared", "a"),
        )
        val afterUnpinningBoth = DownloadPlanner.plan(
            desired = DownloadPlanner.desiredSongs(emptyList()),
            existing = setOf("shared"),
        )

        assertThat(DownloadPlanner.desiredSongs(album + playlist)).containsExactly("shared", "a")
        assertThat(afterUnpinningAlbum.toRemove).containsExactly("a")
        assertThat(afterUnpinningBoth.toRemove).containsExactly("shared")
    }

    @Test
    fun `pin status counts each song once and weighs partial downloads by bytes`() {
        val pins = listOf(pin(PinKind.Playlist, "p"))
        val membership = listOf(
            PinSongRow(PinKind.Playlist, "p", "a"),
            PinSongRow(PinKind.Playlist, "p", "b"),
            PinSongRow(PinKind.Playlist, "p", "a"),
        )
        val rows = mapOf(
            "a" to row("a", DownloadState.Completed, bytes = 100),
            "b" to row("b", DownloadState.Downloading),
        )
        val live = mapOf("b" to LiveProgress(bytes = 50, total = 100))

        val status = DownloadPlanner.pinStatuses(pins, membership, rows, live).single()

        assertThat(status.songCount).isEqualTo(2)
        assertThat(status.completed).isEqualTo(1)
        assertThat(status.progress).isWithin(0.001f).of(0.75f)
        assertThat(status.bytes).isEqualTo(150)
        assertThat(status.status).isEqualTo(DownloadStatus.Downloading(0.75f))
    }

    @Test
    fun `a pin whose songs are all done is downloaded, and one with only failures left has failed`() {
        val pins = listOf(pin(PinKind.Album, "done"), pin(PinKind.Album, "bad"))
        val membership = listOf(
            PinSongRow(PinKind.Album, "done", "a"),
            PinSongRow(PinKind.Album, "bad", "a"),
            PinSongRow(PinKind.Album, "bad", "b"),
        )
        val rows = mapOf("a" to row("a", DownloadState.Completed, bytes = 10), "b" to row("b", DownloadState.Failed))

        val statuses = DownloadPlanner.pinStatuses(pins, membership, rows, emptyMap()).associateBy { it.id }

        assertThat(statuses.getValue("done").status).isEqualTo(DownloadStatus.Downloaded)
        assertThat(statuses.getValue("bad").status).isEqualTo(DownloadStatus.Failed)
    }

    @Test
    fun `a pin whose songs are not in the library yet is queued`() {
        val status = DownloadPlanner.pinStatuses(listOf(pin(PinKind.Album, "x")), emptyList(), emptyMap(), emptyMap())

        assertThat(status.single().status).isEqualTo(DownloadStatus.Queued)
    }
}
