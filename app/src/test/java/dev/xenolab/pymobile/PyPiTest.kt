package dev.xenolab.pymobile

import dev.xenolab.pymobile.pypi.PyPi
import dev.xenolab.pymobile.pypi.SearchHit
import dev.xenolab.pymobile.pypi.VersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PyPiTest {

    @Test fun normalizesProjectNames() {
        assertEquals("foo-bar-baz", PyPi.normalize("Foo_Bar.baz"))
        assertEquals("requests", PyPi.normalize("  Requests "))
    }

    @Test fun recognizesNames() {
        assertTrue(PyPi.looksLikeName("requests"))
        assertTrue(PyPi.looksLikeName("python-telegram-bot"))
        assertFalse(PyPi.looksLikeName("http client"))
        assertFalse(PyPi.looksLikeName("-bad"))
        assertFalse(PyPi.looksLikeName(""))
    }

    @Test fun ordersVersionsNewestFirst() {
        val sorted = listOf("1.9", "2.0b1", "1.10", "2.0", "1.9.1").sortedWith(PyPi.newestFirst)
        assertEquals(listOf("2.0", "2.0b1", "1.10", "1.9.1", "1.9"), sorted)
    }

    @Test fun detectsPrereleases() {
        assertTrue(PyPi.isPrerelease("2.0rc1"))
        assertTrue(PyPi.isPrerelease("1.0a3"))
        assertTrue(PyPi.isPrerelease("3.0.dev1"))
        assertFalse(PyPi.isPrerelease("1.0"))
        assertFalse(PyPi.isPrerelease("1.0.post2"))
    }

    @Test fun readsVersionsFromFileNames() {
        assertEquals("1.0", PyPi.versionOfFile("foo-1.0-py3-none-any.whl"))
        assertEquals("1.5", PyPi.versionOfFile("foo-1.5-cp312-cp312-win_amd64.whl"))
        assertEquals("0.9", PyPi.versionOfFile("foo_bar-0.9.zip"))
        assertEquals("2.0rc1", PyPi.versionOfFile("foo-2.0rc1.tar.gz"))
        assertNull(PyPi.versionOfFile("readme.txt"))
    }

    @Test fun simpleIndexListsUsableVersionsAndPureWheels() {
        val json = """
            {"files":[
              {"filename":"foo-1.0-py3-none-any.whl"},
              {"filename":"foo-1.0.tar.gz"},
              {"filename":"foo-2.0rc1.tar.gz"},
              {"filename":"foo-1.5-cp312-cp312-win_amd64.whl"},
              {"filename":"foo-1.2.tar.gz","yanked":true},
              {"filename":"foo-1.3.tar.gz","yanked":"broken"},
              {"filename":"foo_bar-0.9.zip"}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(VersionInfo("1.5", false), VersionInfo("1.0", true), VersionInfo("0.9", false)),
            PyPi.parseSimple(json),
        )
    }

    @Test fun onlyPrereleasesAreShownWhenThereIsNothingElse() {
        val json = """{"files":[{"filename":"foo-1.0b1.tar.gz"},{"filename":"foo-1.0a1.tar.gz"}]}"""
        assertEquals(listOf("1.0b1", "1.0a1"), PyPi.parseSimple(json).map { it.version })
    }

    @Test fun emptyIndexGivesNoVersions() {
        assertTrue(PyPi.parseSimple("""{"files":[]}""").isEmpty())
        assertTrue(PyPi.parseSimple("""{}""").isEmpty())
    }

    @Test fun readsProjectInfoWithMissingFields() {
        val d = PyPi.parseInfo(
            """{"info":{"name":"Foo","version":"2.0","summary":null,"requires_python":">=3.8","package_url":"https://pypi.org/project/Foo/"}}""",
        )
        assertNotNull(d)
        assertEquals("Foo", d!!.name)
        assertEquals("2.0", d.latest)
        assertEquals("", d.summary)
        assertEquals(">=3.8", d.requiresPython)
        assertNull(PyPi.parseInfo("""{"info":{"version":"1"}}"""))
        assertNull(PyPi.parseInfo("""{}"""))
    }

    @Test fun readsSearchResultsPage() {
        val html = """
            <ul>
            <li><a class="package-snippet" href="/project/requests/">
              <h3 class="package-snippet__title">
                <span class="package-snippet__name">requests</span>
                <span class="package-snippet__version">2.32.3</span>
                <span class="package-snippet__created"><time>Jun 2024</time></span>
              </h3>
              <p class="package-snippet__description">Python HTTP for Humans &amp; more.</p>
            </a></li>
            <li><a class="package-snippet" href="/project/requests-toolbelt/">
              <h3 class="package-snippet__title">
                <span class="package-snippet__name">requests-toolbelt</span>
                <span class="package-snippet__version">1.0.0</span>
              </h3>
              <p class="package-snippet__description"></p>
            </a></li>
            </ul>
        """.trimIndent()
        assertEquals(
            listOf(
                SearchHit("requests", "2.32.3", "Python HTTP for Humans & more."),
                SearchHit("requests-toolbelt", "1.0.0", ""),
            ),
            PyPi.parseSearch(html),
        )
        assertTrue(PyPi.parseSearch("<html>Client Challenge</html>").isEmpty())
    }
}
