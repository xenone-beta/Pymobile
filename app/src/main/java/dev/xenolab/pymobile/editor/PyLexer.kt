package dev.xenolab.pymobile.editor

enum class TokenType { KEYWORD, BUILTIN, STRING, COMMENT, NUMBER, DECORATOR, FUNCTION, CLASS }

/** A highlighted range, [start] inclusive and [end] exclusive. Text outside every span is plain. */
data class Span(val start: Int, val end: Int, val type: TokenType)

/** The result of reading a string literal. [end] is where scanning stopped. */
class StringScan(val end: Int, val terminated: Boolean, val triple: Boolean)

/** A small hand-written scanner for Python source. It never fails: odd input just stays plain. */
object PyLexer {

    val keywords: Set<String> = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
    )

    val builtins: Set<String> = setOf(
        "self", "cls", "print", "len", "range", "int", "str", "float", "list", "dict", "set", "tuple", "bool",
        "bytes", "bytearray", "open", "input", "type", "isinstance", "issubclass", "enumerate", "zip", "map",
        "filter", "sorted", "reversed", "sum", "min", "max", "abs", "round", "any", "all", "super", "object",
        "repr", "format", "iter", "next", "hasattr", "getattr", "setattr", "delattr", "id", "hash", "hex",
        "oct", "bin", "ord", "chr", "divmod", "pow", "slice", "vars", "dir", "callable", "property",
        "staticmethod", "classmethod", "frozenset", "complex", "globals", "locals", "exec", "eval", "compile",
        "Exception", "BaseException", "ValueError", "TypeError", "KeyError", "IndexError", "AttributeError",
        "NameError", "RuntimeError", "OSError", "IOError", "ImportError", "StopIteration", "ZeroDivisionError",
        "NotImplementedError", "KeyboardInterrupt", "AssertionError", "FileNotFoundError",
        "__name__", "__file__", "__init__",
    )

    private const val STRING_PREFIX_CHARS = "rRbBfFuU"

    fun isIdentStart(c: Char): Boolean = c == '_' || c.isLetter()

    fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    /**
     * If a string literal begins at [i], returns the number of prefix letters before its opening quote
     * (0 for a plain quote). Returns -1 when no string starts there.
     */
    fun stringPrefixLength(text: String, i: Int): Int {
        var j = i
        while (j < text.length && j - i < 2 && text[j] in STRING_PREFIX_CHARS) j++
        return if (j < text.length && (text[j] == '"' || text[j] == '\'')) j - i else -1
    }

    /** Reads the literal that starts at [start] (prefix letters included). */
    fun scanString(text: String, start: Int, prefixLength: Int): StringScan {
        val n = text.length
        val quoteAt = start + prefixLength
        val quote = text[quoteAt]
        val triple = quoteAt + 2 < n && text[quoteAt + 1] == quote && text[quoteAt + 2] == quote
        var p = quoteAt + if (triple) 3 else 1
        while (p < n) {
            val c = text[p]
            when {
                c == '\\' -> p += 2
                triple -> {
                    if (c == quote && p + 2 < n && text[p + 1] == quote && text[p + 2] == quote) {
                        return StringScan(p + 3, terminated = true, triple = true)
                    }
                    p++
                }
                c == quote -> return StringScan(p + 1, terminated = true, triple = false)
                c == '\n' -> return StringScan(p, terminated = false, triple = false)
                else -> p++
            }
        }
        return StringScan(minOf(p, n), terminated = false, triple = triple)
    }

    fun highlight(text: String): List<Span> {
        val spans = ArrayList<Span>()
        val n = text.length
        var i = 0
        var expect: TokenType? = null
        while (i < n) {
            val c = text[i]
            when {
                c == '#' -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) n else it }
                    spans += Span(i, end, TokenType.COMMENT)
                    i = end
                }
                c == '"' || c == '\'' || (isIdentStart(c) && stringPrefixLength(text, i) >= 0) -> {
                    val prefix = stringPrefixLength(text, i)
                    if (prefix < 0) {
                        i++
                    } else {
                        val scan = scanString(text, i, prefix)
                        spans += Span(i, scan.end, TokenType.STRING)
                        i = maxOf(scan.end, i + 1)
                    }
                    expect = null
                }
                c.isDigit() || (c == '.' && i + 1 < n && text[i + 1].isDigit() && (i == 0 || !isIdentPart(text[i - 1]))) -> {
                    val start = i
                    val hex = c == '0' && i + 1 < n && (text[i + 1] == 'x' || text[i + 1] == 'X')
                    i++
                    while (i < n) {
                        val d = text[i]
                        if (isIdentPart(d) || d == '.') {
                            i++
                        } else if ((d == '+' || d == '-') && !hex && (text[i - 1] == 'e' || text[i - 1] == 'E')) {
                            i++
                        } else {
                            break
                        }
                    }
                    spans += Span(start, i, TokenType.NUMBER)
                    expect = null
                }
                c == '@' && onlyBlankBefore(text, i) -> {
                    val start = i
                    i++
                    while (i < n && (isIdentPart(text[i]) || text[i] == '.')) i++
                    spans += Span(start, i, TokenType.DECORATOR)
                    expect = null
                }
                isIdentStart(c) -> {
                    val start = i
                    while (i < n && isIdentPart(text[i])) i++
                    val word = text.substring(start, i)
                    val afterDot = previousSignificantChar(text, start) == '.'
                    val type = when {
                        afterDot -> null
                        expect != null -> expect
                        word in keywords -> TokenType.KEYWORD
                        word in builtins -> TokenType.BUILTIN
                        else -> null
                    }
                    if (type != null) spans += Span(start, i, type)
                    expect = if (!afterDot && word == "def") TokenType.FUNCTION
                    else if (!afterDot && word == "class") TokenType.CLASS else null
                }
                else -> {
                    if (!c.isWhitespace()) expect = null
                    i++
                }
            }
        }
        return spans
    }

    private fun onlyBlankBefore(text: String, index: Int): Boolean {
        var j = index - 1
        while (j >= 0 && text[j] != '\n') {
            if (text[j] != ' ' && text[j] != '\t') return false
            j--
        }
        return true
    }

    private fun previousSignificantChar(text: String, index: Int): Char? {
        var j = index - 1
        while (j >= 0 && (text[j] == ' ' || text[j] == '\t')) j--
        return if (j >= 0) text[j] else null
    }
}
