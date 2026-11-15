package dev.xenolab.pymobile.editor

/** What can be completed at the caret: the typed [prefix] starts at [start], [items] are the choices. */
data class CompletionResult(val prefix: String, val start: Int, val items: List<String>)

/** Word completion from the open file, the keywords, the built-ins and a few well-known names. */
object Completion {

    val modules: List<String> = listOf(
        "abc", "argparse", "asyncio", "base64", "bisect", "calendar", "collections", "configparser",
        "contextlib", "copy", "csv", "dataclasses", "datetime", "decimal", "enum", "functools", "getpass",
        "glob", "hashlib", "heapq", "html", "http", "inspect", "io", "itertools", "json", "logging", "math",
        "operator", "os", "pathlib", "pickle", "platform", "queue", "random", "re", "secrets", "select",
        "shutil", "signal", "socket", "sqlite3", "ssl", "statistics", "string", "struct", "subprocess",
        "sys", "tempfile", "textwrap", "threading", "time", "tomllib", "traceback", "typing", "unittest",
        "urllib", "uuid", "xml", "zipfile", "zoneinfo",
    )

    val methods: List<String> = listOf(
        "append", "clear", "close", "copy", "count", "decode", "encode", "endswith", "extend", "find",
        "format", "get", "index", "insert", "isalpha", "isdigit", "items", "join", "keys", "lower",
        "lstrip", "pop", "read", "readline", "readlines", "remove", "replace", "reverse", "rstrip",
        "setdefault", "sort", "split", "startswith", "strip", "title", "update", "upper", "values", "write",
    )

    private val importLine = Regex("""^\s*(import|from)\s+([\w.]*)$""")
    private val wordPattern = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

    fun suggest(text: String, caret: Int, spans: List<Span>, max: Int = 8): CompletionResult? {
        if (caret <= 0 || caret > text.length) return null
        var start = caret
        while (start > 0 && PyLexer.isIdentPart(text[start - 1])) start--
        val prefix = text.substring(start, caret)
        if (prefix.isEmpty() || prefix[0].isDigit()) return null
        val afterDot = start > 0 && text[start - 1] == '.'
        if (prefix.length < (if (afterDot) 1 else 2)) return null
        if (spans.any { (it.type == TokenType.STRING && caret > it.start && caret < it.end) ||
                (it.type == TokenType.COMMENT && caret > it.start && caret <= it.end) }
        ) return null

        val lineBefore = text.substring(EditActions.lineStart(text, start), start)
        val items = LinkedHashSet<String>()

        if (importLine.matches(lineBefore) && !afterDot) {
            modules.filter { it.startsWith(prefix) && it != prefix }.forEach { items += it }
            return finish(prefix, start, items, max)
        }

        // How often each word occurs in the file; the word being typed does not count for itself.
        val counts = HashMap<String, Int>()
        for (m in wordPattern.findAll(text)) counts[m.value] = (counts[m.value] ?: 0) + 1
        counts[prefix] = (counts[prefix] ?: 1) - 1

        if (afterDot) {
            val attributes = Regex("""\.([A-Za-z_][A-Za-z0-9_]*)""").findAll(text).map { it.groupValues[1] }.toSet()
            attributes.filter { it.startsWith(prefix) && counts.getOrDefault(it, 0) > 0 }
                .sortedByDescending { counts[it] }.forEach { items += it }
            methods.filter { it.startsWith(prefix) }.forEach { items += it }
        } else {
            counts.entries.filter { it.key.startsWith(prefix) && it.value > 0 && it.key != prefix }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .forEach { items += it.key }
            (PyLexer.keywords + PyLexer.builtins).filter { it.startsWith(prefix) && it != prefix }.sorted().forEach { items += it }
        }
        return finish(prefix, start, items, max)
    }

    private fun finish(prefix: String, start: Int, items: Collection<String>, max: Int): CompletionResult? =
        if (items.isEmpty()) null else CompletionResult(prefix, start, items.take(max))

    /** Replaces the typed prefix with [choice]; the caret ends after it. */
    fun apply(text: String, caret: Int, result: CompletionResult, choice: String): EditState {
        val newText = text.substring(0, result.start) + choice + text.substring(caret)
        return EditState(newText, result.start + choice.length)
    }
}
