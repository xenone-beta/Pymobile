package dev.xenolab.pymobile.python

/** Per-file run settings: command-line arguments and extra environment variables, both as typed. */
data class RunConfig(val args: String = "", val env: String = "") {
    val isEmpty: Boolean get() = args.isBlank() && env.isBlank()
}

object RunConfigs {

    private val envName = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** Variables that would break the bundled interpreter if overridden. */
    val reserved = setOf("PYTHONHOME", "PYTHONUSERBASE")

    /**
     * Splits [input] the way a shell would: whitespace separates arguments, single or double quotes
     * group, a backslash escapes the next character (outside single quotes). Null if a quote is left open.
     */
    fun splitArgs(input: String): List<String>? {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inToken = false
        var quote = ' '
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (quote == '\'') {
                if (c == '\'') quote = ' ' else cur.append(c)
            } else if (quote == '"') {
                if (c == '"') {
                    quote = ' '
                } else if (c == '\\' && i + 1 < input.length && (input[i + 1] == '"' || input[i + 1] == '\\')) {
                    cur.append(input[i + 1])
                    i++
                } else {
                    cur.append(c)
                }
            } else if (c == '\'' || c == '"') {
                quote = c
                inToken = true
            } else if (c == '\\' && i + 1 < input.length) {
                cur.append(input[i + 1])
                i++
                inToken = true
            } else if (c.isWhitespace()) {
                if (inToken) {
                    out += cur.toString()
                    cur.setLength(0)
                    inToken = false
                }
            } else {
                cur.append(c)
                inToken = true
            }
            i++
        }
        if (quote != ' ') return null
        if (inToken) out += cur.toString()
        return out
    }

    /**
     * Reads `KEY=VALUE` lines. Blank lines and lines starting with `#` are skipped, a value may be wrapped
     * in matching quotes. Null if a line is malformed or names a reserved variable.
     */
    fun parseEnv(text: String): Map<String, String>? {
        val result = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) return null
            val key = line.substring(0, eq).trim()
            if (!envName.matches(key) || key in reserved) return null
            var value = line.substring(eq + 1).trim()
            if (value.length >= 2 && (value[0] == '"' || value[0] == '\'') && value.last() == value[0]) {
                value = value.substring(1, value.length - 1)
            }
            result[key] = value
        }
        return result
    }
}
