package dev.xenolab.pymobile

import dev.xenolab.pymobile.project.ProjectSearch
import dev.xenolab.pymobile.project.SearchOptions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectSearchTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun project(): File {
        val root = tmp.newFolder("p")
        File(root, "a.py").writeText("import os\n\ndef Hello(name):\n    print('hello', name)\n\nclass Hello2:\n    pass\n")
        File(root, "sub").mkdirs()
        File(root, "sub/b.txt").writeText("say hello\nHELLO again\nnothing\n")
        File(root, "__pycache__").mkdirs()
        File(root, "__pycache__/c.txt").writeText("hello hidden\n")
        File(root, "bin.dat").writeBytes(byteArrayOf(1, 0, 2, 0, 3))
        return root
    }

    @Test fun findsMatchesIgnoringCaseAndCaches() {
        val r = ProjectSearch.search(project(), SearchOptions("hello"))
        assertNull(r.error)
        assertEquals(5, r.matches.size)
        assertEquals(setOf("a.py", "b.txt"), r.matches.map { it.file.name }.toSet())
        assertEquals(2, r.files)
        assertTrue(r.matches.none { it.file.name == "c.txt" })
    }

    @Test fun reportsLineAndColumn() {
        val m = ProjectSearch.search(project(), SearchOptions("name", caseSensitive = true)).matches
        assertEquals(2, m.size)
        assertEquals(3, m[0].line)
        assertEquals("def Hello(name):", m[0].text)
        assertEquals(10, m[0].column)
        assertEquals(4, m[0].length)
    }

    @Test fun matchCaseAndWholeWord() {
        val root = project()
        assertEquals(1, ProjectSearch.search(root, SearchOptions("HELLO", caseSensitive = true)).matches.size)
        val whole = ProjectSearch.search(root, SearchOptions("Hello", caseSensitive = true, wholeWord = true)).matches
        assertEquals(listOf(3), whole.map { it.line })
    }

    @Test fun definitionsOnly() {
        val d = ProjectSearch.search(project(), SearchOptions("hello", definitionsOnly = true)).matches
        assertEquals(listOf(3, 6), d.map { it.line })
    }

    @Test fun regexAndBadRegex() {
        val root = project()
        assertEquals(2, ProjectSearch.search(root, SearchOptions("^say|again$", regex = true)).matches.size)
        val bad = ProjectSearch.search(root, SearchOptions("(", regex = true))
        assertNotNull(bad.error)
        assertTrue(bad.matches.isEmpty())
        assertEquals(0, ProjectSearch.search(root, SearchOptions("")).matches.size)
    }

    @Test fun plainQueryTreatsSpecialCharactersLiterally() {
        val root = tmp.newFolder("q")
        File(root, "x.txt").writeText("a.b\naxb\n")
        assertEquals(listOf(1), ProjectSearch.search(root, SearchOptions("a.b")).matches.map { it.line })
    }

    @Test fun replacesEverywhereAndCountsReplacements() {
        val root = project()
        val n = ProjectSearch.replaceAll(root, SearchOptions("hello", caseSensitive = true), "hi")
        assertEquals(2, n) // the print() in a.py and "say hello" in b.txt; case-sensitive skips Hello and HELLO
        assertTrue(File(root, "a.py").readText().contains("print('hi', name)"))
        assertTrue(File(root, "a.py").readText().contains("def Hello(name)"))
        assertEquals("say hi\nHELLO again\nnothing\n", File(root, "sub/b.txt").readText())
        assertEquals("hello hidden\n", File(root, "__pycache__/c.txt").readText())
    }

    @Test fun replacementIsLiteralUnlessRegex() {
        val root = tmp.newFolder("r")
        File(root, "x.txt").writeText("price: 5\n")
        ProjectSearch.replaceAll(root, SearchOptions("5"), "\$1 and \\n")
        assertEquals("price: \$1 and \\n\n", File(root, "x.txt").readText())
        File(root, "y.txt").writeText("ab\n")
        ProjectSearch.replaceAll(root, SearchOptions("(a)(b)", regex = true), "\$2\$1")
        assertEquals("ba\n", File(root, "y.txt").readText())
    }

    @Test fun stopsAtTheLimit() {
        val root = tmp.newFolder("big")
        File(root, "many.txt").writeText("x\n".repeat(ProjectSearch.MAX_MATCHES + 20))
        val r = ProjectSearch.search(root, SearchOptions("x"))
        assertEquals(ProjectSearch.MAX_MATCHES, r.matches.size)
        assertTrue(r.truncated)
    }
}
