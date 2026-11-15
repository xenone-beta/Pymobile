package dev.xenolab.pymobile.editor

/**
 * A piece of console text in one style. [color] is 0 for the default colour, 1-8 for the normal ANSI
 * colours and 9-16 for the bright ones.
 */
data class ConsoleSegment(
    val text: String,
    val color: Int = 0,
    val bold: Boolean = false,
    val error: Boolean = false,
    val input: Boolean = false,
)

data class ConsoleLine(val segments: List<ConsoleSegment>) {
    val text: String get() = segments.joinToString("") { it.text }
}

/** A traceback line like `File "/a/b.py", line 12, in <module>`. */
data class TraceLocation(val file: String, val line: Int)

object Tracebacks {
    private val pattern = Regex("""File "([^"]+)", line (\d+)""")

    fun find(line: String): TraceLocation? {
        val m = pattern.find(line) ?: return null
        val n = m.groupValues[2].toIntOrNull() ?: return null
        return TraceLocation(m.groupValues[1], n)
    }
}

/**
 * The text shown in the console. It understands what programs print to a terminal: ANSI colours,
 * carriage returns (progress bars) and escape sequences cut in half between two reads.
 */
class ConsoleBuffer(private val maxLines: Int = 5000) {

    private val lines = ArrayList<ConsoleLine>()
    private var segments = ArrayList<ConsoleSegment>()
    private val run = StringBuilder()
    private var runColor = 0
    private var runBold = false
    private var runError = false
    private var runInput = false

    private var color = 0
    private var bold = false
    private var error = false
    private var input = false

    private var escape: StringBuilder? = null
    private var carriageReturn = false

    val lineCount: Int get() = lines.size

    fun clear() {
        lines.clear()
        segments = ArrayList()
        run.setLength(0)
        color = 0
        bold = false
        escape = null
        carriageReturn = false
    }

    /** Output from the program. [isError] marks stderr. */
    fun append(chunk: String, isError: Boolean = false) {
        setStream(isError, false)
        process(chunk)
    }

    /** Text the user typed, shown as typed. */
    fun appendInput(text: String) {
        setStream(false, true)
        val saved = color
        val savedBold = bold
        color = 0
        bold = false
        process(text)
        color = saved
        bold = savedBold
        setStream(false, false)
    }

    /** All finished lines plus the line being written, if any. */
    fun snapshot(): List<ConsoleLine> {
        if (segments.isEmpty() && run.isEmpty()) return ArrayList(lines)
        val partial = ArrayList(segments)
        if (run.isNotEmpty()) partial += currentRun()
        val out = ArrayList<ConsoleLine>(lines.size + 1)
        out.addAll(lines)
        out += ConsoleLine(partial)
        return out
    }

    private fun setStream(isError: Boolean, isInput: Boolean) {
        if (error != isError || input != isInput) flushRun()
        error = isError
        input = isInput
    }

    private fun currentRun() = ConsoleSegment(run.toString(), runColor, runBold, runError, runInput)

    private fun flushRun() {
        if (run.isNotEmpty()) {
            segments += currentRun()
            run.setLength(0)
        }
    }

    private fun commitLine() {
        flushRun()
        lines += ConsoleLine(segments)
        segments = ArrayList()
        if (lines.size > maxLines + TRIM_STEP) {
            lines.subList(0, lines.size - maxLines).clear()
        }
    }

    private fun process(chunk: String) {
        for (c in chunk) {
            val esc = escape
            if (esc != null) {
                esc.append(c)
                if (esc.length == 2 && c != '[') {
                    escape = null
                } else if (esc.length > 2 && c in '@'..'~') {
                    if (c == 'm') applySgr(esc.substring(2, esc.length - 1))
                    escape = null
                } else if (esc.length > 64) {
                    escape = null
                }
                continue
            }
            when (c) {
                '\u001B' -> {
                    escape = StringBuilder().append(c)
                }
                '\n' -> {
                    carriageReturn = false
                    commitLine()
                }
                '\r' -> carriageReturn = true
                else -> {
                    if (carriageReturn) {
                        segments = ArrayList()
                        run.setLength(0)
                        carriageReturn = false
                    }
                    if (run.isEmpty()) {
                        runColor = color
                        runBold = bold
                        runError = error
                        runInput = input
                    } else if (runColor != color || runBold != bold || runError != error || runInput != input) {
                        flushRun()
                        runColor = color
                        runBold = bold
                        runError = error
                        runInput = input
                    }
                    run.append(c)
                }
            }
        }
    }

    private fun applySgr(params: String) {
        val codes = if (params.isEmpty()) listOf(0) else params.split(';').map { it.toIntOrNull() ?: 0 }
        var k = 0
        while (k < codes.size) {
            when (val code = codes[k]) {
                0 -> { color = 0; bold = false }
                1 -> bold = true
                22 -> bold = false
                in 30..37 -> color = code - 29
                39 -> color = 0
                in 90..97 -> color = code - 89 + 8
                38, 48 -> {
                    // Extended colours are skipped: 38;5;n has one more value, 38;2;r;g;b has three.
                    k += if (codes.getOrNull(k + 1) == 5) 2 else if (codes.getOrNull(k + 1) == 2) 4 else 0
                }
            }
            k++
        }
    }

    private companion object {
        const val TRIM_STEP = 500
    }
}
