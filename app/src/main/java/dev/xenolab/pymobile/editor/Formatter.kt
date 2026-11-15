package dev.xenolab.pymobile.editor

/**
 * A careful tidy-up. It only touches whitespace that is never part of the program's meaning:
 * trailing spaces, tabs in indentation, runs of blank lines, blank lines before top-level
 * definitions and the end of the file. Text inside multi-line strings is left alone.
 */
object Formatter {

    const val INDENT = "    "

    fun format(source: String): String {
        if (source.isBlank()) return ""
        val text = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')

        // Lines that begin inside a multi-line string must keep every character.
        val protectedLines = BooleanArray(lines.size)
        val lineStarts = IntArray(lines.size)
        var offset = 0
        for (k in lines.indices) {
            lineStarts[k] = offset
            offset += lines[k].length + 1
        }
        for (span in PyLexer.highlight(text)) {
            if (span.type != TokenType.STRING) continue
            for (k in lines.indices) {
                if (lineStarts[k] > span.start && lineStarts[k] < span.end) protectedLines[k] = true
                val lineEnd = lineStarts[k] + lines[k].length
                if (lineEnd > span.start && lineEnd < span.end) protectedLines[k] = true
            }
        }

        val cleaned = ArrayList<String>(lines.size)
        for (k in lines.indices) {
            val line = lines[k]
            cleaned += if (protectedLines[k]) line else tidy(line)
        }

        val result = ArrayList<String>(cleaned.size)
        var pendingBlanks = 0
        for (k in cleaned.indices) {
            val line = cleaned[k]
            if (line.isEmpty() && !protectedLines[k]) {
                pendingBlanks++
                continue
            }
            if (result.isNotEmpty()) {
                val indent = line.length - line.trimStart().length
                var blanks = pendingBlanks
                if (!protectedLines[k]) {
                    blanks = blanks.coerceAtMost(if (indent == 0) 2 else 1)
                    if (indent == 0 && startsDefinition(line) && !attachedAbove(result)) blanks = 2
                    if (indent == 0 && blanks == 0 && isDecoratorLineAbove(result)) blanks = 0
                }
                repeat(blanks) { result += "" }
            }
            pendingBlanks = 0
            result += line
        }
        return result.joinToString("\n") + "\n"
    }

    private fun tidy(line: String): String {
        val trimmed = line.trimEnd(' ', '\t', '\u000C')
        if (trimmed.isEmpty()) return ""
        var lead = 0
        while (lead < trimmed.length && (trimmed[lead] == ' ' || trimmed[lead] == '\t')) lead++
        val indent = trimmed.substring(0, lead)
        if ('\t' !in indent) return trimmed
        var column = 0
        for (c in indent) column = if (c == '\t') (column / 4 + 1) * 4 else column + 1
        return " ".repeat(column) + trimmed.substring(lead)
    }

    private fun startsDefinition(line: String): Boolean {
        val s = line.trimStart()
        return s.startsWith("def ") || s.startsWith("class ") || s.startsWith("async def ") || s.startsWith("@")
    }

    /** True when the line right above is a decorator or a comment that belongs to the definition. */
    private fun attachedAbove(result: List<String>): Boolean {
        val above = result.lastOrNull() ?: return true
        val s = above.trimStart()
        return s.startsWith("@") || (above.isNotEmpty() && above[0] == '#')
    }

    private fun isDecoratorLineAbove(result: List<String>): Boolean = result.lastOrNull()?.trimStart()?.startsWith("@") == true
}
