package com.music.konnekt

import com.music.konnekt.data.model.Song
import com.music.konnekt.data.sources.AddonSearchCandidate
import com.music.konnekt.data.sources.ModuleSource
import com.music.konnekt.data.sources.mergeAddonSearchResults
import org.junit.Assert.assertEquals
import org.junit.Test

class AddonSearchMergerTest {

    @Test
    fun keepsOnlyTheBestQualityCopyOfARecording() {
        val result = mergeAddonSearchResults(
            listOf(
                candidate("addon-low", "LOW", 0),
                candidate("addon-flac", ModuleSource.LOSSLESS, 1),
                candidate("addon-high", ModuleSource.HIGH, 2),
            ),
        )

        assertEquals(listOf("addon-flac"), result.map(Song::videoId))
    }

    @Test
    fun usesConfiguredAddonOrderWhenQualityIsEqual() {
        val result = mergeAddonSearchResults(
            listOf(
                candidate("second", ModuleSource.HIGH, 1),
                candidate("first", ModuleSource.HIGH, 0),
            ),
        )

        assertEquals(listOf("first"), result.map(Song::videoId))
    }

    @Test
    fun keepsNonLatinRecordingsDistinct() {
        val result = mergeAddonSearchResults(
            listOf(
                AddonSearchCandidate(song("one", "夜に駆ける", "YOASOBI"), 0),
                AddonSearchCandidate(song("two", "群青", "YOASOBI"), 0),
            ),
        )

        assertEquals(listOf("one", "two"), result.map(Song::videoId))
    }

    private fun candidate(id: String, quality: String, rank: Int) = AddonSearchCandidate(
        song = song(id, "Song (Remastered)", "Artist feat. Guest", quality),
        sourceRank = rank,
    )

    private fun song(id: String, title: String, artist: String, quality: String? = null) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        fromAutoplay = false,
        sourceQuality = quality,
    )
}
