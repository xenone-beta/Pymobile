package dev.xenolab.pymobile

import dev.xenolab.pymobile.git.GitCredentials
import dev.xenolab.pymobile.git.GitService
import dev.xenolab.pymobile.project.FileError
import dev.xenolab.pymobile.project.ProjectFiles
import dev.xenolab.pymobile.python.Pip
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectAndPipTest {
    private lateinit var dir: File

    @Before fun setUp() {
        dir = Files.createTempDirectory("pymobile-test").toFile()
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun createsFilesFoldersAndRejectsBadNames() {
        assertTrue(ProjectFiles.create(dir, "a.py", folder = false).ok)
        assertTrue(File(dir, "a.py").isFile)
        assertEquals(FileError.EXISTS, ProjectFiles.create(dir, "a.py", folder = false).error)
        assertEquals(FileError.BAD_NAME, ProjectFiles.create(dir, "x/y", folder = false).error)
        assertEquals(FileError.BAD_NAME, ProjectFiles.create(dir, "..", folder = true).error)
        assertTrue(ProjectFiles.create(dir, "pkg", folder = true).ok)
        assertTrue(File(dir, "pkg").isDirectory)
    }

    @Test fun projectGetsStarterFile() {
        val made = ProjectFiles.createProject(dir, "demo")
        assertTrue(made.ok)
        assertEquals(ProjectFiles.STARTER_SOURCE, File(made.file, "main.py").readText())
    }

    @Test fun listingPutsFoldersFirst() {
        File(dir, "b.py").writeText("")
        File(dir, "z").mkdir()
        File(dir, "a.py").writeText("")
        assertEquals(listOf("z", "a.py", "b.py"), ProjectFiles.list(dir).map { it.name })
    }

    @Test fun writeIsAtomicAndReadNormalizesLineEndings() {
        val f = File(dir, "t.py")
        assertTrue(ProjectFiles.write(f, "a\nb\n"))
        assertEquals("a\nb\n", ProjectFiles.read(f))
        f.writeText("a\r\nb\r\n")
        assertEquals("a\nb\n", ProjectFiles.read(f))
        assertEquals(listOf("t.py"), dir.list()!!.toList())
    }

    @Test fun renameAndDelete() {
        val f = File(dir, "old.py").apply { writeText("x") }
        val op = ProjectFiles.rename(f, "new.py")
        assertTrue(op.ok)
        assertFalse(f.exists())
        assertTrue(File(dir, "new.py").exists())
        assertTrue(ProjectFiles.delete(File(dir, "new.py")))
        assertFalse(File(dir, "new.py").exists())
    }

    @Test fun binaryFilesAreNotEditable() {
        val f = File(dir, "blob.dat").apply { writeBytes(byteArrayOf(0, 1, 2, 0, 3)) }
        assertFalse(ProjectFiles.isEditable(f))
        assertTrue(ProjectFiles.isEditable(File(dir, "x.py").apply { writeText("print(1)") }))
    }

    @Test fun relativePaths() {
        assertEquals("a/b.py", ProjectFiles.relative(File("/r"), File("/r/a/b.py")))
    }

    @Test fun pipRequirementsAreValidated() {
        assertEquals(listOf("requests", "flask==2.0.1"), Pip.parseRequirements("requests flask==2.0.1"))
        assertEquals(listOf("urllib3>=1.26,<3"), Pip.parseRequirements("urllib3>=1.26,<3"))
        assertNull(Pip.parseRequirements(""))
        assertNull(Pip.parseRequirements("--index-url=http://evil"))
        assertNull(Pip.parseRequirements("requests; rm -rf /"))
        assertNull(Pip.parseRequirements("git+https://x/y.git"))
    }

    @Test fun pipArguments() {
        assertEquals(listOf("-m", "pip", "install", "--upgrade", "requests"), Pip.installArgs(listOf("requests"), upgrade = true))
        assertEquals(listOf("-m", "pip", "uninstall", "-y", "requests"), Pip.uninstallArgs("requests"))
    }

    @Test fun pipListIsParsedEvenWithNoise() {
        val out = "WARNING: something\n[{\"name\": \"requests\", \"version\": \"2.32.3\"}, {\"name\": \"idna\", \"version\": \"3.7\"}]\n"
        val list = Pip.parseList(out)
        assertEquals(2, list.size)
        assertEquals("idna", list[0].name)
        assertEquals("2.32.3", list.first { it.name == "requests" }.version)
        assertTrue(Pip.parseList("garbage").isEmpty())
    }

    @Test fun gitUrlHelpers() {
        assertTrue(GitService.isSupportedUrl("https://github.com/a/b.git"))
        assertFalse(GitService.isSupportedUrl("git@github.com:a/b.git"))
        assertFalse(GitService.isSupportedUrl("http://github.com/a/b"))
        assertEquals("b", GitService.repoNameFromUrl("https://github.com/a/b.git"))
        assertEquals("b", GitService.repoNameFromUrl("https://github.com/a/b/"))
    }

    @Test fun gitInitCommitAndLog() {
        val git = GitService()
        val repo = File(dir, "repo").apply { mkdirs() }
        assertFalse(git.isRepo(repo))
        git.init(repo)
        assertTrue(git.isRepo(repo))
        File(repo, "main.py").writeText("print(1)\n")
        val before = git.status(repo)
        assertTrue(before.isRepo)
        assertEquals(1, before.changes.size)
        val id = git.commitAll(repo, "first", "Tester", "tester@example.com")
        assertTrue(id.isNotBlank())
        assertTrue(git.status(repo).changes.isEmpty())
        val log = git.log(repo)
        assertEquals(1, log.size)
        assertEquals("first", log[0].message.trim())
        assertNotNull(GitCredentials("token", "x"))
    }
}
