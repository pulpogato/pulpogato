package io.github.pulpogato.restcodegen

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class SchemaDownloaderTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val requests = AtomicInteger()
    private val url = URI("http://127.0.0.1:${server.address.port}/schema").toURL()

    @AfterEach
    fun closeServer() {
        server.stop(0)
        executor.shutdownNow()
    }

    @ParameterizedTest
    @ValueSource(ints = [408, 429, 500, 502, 503, 504])
    fun `retries transient HTTP failures and returns complete schema`(status: Int) {
        serve { exchange, attempt ->
            if (attempt < 3) {
                exchange.sendResponseHeaders(status, -1)
            } else {
                val body = "{\"openapi\":\"3.0.0\"}".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.write(body)
            }
        }

        val bytes = SchemaDownloader(retryDelayMillis = 0).download(url)

        assertThat(bytes.toString(Charsets.UTF_8)).isEqualTo("{\"openapi\":\"3.0.0\"}")
        assertThat(requests.get()).isEqualTo(3)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404])
    fun `does not retry permanent HTTP failures`(status: Int) {
        serve { exchange, _ -> exchange.sendResponseHeaders(status, -1) }

        assertThatThrownBy { SchemaDownloader(retryDelayMillis = 0).download(url) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("after 1 attempt")
            .hasCauseInstanceOf(IOException::class.java)
            .hasStackTraceContaining("HTTP $status")
        assertThat(requests.get()).isEqualTo(1)
    }

    @Test
    fun `stops retrying after three failed attempts`() {
        serve { exchange, _ -> exchange.sendResponseHeaders(503, -1) }

        assertThatThrownBy { SchemaDownloader(retryDelayMillis = 0).download(url) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("after 3 attempt")
            .hasStackTraceContaining("HTTP 503")
        assertThat(requests.get()).isEqualTo(3)
    }

    @Test
    fun `times out stalled response bodies`() {
        val release = CountDownLatch(1)
        serve { exchange, _ ->
            exchange.sendResponseHeaders(200, 10)
            exchange.responseBody.flush()
            release.await()
        }

        try {
            assertThatThrownBy {
                SchemaDownloader(readTimeoutMillis = 100, maxAttempts = 2, retryDelayMillis = 0).download(url)
            }.isInstanceOf(IOException::class.java)
                .hasMessageContaining("after 2 attempt")
                .hasCauseInstanceOf(SocketTimeoutException::class.java)
            assertThat(requests.get()).isEqualTo(2)
        } finally {
            release.countDown()
        }
    }

    private fun serve(handler: (HttpExchange, Int) -> Unit) {
        server.executor = executor
        server.createContext("/schema") { exchange ->
            try {
                handler(exchange, requests.incrementAndGet())
            } finally {
                exchange.close()
            }
        }
        server.start()
    }
}