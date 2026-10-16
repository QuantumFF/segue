package com.quantumff.segue.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Makes `getCoverArt` failures look like failures to every image consumer (Coil, the notification's
 * bitmap loader, the Android Auto artwork provider).
 *
 * A Subsonic server reports errors with HTTP 200 and an XML or JSON error document. Navidrome does
 * that for cover art when it is busy ("context deadline exceeded") and answers 429 when it
 * throttles, which a queue of one album's tracks can trigger (every track has its own art ID, so
 * each row is its own request). A loader that trusts the 200 caches the error document as the
 * image, and that track then shows the placeholder for good while its neighbours show the cover.
 *
 * Transient failures (429, 503, a generic error document) are retried up to [MAX_ATTEMPTS] times
 * in all, after the server's `Retry-After` or a short backoff. An error document that is still
 * there afterwards, or a permanent one such as "not found", becomes a 502 with an empty body.
 * Other endpoints pass through untouched.
 */
internal class CoverArtResponseInterceptor(private val sleep: (Long) -> Unit = Thread::sleep) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.pathSegments.lastOrNull() !in COVER_ART_ENDPOINTS) return chain.proceed(request)
        var attempt = 1
        while (true) {
            val response = chain.proceed(request)
            val error = response.errorDocument()
            val transient = response.code in TRANSIENT_CODES || error?.code == GENERIC_ERROR
            if (transient && attempt < MAX_ATTEMPTS && !chain.call().isCanceled()) {
                val wait = backoffMillis(response, attempt)
                response.close()
                sleep(wait)
                attempt++
                continue
            }
            if (error == null) return response
            response.close()
            return response.newBuilder()
                .code(HTTP_BAD_GATEWAY)
                .message("Subsonic error ${error.code}: ${error.message}")
                .removeHeader("Content-Type")
                .removeHeader("Cache-Control")
                .body(ByteArray(0).toResponseBody(null))
                .build()
        }
    }

    private class ErrorDocument(val code: Int?, val message: String)

    /** The Subsonic error a "successful" response carries instead of an image; null for an image. */
    private fun Response.errorDocument(): ErrorDocument? {
        if (!isSuccessful) return null
        val type = body.contentType() ?: return null
        val isDocument = type.type == "text" || type.subtype == "xml" || type.subtype == "json"
        if (!isDocument) return null
        val text = peekBody(PEEK_BYTES).string()
        val code = (XML_CODE.find(text) ?: JSON_CODE.find(text))?.groupValues?.get(1)?.toIntOrNull()
        val message = (XML_MESSAGE.find(text) ?: JSON_MESSAGE.find(text))?.groupValues?.get(1).orEmpty()
        return ErrorDocument(code, message)
    }

    private fun backoffMillis(response: Response, attempt: Int): Long {
        val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()?.let { it * MILLIS_PER_SECOND }
        val backoff = retryAfter ?: (BASE_BACKOFF_MILLIS shl (attempt - 1))
        return backoff.coerceIn(0, MAX_BACKOFF_MILLIS)
    }

    companion object {
        /** Tries per image, the first one included. */
        const val MAX_ATTEMPTS = 3

        /** The longest wait before a retry, whatever `Retry-After` asks for. */
        const val MAX_BACKOFF_MILLIS = 2_000L

        private const val BASE_BACKOFF_MILLIS = 500L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val PEEK_BYTES = 4_096L
        private const val HTTP_BAD_GATEWAY = 502

        /** Subsonic's "a generic error": what Navidrome reports for timeouts and overload. */
        private const val GENERIC_ERROR = 0
        private val TRANSIENT_CODES = setOf(429, 503)
        private val COVER_ART_ENDPOINTS = setOf("getCoverArt", "getCoverArt.view")
        private val XML_CODE = Regex("""<error[^>]*\bcode="(\d+)"""")
        private val XML_MESSAGE = Regex("""<error[^>]*\bmessage="([^"]*)"""")
        private val JSON_CODE = Regex(""""error"\s*:\s*\{[^}]*"code"\s*:\s*(\d+)""")
        private val JSON_MESSAGE = Regex(""""error"\s*:\s*\{[^}]*"message"\s*:\s*"([^"]*)"""")
    }
}
