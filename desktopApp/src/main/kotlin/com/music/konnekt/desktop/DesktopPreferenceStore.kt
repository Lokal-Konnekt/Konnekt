package com.music.konnekt.desktop

import java.util.prefs.Preferences

/** Adopt a compatible previous desktop store without changing current saved values. */
internal object DesktopPreferenceStore {
    val main: Preferences by lazy { migrate(Preferences.userRoot(), "com.music.konnekt.desktop") }
    val stats: Preferences by lazy { main; Preferences.userRoot().node("com.music.konnekt.desktop.stats") }

    internal fun migrate(root: Preferences, targetName: String): Preferences {
        val target = root.node(targetName)
        if (target.getBoolean("identity_migrated_v1", false)) return target
        runCatching {
            val markers = setOf("liked_ids", "history", "queue", "downloads", "playlists", "source_configs", "presence_install_id")
            val candidates = root.childrenNames().filter {
                it != targetName && it.startsWith("com.music.") && it.endsWith(".desktop")
            }.map(root::node).filter { node -> node.keys().any { it in markers } }
            // Never guess between two stores: ambiguous candidates need an explicit import.
            if (candidates.size == 1) {
                val previous = candidates.single()
                copyMissing(previous, target)
                val statsName = previous.name() + ".stats"
                if (root.nodeExists(statsName)) copyMissing(root.node(statsName), root.node(targetName + ".stats"))
            }
            target.putBoolean("identity_migrated_v1", true)
            target.flush()
        }
        return target
    }

    private fun copyMissing(source: Preferences, target: Preferences) {
        source.keys().forEach { key ->
            if (target.get(key, null) == null) source.get(key, null)?.let { target.put(key, it) }
        }
        source.childrenNames().forEach { copyMissing(source.node(it), target.node(it)) }
        target.flush()
    }
}
