package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.model.StreamingQuality
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** How one download attempt ended. */
internal sealed interface DownloadOutcome {
    data class Completed(val file: File, val bytes: Long) : DownloadOutcome

    /** The row stopped being Downloading (paused or unpinned) while the file was being written. */
    data object Stopped : DownloadOutcome

    /** Network trouble or a server hiccup; the partial file is kept for resuming. */
    data class Retry(val bytes: Long, val total: Long?, val reason: String) : DownloadOutcome

    /** The server will not serve this song (gone, forbidden): retrying will not help. */
    data class Failed(val reason: String) : DownloadOutcome
}

/** Downloads one song to its file. */
internal interface SongDownloader {
    /**
     * [onProgress] gets bytes written and the total if known. [stillWanted] is checked every few
     * hundred KB; when it turns false the attempt ends as [DownloadOutcome.Stopped].
     */
    suspend fun download(
        row: DownloadEntity,
        quality: StreamingQuality,
        onProgress: (bytes: Long, total: Long?) -> Unit,
        stillWanted: suspend () -> Boolean,
    ): DownloadOutcome
}

/**
 * Fetches `stream` (`format=raw` for the original file, else the chosen transcode) with OkHttp
 * into a `.part` file, resuming with a `Range` request when part of it is already there. A server
 * that ignores the range (transcodes usually do) answers 200 and the file restarts from zero.
 */
internal class HttpSongDownloader @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val songDao: SongDao,
    private val okHttpClient: OkHttpClient,
    private val files: DownloadFiles,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : SongDownloader {
    override suspend fun download(
        row: DownloadEntity,
        quality: StreamingQuality,
        onProgress: (bytes: Long, total: Long?) -> Unit,
        stillWanted: suspend () -> Boolean,
    ): DownloadOutcome = withContext(ioDispatcher) {
        val original = quality.maxBitRateKbps == 0 && quality.format == null
        val url = try {
            val client = clients.client(row.serverId)
            if (original) {
                client.streamUrl(row.songId, maxBitRateKbps = null, format = RAW, timeOffsetSeconds = null)
            } else {
                client.streamUrl(row.songId, quality.maxBitRateKbps, quality.format, timeOffsetSeconds = null)
            }
        } catch (e: RemoteException) {
            return@withContext DownloadOutcome.Failed(e.message ?: "Not signed in")
        }
        val suffix = songDao.getByIds(row.serverId, listOf(row.songId)).firstOrNull()?.suffix
        val extension = if (original) suffix ?: "audio" else "${quality.maxBitRateKbps}.${quality.format ?: "audio"}"
        val target = files.fileFor(row.serverId, row.songId, extension)
        val part = files.partFileFor(target)
        target.parentFile?.mkdirs()
        if (target.exists()) return@withContext DownloadOutcome.Completed(target, target.length())

        val existing = part.length()
        val request = Request.Builder().url(url)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                receive(response, part, target, existing, onProgress, stillWanted)
            }
        } catch (e: IOException) {
            DownloadOutcome.Retry(part.length(), null, e.message ?: e::class.java.simpleName)
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun receive(
        response: Response,
        part: File,
        target: File,
        existing: Long,
        onProgress: (Long, Long?) -> Unit,
        stillWanted: suspend () -> Boolean,
    ): DownloadOutcome {
        val code = response.code
        when {
            code == HTTP_RANGE_NOT_SATISFIABLE && existing > 0 -> return finish(part, target)
            code >= HTTP_SERVER_ERROR -> return DownloadOutcome.Retry(existing, null, "HTTP $code")
            !response.isSuccessful -> return DownloadOutcome.Failed("HTTP $code")
        }
        // Subsonic reports errors (song not found, transcoding failed) as a 200 JSON or XML body.
        val type = response.header("Content-Type").orEmpty()
        if (type.startsWith("application/json") || type.contains("xml")) {
            return DownloadOutcome.Failed("The server refused the download")
        }
        val append = code == HTTP_PARTIAL && existing > 0
        val length = response.body.contentLength().takeIf { it >= 0 }
        var written = if (append) existing else 0L
        val total = length?.let { it + written }
        onProgress(written, total)
        val buffer = ByteArray(BUFFER_BYTES)
        var sinceCheck = 0L
        FileOutputStream(part, append).use { out ->
            val input = response.body.byteStream()
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                written += read
                sinceCheck += read
                if (sinceCheck >= CHECK_EVERY_BYTES) {
                    sinceCheck = 0
                    onProgress(written, total)
                    if (!stillWanted()) return DownloadOutcome.Stopped
                }
            }
        }
        if (total != null && written < total) return DownloadOutcome.Retry(written, total, "Connection closed early")
        return finish(part, target)
    }

    private fun finish(part: File, target: File): DownloadOutcome {
        if (!part.renameTo(target)) return DownloadOutcome.Retry(part.length(), null, "Could not save the file")
        return DownloadOutcome.Completed(target, target.length())
    }

    private companion object {
        const val RAW = "raw"
        const val BUFFER_BYTES = 64 * 1024
        const val CHECK_EVERY_BYTES = 512L * 1024
        const val HTTP_PARTIAL = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HTTP_SERVER_ERROR = 500
    }
}
