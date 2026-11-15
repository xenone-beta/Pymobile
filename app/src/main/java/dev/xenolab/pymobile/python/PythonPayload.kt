package dev.xenolab.pymobile.python

import android.content.Context
import dev.xenolab.pymobile.BuildConfig
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Unpacks what the APK carries (the standard library and pip) into the app's files.
 * The libraries themselves are loaded straight from the APK's native library folder.
 */
object PythonPayload {

    private const val STDLIB_ASSET = "pylib.zip"
    private const val PIP_ASSET = "pip.whl"

    /** Changes whenever the APK carries a different Python or pip. */
    fun signature(context: Context): String {
        fun size(name: String): Long = context.assets.openFd(name).use { it.length }
        return "${BuildConfig.PYTHON_VERSION}:${size(STDLIB_ASSET)}:${size(PIP_ASSET)}"
    }

    fun isInstalled(context: Context): Boolean {
        val marker = PythonPaths(context).marker
        return marker.isFile && marker.readText() == signature(context)
    }

    /** Blocking. [progress] gets values from 0 to 1. Throws if the payload is missing or broken. */
    fun install(context: Context, progress: (Float) -> Unit) {
        val paths = PythonPaths(context)
        val signature = signature(context)
        paths.marker.delete()
        paths.home.deleteRecursively()
        paths.ensureDirs()

        val total = context.assets.openFd(STDLIB_ASSET).use { it.length }.coerceAtLeast(1)
        context.assets.open(STDLIB_ASSET).use { raw ->
            val counting = CountingStream(raw)
            unzip(counting, paths.home) { progress(counting.count.toFloat() / total * 0.9f) }
        }

        // pip is a pure Python wheel: unpacking it into the user site installs it.
        File(paths.userSite, "pip").deleteRecursively()
        paths.userSite.listFiles { f -> f.name.startsWith("pip-") && f.name.endsWith(".dist-info") }
            ?.forEach { it.deleteRecursively() }
        context.assets.open(PIP_ASSET).use { unzip(it, paths.userSite) { } }
        progress(1f)

        paths.marker.writeText(signature)
    }

    private class CountingStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }
    }

    private fun unzip(input: InputStream, target: File, onEntry: () -> Unit) {
        val root = target.canonicalFile
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            var n = 0
            while (entry != null) {
                val out = File(root, entry.name).canonicalFile
                // A crafted entry name must not escape the target folder.
                require(out.path == root.path || out.path.startsWith(root.path + File.separator)) {
                    "Unsafe path in archive: ${entry!!.name}"
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                if (++n % 100 == 0) onEntry()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
