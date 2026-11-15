package dev.xenolab.pymobile.project

import java.io.File

enum class FileError { BAD_NAME, EXISTS, FAILED }

/** The outcome of a file operation: either [file] or [error] is set. */
data class FileOp(val file: File?, val error: FileError?) {
    val ok: Boolean get() = file != null
}

/** Plain file handling for the project folders. Nothing here knows about the UI. */
object ProjectFiles {

    const val STARTER_SOURCE = "print(\"Hello, world!\")\n"
    const val MAX_EDITABLE_BYTES = 2L * 1024 * 1024

    private val hiddenNames = setOf("__pycache__", ".git", ".idea", ".DS_Store")
    private val textExtensions = setOf(
        "py", "pyi", "txt", "md", "json", "toml", "yaml", "yml", "ini", "cfg", "csv", "html", "css", "js",
        "xml", "sh", "env", "gitignore", "rst", "log", "sql", "bat", "tsv",
    )

    /** Folders first, then files, each sorted by name ignoring case. */
    fun list(dir: File, showHidden: Boolean = false): List<File> {
        val all = dir.listFiles() ?: return emptyList()
        return all.filter { showHidden || it.name !in hiddenNames }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** A name for one file or folder: no slashes, no dots-only names, no control characters. */
    fun isValidName(name: String): Boolean {
        val n = name.trim()
        return n.isNotEmpty() && n.length <= 100 && n != "." && n != ".." &&
            n.none { it == '/' || it == '\\' || it.isISOControl() }
    }

    fun create(parent: File, name: String, folder: Boolean): FileOp {
        if (!isValidName(name)) return FileOp(null, FileError.BAD_NAME)
        val target = File(parent, name.trim())
        if (target.exists()) return FileOp(null, FileError.EXISTS)
        val ok = try {
            if (folder) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.createNewFile()
            }
        } catch (_: java.io.IOException) {
            false
        }
        return if (ok) FileOp(target, null) else FileOp(null, FileError.FAILED)
    }

    fun rename(file: File, newName: String): FileOp {
        if (!isValidName(newName)) return FileOp(null, FileError.BAD_NAME)
        val target = File(file.parentFile, newName.trim())
        if (target.exists()) return FileOp(null, FileError.EXISTS)
        return if (file.renameTo(target)) FileOp(target, null) else FileOp(null, FileError.FAILED)
    }

    fun delete(file: File): Boolean = if (file.isDirectory) file.deleteRecursively() else file.delete()

    /** "name.py", then "name2.py", "name3.py" ... whichever is free in [dir]. */
    fun uniqueName(dir: File, base: String, extension: String): String {
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var candidate = "$base$suffix"
        var n = 2
        while (File(dir, candidate).exists()) candidate = "$base${n++}$suffix"
        return candidate
    }

    /** A folder with a ready-to-run main.py. */
    fun createProject(root: File, name: String): FileOp {
        val made = create(root, name, folder = true)
        val dir = made.file ?: return made
        File(dir, "main.py").writeText(STARTER_SOURCE)
        return made
    }

    /** True for files the editor should open: known text types, or anything without binary bytes. */
    fun isEditable(file: File): Boolean {
        if (!file.isFile || file.length() > MAX_EDITABLE_BYTES) return false
        val ext = file.extension.lowercase()
        if (ext in textExtensions || (file.extension.isEmpty() && file.name.startsWith("."))) return true
        return try {
            file.inputStream().use { input ->
                val head = ByteArray(4096)
                val n = input.read(head)
                (0 until maxOf(n, 0)).none { head[it].toInt() == 0 }
            }
        } catch (_: java.io.IOException) {
            false
        }
    }

    fun read(file: File): String = file.readText(Charsets.UTF_8).replace("\r\n", "\n")

    /** Writes through a temporary file so a crash cannot leave a half-written script behind. */
    fun write(file: File, text: String): Boolean = try {
        val temp = File(file.parentFile, "." + file.name + ".tmp")
        temp.writeText(text, Charsets.UTF_8)
        if (file.exists() && !file.delete()) {
            temp.delete()
            false
        } else {
            temp.renameTo(file)
        }
    } catch (_: java.io.IOException) {
        false
    }

    /** Path of [file] relative to [root], or its full path when it lies elsewhere. */
    fun relative(root: File, file: File): String {
        val r = root.absoluteFile.path
        val f = file.absoluteFile.path
        return if (f.startsWith(r + File.separator)) f.substring(r.length + 1) else f
    }
}
