package dev.xenolab.pymobile.data

import android.content.Context
import org.json.JSONArray

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

    companion object {
        const val MIN_FONT = 10
        const val MAX_FONT = 24
    }
}
