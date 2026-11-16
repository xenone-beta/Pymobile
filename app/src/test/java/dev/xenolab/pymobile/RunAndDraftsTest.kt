package dev.xenolab.pymobile

import dev.xenolab.pymobile.project.Drafts
import dev.xenolab.pymobile.python.FileTail
import dev.xenolab.pymobile.python.RunConfigs
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RunAndDraftsTest {
    private lateinit var dir: File

    @Before fun setUp() {
        dir = Files.createTempDirectory("pymobile-run").toFile()
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun splitsArgumentsLikeAShell() {
        assertEquals(listOf("a", "b", "c"), RunConfigs.splitArgs("a  b\tc"))
        assertEquals(listOf("--name", "my file.txt"), RunConfigs.splitArgs("--name \"my file.txt\""))
        assertEquals(listOf("it's", "x y"), RunConfigs.splitArgs("it\\'s 'x y'"))
        assertEquals(listOf("a\"b"), RunConfigs.splitArgs("\"a\\\"b\""))
        assertEquals(listOf(""), RunConfigs.splitArgs("\"\""))
        assertEquals(emptyList<String>(), RunConfigs.splitArgs("   "))
    }

    @Test fun unclosedQuoteIsRejected() {
        assertNull(RunConfigs.splitArgs("a \"b"))
        assertNull(RunConfigs.splitArgs("'x"))
    }

    @Test fun parsesEnvironmentLines() {
        val env = RunConfigs.parseEnv("# comment\nTOKEN=abc123\n\nNAME = \"Ann Lee\"\nEMPTY=\nURL=http://x/?a=b")
        assertNotNull(env)
        assertEquals("abc123", env!!["TOKEN"])
        assertEquals("Ann Lee", env["NAME"])
        assertEquals("", env["EMPTY"])
        assertEquals("http://x/?a=b", env["URL"])
        assertEquals(listOf("TOKEN", "NAME", "EMPTY", "URL"), env.keys.toList())
    }

    @Test fun rejectsBadEnvironmentLines() {
        assertNull(RunConfigs.parseEnv("novalue"))
        assertNull(RunConfigs.parseEnv("=x"))
        assertNull(RunConfigs.parseEnv("1BAD=x"))
        assertNull(RunConfigs.parseEnv("A B=x"))
        assertNull(RunConfigs.parseEnv("PYTHONHOME=/tmp"))
        assertNull(RunConfigs.parseEnv("PYTHONUSERBASE=/tmp"))
        assertTrue(RunConfigs.parseEnv("")!!.isEmpty())
    }

    @Test fun tailReadsOnlyNewBytes() {
        val f = File(dir, "out.log")
        val tail = FileTail(f)
        assertEquals("", tail.read())
        f.writeText("one\n")
        assertEquals("one\n", tail.read())
        assertEquals("", tail.read())
        f.appendText("two\n")
        assertEquals("two\n", tail.read())
        assertTrue(tail.caughtUp())
    }

    @Test fun tailDoesNotSplitMultiByteCharacters() {
        val f = File(dir, "u.log")
        val tail = FileTail(f)
        val bytes = "ab€".toByteArray(Charsets.UTF_8) // € is three bytes
        f.writeBytes(bytes.copyOfRange(0, bytes.size - 1))
        assertEquals("ab", tail.read())
        f.appendBytes(bytes.copyOfRange(bytes.size - 1, bytes.size))
        assertEquals("€", tail.read())
    }

    @Test fun tailStartsOverAfterTruncation() {
        val f = File(dir, "t.log")
        val tail = FileTail(f)
        f.writeText("a lot of text\n")
        tail.read()
        f.writeText("x")
        assertEquals("x", tail.read())
    }

    @Test fun completePrefixHandlesPlainAndPartialBytes() {
        assertEquals(3, FileTail.completeUtf8Prefix("abc".toByteArray()))
        val euro = "€".toByteArray(Charsets.UTF_8)
        assertEquals(0, FileTail.completeUtf8Prefix(euro.copyOfRange(0, 1)))
        assertEquals(0, FileTail.completeUtf8Prefix(euro.copyOfRange(0, 2)))
        assertEquals(3, FileTail.completeUtf8Prefix(euro))
    }

    @Test fun draftsRoundTripPerPath() {
        val drafts = Drafts(File(dir, "drafts"))
        assertNull(drafts.read("/a/main.py"))
        assertTrue(drafts.write("/a/main.py", "print('hi')\né"))
        assertTrue(drafts.write("/b/main.py", "other"))
        assertEquals("print('hi')\né", drafts.read("/a/main.py"))
        assertEquals("other", drafts.read("/b/main.py"))
        drafts.write("/a/main.py", "newer")
        assertEquals("newer", drafts.read("/a/main.py"))
        drafts.delete("/a/main.py")
        assertNull(drafts.read("/a/main.py"))
        assertEquals("other", drafts.read("/b/main.py"))
    }
}
