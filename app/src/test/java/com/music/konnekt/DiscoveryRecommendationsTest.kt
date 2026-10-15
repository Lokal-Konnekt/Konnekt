package com.music.konnekt

import com.music.konnekt.data.diverseDiscoveries
import com.music.konnekt.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class DiscoveryRecommendationsTest {
    private fun song(id: String, artist: String = id) = Song(videoId = id, title = id, artist = artist, thumbnailUrl = null, fromAutoplay = false)

    @Test fun seedsAndDuplicateSuggestionsAreExcludedAcrossQueues() {
        val songs = diverseDiscoveries(listOf(listOf(song("seed"), song("new")),
            listOf(song("new"), song("other"))), setOf("seed"))
        assertEquals(listOf("new", "other"), songs.map { it.videoId })
    }
    @Test fun queuesAreInterleavedAndOneArtistCannotFillTheShelf() {
        val songs = diverseDiscoveries(listOf(
            listOf(song("a", "Artist"), song("b", "ARTIST"), song("c", "artist")),
            listOf(song("d"), song("e"), song("f"))), emptySet())
        assertEquals(listOf("a", "d", "b", "e", "f"), songs.map { it.videoId })
    }
    @Test fun emptyQueuesStayEmptyAndLimitIsRespected() {
        assertTrue(diverseDiscoveries(emptyList(), emptySet()).isEmpty())
        assertEquals(1, diverseDiscoveries(listOf(listOf(song("a"), song("b"))), emptySet(), 1).size)
    }
}
