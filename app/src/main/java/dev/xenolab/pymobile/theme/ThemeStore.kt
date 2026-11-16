package dev.xenolab.pymobile.theme

import java.io.File

/** A theme the user imported or made. [id] is what the settings remember. */
data class StoredTheme(val id: String, val palette: Palette)

/** Keeps the user's `.pmt` files in one folder. Ids look like `user:my-theme`. */
class ThemeStore(private val dir: File) {

    private fun fileFor(id: String): File? {
        if (!id.startsWith(PREFIX)) return null
        val slug = id.removePrefix(PREFIX)
        if (slug.isEmpty() || !slug.all { it.isLetterOrDigit() || it == '-' }) return null
        return File(dir, "$slug.${Pmt.EXTENSION}")
    }

    fun list(): List<StoredTheme> {
        val files = dir.listFiles { f -> f.isFile && f.extension == Pmt.EXTENSION } ?: return emptyList()
        return files.sortedBy { it.name.lowercase() }.mapNotNull { f ->
            val result = runCatching { Pmt.parse(f.readText(Charsets.UTF_8)) }.getOrNull()
            (result as? Pmt.Result.Ok)?.let { StoredTheme(PREFIX + f.nameWithoutExtension, it.palette) }
        }
    }

    fun read(id: String): Palette? {
        val f = fileFor(id)?.takeIf { it.isFile } ?: return null
        return (runCatching { Pmt.parse(f.readText(Charsets.UTF_8)) }.getOrNull() as? Pmt.Result.Ok)?.palette
    }

    /** Saves [palette]. With [replaceId] that file is overwritten, otherwise a free name is chosen. Returns the id. */
    fun save(palette: Palette, replaceId: String? = null): String? {
        val target = replaceId?.let { fileFor(it) } ?: run {
            val base = slug(palette.name)
            var candidate = base
            var n = 2
            while (File(dir, "$candidate.${Pmt.EXTENSION}").exists()) candidate = "$base-${n++}"
            File(dir, "$candidate.${Pmt.EXTENSION}")
        }
        return try {
            dir.mkdirs()
            target.writeText(Pmt.write(palette), Charsets.UTF_8)
            PREFIX + target.nameWithoutExtension
        } catch (_: Exception) {
            null
        }
    }

    fun delete(id: String): Boolean = fileFor(id)?.delete() == true

    companion object {
        const val PREFIX = "user:"

        fun slug(name: String): String {
            val s = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
                .replace(Regex("-+"), "-").trim('-')
            return s.ifEmpty { "theme" }.take(40)
        }
    }
}
