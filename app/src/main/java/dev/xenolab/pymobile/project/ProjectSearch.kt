package dev.xenolab.pymobile.project

import java.io.File

data class SearchOptions(
    val query: String,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val regex: Boolean = false,
    /** Only lines that define a function or a class with a matching name. */
    val definitionsOnly: Boolean = false,
)

/** One matching line. [column] and [length] are zero-based offsets inside [text] (the whole line). */
data class SearchMatch(val file: File, val line: Int, val column: Int, val length: Int, val text: String)

data class SearchResult(
    val matches: List<SearchMatch>,
    val files: Int,
    val truncated: Boolean,
    val error: String? = null,
)

/** Text search and replace over a folder. Nothing here knows about the UI. */
object ProjectSearch {

    const val MAX_MATCHES = 500
    private const val MAX_FILE_BYTES = 1_000_000L
    private val skipDirs = setOf(".git", "__pycache__", "node_modules", ".venv", "venv", "build", ".idea", ".gradle")

    /** The pattern for [o], or null when the query is empty. Throws [IllegalArgumentException] for a bad regex. */
    fun compile(o: SearchOptions): Regex? {
        if (o.query.isEmpty()) return null
        var core = if (o.regex) o.query else Regex.escape(o.query)
        if (o.wholeWord) core = "\\b(?:$core)\\b"
        if (o.definitionsOnly) core = "^\\s*(?:async\\s+def|def|class)\\s+(?:$core)"
        val options = if (o.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return try {
            Regex(core, options)
        } catch (e: java.util.regex.PatternSyntaxException) {
            throw IllegalArgumentException(e.description ?: "bad pattern", e)
        }
    }

    /** Files below [root] worth searching: text files of a sensible size, skipping caches and VCS folders. */
    fun candidates(root: File): List<File> {
        val out = ArrayList<File>()
        fun walk(dir: File) {
            val children = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return
            for (c in children) {
                if (c.isDirectory) {
                    if (c.name !in skipDirs) walk(c)
                } else if (c.length() <= MAX_FILE_BYTES && ProjectFiles.isEditable(c)) {
                    out += c
                }
            }
        }
        if (root.isFile) out += root else walk(root)
        return out
    }

    fun search(root: File, o: SearchOptions): SearchResult {
        val regex = try {
            compile(o) ?: return SearchResult(emptyList(), 0, false)
        } catch (e: IllegalArgumentException) {
            return SearchResult(emptyList(), 0, false, e.message ?: "bad pattern")
        }
        val matches = ArrayList<SearchMatch>()
        val files = HashSet<File>()
        var truncated = false
        outer@ for (file in candidates(root)) {
            val text = try {
                ProjectFiles.read(file)
            } catch (_: Exception) {
                continue
            }
            var number = 0
            for (line in text.split('\n')) {
                number++
                val m = regex.find(line) ?: continue
                if (matches.size >= MAX_MATCHES) {
                    truncated = true
                    break@outer
                }
                files += file
                val shown = if (line.length > 300) line.substring(0, 300) else line
                matches += SearchMatch(file, number, m.range.first.coerceAtMost(shown.length), m.value.length, shown)
            }
        }
        return SearchResult(matches, files.size, truncated)
    }

    /**
     * Replaces every match in [files] (all candidates below [root] when null) and returns how many
     * replacements were made. Files without a match are not touched.
     */
    fun replaceAll(root: File, o: SearchOptions, replacement: String, files: Collection<File>? = null): Int {
        val regex = compile(o) ?: return 0
        val template = if (o.regex) replacement else Regex.escapeReplacement(replacement)
        var total = 0
        for (file in files ?: candidates(root)) {
            val text = try {
                ProjectFiles.read(file)
            } catch (_: Exception) {
                continue
            }
            val count = regex.findAll(text).count()
            if (count == 0) continue
            val changed = regex.replace(text, template)
            if (ProjectFiles.write(file, changed)) total += count
        }
        return total
    }
}
