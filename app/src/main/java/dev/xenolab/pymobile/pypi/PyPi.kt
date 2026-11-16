package dev.xenolab.pymobile.pypi

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SearchHit(val name: String, val version: String, val summary: String)

/** What a project's index page says about one version. */
data class VersionInfo(val version: String, val pure: Boolean)

data class PackageDetail(
    val name: String,
    val latest: String,
    val summary: String,
    val requiresPython: String,
    val link: String,
    /** Newest first. */
    val versions: List<VersionInfo>,
)

/** A look at pypi.org: search, and the versions of one project. */
object PyPi {

    private const val BASE = "https://pypi.org"
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val SIMPLE_JSON = "application/vnd.pypi.simple.v1+json"
    private val validName = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    private val prerelease = Regex("(a|b|c|rc|alpha|beta|pre|preview|dev)\\d*", RegexOption.IGNORE_CASE)

    /** How PyPI spells a project in URLs: lower case, runs of `-`, `_` and `.` become one `-`. */
    fun normalize(name: String): String = name.trim().lowercase().replace(Regex("[-_.]+"), "-")

    fun looksLikeName(query: String): Boolean = validName.matches(query.trim())

    // ---------------------------------------------------------------- parsing (no network)

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()

    /** The parts of a version that decide ordering: its numbers, and whether it is a pre-release. */
    internal fun versionKey(v: String): Pair<List<Int>, Boolean> {
        val m = Regex("^v?(\\d+(?:\\.\\d+)*)(.*)$").find(v.trim()) ?: return emptyList<Int>() to true
        val numbers = m.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 }
        val rest = m.groupValues[2].trimStart('.', '-', '_').replace(Regex("^post\\d*", RegexOption.IGNORE_CASE), "")
        return numbers to (rest.isNotEmpty() && prerelease.containsMatchIn(rest))
    }

    internal fun isPrerelease(v: String): Boolean = versionKey(v).second

    /** Newest first. */
    internal val newestFirst = Comparator<String> { a, b ->
        val (na, pa) = versionKey(a)
        val (nb, pb) = versionKey(b)
        var result = 0
        for (i in 0 until maxOf(na.size, nb.size)) {
            val x = na.getOrElse(i) { 0 }
            val y = nb.getOrElse(i) { 0 }
            if (x != y) {
                result = y.compareTo(x)
                break
            }
        }
        if (result == 0 && pa != pb) result = if (pa) 1 else -1
        if (result == 0) result = b.compareTo(a)
        result
    }

    /** The version a distribution file belongs to, from its file name. Null for names it cannot read. */
    internal fun versionOfFile(filename: String): String? {
        if (filename.endsWith(".whl")) {
            val parts = filename.removeSuffix(".whl").split('-')
            return parts.getOrNull(1)
        }
        for (ext in listOf(".tar.gz", ".zip", ".tar.bz2", ".tgz", ".egg")) {
            if (filename.endsWith(ext)) return filename.removeSuffix(ext).substringAfterLast('-').ifEmpty { null }
        }
        return null
    }

    /**
     * Reads a PEP 691 index page: the versions that still have a downloadable file, each marked pure when a
     * `none-any` wheel exists (those install on Android; other wheels are built for other platforms).
     */
    fun parseSimple(json: String): List<VersionInfo> {
        val root = JSONObject(json)
        val files = root.optJSONArray("files") ?: return emptyList()
        val usable = HashSet<String>()
        val pure = HashSet<String>()
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            val yanked = f.opt("yanked").let { it == true || (it is String && it.isNotEmpty()) }
            if (yanked) continue
            val name = f.text("filename")
            val version = versionOfFile(name) ?: continue
            usable += version
            if (name.endsWith("-none-any.whl")) pure += version
        }
        val all = usable.sortedWith(newestFirst)
        val stable = all.filterNot { isPrerelease(it) }
        return (if (stable.isNotEmpty()) stable else all).take(30).map { VersionInfo(it, it in pure) }
    }

    /** Reads `/pypi/<name>/<version>/json`. */
    fun parseInfo(json: String): PackageDetail? {
        val info = JSONObject(json).optJSONObject("info") ?: return null
        val name = info.text("name")
        if (name.isEmpty()) return null
        val link = info.text("package_url").ifEmpty { "$BASE/project/${normalize(name)}/" }
        return PackageDetail(name, info.text("version"), info.text("summary"), info.text("requires_python"), link, emptyList())
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&amp;", "&")

    /** Reads the results page of pypi.org/search. */
    fun parseSearch(html: String): List<SearchHit> {
        val block = Regex("<a class=\"package-snippet\" href=\"/project/([^\"/]+)/?\">(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        fun part(body: String, cls: String): String =
            Regex("<(span|p) class=\"package-snippet__$cls\"[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
                .find(body)?.groupValues?.get(2)?.replace(Regex("<[^>]+>"), "")?.trim()?.let(::decodeEntities).orEmpty()
        return block.findAll(html).mapNotNull { m ->
            val body = m.groupValues[2]
            val name = part(body, "name").ifEmpty { decodeEntities(m.groupValues[1]) }
            if (name.isEmpty()) null else SearchHit(name, part(body, "version"), part(body, "description"))
        }.toList()
    }

    // ---------------------------------------------------------------- network

    private fun get(url: String, accept: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Pymobile (+https://github.com/xenone-beta/Pymobile)")
            conn.setRequestProperty("Accept", accept)
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("The answer is too large")
                }
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            conn.disconnect()
        }
    }

    /** Hits for [query]. If the search page gives nothing usable, an exact project name is looked up instead. */
    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        require(q.isNotEmpty())
        var hits: List<SearchHit> = emptyList()
        var failure: IOException? = null
        try {
            hits = parseSearch(get("$BASE/search/?q=" + URLEncoder.encode(q, "UTF-8"), "text/html"))
        } catch (e: IOException) {
            failure = e
        }
        if (hits.isEmpty() && looksLikeName(q)) {
            try {
                val d = parseInfo(get("$BASE/pypi/${normalize(q)}/json", "application/json"))
                if (d != null) hits = listOf(SearchHit(d.name, d.latest, d.summary))
            } catch (e: IOException) {
                if (failure == null && !e.message.orEmpty().startsWith("HTTP 404")) failure = e
            }
        }
        if (hits.isEmpty() && failure != null) throw failure
        hits
    }

    suspend fun detail(name: String): PackageDetail = withContext(Dispatchers.IO) {
        val slug = normalize(name)
        val versions = parseSimple(get("$BASE/simple/$slug/", SIMPLE_JSON))
        val newest = versions.firstOrNull()?.version
        val info = try {
            parseInfo(get(if (newest != null) "$BASE/pypi/$slug/$newest/json" else "$BASE/pypi/$slug/json", "application/json"))
        } catch (_: IOException) {
            null
        }
        (info ?: PackageDetail(name, newest.orEmpty(), "", "", "$BASE/project/$slug/", emptyList())).copy(versions = versions)
    }
}
