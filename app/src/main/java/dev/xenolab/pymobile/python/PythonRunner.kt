package dev.xenolab.pymobile.python

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** What to run: `python3 <args>` in [cwd]. */
data class PythonRequest(val args: List<String>, val cwd: File, val extraEnv: Map<String, String> = emptyMap())

interface RunListener {
    /** Called on the main thread. */
    fun onOutput(text: String, isError: Boolean)

    /** Called once on the main thread, after all output has been delivered. */
    fun onExit(code: Int)
}

data class Capture(val exitCode: Int, val output: String)

/** One run of the interpreter, in its own process. */
class PythonSession internal constructor(
    private val context: Context,
    private val request: PythonRequest,
    private val listener: RunListener,
    private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private var servicePid = 0
    private var finished = false
    private var stopped = false
    private var exitCode = EXIT_CRASHED
    private var stdin: OutputStream? = null
    private val readers = ArrayList<Job>()
    private var connection: ServiceConnection? = null

    private val reply = Messenger(Handler(Looper.getMainLooper()) { m ->
        when (m.what) {
            PythonService.MSG_STARTED -> servicePid = m.arg1
            PythonService.MSG_EXIT -> {
                exitCode = m.arg1
                // The process is killed shortly after; this is the fallback if that never reaches us.
                main.postDelayed({ finish() }, FALLBACK_FINISH_MS)
            }
        }
        true
    })

    internal fun start() {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (finished) return
                if (!send(Messenger(binder))) finish()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                finish()
            }

            override fun onBindingDied(name: ComponentName) {
                finish()
            }
        }
        connection = conn
        val bound = context.bindService(Intent(context, PythonService::class.java), conn, Context.BIND_AUTO_CREATE)
        if (!bound) {
            listener.onOutput("Could not start the Python service.\n", true)
            finish()
        }
    }

    private fun send(service: Messenger): Boolean {
        val inPipe = ParcelFileDescriptor.createPipe()
        val outPipe = ParcelFileDescriptor.createPipe()
        val errPipe = ParcelFileDescriptor.createPipe()
        val data = Bundle().apply {
            putStringArray(PythonService.KEY_ARGS, request.args.toTypedArray())
            putStringArray(PythonService.KEY_ENV, PythonPaths(context).environment(request.extraEnv).toTypedArray())
            putString(PythonService.KEY_CWD, request.cwd.absolutePath)
            putParcelable(PythonService.KEY_IN, inPipe[0])
            putParcelable(PythonService.KEY_OUT, outPipe[1])
            putParcelable(PythonService.KEY_ERR, errPipe[1])
        }
        val msg = Message.obtain(null, PythonService.MSG_RUN).apply {
            this.data = data
            replyTo = reply
        }
        try {
            service.send(msg)
        } catch (e: Exception) {
            listener.onOutput("Could not talk to the Python service: $e\n", true)
            for (p in listOf(inPipe, outPipe, errPipe)) for (fd in p) runCatching { fd.close() }
            return false
        }
        // The service holds its own copies now. Closing ours is what lets us see end-of-file.
        runCatching { inPipe[0].close() }
        runCatching { outPipe[1].close() }
        runCatching { errPipe[1].close() }

        stdin = ParcelFileDescriptor.AutoCloseOutputStream(inPipe[1])
        readers += read(outPipe[0], false)
        readers += read(errPipe[0], true)
        return true
    }

    private fun read(pfd: ParcelFileDescriptor, isError: Boolean): Job = scope.launch(Dispatchers.IO) {
        try {
            InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd), Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    val text = String(buffer, 0, n)
                    main.post { listener.onOutput(text, isError) }
                }
            }
        } catch (_: java.io.IOException) {
            // The pipe closed under us: the process is gone.
        }
    }

    /** Sends text to the program's standard input. */
    fun writeInput(text: String) {
        val out = stdin ?: return
        scope.launch(Dispatchers.IO) {
            try {
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: java.io.IOException) {
                // The program has already ended.
            }
        }
    }

    /** Closes standard input (Ctrl+D). */
    fun closeInput() {
        val out = stdin ?: return
        stdin = null
        scope.launch(Dispatchers.IO) { runCatching { out.close() } }
    }

    /** Ends the run at once by killing the interpreter's process. */
    fun stop() {
        if (finished) return
        stopped = true
        if (servicePid != 0) Process.killProcess(servicePid) else finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        scope.launch {
            readers.toList().joinAll()
            main.post {
                connection?.let { runCatching { context.unbindService(it) } }
                connection = null
                runCatching { stdin?.close() }
                stdin = null
                listener.onExit(if (stopped) EXIT_STOPPED else exitCode)
            }
        }
    }

    companion object {
        const val EXIT_CRASHED = -1
        const val EXIT_STOPPED = -2
        private const val FALLBACK_FINISH_MS = 2000L
    }
}

/** Starts runs of the interpreter. */
class PythonRunner(context: Context, private val scope: CoroutineScope) {

    private val app = context.applicationContext

    fun start(request: PythonRequest, listener: RunListener): PythonSession {
        val session = PythonSession(app, request, listener, scope)
        session.start()
        return session
    }

    /** Runs to the end and returns everything printed. Null if it takes longer than [timeoutMs]. */
    suspend fun capture(request: PythonRequest, timeoutMs: Long = 60_000): Capture? {
        var session: PythonSession? = null
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Capture> { cont ->
                val collected = StringBuilder()
                session = start(request, object : RunListener {
                    override fun onOutput(text: String, isError: Boolean) {
                        collected.append(text)
                    }

                    override fun onExit(code: Int) {
                        if (cont.isActive) cont.resume(Capture(code, collected.toString()))
                    }
                })
                cont.invokeOnCancellation { session?.stop() }
            }
        }
        if (result == null) session?.stop()
        return result
    }
}
