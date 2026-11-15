package dev.xenolab.pymobile.editor

/** Text plus selection. [start] and [end] are the selection ends, equal when it is just a caret. */
data class EditState(val text: String, val start: Int, val end: Int = start) {
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
    val hasSelection: Boolean get() = start != end
}

/** The small pieces of editor behaviour: indentation, bracket pairing, comments and caret moves. */
object EditActions {

    const val UNIT = "    "

    private val pairs = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')
    private const val PREFIX_LETTERS = "rRbBfFuU"
    private val dedentWords = setOf("return", "pass", "break", "continue", "raise")

    fun lineStart(text: String, index: Int): Int = text.lastIndexOf('\n', index - 1) + 1

    fun lineEnd(text: String, index: Int): Int = text.indexOf('\n', index).let { if (it < 0) text.length else it }

    /** Zero-based line of [index]. */
    fun lineOf(text: String, index: Int): Int {
        var line = 0
        for (k in 0 until index.coerceIn(0, text.length)) if (text[k] == '\n') line++
        return line
    }

    fun columnOf(text: String, index: Int): Int = index - lineStart(text, index)

    /** Offset of the start of zero-based [line] (the end of the text if there are fewer lines). */
    fun offsetOfLine(text: String, line: Int): Int {
        var offset = 0
        var current = 0
        while (current < line) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
            current++
        }
        return offset
    }

    private fun codePart(lineBeforeCaret: String): String {
        val comment = PyLexer.highlight(lineBeforeCaret).firstOrNull { it.type == TokenType.COMMENT }
        return if (comment == null) lineBeforeCaret else lineBeforeCaret.substring(0, comment.start)
    }

    private fun insideStringOrComment(text: String, index: Int): Boolean =
        PyLexer.highlight(text).any { span ->
            when (span.type) {
                TokenType.COMMENT -> index > span.start && index <= span.end
                TokenType.STRING -> index > span.start && index < span.end
                else -> false
            }
        }

    /** Enter: keeps the indentation, adds a level after ':' or an opening bracket. */
    fun onEnter(s: EditState): EditState {
        val from = s.min
        val to = s.max
        val before = s.text.substring(lineStart(s.text, from), from)
        val baseIndent = before.takeWhile { it == ' ' || it == '\t' }
        val code = codePart(before).trimEnd()
        val prev = s.text.getOrNull(from - 1)
        val next = s.text.getOrNull(to)

        if (prev != null && next != null && pairs[prev] == next && prev != '"' && prev != '\'') {
            val inner = baseIndent + UNIT
            val text = s.text.substring(0, from) + "\n" + inner + "\n" + baseIndent + s.text.substring(to)
            return EditState(text, from + 1 + inner.length)
        }

        var indent = baseIndent
        val word = code.trimStart().takeWhile { it.isLetter() }
        if (code.endsWith(":") || code.endsWith("(") || code.endsWith("[") || code.endsWith("{")) {
            indent += UNIT
        } else if (word in dedentWords && baseIndent.length >= UNIT.length && baseIndent.endsWith(UNIT)) {
            indent = baseIndent.dropLast(UNIT.length)
        }
        val text = s.text.substring(0, from) + "\n" + indent + s.text.substring(to)
        return EditState(text, from + 1 + indent.length)
    }

    /**
     * Typing [ch]: pairs brackets and quotes, and types over a closing one that is already there.
     * Returns null when the character should simply be inserted as usual.
     */
    fun onChar(s: EditState, ch: Char): EditState? {
        if (s.hasSelection) {
            val close = pairs[ch] ?: return null
            val selected = s.text.substring(s.min, s.max)
            val text = s.text.substring(0, s.min) + ch + selected + close + s.text.substring(s.max)
            return EditState(text, s.min + 1, s.min + 1 + selected.length)
        }
        val i = s.start
        val next = s.text.getOrNull(i)
        val prev = s.text.getOrNull(i - 1)

        if ((ch == ')' || ch == ']' || ch == '}') && next == ch) return EditState(s.text, i + 1)

        if (ch == '"' || ch == '\'') {
            if (next == ch && prev != ch) return EditState(s.text, i + 1)
            if (prev == ch && s.text.getOrNull(i - 2) == ch && next != ch) {
                val text = s.text.substring(0, i) + ch + ch + ch + ch + s.text.substring(i)
                return EditState(text, i + 1)
            }
            if (next == ch) return EditState(s.text, i + 1)
            if (prev != null && (prev.isLetterOrDigit() || prev == '_') && prev !in PREFIX_LETTERS) return null
            if (next != null && (next.isLetterOrDigit() || next == '_')) return null
            if (insideStringOrComment(s.text, i)) return null
            val text = s.text.substring(0, i) + ch + ch + s.text.substring(i)
            return EditState(text, i + 1)
        }

        if (ch == '(' || ch == '[' || ch == '{') {
            val ok = next == null || next.isWhitespace() || next in ")]},:;"
            if (!ok || insideStringOrComment(s.text, i)) return null
            val text = s.text.substring(0, i) + ch + pairs.getValue(ch) + s.text.substring(i)
            return EditState(text, i + 1)
        }
        return null
    }

    /** Backspace: removes an empty pair together, or a whole indentation step. Null means default. */
    fun onBackspace(s: EditState): EditState? {
        if (s.hasSelection || s.start == 0) return null
        val i = s.start
        val prev = s.text[i - 1]
        val next = s.text.getOrNull(i)
        if (next != null && pairs[prev] == next) {
            return EditState(s.text.substring(0, i - 1) + s.text.substring(i + 1), i - 1)
        }
        val ls = lineStart(s.text, i)
        val before = s.text.substring(ls, i)
        if (before.isNotEmpty() && before.all { it == ' ' }) {
            val remove = (before.length - 1) % UNIT.length + 1
            return EditState(s.text.substring(0, i - remove) + s.text.substring(i), i - remove)
        }
        return null
    }

    private class Edit(val at: Int, val remove: Int, val insert: String)

    private fun apply(s: EditState, edits: List<Edit>): EditState {
        if (edits.isEmpty()) return s
        val sb = StringBuilder()
        var pos = 0
        for (e in edits) {
            sb.append(s.text, pos, e.at)
            sb.append(e.insert)
            pos = e.at + e.remove
        }
        sb.append(s.text, pos, s.text.length)
        fun map(p: Int): Int {
            var r = p
            for (e in edits) {
                if (p >= e.at + e.remove) r += e.insert.length - e.remove
                else if (p > e.at) r += (e.at + e.insert.length) - p
            }
            return r
        }
        return EditState(sb.toString(), map(s.start), map(s.end))
    }

    /** Start offsets of the lines the selection touches (a selection ending at a line start excludes it). */
    private fun affectedLineStarts(s: EditState): List<Int> {
        val starts = ArrayList<Int>()
        var ls = lineStart(s.text, s.min)
        var stop = s.max
        if (s.hasSelection && stop > 0 && s.text[stop - 1] == '\n') stop--
        while (true) {
            starts += ls
            val next = s.text.indexOf('\n', ls)
            if (next < 0 || next + 1 > stop) break
            ls = next + 1
        }
        return starts
    }

    /** Tab: indents the selected lines, or inserts spaces up to the next multiple of four. */
    fun indent(s: EditState): EditState {
        if (!s.hasSelection) {
            val column = columnOf(s.text, s.min)
            val spaces = " ".repeat(UNIT.length - column % UNIT.length)
            val text = s.text.substring(0, s.min) + spaces + s.text.substring(s.max)
            return EditState(text, s.min + spaces.length)
        }
        val edits = affectedLineStarts(s)
            .filter { s.text.getOrNull(it).let { c -> c != null && c != '\n' } }
            .map { Edit(it, 0, UNIT) }
        return apply(s, edits)
    }

    /** Shift+Tab: removes up to one indentation step from the caret line or the selected lines. */
    fun outdent(s: EditState): EditState {
        val edits = affectedLineStarts(s).mapNotNull { ls ->
            var n = 0
            while (n < UNIT.length && s.text.getOrNull(ls + n) == ' ') n++
            if (n == 0 && s.text.getOrNull(ls) == '\t') n = 1
            if (n == 0) null else Edit(ls, n, "")
        }
        return apply(s, edits)
    }

    /** Comments the lines out, or removes the comment marks if every non-blank line already has one. */
    fun toggleComment(s: EditState): EditState {
        val starts = affectedLineStarts(s)
        data class Line(val start: Int, val indent: Int, val blank: Boolean)
        val lines = starts.map { ls ->
            val le = lineEnd(s.text, ls)
            val body = s.text.substring(ls, le)
            Line(ls, body.length - body.trimStart().length, body.isBlank())
        }
        val content = lines.filter { !it.blank }
        if (content.isEmpty()) return s
        val allCommented = content.all { s.text.getOrNull(it.start + it.indent) == '#' }
        val edits = if (allCommented) {
            content.map { l ->
                val at = l.start + l.indent
                val len = if (s.text.getOrNull(at + 1) == ' ') 2 else 1
                Edit(at, len, "")
            }
        } else {
            val column = content.minOf { it.indent }
            content.map { Edit(it.start + column, 0, "# ") }
        }
        return apply(s, edits)
    }

    fun left(s: EditState): EditState =
        if (s.hasSelection) EditState(s.text, s.min) else EditState(s.text, (s.start - 1).coerceAtLeast(0))

    fun right(s: EditState): EditState =
        if (s.hasSelection) EditState(s.text, s.max) else EditState(s.text, (s.start + 1).coerceAtMost(s.text.length))

    fun up(s: EditState): EditState {
        val ls = lineStart(s.text, s.min)
        if (ls == 0) return EditState(s.text, 0)
        val column = s.min - ls
        val prevStart = lineStart(s.text, ls - 1)
        return EditState(s.text, minOf(prevStart + column, ls - 1))
    }

    fun down(s: EditState): EditState {
        val le = lineEnd(s.text, s.max)
        if (le >= s.text.length) return EditState(s.text, s.text.length)
        val column = s.max - lineStart(s.text, s.max)
        val nextStart = le + 1
        return EditState(s.text, minOf(nextStart + column, lineEnd(s.text, nextStart)))
    }
}
