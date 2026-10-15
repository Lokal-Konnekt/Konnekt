package com.music.konnekt.data

/** Release parsing kept independent of Android so CPU selection can be verified. */
internal object UpdateReleasePolicy {
    data class Apk(val name: String, val url: String, val size: Long = 0, val sha256: String? = null)

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
