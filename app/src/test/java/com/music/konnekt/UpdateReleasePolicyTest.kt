package com.music.konnekt

import com.music.konnekt.data.UpdateReleasePolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
    @Test fun checkCooldownAllowsFirstAndAfterMinuteOnly() {
        assertTrue(UpdateReleasePolicy.isCheckDue(1_000L, 0L))
        assertFalse(UpdateReleasePolicy.isCheckDue(60_999L, 1_000L))
        assertTrue(UpdateReleasePolicy.isCheckDue(61_000L, 1_000L))
    }
    @Test fun stableReleaseReplacesItsBetaButInvalidTagsAreIgnored() {
        assertTrue(UpdateReleasePolicy.isNewer("1.3.4", "1.3.4-beta2"))
        assertFalse(UpdateReleasePolicy.isNewer("latest", "1.3.3"))
        assertFalse(UpdateReleasePolicy.isNewer("../../1.4.0", "1.3.3"))
    }

    @Test fun newerReleaseWithoutApkStillProducesPageUpdate() {
        val release = Json.parseToJsonElement(
            """{"tag_name":"v1.5.0","html_url":"https://github.com/Lokal-Konnekt/Konnekt/releases/tag/v1.5.0","body":"Notes","assets":[]}""",
        ).jsonObject

        val parsed = UpdateReleasePolicy.parseRelease(release, listOf("arm64-v8a"))

        assertNotNull(parsed)
        assertEquals("1.5.0", parsed?.version)
        assertNull(parsed?.apk)
        assertEquals("Notes", parsed?.notes)
    }

    @Test fun uploadedApkUsesDigestAndIgnoresPrereleases() {
        val release = Json.parseToJsonElement(
            """{"tag_name":"v1.5.0","html_url":"https://example.com/release","prerelease":false,"assets":[{"name":"Konnekt-1.5.0-arm64.apk","browser_download_url":"https://example.com/app.apk","state":"uploaded","size":42,"digest":"sha256:abc"}]}""",
        ).jsonObject

        val parsed = UpdateReleasePolicy.parseRelease(release, listOf("arm64-v8a"))

        assertEquals("https://example.com/app.apk", parsed?.apk?.url)
        assertEquals(42L, parsed?.apk?.size)
        assertEquals("abc", parsed?.apk?.sha256)

        val prerelease = Json.parseToJsonElement(
            """{"tag_name":"v1.5.0-beta1","html_url":"https://example.com/release","prerelease":true}""",
        ).jsonObject
        assertNull(UpdateReleasePolicy.parseRelease(prerelease, emptyList()))
    }
}
