package com.music.konnekt.ui.player

import com.music.konnekt.data.lyrics.LyricLine

/** Keep every unfinished vocal visible, including overlaps spanning more than two rows. */
internal fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    return LyricTimeline(lines).activeAt(positionMs)
}

/** Built once per song, so each display frame searches timestamps rather than the whole song. */
internal class LyricTimeline(private val lines: List<LyricLine>) {
    private val ends = LongArray(lines.size) { index ->
        val line = lines[index]
        if (!line.isGap && (line.hasKnownEnd || line.background?.hasKnownEnd == true)) line.endMs else Long.MIN_VALUE
    }
    private val prefixEnd = LongArray(lines.size).also { prefix ->
        var maximum = Long.MIN_VALUE
        ends.forEachIndexed { index, end -> maximum = maxOf(maximum, end); prefix[index] = maximum }
    }

    fun latestAt(positionMs: Long): Int {
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle
        }
        return low - 1
    }

    fun activeAt(positionMs: Long): List<Int> {
        val latest = latestAt(positionMs)
        if (latest < 0) return emptyList()
        val active = ArrayList<Int>(3)
        active.add(latest)
        var index = latest - 1
        while (index >= 0 && prefixEnd[index] > positionMs) {
            if (ends[index] > positionMs) active.add(index)
            index--
        }
        active.reverse()
        return active
    }
}
