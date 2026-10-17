package com.quantumff.segue.core.network

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Segue's own releases on GitHub, for the update check. Not a Subsonic call. */
class GitHubReleaseClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {
    /**
     * The newest published release; GitHub leaves out drafts and pre-releases. Blocking. Throws
     * IOException, also for an HTTP error or a body that is not a release.
     */
    fun latest(): NetworkRelease {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub answered HTTP ${response.code}")
            return try {
                json.decodeFromString<NetworkRelease>(response.body.string())
            } catch (e: IllegalArgumentException) {
                throw IOException("Not a GitHub release", e)
            }
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/QuantumFF/segue/releases/latest"
    }
}

/** The fields of a GitHub release the update check reads. */
@Serializable
data class NetworkRelease(
    /** e.g. "v0.1.1". */
    @SerialName("tag_name") val tagName: String,
    /** The release page. */
    @SerialName("html_url") val htmlUrl: String,
)
