package com.music.konnekt.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import com.music.konnekt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Konnekt ships as a sideloaded APK off GitHub Releases rather than through
 * a store, so there's nothing to push an update notice on its own — this
 * polls the repo's "latest release" once per launch and compares its tag
 * against the running build.
 *
 * The update itself is also handled here: the release's `.apk` asset is
 * downloaded into the app's cache and handed to the system package installer,
 * so the whole round trip stays inside the app instead of bouncing out to a
 * browser.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
        val size: Long = 0,
        val sha256: String? = null,
    )

    private const val CACHE_SUBDIR = "updates"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/Lokal-Konnekt/Konnekt/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float, val bytes: Long = 0, val total: Long = 0) : DownloadState
        data object Verifying : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val invalidApk: Boolean = false) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkMutex = Mutex()
    private val downloadMutex = Mutex()
    private var lastCheckAt = 0L
    @Volatile private var activeCall: Call? = null
    private var pendingInstall: File? = null
    private val downloadClient = Http.client.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()

    suspend fun check(context: Context) = withContext(Dispatchers.IO) { checkMutex.withLock {
        val now = SystemClock.elapsedRealtime()
        if (lastCheckAt != 0L && now - lastCheckAt < 60_000L) return@withLock
        lastCheckAt = now
        runCatching {
            val request = Request.Builder().url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Konnekt/${BuildConfig.VERSION_NAME}").build()
            val body = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            } ?: return@runCatching
            val release = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching
            if (release["draft"]?.jsonPrimitive?.content == "true" ||
                release["prerelease"]?.jsonPrimitive?.content == "true") return@runCatching
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val apk = apkAsset(release) ?: return@runCatching
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            val latest = tag.removePrefix("v").removePrefix("V")
            if (UpdateReleasePolicy.isNewer(latest, BuildConfig.VERSION_NAME)) {
                if (downloadMutex.isLocked && _available.value?.version != latest) return@runCatching
                val info = UpdateInfo(latest, url, apk.url, notes, apk.size, apk.sha256)
                if (_available.value?.version != latest && !downloadMutex.isLocked) _download.value = DownloadState.Idle
                _available.value = info
                val target = File(File(context.cacheDir, CACHE_SUBDIR), "Konnekt-$latest.apk")
                if (target.exists() && downloadMutex.tryLock()) {
                    try {
                        _download.value = DownloadState.Verifying
                        runCatching { validateDownloadedApk(context, target, info) }
                            .onSuccess { _download.value = DownloadState.Ready(target) }
                            .onFailure { target.delete(); _download.value = DownloadState.Idle }
                    } finally { downloadMutex.unlock() }
                }
            } else {
                _available.value = null
            }
        }.onFailure { if (it is CancellationException) throw it }
    } }

    /** Prefer the APK for this device's CPU; the universal asset is a fallback. */
    private fun apkAsset(release: JsonObject): UpdateReleasePolicy.Apk? = runCatching {
        val assets = release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.filter { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            ?.mapNotNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                UpdateReleasePolicy.Apk(name, url, asset["size"]?.jsonPrimitive?.longOrNull ?: 0,
                    asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:"))
            }
            .orEmpty()
        UpdateReleasePolicy.selectApk(assets, Build.SUPPORTED_ABIS.toList())
    }.getOrNull()

    fun startDownload(context: Context) {
        scope.launch { downloadApk(context.applicationContext) }
    }

    /**
     * Streams the current update's APK into the app cache, reporting progress
     * through [download]. A finished file survives a cancelled dialog: until
     * the state is reset, "Install Now" comes straight back without a second
     * download.
     */
    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO) {
        val info = _available.value ?: return@withContext
        val url = info.apkUrl ?: return@withContext
        if (!downloadMutex.tryLock()) return@withContext
        if (_download.value is DownloadState.Ready) { downloadMutex.unlock(); return@withContext }
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f, total = info.size)
        val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
        val target = File(dir, "Konnekt-${info.version}.apk")
        val partial = File(dir, "${target.name}.part")
        var verifying = false

        try {
            dir.listFiles()?.filter { it != target && it != partial }?.forEach { it.delete() }
            val request = Request.Builder().url(url).build()
            val call = downloadClient.newCall(request)
            activeCall = call
            call.execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 } ?: info.size

                var readTotal = 0L
                var lastPublishedAt = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered(128 * 1024).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (downloadCancelled) {
                                _download.value = DownloadState.Idle
                                return@withContext
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPublishedAt >= 100L) {
                                _download.value =
                                    DownloadState.Downloading(if (total > 0) (readTotal.toFloat() / total).coerceIn(0f, 1f) else -1f,
                                        readTotal, total)
                                lastPublishedAt = now
                            }
                        }
                    }
                }
                check(total <= 0 || readTotal == total) { "Incomplete APK download" }
            }
            verifying = true
            _download.value = DownloadState.Verifying
            validateDownloadedApk(context, partial, info)
            if (downloadCancelled) return@withContext
            check(partial.renameTo(target)) { "Cannot save APK" }
            _download.value = DownloadState.Ready(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _download.value = if (downloadCancelled) {
                DownloadState.Idle
            } else {
                DownloadState.Failed(invalidApk = verifying)
            }
        } finally {
            partial.delete()
            activeCall = null
            if (downloadCancelled) _download.value = DownloadState.Idle
            downloadMutex.unlock()
        }
    }

    /** Refuse a damaged, wrong-package or wrong-signer asset before opening Installer. */
    private fun validateDownloadedApk(context: Context, file: File, info: UpdateInfo) {
        check(info.size <= 0 || file.length() == info.size) { "APK size does not match" }
        info.sha256?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, true)) { "APK digest does not match" }
        }
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flag)
            ?: error("Downloaded APK is invalid")
        check(archive.packageName == context.packageName) { "APK package does not match Konnekt" }
        check(archive.versionName == info.version) { "APK version does not match the release" }
        val installed = pm.getPackageInfo(context.packageName, flag)
        @Suppress("DEPRECATION")
        check(if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode > installed.longVersionCode
            else archive.versionCode > installed.versionCode) { "APK version code must increase" }
        val compatible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signatures = archive.signingInfo?.apkContentsSigners.orEmpty()
            signatures.isNotEmpty() && signatures.all { signature ->
                pm.hasSigningCertificate(
                    context.packageName,
                    signature.toByteArray(),
                    PackageManager.CERT_INPUT_RAW_X509,
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val current = installed.signatures.orEmpty().map { it.toCharsString() }.toSet()
            @Suppress("DEPRECATION")
            val next = archive.signatures.orEmpty().map { it.toCharsString() }.toSet()
            current.isNotEmpty() && current == next
        }
        check(compatible) { "APK signature cannot update this installation" }
    }

    /** Stops an in-flight download; the next read loop sees this and bails. */
    fun cancelDownload() {
        downloadCancelled = true
        activeCall?.cancel()
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        if (!downloadMutex.isLocked) _download.value = DownloadState.Idle
    }

    /**
     * Hands a downloaded APK to the system installer.
     *
     * Sideloaded apps need the user's blessing per app ("install unknown apps");
     * without it the installer intent silently does nothing on most ROMs, so
     * the user is sent to that one switch first and taps Install again after.
     */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) { _download.value = DownloadState.Idle; return }
        try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstall = file
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        pendingInstall = null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                ),
        )
        } catch (_: Exception) {
            pendingInstall = null
            _download.value = DownloadState.Failed(invalidApk = true)
        }
    }

    fun resumePendingInstall(context: Context) {
        val file = pendingInstall ?: return
        pendingInstall = null
        if (file.exists() && context.packageManager.canRequestPackageInstalls()) installApk(context, file)
    }

}
