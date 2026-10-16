package com.music.konnekt.download

import android.content.Context
import android.net.Uri
import com.music.konnekt.R
import com.music.konnekt.data.DebugLog as Log
import com.music.konnekt.data.settings.AppSettings
import com.music.konnekt.data.settings.DownloadQuality
import com.music.konnekt.data.stats.ListeningStats
import com.music.konnekt.data.stats.ReplayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps a small, opt-in set of frequently played tracks on this device. */
object SmartOffline {
    enum class Status { IDLE, WAITING_FOR_NETWORK, NO_HISTORY, UPDATING, QUEUED, READY, ERROR }

    private const val TAG = "Konnekt"
    private const val PREFS = "Konnekt_smart_offline"
    private const val KEY_TRACKS = "managed_tracks"
    private const val KEY_LAST_REFRESH = "last_refresh_ms"
    private const val REFRESH_INTERVAL_MS = 12L * 60 * 60 * 1000
    private val mutex = Mutex()
    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status

    /** An explicit Download tap makes a managed copy the user's permanent download. */
    suspend fun keepManually(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val managed = prefs.getStringSet(KEY_TRACKS, emptySet()).orEmpty().toMutableSet()
            if (managed.removeAll(ids.toSet())) prefs.edit().putStringSet(KEY_TRACKS, managed).apply()
        }
    }

    suspend fun refresh(context: Context, force: Boolean = false) {
        if (!AppSettings.smartOfflineEnabled.value) {
            _status.value = Status.IDLE
            return
        }
        if (AppSettings.meteredConnection.value != false) {
            _status.value = Status.WAITING_FOR_NETWORK
            return
        }
        if (!mutex.tryLock()) return
        try {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!force && now - prefs.getLong(KEY_LAST_REFRESH, 0L) < REFRESH_INTERVAL_MS) {
                if (_status.value == Status.IDLE) _status.value = Status.READY
                return
            }
            _status.value = Status.UPDATING
            val recent = ListeningStats.summary(ReplayPeriod.THIS_MONTH).songs.take(12)
            val longTerm = ListeningStats.summary(ReplayPeriod.ALL_TIME).songs.take(12)
            val candidates = smartOfflineCandidates(recent.map { it.song }, longTerm.map { it.song })
            if (candidates.isEmpty()) {
                _status.value = Status.NO_HISTORY
                return
            }

            val managed = prefs.getStringSet(KEY_TRACKS, emptySet()).orEmpty().toMutableSet()
            val saved = Downloads.saved.value
            val inFlight = Downloads.active.value.filterValues {
                it is DownloadState.Queued || it is DownloadState.Running
            }.keys
            managed.removeAll { it !in saved && it !in inFlight }
            val rankedIds = candidates.map { it.videoId }.toSet()
            val sizes = managed.filter { it in saved }.associateWith { id ->
                val uri = Downloads.savedUri(app, id)
                if (uri == null) 0L else fileSize(app, uri)
            }
            managed.removeAll { it !in Downloads.saved.value && it !in inFlight }
            var used = sizes.values.sum()
            val budget = AppSettings.smartOfflineBudgetMb.value.toLong() * 1024 * 1024

            // Only files this feature queued may be removed; manual downloads are never touched.
            if (used > budget) {
                for (id in sizes.keys.sortedWith(
                    compareBy<String> { it in rankedIds }
                        .thenByDescending { id -> candidates.indexOfFirst { it.videoId == id } },
                )) {
                    if (used <= budget) break
                    if (Downloads.delete(app, id)) {
                        used -= sizes.getValue(id)
                        managed.remove(id)
                    }
                }
            }

            val reserve = when (AppSettings.downloadQuality.value) {
                DownloadQuality.STANDARD -> 8L * 1024 * 1024
                DownloadQuality.HIGH -> 16L * 1024 * 1024
                DownloadQuality.LOSSLESS -> 64L * 1024 * 1024
            }
            used += managed.count { it in inFlight && it !in Downloads.saved.value } * reserve
            // Make room for a newly favored song before lower-ranked auto-managed tracks.
            val firstMissingRank = candidates.indexOfFirst {
                it.videoId !in Downloads.saved.value && it.videoId !in inFlight
            }
            if (firstMissingRank >= 0 && used + reserve > budget) {
                val replaceable = sizes.keys.filter { id ->
                    id in managed && id !in inFlight &&
                        candidates.indexOfFirst { it.videoId == id }.let { it < 0 || it > firstMissingRank }
                }.sortedWith(compareBy<String> { it in rankedIds }
                    .thenByDescending { id -> candidates.indexOfFirst { it.videoId == id } })
                for (id in replaceable) {
                    if (used + reserve <= budget) break
                    if (Downloads.delete(app, id)) {
                        used -= sizes.getValue(id)
                        managed.remove(id)
                    }
                }
            }
            var queued = 0
            var failed = false
            for (song in planSmartOfflineDownloads(candidates, Downloads.saved.value.keys, inFlight, used, budget, reserve)) {
                val id = song.videoId
                Downloads.enqueue(app, song, from = app.getString(R.string.smart_offline))
                if (Downloads.active.value[id] is DownloadState.Queued ||
                    Downloads.active.value[id] is DownloadState.Running
                ) {
                    managed.add(id)
                    queued++
                } else {
                    failed = true
                }
            }
            val editor = prefs.edit().putStringSet(KEY_TRACKS, managed)
            if (!failed) editor.putLong(KEY_LAST_REFRESH, now)
            editor.apply()
            _status.value = when {
                failed -> Status.ERROR
                queued > 0 -> Status.QUEUED
                else -> Status.READY
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "smart offline refresh failed: ${error.message}")
            _status.value = Status.ERROR
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun fileSize(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        runCatching {
            if (uri.scheme == "file") {
                val file = File(uri.path.orEmpty())
                if (file.name == "playlist.m3u8" && file.parentFile?.parentFile?.name == "offline-hls") {
                    file.parentFile?.walkTopDown()?.filter(File::isFile)?.sumOf(File::length) ?: 0L
                } else file.length()
            }
            else context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        }.getOrDefault(0L).takeIf { it > 0L } ?: 64L * 1024 * 1024
    }
}
