package com.quantumff.segue.core.playback.replaygain

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ReplayGainTarget
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.testing.data.TestData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The processor finds each stream's gain through the timeline Media3 hands it on flush. */
@RunWith(RobolectricTestRunner::class)
class ReplayGainProcessorTest {
    private val boost = ReplayGainSettings(ReplayGainMode.Track, ReplayGainTarget.ReplayGain2, boost = true)
    private val processor = ReplayGainProcessor().apply { settings = boost }

    private fun item(gain: ReplayGain?) = TestData.song("s").copy(replayGain = gain).toMediaItem()

    /** Configures for [format] and starts the stream of [item], as DefaultAudioSink does per track. */
    private fun start(item: MediaItem?, format: AudioFormat = PCM16) {
        processor.configure(format)
        val metadata = AudioProcessor.StreamMetadata.Builder()
        if (item != null) {
            val timeline = SinglePeriodTimeline(
                /* durationUs = */
                C.MICROS_PER_SECOND,
                /* isSeekable = */
                true,
                /* isDynamic = */
                false,
                /* useLiveConfiguration = */
                false,
                /* manifest = */
                null,
                item,
            )
            metadata.setTimeline(timeline).setPeriodUid(timeline.getUidOfPeriod(0))
        }
        processor.flush(metadata.build())
    }

    private fun process16(vararg samples: Short): List<Int> {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.asShortBuffer()
        return ShortArray(output.remaining()).also(output::get).map { it.toInt() }
    }

    private fun processFloat(vararg samples: Float): FloatArray {
        val input = ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.asFloatBuffer()
        return FloatArray(output.remaining()).also(output::get)
    }

    @Test
    fun `applies the stream's track gain`() {
        start(item(ReplayGain(trackGain = -6.0206f)))
        assertThat(process16(1000, -1000, 2000, -2000)).containsExactly(500, -500, 1000, -1000).inOrder()
    }

    @Test
    fun `boosts a quiet track up to its peak`() {
        start(item(ReplayGain(trackGain = 12f, trackPeak = 0.5f)))
        // x2 from the peak cap, not the x3.98 of +12 dB.
        assertThat(process16(1000, 16_000)).containsExactly(2000, 32_000).inOrder()
    }

    @Test
    fun `limits a boosted track without a peak instead of clipping`() {
        start(item(ReplayGain(trackGain = 12f)))
        val output = process16(*ShortArray(2000) { if (it % 2 == 0) 30_000 else -30_000 })
        // Without the limiter, 30 000 x 3.98 would clip hard at 32 767.
        assertThat(output.maxOf { abs(it) }).isAtMost((PeakLimiter.THRESHOLD * 32_768).roundToInt())
    }

    @Test
    fun `processes float PCM`() {
        start(item(ReplayGain(trackGain = 12f, trackPeak = 0.5f)), PCM_FLOAT)
        assertThat(processFloat(0.25f, -0.1f).toList()).containsExactly(0.5f, -0.2f).inOrder()
    }

    @Test
    fun `is unity without boost, so player volume does the work`() {
        processor.settings = boost.copy(boost = false)
        start(item(ReplayGain(trackGain = -6f)))
        assertThat(process16(1000, -1000)).containsExactly(1000, -1000).inOrder()
    }

    @Test
    fun `stays in the chain without boost, so switching boost on mid-track takes effect at once`() {
        processor.settings = boost.copy(boost = false)
        start(item(ReplayGain(trackGain = -6.0206f)))
        assertThat(processor.isActive).isTrue()

        processor.settings = boost
        assertThat(process16(1000)).containsExactly(500)
    }

    @Test
    fun `a stream without tags or without a known item is unity`() {
        start(item(gain = null))
        assertThat(process16(1000)).containsExactly(1000)
        start(item = null)
        assertThat(process16(1000)).containsExactly(1000)
    }

    @Test
    fun `a settings change applies from the next buffer`() {
        start(item(ReplayGain(trackGain = -6.0206f)))
        assertThat(process16(1000)).containsExactly(500)
        processor.settings = boost.copy(mode = ReplayGainMode.Off)
        assertThat(process16(1000)).containsExactly(1000)
    }

    @Test
    fun `stays out of the chain for encodings it does not handle`() {
        processor.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertThat(processor.isActive).isFalse()
    }

    private companion object {
        val PCM16 = AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        val PCM_FLOAT = AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT)
    }
}
