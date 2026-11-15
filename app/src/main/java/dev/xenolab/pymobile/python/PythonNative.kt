package dev.xenolab.pymobile.python

/** The JNI bridge to `pymobile.c`. Only ever used inside the `:python` process. */
object PythonNative {

    fun load() {
        System.loadLibrary("pymobile")
    }

    /**
     * Runs `python3 <args>` with the given environment and working directory, using the three file
     * descriptors as stdin, stdout and stderr. Returns the exit code. It must be called at most once
     * per process.
     */
    @JvmStatic
    external fun run(
        args: Array<String>,
        env: Array<String>,
        cwd: String,
        fdIn: Int,
        fdOut: Int,
        fdErr: Int,
    ): Int
}
