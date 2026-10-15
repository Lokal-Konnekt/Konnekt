package com.music.konnekt.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.music.konnekt.BuildConfig
import com.music.konnekt.data.Http
import com.music.konnekt.data.settings.AppSettings
import com.music.konnekt.data.stats.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A Konnekt username maps to a reserved internal Auth address. No real email is collected. */
object KonnektCloud {
    private const val PREF_NAME = "Konnekt_cloud_auth"
    private const val SESSION_KEY = "session"
    private const val SALT_KEY = "salt"
    private const val ENCRYPTION_KEY = "encryption_key"
    private const val AUTO_SYNC_KEY = "auto_sync_enabled"
    private const val SNAPSHOTS = "konnekt_account_snapshots"
    private val usernamePattern = Regex("^[a-z0-9_]{3,24}$")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val watchedPrefs = mutableListOf<SharedPreferences>()
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        localChanges.tryEmit(Unit)
    }
    private fun securePrefs(context: Context) = EncryptedPrefs.openStrict(context, PREF_NAME)

    private val _username = MutableStateFlow<String?>(null)
    val username = _username.asStateFlow()
    val configured: Boolean get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_PUBLISHABLE_KEY.startsWith("sb_publishable_")

    private data class Session(
        val username: String,
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
    ) {
        fun toJson() = JSONObject().apply {
            put("username", username)
            put("user_id", userId)
            put("access_token", accessToken)
            put("refresh_token", refreshToken)
            put("expires_at", expiresAt)
        }.toString()
    }

    @Serializable
    private data class Snapshot(
        val backup: String,
        val googleSessions: String,
        val activeAccountId: String? = null,
        val activeProfileId: String? = null,
        val legacyGoogleCookie: String? = null,
        val discordToken: String? = null,
        val spotifyToken: String = "",
        val listenBrainzToken: String = "",
        val lastfmSessionKey: String = "",
        val lastfmApiKey: String = "",
        val lastfmSecret: String = "",
        val paxSenixApiKey: String = "",
        val webdavPassword: String? = null,
        val smbPassword: String? = null,
    )

    fun init(context: Context) {
        _username.value = readSession(context)?.username
    }

    /** Save changed settings, playlists and connections after a short quiet period. */
    fun startAutoSync(context: Context) {
        if (!configured || watchedPrefs.isNotEmpty()) return
        val app = context.applicationContext
        watchedPrefs += app.getSharedPreferences("Konnekt_settings", Context.MODE_PRIVATE)
        watchedPrefs += app.getSharedPreferences("Konnekt_local_playlists", Context.MODE_PRIVATE)
        watchedPrefs += EncryptedPrefs.open(app, "Konnekt_auth", "Konnekt_auth_plain")
        watchedPrefs.forEach { it.registerOnSharedPreferenceChangeListener(changeListener) }
        scope.launch {
            localChanges.collectLatest {
                delay(3_000)
                if (runCatching { autoSyncEnabled(app) }.getOrDefault(false)) save(app)
            }
        }
    }

    suspend fun register(context: Context, rawUsername: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching {
            require(configured) { "Cloud service is not configured" }
            val username = validUsername(rawUsername)
            require(password.length >= 12) { "Use at least 12 characters for the password" }
            val response = request(
                "/auth/v1/signup", "POST",
                JSONObject().put("email", internalEmail(username)).put("password", password).toString(),
            )
            check(response.optString("access_token").isNotBlank()) {
                "Sign-up requires email confirmation; disable Confirm email in Supabase Auth"
            }
            val session = parseSession(username, response)
            val salt = CloudCipher.randomBytes(16)
            persist(context, session, salt, CloudCipher.deriveKey(password, salt))
            setAutoSync(context, false)
        } }

    suspend fun signIn(context: Context, rawUsername: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching {
            require(configured) { "Cloud service is not configured" }
            val username = validUsername(rawUsername)
            val response = request(
                "/auth/v1/token?grant_type=password", "POST",
                JSONObject().put("email", internalEmail(username)).put("password", password).toString(),
            )
            val session = parseSession(username, response)
            val remote = fetchPayload(session)
            val salt = remote?.let { decode(it.getString("salt")) } ?: CloudCipher.randomBytes(16)
            val key = CloudCipher.deriveKey(password, salt)
            if (remote != null) decrypt(remote, key) // Wrong encryption password must not replace a good local session.
            persist(context, session, salt, key)
            setAutoSync(context, false)
        } }

    fun signOut(context: Context) {
        runCatching { securePrefs(context).edit().clear().apply() }
        _username.value = null
    }

    /** Audio bytes and device-specific download URIs never leave the phone. */
    suspend fun save(context: Context): Result<Unit> = withContext(Dispatchers.IO) { saveMutex.withLock { runCatching {
        val session = currentSession(context)
        val prefs = securePrefs(context)
        val salt = decode(prefs.getString(SALT_KEY, null) ?: error("Cloud encryption key is missing"))
        val key = decode(prefs.getString(ENCRYPTION_KEY, null) ?: error("Cloud encryption key is missing"))
        val auth = AuthStore(context)
        val snapshot = Snapshot(
            backup = Backup.snapshotJson(),
            googleSessions = auth.sessions.toJson(),
            activeAccountId = auth.activeAccountId,
            activeProfileId = auth.activeProfileId,
            legacyGoogleCookie = auth.cookie,
            discordToken = auth.discordToken,
            spotifyToken = AppSettings.spotifySpdcToken.value,
            listenBrainzToken = AppSettings.listenBrainzToken.value,
            lastfmSessionKey = AppSettings.lastfmSessionKey.value,
            lastfmApiKey = AppSettings.lastfmApiKey.value,
            lastfmSecret = AppSettings.lastfmSecret.value,
            paxSenixApiKey = AppSettings.paxSenixApiKey.value,
            webdavPassword = auth.webdavPassword,
            smbPassword = auth.smbPassword,
        )
        val body = JSONObject().put("user_id", session.userId)
            .put("updated_at", Instant.now().toString())
            .put("encrypted_payload", encrypt(json.encodeToString(Snapshot.serializer(), snapshot), salt, key))
            .toString()
        request(
            "/rest/v1/$SNAPSHOTS?on_conflict=user_id", "POST", body, session.accessToken,
            "resolution=merge-duplicates,return=minimal",
        )
        setAutoSync(context, true)
        Unit
    } } }

    /** An explicit restore avoids overwriting local changes merely by signing in. */
    suspend fun restore(context: Context): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val session = currentSession(context)
        val remote = fetchPayload(session) ?: error("No cloud backup has been saved yet")
        val prefs = securePrefs(context)
        val key = decode(prefs.getString(ENCRYPTION_KEY, null) ?: error("Cloud encryption key is missing"))
        val snapshot = json.decodeFromString(Snapshot.serializer(), decrypt(remote, key))
        Backup.restoreJson(snapshot.backup)
        val auth = AuthStore(context)
        auth.sessions = sessionsFromJson(snapshot.googleSessions)
        auth.activeAccountId = snapshot.activeAccountId
        auth.activeProfileId = snapshot.activeProfileId
        auth.cookie = snapshot.legacyGoogleCookie
        auth.discordToken = snapshot.discordToken
        auth.webdavPassword = snapshot.webdavPassword
        auth.smbPassword = snapshot.smbPassword
        AppSettings.setSpotifySpdcToken(snapshot.spotifyToken)
        AppSettings.setListenBrainzToken(snapshot.listenBrainzToken)
        AppSettings.setLastfmSessionKey(snapshot.lastfmSessionKey)
        AppSettings.setLastfmApiKey(snapshot.lastfmApiKey)
        AppSettings.setLastfmSecret(snapshot.lastfmSecret)
        AppSettings.setPaxSenixApiKey(snapshot.paxSenixApiKey)
        AppSettings.reload()
        setAutoSync(context, true)
    } }

    private fun autoSyncEnabled(context: Context): Boolean =
        securePrefs(context).getBoolean(AUTO_SYNC_KEY, false)

    private fun setAutoSync(context: Context, enabled: Boolean) {
        securePrefs(context).edit()
            .putBoolean(AUTO_SYNC_KEY, enabled).apply()
    }

    private fun validUsername(value: String): String = value.trim().lowercase().also {
        require(usernamePattern.matches(it)) { "Username: 3–24 lowercase letters, numbers or _" }
    }

    private fun internalEmail(username: String) = "$username@accounts.invalid"

    private fun readSession(context: Context): Session? = runCatching {
        val prefs = securePrefs(context)
        val raw = JSONObject(prefs.getString(SESSION_KEY, null) ?: return null)
        Session(
            username = raw.getString("username"),
            userId = raw.getString("user_id"),
            accessToken = raw.getString("access_token"),
            refreshToken = raw.getString("refresh_token"),
            expiresAt = raw.getLong("expires_at"),
        )
    }.getOrNull()

    private fun persist(context: Context, session: Session, salt: ByteArray, key: ByteArray) {
        securePrefs(context).edit()
            .putString(SESSION_KEY, session.toJson())
            .putString(SALT_KEY, encode(salt))
            .putString(ENCRYPTION_KEY, encode(key))
            .apply()
        _username.value = session.username
    }

    private fun currentSession(context: Context): Session {
        val session = readSession(context) ?: error("Sign in to Konnekt first")
        if (session.expiresAt > System.currentTimeMillis() + 60_000L) return session
        val response = request(
            "/auth/v1/token?grant_type=refresh_token", "POST",
            JSONObject().put("refresh_token", session.refreshToken).toString(),
        )
        val refreshed = parseSession(session.username, response)
        securePrefs(context).edit()
            .putString(SESSION_KEY, refreshed.toJson()).apply()
        return refreshed
    }

    private fun parseSession(username: String, data: JSONObject): Session = Session(
        username = username,
        userId = data.getJSONObject("user").getString("id"),
        accessToken = data.getString("access_token"),
        refreshToken = data.getString("refresh_token"),
        expiresAt = System.currentTimeMillis() + data.optLong("expires_in", 3600L) * 1000L,
    )

    private fun fetchPayload(session: Session): JSONObject? {
        val response = request(
            "/rest/v1/$SNAPSHOTS?user_id=eq.${session.userId}&select=encrypted_payload", "GET",
            token = session.accessToken,
        )
        val rows = JSONArray(response.getString("rows"))
        return if (rows.length() == 0) null else rows.getJSONObject(0).getJSONObject("encrypted_payload")
    }

    private fun request(
        path: String,
        method: String,
        body: String? = null,
        token: String? = null,
        prefer: String? = null,
    ): JSONObject {
        val builder = Request.Builder().url(BuildConfig.SUPABASE_URL + path)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
        if (prefer != null) builder.header("Prefer", prefer)
        builder.method(method, body?.toRequestBody(mediaType))
        Http.client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(text).let { error ->
                        error.optString("msg").ifBlank {
                            error.optString("message").ifBlank {
                                error.optString("error_description")
                            }
                        }
                    }
                }.getOrDefault("")
                error(detail.ifBlank { "Cloud request failed (${response.code})" })
            }
            return if (text.trimStart().startsWith("[")) JSONObject().put("rows", text)
                else if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun encrypt(plain: String, salt: ByteArray, key: ByteArray): JSONObject {
        val iv = CloudCipher.randomBytes(12)
        return JSONObject().put("salt", encode(salt)).put("iv", encode(iv))
            .put("ciphertext", encode(CloudCipher.encrypt(plain.toByteArray(Charsets.UTF_8), key, iv)))
    }

    private fun decrypt(payload: JSONObject, key: ByteArray): String {
        return CloudCipher.decrypt(
            decode(payload.getString("ciphertext")), key, decode(payload.getString("iv")),
        ).toString(Charsets.UTF_8)
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.DEFAULT)
}
