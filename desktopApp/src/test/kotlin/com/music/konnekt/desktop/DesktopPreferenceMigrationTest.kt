package com.music.konnekt.desktop

import java.util.UUID
import java.util.prefs.Preferences
import org.junit.Assert.*
import org.junit.Test

class DesktopPreferenceMigrationTest {
    private fun withRoot(block: (Preferences) -> Unit) {
        val root = Preferences.userRoot().node("konnekt-tests/" + UUID.randomUUID())
        try { block(root) } finally { root.removeNode() }
    }

    @Test fun compatibleStoreAndStatsAreCopiedWithoutOverwritingCurrentSettings() = withRoot { root ->
        val previous = root.node("com.music.previous.desktop")
        previous.put("history", "saved songs")
        previous.put("queue", "saved queue")
        previous.put("theme", "dark")
        previous.node("chunks").put("part_0", "saved token")
        root.node(previous.name() + ".stats").put("buckets", "saved stats")
        val target = root.node("com.music.konnekt.desktop")
        target.put("theme", "light")
        val migrated = DesktopPreferenceStore.migrate(root, target.name())
        assertEquals("saved songs", migrated.get("history", null))
        assertEquals("saved token", migrated.node("chunks").get("part_0", null))
        assertEquals("light", migrated.get("theme", null))
        assertEquals("saved stats", root.node(target.name() + ".stats").get("buckets", null))
        assertEquals("saved songs", previous.get("history", null))
    }

    @Test fun ambiguousStoresAreNotCombined() = withRoot { root ->
        root.node("com.music.first.desktop").put("history", "one")
        root.node("com.music.second.desktop").put("history", "two")
        val migrated = DesktopPreferenceStore.migrate(root, "com.music.konnekt.desktop")
        assertNull(migrated.get("history", null))
    }

    @Test fun migrationDoesNotRestoreDataThatWasLaterCleared() = withRoot { root ->
        root.node("com.music.previous.desktop").put("history", "old")
        val migrated = DesktopPreferenceStore.migrate(root, "com.music.konnekt.desktop")
        migrated.remove("history")
        DesktopPreferenceStore.migrate(root, "com.music.konnekt.desktop")
        assertNull(migrated.get("history", null))
    }
}
