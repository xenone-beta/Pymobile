package dev.xenolab.pymobile.python

import org.json.JSONArray
import java.io.File

data class PipPackage(val name: String, val version: String)

/** Helpers around pip: what to run, and how to read what it prints. */
object Pip {

    private const val OP = "(==|>=|<=|~=|!=|<|>)"
    private const val VERSION = "[A-Za-z0-9.*+!_\\-]+"
    private val requirement = Regex(
        "^[A-Za-z0-9][A-Za-z0-9._\\-]*(\\[[A-Za-z0-9,_\\-]+\\])?($OP$VERSION(,$OP$VERSION)*)?$",
    )

    /** Splits user input into requirements and rejects anything that is not a plain package name. */
    fun parseRequirements(input: String): List<String>? {
        val parts = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return if (parts.all { requirement.matches(it) }) parts else null
    }

    fun installArgs(requirements: List<String>, upgrade: Boolean = false): List<String> =
        listOf("-m", "pip", "install") + (if (upgrade) listOf("--upgrade") else emptyList()) + requirements

    fun requirementsFileArgs(file: String): List<String> = listOf("-m", "pip", "install", "-r", file)

    fun uninstallArgs(name: String): List<String> = listOf("-m", "pip", "uninstall", "-y", name)

    /**
     * Lists installed distributions straight from the package metadata, so it works even when pip itself is
     * broken (and ignores PIP_USER, which would hide everything outside the user site).
     */
    private const val LIST_SCRIPT =
        "import importlib.metadata as m, json; " +
            "print(json.dumps([{'name': d.metadata['Name'], 'version': d.version} for d in m.distributions() if d.metadata['Name']]))"

    val listArgs: List<String> = listOf("-c", LIST_SCRIPT)

    val versionArgs: List<String> = listOf("-m", "pip", "--version")

    /**
     * Lists what is installed by reading the `*.dist-info` folders of the given site-packages directories.
     * No interpreter is needed, so the list works even when Python itself cannot start.
     */
    fun scanInstalled(dirs: List<File>): List<PipPackage> {
        val found = ArrayList<PipPackage>()
        for (dir in dirs) {
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!child.isDirectory || !child.name.endsWith(".dist-info")) continue
                val parsed = parseDistInfoName(child.name.removeSuffix(".dist-info")) ?: continue
                val metaName = readMetadataName(File(child, "METADATA"))
                found += PipPackage(metaName ?: parsed.first, parsed.second)
            }
        }
        return found.filter { it.name.isNotEmpty() }.distinctBy { it.name.lowercase().replace('_', '-') }
            .sortedBy { it.name.lowercase() }
    }

    /** `Flask_Cors-4.0.1` gives ("Flask_Cors", "4.0.1"). */
    fun parseDistInfoName(base: String): Pair<String, String>? {
        val i = base.lastIndexOf('-')
        if (i <= 0 || i == base.length - 1) return null
        return base.substring(0, i) to base.substring(i + 1)
    }

    private fun readMetadataName(file: File): String? = try {
        if (!file.isFile) null else file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.take(40).firstOrNull { it.startsWith("Name:") }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
        }
    } catch (_: java.io.IOException) {
        null
    }

    /** Reads the output of `pip list --format=json`. Extra text before the JSON is tolerated. */
    fun parseList(output: String): List<PipPackage> {
        val start = output.indexOf('[')
        val end = output.lastIndexOf(']')
        if (start < 0 || end < start) return emptyList()
        return try {
            val array = JSONArray(output.substring(start, end + 1))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                PipPackage(o.optString("name"), o.optString("version"))
            }.filter { it.name.isNotEmpty() }.distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }
}
