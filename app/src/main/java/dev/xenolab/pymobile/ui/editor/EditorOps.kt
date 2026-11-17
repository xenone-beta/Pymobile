package dev.xenolab.pymobile.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.xenolab.pymobile.editor.EditActions
import dev.xenolab.pymobile.editor.EditState
import dev.xenolab.pymobile.editor.Snippets
import dev.xenolab.pymobile.editor.UndoStack
import dev.xenolab.pymobile.project.EditorTab

/** Everything that changes the text of a tab goes through here so undo and auto-indent stay consistent. */
object EditorOps {

    private fun snap(v: TextFieldValue) = UndoStack.Snapshot(v.text, v.selection.min, v.selection.max)

    private fun valueOf(s: EditState) = TextFieldValue(s.text, TextRange(s.start, s.end))

    fun apply(tab: EditorTab, s: EditState) {
        val old = tab.value
        val next = valueOf(s)
        if (next.text != old.text) {
            tab.undo.record(snap(old), UndoStack.Snapshot(next.text, next.selection.start, next.selection.end), System.currentTimeMillis())
        }
        tab.value = next
    }

    private fun stateOf(v: TextFieldValue) = EditState(v.text, v.selection.start, v.selection.end)

    /** The value the text field reports after the user typed; may be rewritten for indentation and pairs. */
    fun userChange(tab: EditorTab, new: TextFieldValue, autoPair: Boolean) {
        val old = tab.value
        if (new.text == old.text) {
            tab.value = new
            return
        }
        val sel = old.selection
        val lo = sel.min
        val hi = sel.max
        val replacedWithOne = new.text.length == old.text.length - (hi - lo) + 1 &&
            new.text.startsWith(old.text.substring(0, lo)) &&
            new.text.endsWith(old.text.substring(hi)) &&
            new.selection.collapsed && new.selection.start == lo + 1
        if (replacedWithOne) {
            val ch = new.text[lo]
            val before = stateOf(old)
            if (ch == '\n') {
                apply(tab, EditActions.onEnter(before))
                return
            }
            if (autoPair) {
                val handled = EditActions.onChar(before, ch)
                if (handled != null) {
                    apply(tab, handled)
                    return
                }
            }
        } else if (autoPair && sel.collapsed && new.text.length == old.text.length - 1 &&
            new.selection.collapsed && new.selection.start == lo - 1 && lo > 0 &&
            new.text == old.text.removeRange(lo - 1, lo)
        ) {
            val handled = EditActions.onBackspace(stateOf(old))
            if (handled != null) {
                apply(tab, handled)
                return
            }
        }
        tab.undo.record(snap(old), UndoStack.Snapshot(new.text, new.selection.start, new.selection.end), System.currentTimeMillis())
        tab.value = new
    }

    /** Types [ch] at the caret exactly as the keyboard would (extra key row). */
    fun typeChar(tab: EditorTab, ch: Char, autoPair: Boolean) {
        val v = tab.value
        val text = v.text.substring(0, v.selection.min) + ch + v.text.substring(v.selection.max)
        userChange(tab, TextFieldValue(text, TextRange(v.selection.min + 1)), autoPair)
    }

    fun indent(tab: EditorTab) = apply(tab, EditActions.indent(stateOf(tab.value)))
    fun outdent(tab: EditorTab) = apply(tab, EditActions.outdent(stateOf(tab.value)))
    fun toggleComment(tab: EditorTab) = apply(tab, EditActions.toggleComment(stateOf(tab.value)))
    fun left(tab: EditorTab) = move(tab, EditActions.left(stateOf(tab.value)))
    fun right(tab: EditorTab) = move(tab, EditActions.right(stateOf(tab.value)))
    fun up(tab: EditorTab) = move(tab, EditActions.up(stateOf(tab.value)))
    fun down(tab: EditorTab) = move(tab, EditActions.down(stateOf(tab.value)))

    private fun move(tab: EditorTab, s: EditState) {
        tab.value = valueOf(s)
    }

    fun undo(tab: EditorTab) {
        val v = tab.value
        val s = tab.undo.undo(snap(v)) ?: return
        tab.value = TextFieldValue(s.text, TextRange(s.start, s.end))
    }

    fun redo(tab: EditorTab) {
        val v = tab.value
        val s = tab.undo.redo(snap(v)) ?: return
        tab.value = TextFieldValue(s.text, TextRange(s.start, s.end))
    }

    /** Inserts a snippet at the caret, aligned with the current line; the caret lands on its cursor marker. */
    fun insertSnippet(tab: EditorTab, body: String) {
        val v = tab.value
        val lo = v.selection.min
        val lineStart = v.text.lastIndexOf('\n', lo - 1) + 1
        val indent = v.text.substring(lineStart, lo).takeWhile { it == ' ' || it == '\t' }
        val (text, caret) = Snippets.render(body, indent)
        val newText = v.text.substring(0, lo) + text + v.text.substring(v.selection.max)
        apply(tab, EditState(newText, lo + caret, lo + caret))
    }

    fun moveCaretToLine(tab: EditorTab, line: Int) {
        val offset = EditActions.offsetOfLine(tab.value.text, line - 1)
        tab.value = tab.value.copy(selection = TextRange(offset))
    }
}
