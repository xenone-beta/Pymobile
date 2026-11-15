package dev.xenolab.pymobile.python

import android.content.Context
import dev.xenolab.pymobile.BuildConfig
import java.io.File

/** Where the interpreter, the packages and the scratch files live on the device. */
class PythonPaths(context: Context) {

    private val app = context.applicationContext

    /** The standard library is unpacked to `<home>/lib/pythonX.Y`. */
    val home: File = File(app.filesDir, "python")

    /** `pip install --user` puts packages here, and the interpreter finds them on its own. */
    val userBase: File = File(app.filesDir, "pyuser")

    val userSite: File = File(userBase, "lib/python${BuildConfig.PYTHON_MINOR}/site-packages")

    val tmp: File = File(app.cacheDir, "pytmp")

    private val pycache: File = File(app.cacheDir, "pycache")

    /** HOME for the interpreter: pip's cache and config go here. */
    val homeDir: File = File(app.filesDir, "home")

    val marker: File = File(home, ".payload")

    fun ensureDirs() {
        for (d in listOf(home, userSite, tmp, pycache, homeDir)) d.mkdirs()
    }

    /** `KEY=VALUE` pairs for the interpreter process. [extra] wins over the defaults. */
    fun environment(extra: Map<String, String> = emptyMap()): List<String> {
        val env = linkedMapOf(
            "PYTHONHOME" to home.absolutePath,
            "PYTHONUSERBASE" to userBase.absolutePath,
            "PYTHONUNBUFFERED" to "1",
            "PYTHONUTF8" to "1",
            "PYTHONIOENCODING" to "utf-8",
            // Keeps compiled files out of the project folders.
            "PYTHONPYCACHEPREFIX" to pycache.absolutePath,
            "TMPDIR" to tmp.absolutePath,
            "HOME" to homeDir.absolutePath,
            // Android keeps its trusted certificates here; Python's ssl module does not know that.
            "SSL_CERT_DIR" to "/system/etc/security/cacerts",
            "TERM" to "dumb",
            "PIP_USER" to "1",
            "PIP_DISABLE_PIP_VERSION_CHECK" to "1",
            "PIP_PROGRESS_BAR" to "off",
            "PIP_NO_INPUT" to "1",
        )
        env.putAll(extra)
        return env.map { (k, v) -> "$k=$v" }
    }
}
