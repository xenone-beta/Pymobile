package dev.xenolab.pymobile.data

import android.content.Context
import dev.xenolab.pymobile.python.RunConfig
import org.json.JSONArray
import org.json.JSONObject

/** User settings, kept in SharedPreferences. Nothing here is synced or backed up. */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pymobile", Context.MODE_PRIVATE)

    var projectsRoot: String
        get() = prefs.getString("projects_root", "") ?: ""
        set(v) = prefs.edit().putString("projects_root", v).apply()

    var fontSize: Int
        get() = prefs.getInt("font_size", 14).coerceIn(MIN_FONT, MAX_FONT)
        set(v) = prefs.edit().putInt("font_size", v.coerceIn(MIN_FONT, MAX_FONT)).apply()

    /** 0 = follow the system, 1 = light, 2 = dark. */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) = prefs.edit().putInt("theme_mode", v).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(v) = prefs.edit().putBoolean("dynamic_color", v).apply()

    var lineNumbers: Boolean
        get() = prefs.getBoolean("line_numbers", true)
        set(v) = prefs.edit().putBoolean("line_numbers", v).apply()

    var autoPair: Boolean
        get() = prefs.getBoolean("auto_pair", true)
        set(v) = prefs.edit().putBoolean("auto_pair", v).apply()

    var formatOnSave: Boolean
        get() = prefs.getBoolean("format_on_save", false)
        set(v) = prefs.edit().putBoolean("format_on_save", v).apply()

    var gitName: String
        get() = prefs.getString("git_name", "") ?: ""
        set(v) = prefs.edit().putString("git_name", v).apply()

    var gitEmail: String
        get() = prefs.getString("git_email", "") ?: ""
        set(v) = prefs.edit().putString("git_email", v).apply()

    /** A GitHub personal access token. It stays in this app's private storage. */
    var gitToken: String
        get() = prefs.getString("git_token", "") ?: ""
        set(v) = prefs.edit().putString("git_token", v).apply()

    var tutorialSeen: Boolean
        get() = prefs.getBoolean("tutorial_seen", false)
        set(v) = prefs.edit().putBoolean("tutorial_seen", v).apply()

    var openTabs: List<String>
        get() = runCatching {
            val a = JSONArray(prefs.getString("open_tabs", "[]"))
            List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
        set(v) = prefs.edit().putString("open_tabs", JSONArray(v).toString()).apply()

    var activeTab: String
        get() = prefs.getString("active_tab", "") ?: ""
        set(v) = prefs.edit().putString("active_tab", v).apply()

    /** Save a moment after typing stops, instead of only on request. */
    var autoSave: Boolean
        get() = prefs.getBoolean("auto_save", true)
        set(v) = prefs.edit().putBoolean("auto_save", v).apply()

    /** Folder of the background run that is (or was last) active, so the IDE can re-attach to it. */
    var bgDir: String
        get() = prefs.getString("bg_dir", "") ?: ""
        set(v) = prefs.edit().putString("bg_dir", v).apply()

    /** Arguments and environment variables set for a file. Kept in private storage, not encrypted. */
    fun runConfig(path: String): RunConfig = runCatching {
        val o = JSONObject(prefs.getString("run_configs", "{}")).optJSONObject(path)
        RunConfig(o?.optString("args") ?: "", o?.optString("env") ?: "")
    }.getOrDefault(RunConfig())

    fun setRunConfig(path: String, config: RunConfig) {
        val root = runCatching { JSONObject(prefs.getString("run_configs", "{}")) }.getOrDefault(JSONObject())
        if (config.isEmpty) root.remove(path) else root.put(path, JSONObject().put("args", config.args).put("env", config.env))
        prefs.edit().putString("run_configs", root.toString()).apply()
    }

    companion object {
        const val MIN_FONT = 10
        const val MAX_FONT = 24
    }
}
