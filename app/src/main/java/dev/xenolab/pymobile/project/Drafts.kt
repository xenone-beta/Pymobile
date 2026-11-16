package dev.xenolab.pymobile.project

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Copies of unsaved editor text, kept next to the app so that a crash or a killed app does not lose
 * what was typed. One small file per source file, named after a hash of its path.
 */
class Drafts(private val dir: File) {

    private fun fileFor(path: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.draft")
    }

    fun write(path: String, text: String): Boolean = try {
        dir.mkdirs()
        val json = JSONObject().put("path", path).put("text", text).toString()
        val target = fileFor(path)
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(json, Charsets.UTF_8)
        temp.renameTo(target) || run { target.delete(); temp.renameTo(target) }
    } catch (_: Exception) {
        false
    }

    fun read(path: String): String? {
        val f = fileFor(path)
        if (!f.isFile) return null
        return try {
            val json = JSONObject(f.readText(Charsets.UTF_8))
            if (json.optString("path") == path) json.getString("text") else null
        } catch (_: Exception) {
            null
        }
    }

    fun delete(path: String) {
        runCatching { fileFor(path).delete() }
    }
}
