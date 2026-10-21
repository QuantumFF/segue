package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PlayQueueEndpointsTest {
    @get:Rule
    val rule = SubsonicServerRule()

    @Test
    fun `getPlayQueue decodes the current id, position in ms, and who changed it`() = runTest {
        rule.enqueueFixture("play_queue.json")

        val queue = rule.client.getPlayQueue()!!

        assertThat(rule.takeRequest().url.encodedPath).endsWith("/rest/getPlayQueue.view")
        assertThat(
            queue.entry.map {
                it.id
            },
        ).containsExactly("5pTq9vU2mHB4kGiMDw1z1T", "1gQ0k9XnH2a7sYw3bL5mFe").inOrder()
        assertThat(queue.current).isEqualTo("1gQ0k9XnH2a7sYw3bL5mFe")
        assertThat(queue.position).isEqualTo(73_500)
        assertThat(queue.changed).isEqualTo("2024-09-12T18:30:00.25Z")
        assertThat(queue.changedBy).isEqualTo("Feishin")
    }

    @Test
    fun `getPlayQueue without a saved queue returns null`() = runTest {
        rule.enqueueOk()

        assertThat(rule.client.getPlayQueue()).isNull()
    }

    @Test
    fun `savePlayQueue posts repeated ids, the current id, and the position as a form`() = runTest {
        rule.enqueueOk()

        rule.client.savePlayQueue(
            songIds = listOf("a", "b", "a"),
            currentId = "b",
            positionMillis = 4_200,
            clientName = "Segue-3f2a",
        )

        val request = rule.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).endsWith("/rest/savePlayQueue.view")
        assertThat(request.formParams("id")).containsExactly("a", "b", "a").inOrder()
        assertThat(request.formParam("current")).isEqualTo("b")
        assertThat(request.formParam("position")).isEqualTo("4200")
        assertThat(request.param("c")).isEqualTo("Segue-3f2a")
        assertThat(request.params("id")).isEmpty()
    }

    @Test
    fun `getPlayQueueByIndex keeps duplicate tracks and the index`() = runTest {
        rule.enqueueFixture("play_queue_by_index.json")

        val queue = rule.client.getPlayQueueByIndex()!!

        assertThat(rule.takeRequest().url.encodedPath).endsWith("/rest/getPlayQueueByIndex.view")
        assertThat(queue.entry.map { it.id })
            .containsExactly("5pTq9vU2mHB4kGiMDw1z1T", "1gQ0k9XnH2a7sYw3bL5mFe", "5pTq9vU2mHB4kGiMDw1z1T").inOrder()
        assertThat(queue.currentIndex).isEqualTo(2)
        assertThat(queue.position).isEqualTo(1_200)
        assertThat(queue.changedBy).isEqualTo("Segue-3f2a")
    }

    @Test
    fun `getPlayQueueByIndex with an empty Navidrome queue returns null`() = runTest {
        rule.enqueueFixture("play_queue_by_index_empty.json")

        assertThat(rule.client.getPlayQueueByIndex()).isNull()
    }

    @Test
    fun `savePlayQueueByIndex posts ids, the index, and the position as a form`() = runTest {
        rule.enqueueOk()

        rule.client.savePlayQueueByIndex(
            songIds = listOf("a", "b", "a"),
            currentIndex = 2,
            positionMillis = 1_200,
            clientName = "Segue-3f2a",
        )

        val request = rule.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).endsWith("/rest/savePlayQueueByIndex.view")
        assertThat(request.formParams("id")).containsExactly("a", "b", "a").inOrder()
        assertThat(request.formParam("currentIndex")).isEqualTo("2")
        assertThat(request.formParam("position")).isEqualTo("1200")
    }

    @Test
    fun `saving an empty queue by index clears it without a currentIndex`() = runTest {
        rule.enqueueOk()

        rule.client.savePlayQueueByIndex(songIds = emptyList(), currentIndex = 0, positionMillis = 0, clientName = null)

        val request = rule.takeRequest()
        assertThat(request.formParams("id")).isEmpty()
        assertThat(request.formParam("currentIndex")).isNull()
        assertThat(request.formParam("position")).isNull()
    }

    @Test
    fun `an out-of-range index is rejected before the request`() = runTest {
        val error = runCatching {
            rule.client.savePlayQueueByIndex(
                songIds = listOf("a"),
                currentIndex = 1,
                positionMillis = 0,
                clientName = null,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(rule.server.requestCount).isEqualTo(0)
    }
}
