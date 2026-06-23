package app.pebo.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemVerTest {
    @Test
    fun parsesPlainThreePartVersion() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
    }

    @Test
    fun stripsLeadingVAndPrereleaseAndBuild() {
        assertEquals(SemVer(1, 0, 0), SemVer.parse("v1.0.0"))
        assertEquals(SemVer(2, 1, 0), SemVer.parse("V2.1.0-beta.2"))
        assertEquals(SemVer(3, 4, 5), SemVer.parse("3.4.5+build.99"))
    }

    @Test
    fun treatsMissingComponentsAsZero() {
        assertEquals(SemVer(1, 0, 0), SemVer.parse("1"))
        assertEquals(SemVer(1, 2, 0), SemVer.parse("1.2"))
    }

    @Test
    fun returnsNullForNonNumeric() {
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("nightly"))
        assertNull(SemVer.parse("1.x.0"))
    }

    @Test
    fun comparesByPrecedence() {
        assertTrue(SemVer.parse("1.2.0")!! > SemVer.parse("1.1.9")!!)
        assertTrue(SemVer.parse("2.0.0")!! > SemVer.parse("1.9.9")!!)
        assertTrue(SemVer.parse("1.0.1")!! > SemVer.parse("1.0.0")!!)
        assertEquals(SemVer.parse("1.0"), SemVer.parse("1.0.0"))
    }

    @Test
    fun isNewerVersionDetectsUpgrade() {
        assertTrue(isNewerVersion(current = "1.0.0", latest = "1.0.1"))
        assertTrue(isNewerVersion(current = "1.0.0", latest = "v1.1.0"))
        assertTrue(isNewerVersion(current = "1.9.0", latest = "2.0.0"))
    }

    @Test
    fun isNewerVersionRejectsSameOrOlder() {
        assertFalse(isNewerVersion(current = "1.0.0", latest = "1.0.0"))
        assertFalse(isNewerVersion(current = "1.0.0", latest = "v1.0.0"))
        assertFalse(isNewerVersion(current = "2.0.0", latest = "1.9.9"))
    }

    @Test
    fun isNewerVersionFalseWhenUnparseable() {
        assertFalse(isNewerVersion(current = "1.0.0", latest = "garbage"))
        assertFalse(isNewerVersion(current = "garbage", latest = "1.0.0"))
    }
}

class ReleaseInfoAssetTest {
    private fun release(vararg names: String) = ReleaseInfo(
        version = "1.1.0",
        tag = "v1.1.0",
        name = "Pebo 1.1.0",
        notes = "",
        htmlUrl = "https://example.com",
        assets = names.map { ReleaseAsset(it, "https://dl/$it", 10) },
    )

    @Test
    fun windowsPrefersMsiOverExe() {
        val asset = release("Pebo-1.1.0.exe", "Pebo-1.1.0.msi", "Pebo-1.1.0.dmg").assetFor(UpdatePlatform.Windows)
        assertEquals("Pebo-1.1.0.msi", asset?.name)
    }

    @Test
    fun windowsFallsBackToExeWhenNoMsi() {
        val asset = release("Pebo-1.1.0.exe", "Pebo-1.1.0.dmg").assetFor(UpdatePlatform.Windows)
        assertEquals("Pebo-1.1.0.exe", asset?.name)
    }

    @Test
    fun picksPerPlatformAsset() {
        val r = release("Pebo-1.1.0.msi", "Pebo-1.1.0.dmg", "pebo_1.1.0_amd64.deb", "Pebo-v1.1.0.apk")
        assertEquals("Pebo-1.1.0.dmg", r.assetFor(UpdatePlatform.MacOs)?.name)
        assertEquals("pebo_1.1.0_amd64.deb", r.assetFor(UpdatePlatform.Linux)?.name)
        assertEquals("Pebo-v1.1.0.apk", r.assetFor(UpdatePlatform.Android)?.name)
    }

    @Test
    fun nullWhenNoMatchingAssetOrUnsupported() {
        assertNull(release("Pebo-1.1.0.dmg").assetFor(UpdatePlatform.Windows))
        assertNull(release("Pebo-1.1.0.msi").assetFor(UpdatePlatform.Unsupported))
    }
}
