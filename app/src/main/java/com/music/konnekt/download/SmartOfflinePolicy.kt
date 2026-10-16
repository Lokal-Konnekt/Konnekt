package com.music.konnekt.download

import com.music.konnekt.data.model.Song

/** A stable mix of recent and long-term favorites, excluding device-only identities. */
internal fun smartOfflineCandidates(recent: List<Song>, longTerm: List<Song>): List<Song> =
    (recent.take(12) + longTerm.take(12))
        .filter {
            it.videoId.isNotBlank() && !it.videoId.startsWith("content:") &&
                !it.videoId.startsWith("file:") && !it.videoId.startsWith("local:")
        }
        .distinctBy(Song::videoId)
        .take(20)

/** Reserve space before queuing; actual file sizes are measured at the next refresh. */
internal fun planSmartOfflineDownloads(
    candidates: List<Song>,
    savedIds: Set<String>,
    inFlightIds: Set<String>,
    usedBytes: Long,
    budgetBytes: Long,
    reserveBytes: Long,
    maxNew: Int = 3,
): List<Song> {
    if (reserveBytes <= 0L || budgetBytes <= usedBytes) return emptyList()
    val available = (budgetBytes - usedBytes) / reserveBytes
    return candidates.asSequence()
        .filter { it.videoId !in savedIds && it.videoId !in inFlightIds }
        .take(minOf(maxNew.toLong(), available).toInt())
        .toList()
}
