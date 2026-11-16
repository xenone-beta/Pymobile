package dev.xenolab.pymobile.python

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.content.ContextCompat
import dev.xenolab.pymobile.MainActivity
import dev.xenolab.pymobile.R
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The files a background run communicates through. The service writes them, the IDE only reads. */
object BackgroundFiles {
    const val OUT = "out.log"
    const val ERR = "err.log"
    const val PID = "pid"
    const val EXIT = "exit"

    fun dir(context: Context) = File(context.applicationContext.filesDir, "bg")
}

/**
 * Runs a script in the background as a foreground service, in its own process (`:pybg`), so it can
 * run next to a normal run or pip. Output goes to log files instead of pipes, which is what lets the
 * script keep running (and lets the IDE re-attach) after the app's own process has gone away.
 * There is no standard input.
 */
class BackgroundPythonService : Service() {

    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_STOP) {
            Process.killProcess(Process.myPid())
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        val dirPath = intent.getStringExtra(EXTRA_DIR)
        if (dirPath == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        val dir = File(dirPath)
        val args = intent.getStringArrayExtra(EXTRA_ARGS) ?: emptyArray()
        val env = intent.getStringArrayExtra(EXTRA_ENV) ?: emptyArray()
        val cwd = intent.getStringExtra(EXTRA_CWD) ?: "/"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Python"

        enterForeground(title)
        runCatching { File(dir, BackgroundFiles.PID).writeText(Process.myPid().toString()) }

        Thread({
            var code = EXIT_LAUNCH_FAILED
            try {
                PythonNative.load()
                val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND
                val fdIn = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)
                val fdOut = ParcelFileDescriptor.open(File(dir, BackgroundFiles.OUT), mode)
                val fdErr = ParcelFileDescriptor.open(File(dir, BackgroundFiles.ERR), mode)
                code = PythonNative.run(args, env, cwd, fdIn.detachFd(), fdOut.detachFd(), fdErr.detachFd())
            } catch (t: Throwable) {
                runCatching { File(dir, BackgroundFiles.ERR).appendText("Could not start Python: $t\n") }
            } finally {
                runCatching { File(dir, BackgroundFiles.EXIT).writeText(code.toString()) }
                notifyFinished(title, code)
                Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 400)
            }
        }, "python-bg").start()
        return START_NOT_STICKY
    }

    private fun channel(): String {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.bg_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        return CHANNEL
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun enterForeground(title: String) {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BackgroundPythonService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_launcher_monochrome)
        val notification = Notification.Builder(this, channel())
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(getString(R.string.bg_running))
            .setContentIntent(openApp())
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icon, getString(R.string.stop), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyFinished(title: String, code: Int) {
        runCatching {
            val notification = Notification.Builder(this, channel())
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(getString(R.string.bg_finished, code))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_DONE_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "dev.xenolab.pymobile.action.STOP_BACKGROUND"
        const val EXTRA_ARGS = "args"
        const val EXTRA_ENV = "env"
        const val EXTRA_CWD = "cwd"
        const val EXTRA_DIR = "dir"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL = "background"
        private const val NOTIFICATION_ID = 41
        private const val NOTIFICATION_DONE_ID = 42
        private const val EXIT_LAUNCH_FAILED = 127
    }
}

object BackgroundRuns {

    /** Clears the run folder and starts the service. Returns the folder to watch, or null if Android refused. */
    fun start(context: Context, request: PythonRequest, title: String): File? {
        val app = context.applicationContext
        val dir = BackgroundFiles.dir(app)
        dir.deleteRecursively()
        if (!dir.mkdirs()) return null
        val intent = Intent(app, BackgroundPythonService::class.java)
            .putExtra(BackgroundPythonService.EXTRA_ARGS, request.args.toTypedArray())
            .putExtra(BackgroundPythonService.EXTRA_ENV, PythonPaths(app).environment(request.extraEnv).toTypedArray())
            .putExtra(BackgroundPythonService.EXTRA_CWD, request.cwd.absolutePath)
            .putExtra(BackgroundPythonService.EXTRA_DIR, dir.absolutePath)
            .putExtra(BackgroundPythonService.EXTRA_TITLE, title)
        return try {
            ContextCompat.startForegroundService(app, intent)
            dir
        } catch (e: Exception) {
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        }
    }
}

/** Reads the new bytes of a growing file, never cutting a UTF-8 character in half. */
internal class FileTail(private val file: File) {
    private var offset = 0L
    private var pending = ByteArray(0)

    fun read(): String {
        if (!file.isFile) return ""
        val length = file.length()
        if (length < offset) offset = 0
        if (length == offset) return ""
        val count = (length - offset).coerceAtMost(256L * 1024).toInt()
        val buffer = ByteArray(count)
        RandomAccessFile(file, "r").use {
            it.seek(offset)
            it.readFully(buffer)
        }
        offset += count
        val data = pending + buffer
        val cut = completeUtf8Prefix(data)
        pending = data.copyOfRange(cut, data.size)
        return String(data, 0, cut, Charsets.UTF_8)
    }

    /** True when the whole file has been read. */
    fun caughtUp(): Boolean = !file.isFile || file.length() <= offset

    /** Empties a file that has grown large, once everything in it has been read. */
    fun trimIfLarge(limit: Long) {
        if (file.isFile && file.length() > limit && caughtUp()) {
            runCatching { RandomAccessFile(file, "rw").use { it.setLength(0) } }
            offset = 0
        }
    }

    companion object {
        /** Length of the longest prefix of [b] that does not end inside a multi-byte character. */
        fun completeUtf8Prefix(b: ByteArray): Int {
            var i = b.size - 1
            var back = 0
            while (i >= 0 && back < 4 && (b[i].toInt() and 0xC0) == 0x80) {
                i--
                back++
            }
            if (i < 0) return b.size
            val lead = b[i].toInt() and 0xFF
            val need = when {
                lead >= 0xF0 -> 4
                lead >= 0xE0 -> 3
                lead >= 0xC0 -> 2
                else -> 1
            }
            return if (need > b.size - i) i else b.size
        }
    }
}

/** Follows a background run from the IDE: forwards its log files and notices when it ends. */
class BackgroundWatcher(
    context: Context,
    private val dir: File,
    private val scope: CoroutineScope,
    private val listener: RunListener,
) {
    private val app = context.applicationContext
    private val outTail = FileTail(File(dir, BackgroundFiles.OUT))
    private val errTail = FileTail(File(dir, BackgroundFiles.ERR))
    private var job: Job? = null
    @Volatile private var stopRequested = false

    fun start() {
        val startedAt = System.currentTimeMillis()
        job = scope.launch(Dispatchers.IO) {
            while (true) {
                forward()
                val exit = File(dir, BackgroundFiles.EXIT)
                if (exit.isFile) {
                    delay(120)
                    forward()
                    finish(exit.readText().trim().toIntOrNull() ?: PythonSession.EXIT_CRASHED)
                    return@launch
                }
                val pid = readPid()
                if (pid != null) {
                    if (!isAlive(pid)) {
                        delay(250)
                        forward()
                        val code = File(dir, BackgroundFiles.EXIT).takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
                        finish(code ?: if (stopRequested) PythonSession.EXIT_STOPPED else PythonSession.EXIT_CRASHED)
                        return@launch
                    }
                } else if (System.currentTimeMillis() - startedAt > START_GRACE_MS) {
                    finish(PythonSession.EXIT_CRASHED)
                    return@launch
                }
                outTail.trimIfLarge(MAX_LOG_BYTES)
                errTail.trimIfLarge(MAX_LOG_BYTES)
                delay(POLL_MS)
            }
        }
    }

    private suspend fun forward() {
        val out = outTail.read()
        val err = errTail.read()
        if (out.isEmpty() && err.isEmpty()) return
        withContext(Dispatchers.Main) {
            if (out.isNotEmpty()) listener.onOutput(out, false)
            if (err.isNotEmpty()) listener.onOutput(err, true)
        }
    }

    private suspend fun finish(code: Int) {
        withContext(Dispatchers.Main) { listener.onExit(code) }
    }

    private fun readPid(): Int? =
        File(dir, BackgroundFiles.PID).takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()

    private fun isAlive(pid: Int): Boolean {
        val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val known = manager.runningAppProcesses?.any { it.pid == pid } == true
        return known || File("/proc/$pid").exists()
    }

    fun stop() {
        stopRequested = true
        readPid()?.let { Process.killProcess(it) }
        runCatching { app.stopService(Intent(app, BackgroundPythonService::class.java)) }
    }

    fun detach() {
        job?.cancel()
    }

    companion object {
        private const val POLL_MS = 300L
        private const val START_GRACE_MS = 15_000L
        private const val MAX_LOG_BYTES = 8L * 1024 * 1024
    }
}
