package dev.xenolab.pymobile

import dev.xenolab.pymobile.theme.Palette
import dev.xenolab.pymobile.theme.PaletteKey
import dev.xenolab.pymobile.theme.Palettes
import dev.xenolab.pymobile.theme.Pmt
import dev.xenolab.pymobile.theme.ThemeStore
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeTest {
    private lateinit var dir: File

    @Before fun setUp() {
        dir = Files.createTempDirectory("pymobile-theme").toFile()
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun parsesColorForms() {
        assertEquals(0xFF112233.toInt(), Pmt.parseColor("#112233"))
        assertEquals(0xFFAABBCC.toInt(), Pmt.parseColor("#abc"))
        assertEquals(0x80112233.toInt(), Pmt.parseColor("#80112233"))
        assertNull(Pmt.parseColor("112233"))
        assertNull(Pmt.parseColor("#12"))
        assertNull(Pmt.parseColor("#GGGGGG"))
        assertNull(Pmt.parseColor(""))
    }

    @Test fun formatsColors() {
        assertEquals("#112233", Pmt.formatColor(0xFF112233.toInt()))
        assertEquals("#80112233", Pmt.formatColor(0x80112233.toInt()))
    }

    @Test fun everyBuiltInThemeIsComplete() {
        assertTrue(Palettes.builtIn.size >= 7)
        for ((id, p) in Palettes.builtIn) {
            assertTrue(id, id.isNotEmpty())
            assertEquals(id, PaletteKey.entries.size, p.colors.size)
        }
        assertEquals(Palettes.builtIn.size, Palettes.builtIn.map { it.first }.toSet().size)
    }

    @Test fun writeThenParseRoundTrips() {
        val original = Palettes.byId("dracula")!!.copy(name = "My Dracula", author = "me")
        val result = Pmt.parse(Pmt.write(original))
        assertTrue(result is Pmt.Result.Ok)
        val parsed = (result as Pmt.Result.Ok).palette
        assertEquals("My Dracula", parsed.name)
        assertEquals("me", parsed.author)
        assertEquals(original.dark, parsed.dark)
        assertEquals(original.colors, parsed.colors)
    }

    @Test fun missingColorsFallBackToTheBase() {
        val text = """{"pmt":1,"name":"Tiny","dark":false,"colors":{"background":"#FFEEDD"}}"""
        val p = (Pmt.parse(text) as Pmt.Result.Ok).palette
        assertEquals(0xFFFFEEDD.toInt(), p[PaletteKey.BACKGROUND])
        assertEquals(Palettes.base(false)[PaletteKey.KEYWORD], p[PaletteKey.KEYWORD])
        assertEquals(PaletteKey.entries.size, p.colors.size)
    }

    @Test fun rejectsBrokenFiles() {
        assertTrue(Pmt.parse("not json") is Pmt.Result.Error)
        assertTrue(Pmt.parse("""{"name":"x"}""") is Pmt.Result.Error)
        assertTrue(Pmt.parse("""{"pmt":1,"name":" "}""") is Pmt.Result.Error)
        assertTrue(Pmt.parse("""{"pmt":99,"name":"x"}""") is Pmt.Result.Error)
        val bad = Pmt.parse("""{"pmt":1,"name":"x","colors":{"keyword":"red"}}""")
        assertTrue(bad is Pmt.Result.Error)
        assertTrue((bad as Pmt.Result.Error).message.contains("keyword"))
        assertTrue(Pmt.parse("x".repeat(Pmt.MAX_BYTES + 1)) is Pmt.Result.Error)
    }

    @Test fun storeSavesListsReadsAndDeletes() {
        val store = ThemeStore(File(dir, "themes"))
        assertTrue(store.list().isEmpty())
        val palette = Palettes.byId("monokai")!!.copy(name = "Night Owl!")
        val id = store.save(palette)
        assertEquals("user:night-owl", id)
        val second = store.save(palette)
        assertEquals("user:night-owl-2", second)
        assertEquals(2, store.list().size)
        assertEquals("Night Owl!", store.read(id!!)!!.name)
        val changed = palette.with(PaletteKey.KEYWORD, 0xFF010203.toInt())
        assertEquals(id, store.save(changed, replaceId = id))
        assertEquals(0xFF010203.toInt(), store.read(id)!![PaletteKey.KEYWORD])
        assertTrue(store.delete(id))
        assertNull(store.read(id))
        assertEquals(1, store.list().size)
    }

    @Test fun storeIgnoresBadIdsAndBadFiles() {
        val store = ThemeStore(File(dir, "themes"))
        assertNull(store.read("user:../evil"))
        assertNull(store.read("monokai"))
        assertTrue(!store.delete("user:a/b"))
        File(dir, "themes").mkdirs()
        File(dir, "themes/broken.pmt").writeText("{oops")
        assertTrue(store.list().isEmpty())
        assertNotNull(store.save(Palettes.base(true)))
    }

    @Test fun slugs() {
        assertEquals("my-theme", ThemeStore.slug("  My  Theme!! "))
        assertEquals("theme", ThemeStore.slug("!!!"))
    }

    @Test fun paletteHelpers() {
        val p: Palette = Palettes.base(true)
        val q = p.with(PaletteKey.TEXT, 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), q[PaletteKey.TEXT])
        assertEquals(p[PaletteKey.STRING], q[PaletteKey.STRING])
    }
}
