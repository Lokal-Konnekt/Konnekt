package com.music.konnekt.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Release parsing kept independent of Android so CPU selection can be verified. */
internal object UpdateReleasePolicy {
    const val CHECK_COOLDOWN_MS = 60_000L

    data class Apk(val name: String, val url: String, val size: Long = 0, val sha256: String? = null)

    data class Release(
        val version: String,
        val releaseUrl: String,
        val apk: Apk?,
        val notes: String?,
    )

    private val architectures = linkedMapOf(
        "arm64-v8a" to listOf("arm64-v8a", "arm64", "aarch64"),
        "armeabi-v7a" to listOf("armeabi-v7a", "arm-v7a", "armv7", "arm32"),
        "x86_64" to listOf("x86_64", "x86-64", "amd64"),
        "x86" to listOf("x86"),
    )

    fun selectApk(assets: List<Apk>, supportedAbis: List<String>): Apk? = assets
        .mapNotNull { asset ->
            val name = asset.name.lowercase()
            if (!name.endsWith(".apk") || listOf("debug", "unsigned", "-dev").any(name::contains)) {
                return@mapNotNull null
            }
            val architecture = architectures.entries.firstOrNull { (_, aliases) -> aliases.any(name::contains) }?.key
            val rank = if (architecture == null) supportedAbis.size else supportedAbis.indexOf(architecture)
            if (rank < 0) null else asset to rank
        }
        .minWithOrNull(compareBy<Pair<Apk, Int>> { it.second }.thenBy { it.first.size.takeIf { size -> size > 0 } ?: Long.MAX_VALUE })
        ?.first

    fun isCheckDue(now: Long, lastCheckAt: Long): Boolean =
        lastCheckAt == 0L || now - lastCheckAt >= CHECK_COOLDOWN_MS

    /**
     * Parses the fields used by the update UI from GitHub's latest-release
     * response. A release without a compatible APK is still a valid update:
     * the dialog can send the user to its release page, while the download
     * path remains available only when an uploaded APK was selected.
     */
    fun parseRelease(release: JsonObject, supportedAbis: List<String>): Release? {
        if (release.booleanValue("draft") || release.booleanValue("prerelease")) return null

        val tag = release.stringValue("tag_name") ?: return null
        val releaseUrl = release.stringValue("html_url") ?: return null
        val version = tag.trim().removePrefix("v").removePrefix("V")
        if (!isValidVersion(version)) return null

        val assets = runCatching {
            release["assets"]?.jsonArray
                ?.mapNotNull { it as? JsonObject }
                ?.mapNotNull { asset ->
                    val name = asset.stringValue("name") ?: return@mapNotNull null
                    val url = asset.stringValue("browser_download_url")
                    ?: return@mapNotNull null
                    val state = asset.stringValue("state")
                    if (!name.endsWith(".apk", ignoreCase = true) ||
                        (state != null && state != "uploaded")
                    ) return@mapNotNull null
                    Apk(
                        name = name,
                        url = url,
                        size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0,
                        sha256 = asset.stringValue("digest")?.removePrefix("sha256:"),
                    )
                }
                .orEmpty()
        }.getOrDefault(emptyList())

        return Release(
            version = version,
            releaseUrl = releaseUrl,
            apk = selectApk(assets, supportedAbis),
            notes = release.stringValue("body"),
        )
    }

    private fun JsonObject.stringValue(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun JsonObject.booleanValue(key: String): Boolean =
        runCatching {
            this[key]?.jsonPrimitive?.let { it.booleanOrNull == true || it.content == "true" }
        }.getOrNull() == true

    private fun isValidVersion(version: String): Boolean =
        Regex("^\\d+(?:\\.\\d+)*(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$")
            .matches(version)

    fun isNewer(latest: String, current: String): Boolean {
        fun parse(raw: String): Pair<List<Int>, Boolean>? {
            val match = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
                .matchEntire(raw.trim()) ?: return null
            val parts = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            return parts to match.groupValues[2].isNotEmpty()
        }
        val l = parse(latest) ?: return false
        val c = parse(current) ?: return false
        for (i in 0 until maxOf(l.first.size, c.first.size)) {
            val a = l.first.getOrElse(i) { 0 }
            val b = c.first.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.second && !l.second
    }
}
