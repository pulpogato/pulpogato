package io.github.pulpogato.restcodegen

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class SchemaDownloader(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 60_000,
    private val maxAttempts: Int = 3,
    private val retryDelayMillis: Long = 1_000,
) {
    fun download(url: URL): ByteArray {
        for (attempt in 1..maxAttempts) {
            try {
                return downloadOnce(url)
            } catch (failure: IOException) {
                if (failure is PermanentHttpFailure || attempt == maxAttempts) {
                    throw IOException("Failed to download schema from $url after $attempt attempt(s)", failure)
                }
                Thread.sleep(retryDelayMillis)
            }
        }
        error("No download attempts configured")
    }

    private fun downloadOnce(url: URL): ByteArray {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = "HTTP $status downloading $url"
                if (status in setOf(408, 429, 500, 502, 503, 504)) {
                    throw IOException(message)
                }
                throw PermanentHttpFailure(message)
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private class PermanentHttpFailure(
        message: String,
    ) : IOException(message)
}