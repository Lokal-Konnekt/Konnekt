package com.music.bitchord

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.lyrics.LyricsTranslation
import androidx.appcompat.app.AppCompatDelegate
import com.music.bitchord.ui.player.AndroidPlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.smb.SmbCoverFetcher
import com.music.bitchord.data.webdav.WebDavCoilAuth
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.LastPlayed
import com.music.bitchord.playback.OriginalVersion
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.AndroidStreamHooks
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.download.Downloads
import com.music.bitchord.data.AppUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class KonnektApplication : Application(), SingletonImageLoader.Factory {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AndroidStreamHooks.installEarly()
        PlayerPlatform.install(AndroidPlayerHost(this))
        com.music.bitchord.ui.AppUi.install(com.music.bitchord.ui.AndroidAppUiHost)
        if (BuildConfig.DEBUG) {
            DebugLog.sink = DebugLog.Sink { level, tag, message, error ->
                when (level) {
                    'D' -> android.util.Log.d(tag, message, error)
                    'I' -> android.util.Log.i(tag, message, error)
                    'W' -> android.util.Log.w(tag, message, error)
                    else -> android.util.Log.e(tag, message, error)
                }
            }
        }
        Innertube.appLanguage = { AppCompatDelegate.getApplicationLocales().get(0)?.language }
        LyricsTranslation.cacheDir = cacheDir
        authStore = AuthStore(this)
        val backgroundInit = thread(name = "startup-init") {
            SourceRegistry.init(this)
            AndroidStreamHooks.initInnerTubeX(this)
            CanvasCache.init(this)
        }
        val restoredSession = authStore.activeSession
        if (restoredSession != null && authStore.activeAccountId == null) {
            authStore.select(restoredSession.accountId, restoredSession.activeProfileId)
        }
        authStore.cookie = restoredSession?.cookie
        Innertube.cookie = restoredSession?.cookie
        if (restoredSession != null) {
            restoredSession.profiles.firstOrNull { it.profileId == restoredSession.activeProfileId }
                ?.let { Innertube.selectChannel(it.pageId, it.dataSyncId, it.authUser) }
            CoroutineScope(Dispatchers.IO).launch { Innertube.ensureSessionScope() }
        }
        AppSettings.init(this, authStore)
        com.music.bitchord.playback.audio.LosslessOutput.init(this)
        ListenTogether.init(this)
        SearchHistory.init(this)
        LastPlayed.init(this)
        com.music.bitchord.playback.PartyPersonalQueueStash.init(this)
        OriginalVersion.init(this)
        Downloads.init(this)
        ListeningStats.init(this)
        ArtistFacts.init(this)
        AudioCache.init(this)
        SpotifyToken.init(this)
        if (AppSettings.consumeVersionUpdate(BuildConfig.VERSION_CODE)) {
            AudioCache.clear()
            SingletonImageLoader.get(this).let { loader ->
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }
        initLastfm()
        com.music.bitchord.data.presence.AndroidPresence.install(this)

        // Auto-update check on every app launch against GitHub releases
        CoroutineScope(Dispatchers.IO).launch {
            AppUpdateChecker.clearCache(this@KonnektApplication)
            AppUpdateChecker.check()
        }

        backgroundInit.join()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(WebDavCoilAuth())
                add(SmbCoverFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .build()

    private fun initLastfm() {
        val sessionKey = AppSettings.lastfmSessionKey.value
        if (sessionKey.isBlank()) return
        val endpoint = AppSettings.lastfmEndpoint.value.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
        val apiKey = AppSettings.lastfmApiKey.value.trim()
        val secret = AppSettings.lastfmSecret.value.trim()
        if (apiKey.isBlank() || secret.isBlank()) return
        LastFM.configure(
            endpoint = endpoint,
            apiKey = apiKey,
            secret = secret,
            sessionKey = sessionKey,
        )
    }

    companion object {
        lateinit var authStore: AuthStore
            private set
    }
}
