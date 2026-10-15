package com.music.konnekt.data.lyrics

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LyricsHttpTest {
    @Test fun simultaneousReadersShareOneRequestAndOneCanCancel() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("lyrics").setBodyDelay(200, TimeUnit.MILLISECONDS))
            server.start()
            val client = OkHttpClient()
            val request = Request.Builder().url(server.url("/lyrics")).build()
            val first = async(start = CoroutineStart.UNDISPATCHED) { client.awaitLyricsBody(request) }
            val second = async(start = CoroutineStart.UNDISPATCHED) { client.awaitLyricsBody(request) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            first.cancelAndJoin()
            assertEquals("lyrics", withTimeout(3_000) { second.await() })
            assertEquals("lyrics", client.awaitLyricsBody(request))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun cancellingTheLastReaderCancelsTheNetworkCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val cancelled = CountDownLatch(1)
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            }).build()
            val request = Request.Builder().url(server.url("/cancel")).build()
            val reading = async(start = CoroutineStart.UNDISPATCHED) { client.awaitLyricsBody(request) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            reading.cancelAndJoin()
            assertTrue(cancelled.await(2, TimeUnit.SECONDS))
        }
    }

    @Test fun responsesWithDifferentAuthorizationAreNotShared() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("first account"))
            server.enqueue(MockResponse().setBody("second account"))
            server.start()
            val client = OkHttpClient()
            val request = Request.Builder().url(server.url("/account"))
            assertEquals("first account", client.awaitLyricsBody(request.header("Authorization", "Bearer first").build()))
            assertEquals("second account", client.awaitLyricsBody(request.header("Authorization", "Bearer second").build()))
            assertEquals(2, server.requestCount)
        }
    }
}
