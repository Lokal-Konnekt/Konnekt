package com.music.konnekt

import com.music.konnekt.data.lyrics.LyricLine
import com.music.konnekt.ui.player.activeLyricRows
import com.music.konnekt.ui.player.LyricTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricFocusTest {
    private val overlap = listOf(
        LyricLine(1_000, "upper", sungUntilMs = 4_000),
        LyricLine(2_000, "middle", sungUntilMs = 5_000),
        LyricLine(3_000, "lower", sungUntilMs = 6_000),
    )

    @Test fun upperLineKeepsAnchorAcrossThreeOverlappingVocals() {
        assertEquals(listOf(0, 1, 2), activeLyricRows(overlap, 3_500))
    }

    @Test fun anchorAdvancesExactlyWhenUpperVocalEnds() {
        assertEquals(listOf(1, 2), activeLyricRows(overlap, 4_000))
        assertEquals(listOf(2), activeLyricRows(overlap, 5_000))
    }

    @Test fun finishedMiddleRowDoesNotCutOffLongUpperVocal() {
        val lines = overlap.toMutableList()
        lines[1] = lines[1].copy(sungUntilMs = 2_900)
        assertEquals(listOf(0, 2), activeLyricRows(lines, 3_500))
    }

    @Test fun backgroundVocalKeepsItsParentRowAnchored() {
        val lines = listOf(
            LyricLine(1_000, "lead", sungUntilMs = 1_900,
                background = LyricLine(1_500, "echo", sungUntilMs = 3_000)),
            LyricLine(2_000, "next", sungUntilMs = 4_000),
        )
        assertEquals(listOf(0, 1), activeLyricRows(lines, 2_500))
        assertEquals(listOf(1), activeLyricRows(lines, 3_000))
    }

    @Test fun plainLrcAdvancesWithoutInventingAnOverlap() {
        val lines = overlap.map { it.copy(sungUntilMs = null) }
        assertEquals(listOf(1), activeLyricRows(lines, 2_500))
    }

    @Test fun backwardSeekRecomputesTheAnchor() {
        assertEquals(listOf(2), activeLyricRows(overlap, 5_500))
        assertEquals(listOf(0), activeLyricRows(overlap, 1_500))
        assertEquals(emptyList<Int>(), activeLyricRows(overlap, 500))
    }

    @Test fun oneTimelineHandlesBackwardSeekAndRepeatedTimestamps() {
        val timeline = LyricTimeline(listOf(
            LyricLine(1_000, "one", sungUntilMs = 4_000),
            LyricLine(1_000, "two", sungUntilMs = 2_000),
            LyricLine(3_000, "three", sungUntilMs = 5_000),
        ))
        assertEquals(listOf(2), timeline.activeAt(4_500))
        assertEquals(listOf(0, 1), timeline.activeAt(1_500))
        assertEquals(listOf(0, 2), timeline.activeAt(3_500))
        assertEquals(-1, timeline.latestAt(0))
    }
}
