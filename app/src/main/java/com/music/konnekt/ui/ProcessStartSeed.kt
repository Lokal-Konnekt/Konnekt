package com.music.konnekt.ui

/**
 * One random seed per process start. Used by Home feed shuffling so
 * recommendations change on every cold launch without extra network cost.
 */
object ProcessStartSeed {
    val value: Long = System.nanoTime() xor System.currentTimeMillis()
}
