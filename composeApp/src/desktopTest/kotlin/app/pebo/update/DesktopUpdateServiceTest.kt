package app.pebo.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopUpdateServiceTest {
    @Test
    fun mapsOsNamesToPlatforms() {
        assertEquals(UpdatePlatform.Windows, platformForOsName("Windows 11"))
        assertEquals(UpdatePlatform.MacOs, platformForOsName("Mac OS X"))
        assertEquals(UpdatePlatform.MacOs, platformForOsName("Darwin"))
        assertEquals(UpdatePlatform.Linux, platformForOsName("Linux"))
        assertEquals(UpdatePlatform.Unsupported, platformForOsName("SunOS-weird"))
    }

    @Test
    fun windowsMsiUsesMsiexec() {
        val cmd = installerCommand("Windows 11", File("C:/tmp/Pebo-1.1.0.msi"))
        assertEquals("msiexec", cmd[0])
        assertEquals("/i", cmd[1])
        assertEquals(File("C:/tmp/Pebo-1.1.0.msi").absolutePath, cmd[2])
    }

    @Test
    fun windowsExeRunsDirectly() {
        val cmd = installerCommand("Windows 11", File("C:/tmp/Pebo-1.1.0.exe"))
        assertEquals(listOf(File("C:/tmp/Pebo-1.1.0.exe").absolutePath), cmd)
    }

    @Test
    fun macUsesOpen() {
        val cmd = installerCommand("Mac OS X", File("/tmp/Pebo-1.1.0.dmg"))
        assertEquals(listOf("open", File("/tmp/Pebo-1.1.0.dmg").absolutePath), cmd)
    }

    @Test
    fun linuxUsesXdgOpen() {
        val cmd = installerCommand("Linux", File("/tmp/pebo_1.1.0_amd64.deb"))
        assertEquals(listOf("xdg-open", File("/tmp/pebo_1.1.0_amd64.deb").absolutePath), cmd)
    }
}
