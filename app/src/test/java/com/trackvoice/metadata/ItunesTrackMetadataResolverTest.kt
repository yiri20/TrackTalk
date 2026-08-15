package com.trackvoice.metadata

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItunesTrackMetadataResolverTest {
    @Test
    fun rateLimitResponseIsExposedWithoutThrowing() = runBlocking {
        val resolver = ItunesTrackMetadataResolver { FakeConnection(429, "") }

        val result = resolver.resolve("Track", "Artist", "Album", null)

        assertEquals(ExternalMetadataStatus.RATE_LIMITED, result.status)
        assertEquals(ItunesTrackMetadataResolver.PROVIDER, result.provider)
    }

    @Test
    fun serverFailureIsExposedWithoutThrowing() = runBlocking {
        val resolver = ItunesTrackMetadataResolver { FakeConnection(503, "") }

        val result = resolver.resolve("Track", "Artist", "Album", null)

        assertEquals(ExternalMetadataStatus.FAILED, result.status)
        assertTrue(result.metadata == null)
    }

    @Test
    fun adapterDoesNotMakeMoreThanOneRequestWithinTheProviderWindow() = runBlocking {
        var requests = 0
        val resolver = ItunesTrackMetadataResolver {
            requests += 1
            FakeConnection(429, "")
        }

        resolver.resolve("Track A", "Artist", "Album", null)
        val second = resolver.resolve("Track B", "Artist", "Album", null)

        assertEquals(1, requests)
        assertEquals(ExternalMetadataStatus.RATE_LIMITED, second.status)
    }

    private class FakeConnection(
        private val statusCode: Int,
        private val body: String,
    ) : HttpURLConnection(URL("https://example.test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = statusCode
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    }
}
