package com.quantumff.segue.core.playback.browse

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.quantumff.segue.core.data.image.CoverArtUrlResolver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Serves cover art as `content://<package>.artwork/cover/<coverArtId>?size=<px>` for media
 * browsers in other processes (Android Auto), which cannot load the app's `segue://` URIs.
 *
 * Not exported: browsers get read access through a URI grant when they connect. Images are
 * fetched once at the requested (small) size and kept in a bounded file cache.
 */
class ArtworkProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ArtworkEntryPoint {
        fun coverArtUrlResolver(): CoverArtUrlResolver

        fun okHttpClient(): OkHttpClient
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(requireNotNull(context).applicationContext, ArtworkEntryPoint::class.java)
    }

    override fun onCreate(): Boolean = true

    /** Runs on a binder thread, so blocking on the download is fine. */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Artwork is read-only")
        val coverArtId = uri.pathSegments.takeIf { it.size == 2 && it[0] == COVER }?.get(1)
            ?: throw FileNotFoundException("Not artwork: $uri")
        val size = uri.getQueryParameter(SIZE)?.toIntOrNull()?.coerceIn(MIN_SIZE, MAX_SIZE) ?: DEFAULT_SIZE
        val file = cachedOrFetched(coverArtId, size)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun cachedOrFetched(coverArtId: String, size: Int): File {
        val location = runBlocking { entryPoint.coverArtUrlResolver().resolve(coverArtId, size) }
            ?: throw FileNotFoundException("Signed out")
        val dir = cacheDir(requireNotNull(context))
        val file = File(dir, sha1(location.cacheKey))
        if (file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        dir.mkdirs()
        val temp = File.createTempFile("art", ".tmp", dir)
        try {
            entryPoint.okHttpClient().newCall(Request.Builder().url(location.url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful) throw FileNotFoundException("Cover art ${response.code}")
                temp.outputStream().use { body.byteStream().copyTo(it) }
            }
            if (!temp.renameTo(file)) throw FileNotFoundException("Could not store cover art")
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        } finally {
            temp.delete()
        }
        trim(dir)
        return file
    }

    /** Keeps the newest [MAX_FILES] images. */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sort: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0

    companion object {
        private const val COVER = "cover"
        private const val SIZE = "size"
        private const val MIN_SIZE = 64
        private const val MAX_SIZE = 512

        /** Small: car screens show browse art as list thumbnails and grid tiles. */
        const val DEFAULT_SIZE = 256
        private const val MAX_FILES = 300

        fun authority(context: Context) = "${context.packageName}.artwork"

        fun coverUri(context: Context, coverArtId: String, size: Int = DEFAULT_SIZE): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath(COVER)
            .appendPath(coverArtId)
            .appendQueryParameter(SIZE, size.toString())
            .build()

        /** The prefix to grant a browser read access to. */
        fun rootUri(context: Context): Uri = Uri.Builder().scheme("content").authority(authority(context)).build()

        private fun cacheDir(context: Context) = File(context.cacheDir, "browse_artwork")

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
