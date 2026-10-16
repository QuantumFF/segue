package com.quantumff.segue.core.playback.replaygain

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Test

class PeakLimiterTest {
    private val limiter = PeakLimiter().apply { configure(SAMPLE_RATE) }

    /** A stereo sine of [amplitude], [seconds] long, interleaved. */
    private fun sine(amplitude: Float, seconds: Float): FloatArray {
        val frames = (SAMPLE_RATE * seconds).toInt()
        return FloatArray(frames * 2) { i -> amplitude * sin(2 * PI * 440 * (i / 2) / SAMPLE_RATE).toFloat() }
    }

    @Test
    fun `a signal below the threshold passes unchanged`() {
        val input = sine(0.9f, 0.1f)
        val output = input.copyOf()
        limiter.process(output, output.size, channels = 2)
        assertThat(output).usingTolerance(1e-6).containsExactly(input).inOrder()
    }

    @Test
    fun `no sample exceeds the threshold, however loud the input`() {
        val output = sine(4f, 0.5f)
        limiter.process(output, output.size, channels = 2)
        assertThat(output.maxOf { abs(it) }).isAtMost(PeakLimiter.THRESHOLD + 1e-6f)
    }

    @Test
    fun `the gain recovers after a loud passage`() {
        val loud = sine(4f, 0.2f)
        limiter.process(loud, loud.size, channels = 2)
        val quiet = sine(0.5f, 1f)
        limiter.process(quiet, quiet.size, channels = 2)
        // The last 100 ms are back at (nearly) full level.
        val tail = quiet.copyOfRange(quiet.size - SAMPLE_RATE / 5, quiet.size)
        assertThat(tail.maxOf { abs(it) }.toDouble()).isWithin(0.01).of(0.5)
    }

    @Test
    fun `channels are limited together, keeping the stereo image`() {
        val frame = floatArrayOf(2f, 0.5f)
        limiter.process(frame, frame.size, channels = 2)
        assertThat(frame[0] / frame[1]).isWithin(1e-4f).of(4f)
    }

    @Test
    fun `reset forgets the gain reduction`() {
        val loud = sine(4f, 0.05f)
        limiter.process(loud, loud.size, channels = 2)
        limiter.reset()
        val frame = floatArrayOf(0.5f, -0.5f)
        limiter.process(frame, frame.size, channels = 2)
        assertThat(frame).usingTolerance(1e-6).containsExactly(0.5f, -0.5f).inOrder()
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}
