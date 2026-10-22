package com.quantumff.segue.core.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.quantumff.segue.core.model.StreamFormat
import com.quantumff.segue.core.playback.cache.StreamRequest
import java.util.Locale

/** The format of the selected audio track the player decodes, or null before the song has loaded. */
internal fun Player.decodedAudioFormat(): Format? = currentTracks.groups
    .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }

/**
 * The stream format of a song lasting [durationMillis]: the [decoded] track's, with the [request]
 * filling in a bit rate the codec does not declare (Opus does not). A transcode the server was
 * asked for names its codec until the player has decoded it; its bit rate counts only if the
 * server honoured it. A downloaded file's bit rate is its size over its duration.
 */
internal fun streamFormat(decoded: Format?, request: StreamRequest?, durationMillis: Long): StreamFormat? {
    val stream = request as? StreamRequest.Stream
    val transcode = stream?.transcodeFormat?.uppercase(Locale.ROOT)
    val format = decoded?.let(::streamFormatOf)
        ?: return transcode?.let { StreamFormat(codec = it, bitRateKbps = stream.maxBitRateKbps) }
    if (format.bitRateKbps != null) return format
    val bitRate = when (request) {
        is StreamRequest.Stream -> request.maxBitRateKbps.takeIf { transcode == format.codec }
        is StreamRequest.Download -> fileBitRateKbps(request.sizeBytes, durationMillis)
        null -> null
    }
    return format.copy(bitRateKbps = bitRate)
}

/** Bits per millisecond are kilobits per second. */
private fun fileBitRateKbps(sizeBytes: Long, durationMillis: Long): Int? =
    if (sizeBytes > 0 && durationMillis > 0) (sizeBytes * Byte.SIZE_BITS / durationMillis).toInt() else null

/** [format] as a stream format, or null if it names no codec. */
internal fun streamFormatOf(format: Format): StreamFormat? {
    val codec = codecName(format.sampleMimeType) ?: return null
    return StreamFormat(
        codec = codec,
        bitRateKbps = format.bitrate.takeIf { it > 0 }?.let { (it + BITS_PER_KILOBIT / 2) / BITS_PER_KILOBIT },
        sampleRateHz = format.sampleRate.takeIf { it > 0 },
        bitDepth = bitDepth(format.pcmEncoding),
    )
}

/** "audio/mpeg" → "MP3", "audio/opus" → "OPUS": the codec as the quality pill names it. */
private fun codecName(mimeType: String?): String? = when (mimeType) {
    null -> null
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_RAW -> "PCM"
    else -> mimeType.substringAfter('/').removePrefix("x-").takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
}

/** Bits per sample of a PCM encoding (lossless formats declare one), or null. */
private fun bitDepth(pcmEncoding: Int): Int? = when (pcmEncoding) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
    C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
    else -> null
}

private const val BITS_PER_KILOBIT = 1_000
