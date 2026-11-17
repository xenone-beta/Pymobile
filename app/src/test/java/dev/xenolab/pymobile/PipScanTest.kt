package dev.xenolab.pymobile

import dev.xenolab.pymobile.python.Pip
import dev.xenolab.pymobile.python.PipPackage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PipScanTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun readsDistInfoFolders() {
        val site = tmp.newFolder("site")
        File(site, "pip-24.0.dist-info").mkdirs()
        File(site, "Flask_Cors-4.0.1.dist-info").mkdirs()
        File(site, "Flask_Cors-4.0.1.dist-info/METADATA").writeText("Metadata-Version: 2.1\nName: Flask-Cors\nVersion: 4.0.1\n")
        File(site, "requests").mkdirs()
        File(site, "junk.dist-info").mkdirs()
        assertEquals(
            listOf(PipPackage("Flask-Cors", "4.0.1"), PipPackage("pip", "24.0")),
            Pip.scanInstalled(listOf(site, File(site, "missing"))),
        )
    }

    @Test fun mergesFoldersWithoutDuplicates() {
        val a = tmp.newFolder("a")
        val b = tmp.newFolder("b")
        File(a, "x_y-1.0.dist-info").mkdirs()
        File(b, "x-y-1.0.dist-info").mkdirs()
        assertEquals(1, Pip.scanInstalled(listOf(a, b)).size)
    }

    @Test fun splitsNames() {
        assertEquals("a_b" to "1.2.post1", Pip.parseDistInfoName("a_b-1.2.post1"))
        assertNull(Pip.parseDistInfoName("nodash"))
        assertNull(Pip.parseDistInfoName("-1.0"))
    }
}
