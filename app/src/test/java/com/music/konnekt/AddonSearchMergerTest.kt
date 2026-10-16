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

    private fun candidate(id: String, quality: String, rank: Int) = AddonSearchCandidate(
        song = Song(
            videoId = id,
            title = "Song (Remastered)",
            artist = "Artist feat. Guest",
            thumbnailUrl = null,
            fromAutoplay = false,
            sourceQuality = quality,
        ),
        sourceRank = rank,
    )
}
