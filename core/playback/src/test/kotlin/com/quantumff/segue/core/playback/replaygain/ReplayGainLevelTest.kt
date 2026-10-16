package com.quantumff.segue.core.playback.replaygain

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ReplayGainTarget
import com.quantumff.segue.core.model.UserPreferences
import kotlin.math.log10
import org.junit.Test

class ReplayGainLevelTest {
    private val gains = ReplayGain(trackGain = -6f, albumGain = -9f, trackPeak = 0.5f, albumPeak = 0.9f)
    private val track = ReplayGainSettings(ReplayGainMode.Track, ReplayGainTarget.ReplayGain2, boost = false)
    private val boost = track.copy(boost = true)

    private fun db(linear: Float) = (20 * log10(linear)).toDouble()

    // --- Mode and fallback -------------------------------------------------------------------------

    @Test
    fun `off is unity, with or without boost`() {
        assertThat(ReplayGainLevel.of(gains, track.copy(mode = ReplayGainMode.Off))).isEqualTo(ReplayGainLevel.Unity)
        assertThat(ReplayGainLevel.of(gains, boost.copy(mode = ReplayGainMode.Off))).isEqualTo(ReplayGainLevel.Unity)
    }

    @Test
    fun `track and album modes use their own gain`() {
        assertThat(db(ReplayGainLevel.of(gains, track).volume)).isWithin(0.01).of(-6.0)
        assertThat(
            db(ReplayGainLevel.of(gains, track.copy(mode = ReplayGainMode.Album)).volume),
        ).isWithin(0.01).of(-9.0)
    }

    @Test
    fun `a missing value falls back to the other mode, then the fallback gain`() {
        val trackOnly = ReplayGain(trackGain = -4f)
        assertThat(
            db(ReplayGainLevel.of(trackOnly, track.copy(mode = ReplayGainMode.Album)).volume),
        ).isWithin(0.01).of(-4.0)
        val fallback = ReplayGain(fallbackGain = -2f)
        assertThat(db(ReplayGainLevel.of(fallback, track).volume)).isWithin(0.01).of(-2.0)
    }

    @Test
    fun `a zero gain with a zero or absent peak counts as missing, so the other value is used`() {
        // Servers report a missing album gain as 0; real audio always peaks above 0.
        val noAlbum = ReplayGain(trackGain = -4f, albumGain = 0f, trackPeak = 0.9f, albumPeak = 0f)
        assertThat(db(ReplayGainLevel.of(noAlbum, track.copy(mode = ReplayGainMode.Album)).volume))
            .isWithin(0.01).of(-4.0)
        val noAlbumPeak = ReplayGain(trackGain = -4f, albumGain = 0f, trackPeak = 0.9f)
        assertThat(db(ReplayGainLevel.of(noAlbumPeak, track.copy(mode = ReplayGainMode.Album)).volume))
            .isWithin(0.01).of(-4.0)
        val noTrack = ReplayGain(trackGain = 0f, albumGain = -7f, albumPeak = 0.9f)
        assertThat(db(ReplayGainLevel.of(noTrack, track).volume)).isWithin(0.01).of(-7.0)
    }

    @Test
    fun `Navidrome's filler of gain 0 and peak 1 counts as missing`() {
        // Navidrome's legacy scanner stored absent tags as gain 0, peak 1 (demo.navidrome.org still serves them).
        val filler = ReplayGain(trackGain = -8.33f, albumGain = 0f, trackPeak = 0.9f, albumPeak = 1f)
        assertThat(db(ReplayGainLevel.of(filler, track.copy(mode = ReplayGainMode.Album)).volume))
            .isWithin(0.01).of(-8.33)
        val noTrack = ReplayGain(trackGain = 0f, albumGain = -7f, trackPeak = 1f, albumPeak = 0.9f)
        assertThat(db(ReplayGainLevel.of(noTrack, track).volume)).isWithin(0.01).of(-7.0)
        // The album's own peak, not the filler, caps the boosted album gain.
        assertThat(ReplayGainLevel.of(noTrack.copy(albumGain = 6f, albumPeak = 0.8f), boost).gain.toDouble())
            .isWithin(0.001).of(1.25)
    }

    @Test
    fun `a zero gain with a real peak is a real 0 dB`() {
        val zero = ReplayGain(trackGain = -4f, albumGain = 0f, trackPeak = 0.9f, albumPeak = 0.5f)
        assertThat(ReplayGainLevel.of(zero, track.copy(mode = ReplayGainMode.Album)).volume).isEqualTo(1f)
    }

    @Test
    fun `both values missing as zeros fall through to the fallback gain, then unity`() {
        val zeros = ReplayGain(trackGain = 0f, albumGain = 0f, trackPeak = 0f, albumPeak = 0f)
        assertThat(ReplayGainLevel.of(zeros.copy(fallbackGain = -3f), track).volume.let(::db)).isWithin(0.01).of(-3.0)
        assertThat(ReplayGainLevel.of(zeros, boost)).isEqualTo(ReplayGainLevel.Unity)
    }

    @Test
    fun `missing tags leave the track at unity, with or without boost`() {
        assertThat(ReplayGainLevel.of(null, track)).isEqualTo(ReplayGainLevel.Unity)
        assertThat(ReplayGainLevel.of(null, boost)).isEqualTo(ReplayGainLevel.Unity)
        assertThat(ReplayGainLevel.of(ReplayGain(trackPeak = 0.5f), boost)).isEqualTo(ReplayGainLevel.Unity)
    }

    // --- Target ------------------------------------------------------------------------------------

    @Test
    fun `the -18 LUFS target applies gains as reported`() {
        assertThat(db(ReplayGainLevel.of(ReplayGain(trackGain = -6f), track).volume)).isWithin(0.01).of(-6.0)
    }

    @Test
    fun `the -23 LUFS target is 5 dB quieter`() {
        val ebu = track.copy(target = ReplayGainTarget.Ebu)
        assertThat(db(ReplayGainLevel.of(ReplayGain(trackGain = -6f), ebu).volume)).isWithin(0.01).of(-11.0)
        // A +3 dB track lands at -2 dB: attenuated even without boost.
        assertThat(db(ReplayGainLevel.of(ReplayGain(trackGain = 3f), ebu).volume)).isWithin(0.01).of(-2.0)
        assertThat(db(ReplayGainLevel.of(ReplayGain(trackGain = 3f), boost.copy(target = ReplayGainTarget.Ebu)).gain))
            .isWithin(0.01).of(-2.0)
    }

    // --- Without boost: player volume --------------------------------------------------------------

    @Test
    fun `without boost the gain is player volume and the processor stays at unity`() {
        val level = ReplayGainLevel.of(ReplayGain(trackGain = -6f), track)
        assertThat(level.gain).isEqualTo(1f)
        assertThat(level.limit).isFalse()
    }

    @Test
    fun `without boost positive gain is capped at unity volume`() {
        assertThat(ReplayGainLevel.of(ReplayGain(trackGain = 5f), track)).isEqualTo(ReplayGainLevel.Unity)
    }

    @Test
    fun `the peak caps the volume to prevent clipping`() {
        val peaky = ReplayGain(trackGain = -1f, trackPeak = 1.5f)
        assertThat(ReplayGainLevel.of(peaky, track).volume.toDouble()).isWithin(0.001).of(1 / 1.5)
    }

    // --- With boost: the processor -----------------------------------------------------------------

    @Test
    fun `with boost the processor applies the gain and volume stays at unity`() {
        val level = ReplayGainLevel.of(ReplayGain(trackGain = -6f), boost)
        assertThat(level.volume).isEqualTo(1f)
        assertThat(db(level.gain)).isWithin(0.01).of(-6.0)
        assertThat(level.limit).isFalse()
    }

    @Test
    fun `with boost a positive gain amplifies`() {
        val level = ReplayGainLevel.of(ReplayGain(trackGain = 4f, trackPeak = 0.5f), boost)
        assertThat(db(level.gain)).isWithin(0.01).of(4.0)
        assertThat(level.volume).isEqualTo(1f)
    }

    @Test
    fun `with boost a known peak caps the gain so the loudest sample just reaches full scale`() {
        // +10 dB is x3.16; a peak of 0.5 allows x2.
        val level = ReplayGainLevel.of(ReplayGain(trackGain = 10f, trackPeak = 0.5f), boost)
        assertThat(level.gain.toDouble()).isWithin(0.001).of(2.0)
        assertThat(level.limit).isFalse()
    }

    @Test
    fun `with boost the peak comes from the same source as the gain`() {
        val album = boost.copy(mode = ReplayGainMode.Album)
        val level = ReplayGainLevel.of(ReplayGain(albumGain = 6f, trackPeak = 0.25f, albumPeak = 0.8f), album)
        assertThat(level.gain.toDouble()).isWithin(0.001).of(1.25)
    }

    @Test
    fun `with boost and no peak a positive gain turns the limiter on`() {
        val level = ReplayGainLevel.of(ReplayGain(trackGain = 6f), boost)
        assertThat(db(level.gain)).isWithin(0.01).of(6.0)
        assertThat(level.limit).isTrue()
    }

    @Test
    fun `with boost and no peak an attenuating gain needs no limiter`() {
        assertThat(ReplayGainLevel.of(ReplayGain(trackGain = -3f), boost).limit).isFalse()
    }

    @Test
    fun `boost is capped`() {
        val level = ReplayGainLevel.of(ReplayGain(trackGain = 40f), boost)
        assertThat(db(level.gain)).isWithin(0.01).of(ReplayGainLevel.MAX_BOOST_DB.toDouble())
    }

    // --- Settings ----------------------------------------------------------------------------------

    @Test
    fun `settings come from the preferences`() {
        val prefs = UserPreferences(
            replayGainMode = ReplayGainMode.Album,
            replayGainTarget = ReplayGainTarget.Ebu,
            replayGainBoost = true,
        )
        assertThat(ReplayGainSettings.from(prefs))
            .isEqualTo(ReplayGainSettings(ReplayGainMode.Album, ReplayGainTarget.Ebu, boost = true))
    }
}
