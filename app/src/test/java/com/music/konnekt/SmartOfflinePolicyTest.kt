package com.music.konnekt

import com.music.konnekt.data.model.Song
import com.music.konnekt.download.planSmartOfflineDownloads
import com.music.konnekt.download.smartOfflineCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartOfflinePolicyTest {
    @Test fun recentFavoritesLeadAndLocalTracksAreSkipped() {
        val candidates = smartOfflineCandidates(
            listOf(song("recent"), song("content://media/local")),
            listOf(song("recent"), song("long-term")),
        )
        assertEquals(listOf("recent", "long-term"), candidates.map { it.videoId })
    }

    @Test fun budgetAndExistingDownloadsLimitTheQueue() {
        val planned = planSmartOfflineDownloads(
            candidates = listOf(song("saved"), song("active"), song("new-a"), song("new-b")),
            savedIds = setOf("saved"),
            inFlightIds = setOf("active"),
            usedBytes = 190,
            budgetBytes = 250,
            reserveBytes = 40,
        )
        assertEquals(listOf("new-a"), planned.map { it.videoId })
    }

    private fun song(id: String) = Song(id, id, "Artist", null)
}
