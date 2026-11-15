package dev.xenolab.pymobile.python

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process

/**
 * Hosts the interpreter in the `:python` process. One run per process: when the program ends, or when
 * the IDE sends a stop message, the process is killed, so the next run starts clean.
 */
class PythonService : Service() {

    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
            handle(msg)
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    @Suppress("DEPRECATION")
    private fun handle(msg: Message) {
        when (msg.what) {
            MSG_RUN -> {
                val data = msg.data
                data.classLoader = PythonService::class.java.classLoader
                val args = data.getStringArray(KEY_ARGS) ?: emptyArray()
                val env = data.getStringArray(KEY_ENV) ?: emptyArray()
                val cwd = data.getString(KEY_CWD) ?: "/"
                val fdIn = data.getParcelable<ParcelFileDescriptor>(KEY_IN)
                val fdOut = data.getParcelable<ParcelFileDescriptor>(KEY_OUT)
                val fdErr = data.getParcelable<ParcelFileDescriptor>(KEY_ERR)
                val replyTo = msg.replyTo
                if (fdIn == null || fdOut == null || fdErr == null) {
                    Process.killProcess(Process.myPid())
                    return
                }
                runCatching { replyTo?.send(Message.obtain(null, MSG_STARTED, Process.myPid(), 0)) }
                Thread({
                    var code = EXIT_LAUNCH_FAILED
                    try {
                        PythonNative.load()
                        code = PythonNative.run(args, env, cwd, fdIn.detachFd(), fdOut.detachFd(), fdErr.detachFd())
                    } catch (t: Throwable) {
                        // The native side never started, so the pipes are still ours: report there.
                        runCatching {
                            ParcelFileDescriptor.AutoCloseOutputStream(fdErr).use {
                                it.write("Could not start Python: $t\n".toByteArray())
                            }
                        }
                    } finally {
                        runCatching { replyTo?.send(Message.obtain(null, MSG_EXIT, code, 0)) }
                        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, EXIT_DELAY_MS)
                    }
                }, "python-main").start()
            }
            MSG_STOP -> Process.killProcess(Process.myPid())
        }
    }

    companion object {
        const val MSG_RUN = 1
        const val MSG_STOP = 2
        const val MSG_STARTED = 3
        const val MSG_EXIT = 4

        const val KEY_ARGS = "args"
        const val KEY_ENV = "env"
        const val KEY_CWD = "cwd"
        const val KEY_IN = "in"
        const val KEY_OUT = "out"
        const val KEY_ERR = "err"

        const val EXIT_LAUNCH_FAILED = 127
        private const val EXIT_DELAY_MS = 150L
    }
}
