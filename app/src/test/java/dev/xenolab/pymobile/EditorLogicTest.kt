package dev.xenolab.pymobile

import dev.xenolab.pymobile.editor.Completion
import dev.xenolab.pymobile.editor.ConsoleBuffer
import dev.xenolab.pymobile.editor.EditActions
import dev.xenolab.pymobile.editor.EditState
import dev.xenolab.pymobile.editor.Formatter
import dev.xenolab.pymobile.editor.Lint
import dev.xenolab.pymobile.editor.PyLexer
import dev.xenolab.pymobile.editor.Severity
import dev.xenolab.pymobile.editor.TokenType
import dev.xenolab.pymobile.editor.Tracebacks
import dev.xenolab.pymobile.editor.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLogicTest {

    @Test fun lexerFindsKeywordsFunctionsAndComments() {
        val src = "def foo():\n    return 1  # note\n"
        val spans = PyLexer.highlight(src)
        fun has(type: TokenType, word: String) = spans.any { it.type == type && src.substring(it.start, it.end).startsWith(word) }
        assertTrue(has(TokenType.KEYWORD, "def"))
        assertTrue(has(TokenType.FUNCTION, "foo"))
        assertTrue(has(TokenType.KEYWORD, "return"))
        assertTrue(has(TokenType.NUMBER, "1"))
        assertTrue(has(TokenType.COMMENT, "# note"))
    }

    @Test fun lexerTreatsTripleQuotedTextAsOneString() {
        val src = "x = \"\"\"a\nb # not a comment\n\"\"\"\n"
        val spans = PyLexer.highlight(src)
        assertTrue(spans.none { it.type == TokenType.COMMENT })
        assertTrue(spans.any { it.type == TokenType.STRING && src.substring(it.start, it.end).contains("not a comment") })
    }

    @Test fun enterKeepsAndAddsIndentation() {
        val s = EditActions.onEnter(EditState("if x:", 5))
        assertEquals("if x:\n    ", s.text)
        assertEquals(s.text.length, s.start)
        val t = EditActions.onEnter(EditState("    y = 1", 9))
        assertEquals("    y = 1\n    ", t.text)
    }

    @Test fun bracketsArePairedAndTypedOver() {
        val paired = EditActions.onChar(EditState("print", 5), '(')
        assertNotNull(paired)
        assertEquals("print()", paired!!.text)
        assertEquals(6, paired.start)
        val over = EditActions.onChar(EditState("print()", 6), ')')
        assertNotNull(over)
        assertEquals("print()", over!!.text)
        assertEquals(7, over.start)
    }

    @Test fun backspaceRemovesEmptyPair() {
        val s = EditActions.onBackspace(EditState("f()", 2))
        assertNotNull(s)
        assertEquals("f", s!!.text)
    }

    @Test fun toggleCommentRoundTrips() {
        val on = EditActions.toggleComment(EditState("a = 1", 0))
        assertTrue(on.text.startsWith("#"))
        val off = EditActions.toggleComment(EditState(on.text, 0))
        assertEquals("a = 1", off.text)
    }

    @Test fun lineAndColumn() {
        val t = "ab\ncd\nef"
        assertEquals(1, EditActions.lineOf(t, 4))
        assertEquals(1, EditActions.columnOf(t, 4))
        assertEquals(3, EditActions.offsetOfLine(t, 1))
    }

    @Test fun formatterTrimsAndEndsWithNewline() {
        assertEquals("x = 1\n", Formatter.format("x = 1   "))
        assertEquals("def f():\n    pass\n", Formatter.format("def f():\n\tpass\n"))
    }

    @Test fun formatterKeepsMultilineStringsIntact() {
        val src = "s = \"\"\"a  \n  b\"\"\"\n"
        assertEquals(src, Formatter.format(src))
    }

    @Test fun formatterIsIdempotent() {
        val src = "import os\ndef f():\n    return 1\nclass A:\n    pass\n"
        val once = Formatter.format(src)
        assertEquals(once, Formatter.format(once))
    }

    @Test fun lintAcceptsValidCode() {
        assertTrue(Lint.check("x = [1, 2, 3]\nprint(x)\n").none { it.severity == Severity.ERROR })
    }

    @Test fun lintFindsUnclosedBracketAndMissingColon() {
        assertTrue(Lint.check("print((1)\n").any { it.severity == Severity.ERROR })
        assertTrue(Lint.check("if x\n    pass\n").any { it.severity == Severity.ERROR })
    }

    @Test fun lintIgnoresBracketsInStringsAndComments() {
        assertTrue(Lint.check("s = \"(\"  # [\n").isEmpty())
    }

    @Test fun undoAndRedoRestoreSnapshots() {
        val u = UndoStack()
        val a = UndoStack.Snapshot("a", 1, 1)
        val b = UndoStack.Snapshot("ab", 2, 2)
        u.record(a, b, 0)
        assertTrue(u.canUndo)
        assertEquals(a, u.undo(b))
        assertTrue(u.canRedo)
        assertEquals(b, u.redo(a))
    }

    @Test fun typingWithinOneSecondIsOneUndoStep() {
        val u = UndoStack()
        u.record(UndoStack.Snapshot("", 0, 0), UndoStack.Snapshot("a", 1, 1), 0)
        u.record(UndoStack.Snapshot("a", 1, 1), UndoStack.Snapshot("ab", 2, 2), 200)
        val back = u.undo(UndoStack.Snapshot("ab", 2, 2))
        assertEquals("", back!!.text)
        assertNull(u.undo(back))
    }

    @Test fun tracebackLocationIsParsed() {
        val loc = Tracebacks.find("  File \"/sdcard/Pymobile/app/main.py\", line 12, in <module>")
        assertNotNull(loc)
        assertEquals("/sdcard/Pymobile/app/main.py", loc!!.file)
        assertEquals(12, loc.line)
        assertNull(Tracebacks.find("nothing to see"))
    }

    @Test fun consoleSplitsLinesAndHandlesCarriageReturn() {
        val c = ConsoleBuffer()
        c.append("one\ntwo\n")
        c.append("50%\r100%\n")
        val text = c.snapshot().map { it.text }
        assertTrue(text.contains("one"))
        assertTrue(text.contains("two"))
        assertTrue(text.contains("100%"))
        assertTrue(text.none { it.contains("50%") })
    }

    @Test fun consoleParsesColorsAcrossChunks() {
        val c = ConsoleBuffer()
        c.append("\u001B[3")
        c.append("1mred\u001B[0m plain\n")
        val line = c.snapshot().first()
        assertEquals("red plain", line.text)
        assertTrue(line.segments.first().color != 0)
        assertEquals(0, line.segments.last().color)
    }

    @Test fun consoleMarksErrorOutput() {
        val c = ConsoleBuffer()
        c.append("boom\n", isError = true)
        assertTrue(c.snapshot().first().segments.first().error)
    }

    @Test fun completionOffersModulesAndWords() {
        val modules = Completion.suggest("import ra", 9, emptyList())
        assertNotNull(modules)
        assertTrue("random" in modules!!.items)
        assertNull(Completion.suggest("import r", 8, emptyList()))
        val src = "counter_value = 1\ncoun"
        val words = Completion.suggest(src, src.length, PyLexer.highlight(src))
        assertNotNull(words)
        assertTrue("counter_value" in words!!.items)
        val applied = Completion.apply(src, src.length, words, "counter_value")
        assertTrue(applied.text.endsWith("\ncounter_value"))
    }
}
