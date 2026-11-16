package dev.xenolab.pymobile.theme

import dev.xenolab.pymobile.theme.PaletteKey.*
import org.json.JSONObject

/** The colors an editor theme can set. [key] is the name used in `.pmt` files. */
enum class PaletteKey(val key: String, val label: String) {
    BACKGROUND("background", "Editor background"),
    TEXT("text", "Text"),
    GUTTER("gutter", "Line number background"),
    GUTTER_TEXT("gutterText", "Line numbers"),
    CARET("caret", "Caret and handles"),
    SELECTION("selection", "Selection"),
    KEYWORD("keyword", "Keywords"),
    BUILTIN("builtin", "Built-ins"),
    STRING("string", "Strings"),
    COMMENT("comment", "Comments"),
    NUMBER("number", "Numbers"),
    DECORATOR("decorator", "Decorators"),
    FUNCTION("function", "Function names"),
    CLASS("class", "Class names"),
    ERROR("error", "Errors"),
    WARNING("warning", "Warnings"),
    CONSOLE_BACKGROUND("consoleBackground", "Console background"),
    CONSOLE_TEXT("consoleText", "Console text"),
}

/** A complete editor theme: every [PaletteKey] has an ARGB color. */
data class Palette(
    val name: String,
    val dark: Boolean,
    val colors: Map<PaletteKey, Int>,
    val author: String = "",
) {
    operator fun get(key: PaletteKey): Int = colors.getValue(key)

    fun with(key: PaletteKey, argb: Int): Palette = copy(colors = colors + (key to argb))
}

/** Reading and writing `.pmt` (Pymobile Theme) files: a small JSON document. */
object Pmt {
    const val VERSION = 1
    const val EXTENSION = "pmt"
    const val MAX_BYTES = 64 * 1024
    const val MAX_NAME = 40

    sealed interface Result {
        data class Ok(val palette: Palette) : Result
        data class Error(val message: String) : Result
    }

    /** `#RGB`, `#RRGGBB` or `#AARRGGBB` to an ARGB int; null if it is none of those. */
    fun parseColor(text: String): Int? {
        val t = text.trim()
        if (!t.startsWith("#")) return null
        val hex = t.substring(1)
        if (hex.any { Character.digit(it, 16) < 0 }) return null
        return when (hex.length) {
            3 -> {
                val r = Character.digit(hex[0], 16) * 17
                val g = Character.digit(hex[1], 16) * 17
                val b = Character.digit(hex[2], 16) * 17
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> null
        }
    }

    /** `#RRGGBB` when opaque, otherwise `#AARRGGBB`. */
    fun formatColor(argb: Int): String =
        if ((argb ushr 24) == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)

    fun parse(text: String): Result {
        if (text.length > MAX_BYTES) return Result.Error("The file is too large")
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return Result.Error("Not a valid theme file")
        }
        val version = root.optInt("pmt", 0)
        if (version < 1 || version > VERSION) return Result.Error("Unsupported theme version")
        val name = root.optString("name").trim().take(MAX_NAME)
        if (name.isEmpty()) return Result.Error("The theme has no name")
        val dark = root.optBoolean("dark", true)
        val base = Palettes.base(dark)
        val given = root.optJSONObject("colors")
        val colors = LinkedHashMap<PaletteKey, Int>()
        for (key in PaletteKey.entries) {
            val raw: Any? = if (given != null && given.has(key.key) && !given.isNull(key.key)) given.get(key.key) else null
            if (raw == null) {
                colors[key] = base[key]
                continue
            }
            colors[key] = parseColor(raw.toString()) ?: return Result.Error("Bad color for \"${key.key}\": $raw")
        }
        return Result.Ok(Palette(name, dark, colors, root.optString("author").trim().take(MAX_NAME)))
    }

    fun write(palette: Palette): String {
        val colors = JSONObject()
        for (key in PaletteKey.entries) colors.put(key.key, formatColor(palette[key]))
        return JSONObject()
            .put("pmt", VERSION)
            .put("name", palette.name)
            .put("author", palette.author)
            .put("dark", palette.dark)
            .put("colors", colors)
            .toString(2) + "\n"
    }
}

private fun palette(name: String, dark: Boolean, vararg c: Pair<PaletteKey, Long>): Palette {
    val map = c.associate { (k, v) -> k to (0xFF000000L or v).toInt() }
    check(map.size == PaletteKey.entries.size) { "Palette $name is incomplete" }
    return Palette(name, dark, map)
}

/** The built-in themes. */
object Palettes {
    private val oneDark = palette(
        "One Dark", true,
        BACKGROUND to 0x282C34, TEXT to 0xABB2BF, GUTTER to 0x21252B, GUTTER_TEXT to 0x636D83,
        CARET to 0x528BFF, SELECTION to 0x3E4451, KEYWORD to 0xC678DD, BUILTIN to 0x56B6C2,
        STRING to 0x98C379, COMMENT to 0x5C6370, NUMBER to 0xD19A66, DECORATOR to 0xE5C07B,
        FUNCTION to 0x61AFEF, CLASS to 0xE5C07B, ERROR to 0xE06C75, WARNING to 0xD19A66,
        CONSOLE_BACKGROUND to 0x21252B, CONSOLE_TEXT to 0xABB2BF,
    )

    private val githubLight = palette(
        "GitHub Light", false,
        BACKGROUND to 0xFFFFFF, TEXT to 0x24292F, GUTTER to 0xF6F8FA, GUTTER_TEXT to 0x8C959F,
        CARET to 0x24292F, SELECTION to 0xB6E3FF, KEYWORD to 0xCF222E, BUILTIN to 0x0550AE,
        STRING to 0x0A3069, COMMENT to 0x6E7781, NUMBER to 0x0550AE, DECORATOR to 0x8250DF,
        FUNCTION to 0x8250DF, CLASS to 0x953800, ERROR to 0xCF222E, WARNING to 0x9A6700,
        CONSOLE_BACKGROUND to 0xF6F8FA, CONSOLE_TEXT to 0x24292F,
    )

    val builtIn: List<Pair<String, Palette>> = listOf(
        "one-dark" to oneDark,
        "github-light" to githubLight,
        "monokai" to palette(
            "Monokai", true,
            BACKGROUND to 0x272822, TEXT to 0xF8F8F2, GUTTER to 0x2D2E27, GUTTER_TEXT to 0x90908A,
            CARET to 0xF8F8F0, SELECTION to 0x49483E, KEYWORD to 0xF92672, BUILTIN to 0x66D9EF,
            STRING to 0xE6DB74, COMMENT to 0x75715E, NUMBER to 0xAE81FF, DECORATOR to 0xA6E22E,
            FUNCTION to 0xA6E22E, CLASS to 0xA6E22E, ERROR to 0xFF5555, WARNING to 0xFD971F,
            CONSOLE_BACKGROUND to 0x1E1F1C, CONSOLE_TEXT to 0xF8F8F2,
        ),
        "dracula" to palette(
            "Dracula", true,
            BACKGROUND to 0x282A36, TEXT to 0xF8F8F2, GUTTER to 0x21222C, GUTTER_TEXT to 0x6272A4,
            CARET to 0xF8F8F2, SELECTION to 0x44475A, KEYWORD to 0xFF79C6, BUILTIN to 0x8BE9FD,
            STRING to 0xF1FA8C, COMMENT to 0x6272A4, NUMBER to 0xBD93F9, DECORATOR to 0x50FA7B,
            FUNCTION to 0x50FA7B, CLASS to 0x8BE9FD, ERROR to 0xFF5555, WARNING to 0xFFB86C,
            CONSOLE_BACKGROUND to 0x1E1F29, CONSOLE_TEXT to 0xF8F8F2,
        ),
        "solarized-dark" to palette(
            "Solarized Dark", true,
            BACKGROUND to 0x002B36, TEXT to 0x839496, GUTTER to 0x073642, GUTTER_TEXT to 0x586E75,
            CARET to 0x93A1A1, SELECTION to 0x0F4A5A, KEYWORD to 0x859900, BUILTIN to 0x268BD2,
            STRING to 0x2AA198, COMMENT to 0x586E75, NUMBER to 0xD33682, DECORATOR to 0xB58900,
            FUNCTION to 0x268BD2, CLASS to 0xCB4B16, ERROR to 0xDC322F, WARNING to 0xB58900,
            CONSOLE_BACKGROUND to 0x00212B, CONSOLE_TEXT to 0x93A1A1,
        ),
        "solarized-light" to palette(
            "Solarized Light", false,
            BACKGROUND to 0xFDF6E3, TEXT to 0x657B83, GUTTER to 0xEEE8D5, GUTTER_TEXT to 0x93A1A1,
            CARET to 0x586E75, SELECTION to 0xE4DCC0, KEYWORD to 0x859900, BUILTIN to 0x268BD2,
            STRING to 0x2AA198, COMMENT to 0x93A1A1, NUMBER to 0xD33682, DECORATOR to 0xB58900,
            FUNCTION to 0x268BD2, CLASS to 0xCB4B16, ERROR to 0xDC322F, WARNING to 0xB58900,
            CONSOLE_BACKGROUND to 0xEEE8D5, CONSOLE_TEXT to 0x657B83,
        ),
        "amoled" to palette(
            "AMOLED Black", true,
            BACKGROUND to 0x000000, TEXT to 0xE6E6E6, GUTTER to 0x0A0A0A, GUTTER_TEXT to 0x5A5A5A,
            CARET to 0xFFCC00, SELECTION to 0x2A2A2A, KEYWORD to 0xC792EA, BUILTIN to 0x82AAFF,
            STRING to 0xC3E88D, COMMENT to 0x7A7A7A, NUMBER to 0xF78C6C, DECORATOR to 0xFFCB6B,
            FUNCTION to 0x82AAFF, CLASS to 0xFFCB6B, ERROR to 0xFF5370, WARNING to 0xFFCB6B,
            CONSOLE_BACKGROUND to 0x000000, CONSOLE_TEXT to 0xE6E6E6,
        ),
    )

    /** Colors that a theme file may leave out are taken from here. */
    fun base(dark: Boolean): Palette = if (dark) oneDark else githubLight

    fun byId(id: String): Palette? = builtIn.firstOrNull { it.first == id }?.second
}
