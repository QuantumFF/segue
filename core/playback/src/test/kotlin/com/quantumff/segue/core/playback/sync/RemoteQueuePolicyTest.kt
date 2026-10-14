package com.quantumff.segue.core.playback.sync

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.sync.RemoteQueueDecision.Adopt
import com.quantumff.segue.core.playback.sync.RemoteQueueDecision.Ignore
import com.quantumff.segue.core.playback.sync.RemoteQueueDecision.IgnoreReason
import com.quantumff.segue.core.playback.sync.RemoteQueueDecision.Offer
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class RemoteQueuePolicyTest {
    private val own = "Segue-abcd1234"

    private fun remote(
        ids: List<String> = listOf("1", "2", "3"),
        index: Int = 1,
        changedAt: Long? = 2_000,
        by: String? = "Feishin",
    ) = RemotePlayQueue(
        songs = ids.map {
            Song(id = it, title = "t$it", albumId = null, album = null, artist = null, artistId = null)
        }
            .toImmutableList(),
        currentIndex = index,
        positionMillis = 10_000,
        changedAt = changedAt,
        changedBy = by,
    )

    private fun local(ids: List<String> = listOf("9"), index: Int = 0, updatedAt: Long = 1_000) =
        QueueSnapshot(ids.toImmutableList(), index, positionMillis = 0, updatedAt = updatedAt)

    private fun decide(
        remote: RemotePlayQueue?,
        local: QueueSnapshot? = local(),
        autoAdopt: Boolean = false,
        playing: Boolean = false,
        handled: Long? = null,
    ) = RemoteQueuePolicy.decide(remote, local, own, autoAdopt, playing, handled)

    @Test
    fun `a newer queue from another client is offered`() {
        assertThat(decide(remote())).isEqualTo(Offer)
    }

    @Test
    fun `a newer queue from another client is adopted when the user opted in`() {
        assertThat(decide(remote(), autoAdopt = true)).isEqualTo(Adopt)
    }

    @Test
    fun `auto adopt never interrupts local playback, it offers instead`() {
        assertThat(decide(remote(), autoAdopt = true, playing = true)).isEqualTo(Offer)
    }

    @Test
    fun `our own save coming back is ignored`() {
        assertThat(decide(remote(by = own))).isEqualTo(Ignore(IgnoreReason.OwnSave))
    }

    @Test
    fun `another Segue device is a different client`() {
        assertThat(decide(remote(by = "Segue-ffff0000"))).isEqualTo(Offer)
    }

    @Test
    fun `a remote queue older than the local snapshot is ignored`() {
        assertThat(decide(remote(changedAt = 500))).isEqualTo(Ignore(IgnoreReason.NotNewer))
        assertThat(decide(remote(changedAt = 1_000))).isEqualTo(Ignore(IgnoreReason.NotNewer))
    }

    @Test
    fun `the same queue at the same track is not offered again`() {
        val decision = decide(remote(ids = listOf("9"), index = 0, changedAt = 5_000))
        assertThat(decision).isEqualTo(Ignore(IgnoreReason.SameQueue))
    }

    @Test
    fun `a missing or empty remote queue is ignored`() {
        assertThat(decide(null)).isEqualTo(Ignore(IgnoreReason.Empty))
        assertThat(decide(remote(ids = emptyList(), index = 0))).isEqualTo(Ignore(IgnoreReason.Empty))
    }

    @Test
    fun `a queue the user already dismissed or adopted is not offered again`() {
        assertThat(decide(remote(changedAt = 2_000), handled = 2_000)).isEqualTo(Ignore(IgnoreReason.AlreadyHandled))
        assertThat(decide(remote(changedAt = 3_000), handled = 2_000)).isEqualTo(Offer)
    }

    @Test
    fun `with no local queue any remote queue is offered`() {
        assertThat(decide(remote(changedAt = null), local = null)).isEqualTo(Offer)
    }

    @Test
    fun `without a remote timestamp an existing local queue wins`() {
        assertThat(decide(remote(changedAt = null))).isEqualTo(Ignore(IgnoreReason.NotNewer))
    }
}
