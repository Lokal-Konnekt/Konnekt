package com.music.konnekt.data.sources

import com.music.konnekt.data.model.Song
import java.text.Normalizer
import java.util.Locale

/** One addon result together with its configured source position. */
data class AddonSearchCandidate(
    val song: Song,
    val sourceRank: Int,
)

/**
 * Chooses one playable row for each recording returned by the configured addons.
 *
 * Addons use different ids for the same recording, so grouping by id would leave
 * duplicate rows on screen. The normalized title and primary artist identify a
 * recording well enough for search results; the quality rank then makes the
 * highest-quality copy win, with addon order as a deterministic tie-breaker.
 */
fun mergeAddonSearchResults(candidates: List<AddonSearchCandidate>): List<Song> {
    val winners = LinkedHashMap<String, AddonSearchCandidate>()
    candidates.forEach { candidate ->
        val key = recordingKey(candidate.song)
        val previous = winners[key]
        if (previous == null || isBetter(candidate, previous)) winners[key] = candidate
    }
    return winners.values.map(AddonSearchCandidate::song)
}

private fun isBetter(candidate: AddonSearchCandidate, previous: AddonSearchCandidate): Boolean {
    val quality = qualityRank(candidate.song.sourceQuality) - qualityRank(previous.song.sourceQuality)
    return quality > 0 || (quality == 0 && candidate.sourceRank < previous.sourceRank)
}

private fun qualityRank(value: String?): Int = when (value?.uppercase(Locale.ROOT)) {
    ModuleSource.DOLBY -> 4
    ModuleSource.LOSSLESS -> 3
    ModuleSource.HIGH -> 2
    ModuleSource.LOW -> 1
    else -> 0
}

private fun recordingKey(song: Song): String {
    val title = normalize(song.title)
    val artist = normalize(song.artist)
        .split(Regex("\\s*(?:,|&| feat\\.? | ft\\.? | featuring )\\s*"))
        .firstOrNull()
        .orEmpty()
    return "$title|$artist"
}

private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace("\\p{M}".toRegex(), "")
    .lowercase(Locale.ROOT)
    // Keep every Unicode letter/number: Japanese, Cyrillic and Arabic addon
    // catalogues must not all collapse into the same empty search key.
    .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
    .trim()
    .replace("\\s+".toRegex(), " ")
