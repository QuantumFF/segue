package com.quantumff.segue.core.playback.replaygain

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.playback.media.replayGain
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * ReplayGain with boost: applies [ReplayGainLevel.gain] to PCM, so quiet tracks can be amplified.
 * Without boost the gain is 1 and buffers are copied through; player volume does the work then.
 *
 * Each track gets its own gain without a main-thread race: `DefaultAudioSink` reconfigures the
 * processors at every track change, gapless ones included, and flushes them with the new track's
 * timeline and period. The MediaItem's extras carry the track's ReplayGain values.
 *
 * [settings] may change at any time from the main thread; the next buffer picks them up.
 *
 * Processors are skipped in offload, which is why boost keeps offload off (see ScreenOffOffload).
 */
class ReplayGainProcessor : BaseAudioProcessor() {
    @Volatile
    var settings = ReplayGainSettings()

    // Playback thread only.
    private var streamGain: ReplayGain? = null
    private var streamId: String? = null
    private var levelSettings: ReplayGainSettings? = null
    private var level = ReplayGainLevel.Unity
    private val limiter = PeakLimiter()
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        // DefaultAudioSink hands processors 16-bit or float PCM; anything else passes unprocessed.
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) return AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val item = streamMetadata.mediaItem()
        streamId = item?.mediaId
        streamGain = item?.replayGain
        levelSettings = null
        if (inputAudioFormat.sampleRate > 0) limiter.configure(inputAudioFormat.sampleRate)
        limiter.reset()
    }

    override fun onReset() {
        streamGain = null
        streamId = null
        levelSettings = null
        level = ReplayGainLevel.Unity
        scratch = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val level = currentLevel()
        val output = replaceOutputBuffer(size)
        if (level.gain == 1f && !level.limit) {
            output.put(inputBuffer)
        } else {
            process(inputBuffer, output, level)
        }
        output.flip()
    }

    /** The level for the current stream and settings, recomputed only when the settings change. */
    private fun currentLevel(): ReplayGainLevel {
        val settings = settings
        if (settings != levelSettings) {
            levelSettings = settings
            level = ReplayGainLevel.of(streamGain, settings)
            Log.d(TAG, "Stream $streamId: ${describe(level)} ($settings)")
        }
        return level
    }

    private fun process(input: ByteBuffer, output: ByteBuffer, level: ReplayGainLevel) {
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val count = input.remaining() / if (float) Float.SIZE_BYTES else Short.SIZE_BYTES
        if (scratch.size < count) scratch = FloatArray(count)
        val samples = scratch
        for (i in 0 until count) {
            samples[i] = if (float) input.float else input.short / SHORT_SCALE
        }
        for (i in 0 until count) samples[i] *= level.gain
        if (level.limit) limiter.process(samples, count, inputAudioFormat.channelCount)
        for (i in 0 until count) {
            if (float) {
                output.putFloat(samples[i])
            } else {
                // A peak tag can be a little off, so clamp rather than wrap around.
                output.putShort((samples[i] * SHORT_SCALE).roundToInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort())
            }
        }
    }

    private companion object {
        const val TAG = "SegueReplayGain"
        const val SHORT_SCALE = 32_768f
        const val SHORT_MIN = Short.MIN_VALUE.toInt()
        const val SHORT_MAX = Short.MAX_VALUE.toInt()

        fun AudioProcessor.StreamMetadata.mediaItem() = periodUid?.let { uid ->
            if (timeline.getIndexOfPeriod(uid) == C.INDEX_UNSET) return@let null
            val windowIndex = timeline.getPeriodByUid(uid, Timeline.Period()).windowIndex
            timeline.getWindow(windowIndex, Timeline.Window()).mediaItem
        }

        fun describe(level: ReplayGainLevel) =
            "processor gain %+.2f dB%s".format(20 * log10(level.gain), if (level.limit) ", limiter on" else "")
    }
}
