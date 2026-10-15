package com.music.konnekt.data.lyrics

import com.music.konnekt.data.Http
import kotlinx.serialization.json.Json
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import java.util.concurrent.TimeUnit

/**
 * Shared plumbing for the lyric providers.
 *
 * They are all raced against each other by [LyricsRepository], so a provider
 * that hangs holds up the whole lookup. [LYRICS_TIMEOUT_SECONDS] is deliberately
 * far shorter than [Http]'s stream-oriented timeouts: a lyric that arrives
 * after the second chorus is of no use to anyone, and the fallbacks behind it
 * are the better answer.
 */
private const val LYRICS_TIMEOUT_SECONDS = 6L

internal const val LYRICS_AGENT = "Konnekt (https://github.com/Lokal-Konnekt/Konnekt)"

val lyricsJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val client by lazy {
    // Derived from the shared client, so the connection pool and DNS stay
    // common — only the deadline differs.
    Http.client.newBuilder()
        .callTimeout(LYRICS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()
}

// PaxSenix's authenticated routes often have to query an upstream catalogue
// before answering. Keep the fast deadline for the ordinary providers, but
// match PaxSenix's own client timeout here so valid requests are not discarded
// while its backend is still resolving a track.
private val authenticatedClient by lazy {
    client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
}

/** Body of a successful GET, or null for any failure at all. */
internal suspend fun lyricsGet(url: String): String? {
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json")
        .build()
    return client.awaitLyricsBody(request)
}

/** Body of an authenticated provider GET without service-specific browser headers. */
internal suspend fun lyricsGetBearer(url: String, bearer: String): String? {
    if (bearer.isBlank()) return null
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json, text/plain, */*")
        .header("Authorization", "Bearer $bearer")
        .build()
    return authenticatedClient.awaitLyricsBody(request)
}

/**
 * [lyricsGet], with a bearer token and the headers Apple's own web player
 * sends alongside one — `amp-api.music.apple.com` answers a token with no
 * `Origin` at all the same way it answers a wrong one, with a 403.
 */
internal suspend fun lyricsGetAuthorized(url: String, bearer: String): String? {
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer $bearer")
        .header("Origin", "https://music.apple.com")
        .header("Referer", "https://music.apple.com/")
        .build()
    return client.awaitLyricsBody(request)
}

private class SharedLyricCall(val call: Call) {
    val listeners = mutableSetOf<CancellableContinuation<String?>>()
}
private data class CachedLyricBody(val text: String, val at: Long)
private val requestLock = Any()
private val pendingRequests = mutableMapOf<String, SharedLyricCall>()
private val bodies = object : LinkedHashMap<String, CachedLyricBody>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLyricBody>) = size > 16
}

/** UI and playback service share identical requests; cancelling one reader keeps the other alive. */
internal suspend fun OkHttpClient.awaitLyricsBody(request: Request): String? = suspendCancellableCoroutine { continuation ->
    // Headers.toString() masks credentials and would merge two different accounts.
    val identity = request.url.toString() + request.headers.names().sorted().joinToString("\n") { name ->
        "$name:${request.headers.values(name).joinToString("\u0000")}"
    }
    val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val entry: SharedLyricCall
    var start = false
    synchronized(requestLock) {
        val cached = bodies[key]
        if (cached != null && System.nanoTime() - cached.at < 300_000_000_000L) {
            continuation.resume(cached.text)
            return@suspendCancellableCoroutine
        }
        entry = pendingRequests.getOrPut(key) { start = true; SharedLyricCall(newCall(request)) }
        entry.listeners.add(continuation)
    }
    continuation.invokeOnCancellation {
        synchronized(requestLock) {
            entry.listeners.remove(continuation)
            if (entry.listeners.isEmpty()) {
                if (pendingRequests[key] === entry) pendingRequests.remove(key)
                entry.call.cancel()
            }
        }
    }
    if (!start) return@suspendCancellableCoroutine
    fun finish(body: String?) {
        val readers = synchronized(requestLock) {
            if (pendingRequests[key] === entry) pendingRequests.remove(key)
            // Small successful documents only: at most 4 MiB across 16 cache entries.
            if (body != null && body.length <= 128 * 1024 && entry.listeners.isNotEmpty()) {
                bodies[key] = CachedLyricBody(body, System.nanoTime())
            }
            entry.listeners.toList().also { entry.listeners.clear() }
        }
        readers.forEach { if (it.isActive) it.resume(body) }
    }
    entry.call.enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            finish(null)
        }
        override fun onResponse(call: Call, response: Response) {
            val body = runCatching { response.use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
            finish(body)
        }
    })
}
