package dev.xenolab.pymobile

import dev.xenolab.pymobile.editor.Snippet
import dev.xenolab.pymobile.editor.Snippets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetsTest {

    @Test fun builtinTriggersAreUniqueAndBodiesUseOneCursorAtMost() {
        val triggers = Snippets.builtin.map { it.trigger }
        assertEquals(triggers.size, triggers.toSet().size)
        for (s in Snippets.builtin) {
            assertTrue(s.trigger, s.body.split(Snippets.CURSOR).size <= 2)
            assertFalse(s.trigger, s.body.contains('\t'))
            assertTrue(s.builtin)
        }
    }

    @Test fun placesTheCaretAtTheMarker() {
        val (text, caret) = Snippets.render("def \$0():\n    pass\n", "")
        assertEquals("def ():\n    pass\n", text)
        assertEquals(4, caret)
    }

    @Test fun indentsContinuationLines() {
        val (text, caret) = Snippets.render("if x:\n    \$0\nelse:\n    pass", "    ")
        assertEquals("if x:\n        \n    else:\n        pass", text)
        assertEquals("if x:\n        ".length, caret)
    }

    @Test fun withoutAMarkerTheCaretGoesToTheEnd() {
        val (text, caret) = Snippets.render("a\nb", "  ")
        assertEquals("a\n  b", text)
        assertEquals(text.length, caret)
    }

    @Test fun extraMarkersAreDropped() {
        assertEquals("ab", Snippets.render("a\$0b\$0", "").first)
    }

    @Test fun jsonRoundTripAndBadInput() {
        val list = listOf(Snippet("p", "Print", "print(\$0)"), Snippet("x", "Two\nlines", "a\nb"))
        assertEquals(list, Snippets.fromJson(Snippets.toJson(list)))
        assertTrue(Snippets.fromJson("not json").isEmpty())
        assertTrue(Snippets.fromJson("""[{"trigger":"t","title":"T","body":""}, 5]""").isEmpty())
        assertEquals("t", Snippets.fromJson("""[{"trigger":"t","body":"x"}]""").single().title)
    }
}
