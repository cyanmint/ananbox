package com.cyanmint.anbox.terminal

/**
 * JNI bridge to a real pseudo-terminal (pty) implementation in libanbox.so,
 * modeled after termux-app's terminal-emulator native shim. Used so that the
 * in-app terminal's shell gets a controlling tty (job control, canonical
 * mode, local echo) instead of a pair of anonymous pipes, which otherwise
 * causes shells like `/system/bin/sh` to print
 * "can't find tty fd: No such device or address" and become unresponsive.
 */
internal object PtyNative {
    init {
        System.loadLibrary("anbox")
    }

    /**
     * Forks and execs [cmd] with [args] and [envVars] (`NAME=value` entries)
     * in [cwd], attached to a newly allocated pty of size [rows]x[columns].
     * Returns the pty master file descriptor (or throws on failure) and
     * writes the child pid into `processIdArray[0]`.
     */
    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        processIdArray: IntArray,
        rows: Int,
        columns: Int,
    ): Int

    external fun setWindowSize(fd: Int, rows: Int, columns: Int)

    /** Blocks until [pid] exits, returning its exit status. */
    external fun waitFor(pid: Int): Int

    external fun closeFd(fd: Int)
}
