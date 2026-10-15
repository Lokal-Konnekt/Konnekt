package com.music.konnekt

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
import com.music.konnekt.auth.AuthStore
import com.music.konnekt.auth.KonnektCloud
import com.music.konnekt.data.DebugLog
import com.music.konnekt.data.lyrics.LyricsTranslation
import androidx.appcompat.app.AppCompatDelegate
import com.music.konnekt.ui.player.AndroidPlayerHost
import com.music.konnekt.ui.player.PlayerPlatform
import com.music.konnekt.data.canvas.CanvasCache
import com.music.konnekt.data.smb.SmbCoverFetcher
import com.music.konnekt.data.webdav.WebDavCoilAuth
import com.music.konnekt.data.canvas.SpotifyToken
import com.music.konnekt.playback.AudioCache
import com.music.konnekt.playback.LastPlayed
import com.music.konnekt.playback.OriginalVersion
import com.music.konnekt.data.innertube.Innertube
import com.music.konnekt.data.innertube.AndroidStreamHooks
import com.music.konnekt.data.listentogether.ListenTogether
import com.music.konnekt.data.scrobbling.LastFM
import com.music.konnekt.data.settings.AppSettings
import com.music.konnekt.data.settings.SearchHistory
import com.music.konnekt.data.sources.SourceRegistry
import com.music.konnekt.data.stats.ArtistFacts
import com.music.konnekt.data.stats.ListeningStats
import com.music.konnekt.download.Downloads
import com.music.konnekt.data.AppUpdateChecker
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
        com.music.konnekt.ui.AppUi.install(com.music.konnekt.ui.AndroidAppUiHost)
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
        KonnektCloud.init(this)
        KonnektCloud.startAutoSync(this)
        com.music.konnekt.playback.audio.LosslessOutput.init(this)
        ListenTogether.init(this)
        SearchHistory.init(this)
        LastPlayed.init(this)
        com.music.konnekt.playback.PartyPersonalQueueStash.init(this)
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
        com.music.konnekt.data.presence.AndroidPresence.install(this)

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
