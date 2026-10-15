package com.music.konnekt

import com.music.konnekt.data.UpdateReleasePolicy
import org.junit.Assert.*
import org.junit.Test

class UpdateReleasePolicyTest {
    private fun apk(name: String) = UpdateReleasePolicy.Apk(name, "https://example.com/$name")

    @Test fun arm64AliasBeatsUniversalRegardlessOfReleaseOrder() {
        val specific = apk("Konnekt-1.4.0-arm64.apk")
        assertEquals(specific, UpdateReleasePolicy.selectApk(
            listOf(apk("Konnekt-1.4.0.apk"), specific), listOf("arm64-v8a", "armeabi-v7a")))
    }
    @Test fun incompatibleArchitectureIsNeverChosenAsFallback() {
        assertNull(UpdateReleasePolicy.selectApk(listOf(apk("Konnekt-arm64.apk")), listOf("x86_64")))
    }
    @Test fun universalBeatsWrongCpuAndDebugBuild() {
        val universal = apk("Konnekt-universal.apk")
        assertEquals(universal, UpdateReleasePolicy.selectApk(listOf(
            apk("Konnekt-x86_64.apk"), apk("Konnekt-arm64-debug.apk"), universal), listOf("arm64-v8a")))
    }
    @Test fun x86DoesNotMatchX8664() {
        assertNull(UpdateReleasePolicy.selectApk(listOf(apk("Konnekt-x86_64.apk")), listOf("x86")))
    }
    @Test fun numericVersionsDoNotUseAlphabeticalOrder() {
        assertTrue(UpdateReleasePolicy.isNewer("v1.10.0", "1.9.9"))
        assertFalse(UpdateReleasePolicy.isNewer("1.3.3", "1.3.4"))
        assertFalse(UpdateReleasePolicy.isNewer("1.3.4", "1.3.4"))
    }
    @Test fun stableReleaseReplacesItsBetaButInvalidTagsAreIgnored() {
        assertTrue(UpdateReleasePolicy.isNewer("1.3.4", "1.3.4-beta2"))
        assertFalse(UpdateReleasePolicy.isNewer("latest", "1.3.3"))
        assertFalse(UpdateReleasePolicy.isNewer("../../1.4.0", "1.3.3"))
    }
}
