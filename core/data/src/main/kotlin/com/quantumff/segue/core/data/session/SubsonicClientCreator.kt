package com.quantumff.segue.core.data.session

import com.quantumff.segue.core.network.SubsonicClient
import com.quantumff.segue.core.network.SubsonicServerConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Creates clients; a seam so tests can hand out fakes. */
internal fun interface SubsonicClientCreator {
    fun create(config: SubsonicServerConfig): SubsonicClient
}

internal object ServerUrl {
    /**
     * Cleans up a server address typed by the user: adds `https://` when no scheme is given, and
     * drops trailing slashes and a pasted `/rest` suffix. Throws [IllegalArgumentException] if the
     * result is not an http(s) URL.
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val url = requireNotNull(withScheme.toHttpUrlOrNull()) { "Not a valid server address: $input" }
        val path = url.encodedPath.trimEnd('/').removeSuffix("/rest").trimEnd('/')
        return url.newBuilder().encodedPath(if (path.isEmpty()) "/" else path).query(null).fragment(null).build()
            .toString()
            .trimEnd('/')
    }
}
