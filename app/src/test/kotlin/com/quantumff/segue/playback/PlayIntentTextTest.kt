package com.quantumff.segue.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.play.PlayIntentMessage
import com.quantumff.segue.core.model.QueuePlacement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayIntentTextTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun text(message: PlayIntentMessage) = message.snackbarText().resolve(resources)

    @Test
    fun `one song queued is named`() {
        assertThat(text(PlayIntentMessage.Queued(1, QueuePlacement.Next, "Airbag"))).isEqualTo("Playing next: Airbag")
        assertThat(text(PlayIntentMessage.Queued(1, QueuePlacement.End, "Airbag")))
            .isEqualTo("Added Airbag to the queue")
    }

    @Test
    fun `several songs queued are counted`() {
        assertThat(text(PlayIntentMessage.Queued(12, QueuePlacement.End, "OK Computer")))
            .isEqualTo("Added 12 songs to the queue")
        assertThat(text(PlayIntentMessage.Queued(12, QueuePlacement.Next, null))).isEqualTo("Playing 12 songs next")
        assertThat(text(PlayIntentMessage.Queued(1, QueuePlacement.End, null))).isEqualTo("Added 1 song to the queue")
    }

    @Test
    fun `failures keep their wording`() {
        assertThat(text(PlayIntentMessage.NothingToPlay)).isEqualTo("Nothing to play")
    }
}
