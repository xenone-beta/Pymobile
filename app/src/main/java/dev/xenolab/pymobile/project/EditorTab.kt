package dev.xenolab.pymobile.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.xenolab.pymobile.editor.Issue
import dev.xenolab.pymobile.editor.Span
import dev.xenolab.pymobile.editor.UndoStack
import java.io.File

/** Cached highlighting for one file, so typing does not scan the whole text twice. */
class HighlightCache {
    private var text: String? = null
    private var spans: List<Span> = emptyList()

    fun spansFor(source: String, compute: (String) -> List<Span>): List<Span> {
        if (source != text) {
            spans = compute(source)
            text = source
        }
        return spans
    }
}

/** One open file: its text and caret, what is saved on disk, undo history and current problems. */
class EditorTab(val file: File, text: String) {
    var value by mutableStateOf(TextFieldValue(text, TextRange(0)))
    var saved by mutableStateOf(text)
    var issues by mutableStateOf<List<Issue>>(emptyList())
    val undo = UndoStack()
    val highlight = HighlightCache()

    val dirty: Boolean get() = value.text != saved
    val title: String get() = file.name
}
