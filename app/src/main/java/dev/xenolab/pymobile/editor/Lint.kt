package dev.xenolab.pymobile.editor

enum class Severity { ERROR, WARNING }

/**
 * A problem found while typing. [start] and [end] are text offsets ([end] exclusive, never empty),
 * [line] is zero-based.
 */
data class Issue(val line: Int, val start: Int, val end: Int, val message: String, val severity: Severity)

/**
 * A quick syntax check that runs on every pause in typing, with no Python process involved.
 * It finds the mistakes that are most common while typing: brackets, quotes, missing colons and
 * wrong indentation. Python itself is still the judge when the program runs.
 */
object Lint {

    private val blockKeywords = setOf(
        "if", "elif", "else", "for", "while", "def", "class", "try", "except", "finally", "with", "async",
    )

    private val closers = mapOf(')' to '(', ']' to '[', '}' to '{')

    fun check(text: String): List<Issue> {
        val n = text.length
        val raw = ArrayList<Triple<Int, Int, Pair<String, Severity>>>()
        fun mark(start: Int, end: Int, message: String, severity: Severity = Severity.ERROR) {
            if (n == 0) return
            val s = start.coerceIn(0, n - 1)
            val e = end.coerceIn(s + 1, n)
            raw += Triple(s, e, message to severity)
        }

        val brackets = ArrayList<Pair<Char, Int>>()
        val indents = ArrayList<Int>().apply { add(0) }
        var i = 0
        var atLineStart = true
        var opensBlock = false
        var headerEnd = 0

        var firstWord: String? = null
        var firstTokenSeen = false
        var colonAtTopLevel = false
        var lastSignificant = ' '
        var lineStart = 0

        fun finishLogicalLine(end: Int) {
            val word = firstWord
            if (word != null && word in blockKeywords && !colonAtTopLevel) {
                mark(lineStart, end, "expected ':'")
            }
            opensBlock = lastSignificant == ':'
            headerEnd = end
        }

        while (i < n) {
            if (atLineStart && brackets.isEmpty()) {
                var j = i
                var column = 0
                var tabs = false
                var spaces = false
                while (j < n && (text[j] == ' ' || text[j] == '\t')) {
                    if (text[j] == '\t') {
                        tabs = true
                        column = (column / 8 + 1) * 8
                    } else {
                        spaces = true
                        column++
                    }
                    j++
                }
                if (j >= n) {
                    i = n
                    break
                }
                if (text[j] == '\n' || text[j] == '\r' || text[j] == '#') {
                    // Blank and comment-only lines never affect indentation.
                    val end = text.indexOf('\n', j).let { if (it < 0) n else it + 1 }
                    i = end
                    continue
                }
                if (tabs && spaces) mark(i, j, "mixed tabs and spaces in indentation", Severity.WARNING)
                if (opensBlock) {
                    if (column <= indents.last()) mark(j, j + 1, "expected an indented block")
                    else indents += column
                } else if (column > indents.last()) {
                    mark(i, j.coerceAtLeast(i + 1), "unexpected indent")
                    indents += column
                } else if (column < indents.last()) {
                    while (indents.size > 1 && indents.last() > column) indents.removeAt(indents.size - 1)
                    if (indents.last() != column) mark(i, j.coerceAtLeast(i + 1), "unindent does not match any outer indentation level")
                }
                opensBlock = false
                firstWord = null
                firstTokenSeen = false
                colonAtTopLevel = false
                lastSignificant = ' '
                lineStart = j
                atLineStart = false
                i = j
                continue
            }

            val c = text[i]
            when {
                c == '\\' && i + 1 < n && (text[i + 1] == '\n' || (text[i + 1] == '\r' && i + 2 < n && text[i + 2] == '\n')) -> {
                    i += if (text[i + 1] == '\r') 3 else 2
                }
                c == '\n' -> {
                    if (brackets.isEmpty()) {
                        finishLogicalLine(i)
                        atLineStart = true
                    }
                    i++
                }
                c == '#' -> i = text.indexOf('\n', i).let { if (it < 0) n else it }
                c == ' ' || c == '\t' || c == '\r' || c == '\u000C' -> i++
                c == '"' || c == '\'' || (PyLexer.isIdentStart(c) && PyLexer.stringPrefixLength(text, i) >= 0) -> {
                    val prefix = PyLexer.stringPrefixLength(text, i)
                    val scan = PyLexer.scanString(text, i, prefix)
                    if (!scan.terminated) {
                        if (scan.triple) mark(i, i + prefix + 3, "unterminated triple-quoted string")
                        else mark(i, scan.end, "unterminated string literal")
                    }
                    firstTokenSeen = true
                    lastSignificant = 's'
                    i = maxOf(scan.end, i + 1)
                }
                c == '(' || c == '[' || c == '{' -> {
                    brackets += c to i
                    firstTokenSeen = true
                    lastSignificant = c
                    i++
                }
                c == ')' || c == ']' || c == '}' -> {
                    if (brackets.isEmpty()) {
                        mark(i, i + 1, "unmatched '$c'")
                    } else {
                        val open = brackets.removeAt(brackets.size - 1)
                        if (open.first != closers.getValue(c)) {
                            mark(i, i + 1, "closing '$c' does not match opening '${open.first}'")
                        }
                    }
                    firstTokenSeen = true
                    lastSignificant = c
                    i++
                }
                PyLexer.isIdentStart(c) -> {
                    val start = i
                    while (i < n && PyLexer.isIdentPart(text[i])) i++
                    if (!firstTokenSeen) {
                        firstWord = text.substring(start, i)
                        firstTokenSeen = true
                    }
                    lastSignificant = 'a'
                }
                else -> {
                    if (c == ':' && brackets.isEmpty()) colonAtTopLevel = true
                    firstTokenSeen = true
                    lastSignificant = c
                    i++
                }
            }
        }

        if (!atLineStart && brackets.isEmpty()) finishLogicalLine(n)
        if (opensBlock && brackets.isEmpty()) mark(headerEnd - 1, headerEnd, "expected an indented block")
        for ((open, at) in brackets) mark(at, at + 1, "'$open' was never closed")

        // Line numbers are computed once, after sorting.
        val lineStarts = ArrayList<Int>().apply {
            add(0)
            for (k in 0 until n) if (text[k] == '\n') add(k + 1)
        }
        fun lineOf(offset: Int): Int {
            var lo = 0
            var hi = lineStarts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
            }
            return lo
        }
        return raw.sortedBy { it.first }.map { (s, e, info) -> Issue(lineOf(s), s, e, info.first, info.second) }
    }
}
