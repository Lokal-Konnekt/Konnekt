package com.music.konnekt.data

import com.music.konnekt.data.model.Song
import java.util.Locale

internal fun diverseDiscoveries(queues: List<List<Song>>, knownIds: Set<String>, limit: Int = 20): List<Song> {
    val seen = knownIds.toMutableSet()
    val artists = mutableMapOf<String, Int>()
    val result = ArrayList<Song>(limit)
    for (index in 0 until (queues.maxOfOrNull { it.size } ?: 0)) {
        for (queue in queues) {
            val song = queue.getOrNull(index) ?: continue
            if (song.videoId.isBlank() || song.videoId in seen) continue
            val artist = song.artist.trim().lowercase(Locale.ROOT)
            val count = artists[artist] ?: 0
            if (count >= 2) continue
            seen.add(song.videoId)
            artists[artist] = count + 1
            result.add(song)
            if (result.size >= limit) return result
        }
    }
    return result
}
