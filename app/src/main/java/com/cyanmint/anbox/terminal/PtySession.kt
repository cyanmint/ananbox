package com.cyanmint.anbox.terminal

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * A shell subprocess attached to a real pty (see [PtyNative]), so it behaves
 * like a normal interactive terminal (job control, canonical mode, local
 * echo), unlike a plain `ProcessBuilder`-launched process connected to
 * anonymous pipes.
 */
internal class PtySession private constructor(
    private val masterFd: Int,
    private val pid: Int,
) {
    private val parcelFd = ParcelFileDescriptor.adoptFd(masterFd)
    val inputStream: FileInputStream = ParcelFileDescriptor.AutoCloseInputStream(parcelFd)
    val outputStream: FileOutputStream = ParcelFileDescriptor.AutoCloseOutputStream(
        parcelFd.dup(),
    )

    @Volatile
    private var alive = true

    val isAlive: Boolean get() = alive

    fun updateSize(rows: Int, columns: Int) {
        if (!alive || rows <= 0 || columns <= 0) return
        runCatching { PtyNative.setWindowSize(masterFd, rows, columns) }
    }

    /** Blocks the calling thread until the child process exits. */
    fun waitFor(): Int {
        val exitCode = PtyNative.waitFor(pid)
        alive = false
        return exitCode
    }

    fun destroy() {
        alive = false
        runCatching { android.system.Os.kill(pid, android.system.OsConstants.SIGKILL) }
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
    }

    companion object {
        /**
         * Starts [cmd] with [args] in [cwd], attached to a new pty sized
         * [rows]x[columns].
         */
        fun start(
            cmd: String,
            cwd: File,
            args: Array<String> = emptyArray(),
            env: Array<String> = defaultEnv(cwd),
            rows: Int = 24,
            columns: Int = 80,
        ): PtySession {
            cwd.mkdirs()
            val pid = IntArray(1)
            val fullArgs = arrayOf(cmd, *args)
            val masterFd = PtyNative.createSubprocess(
                cmd, cwd.path, fullArgs, env, pid, rows, columns,
            )
            if (masterFd < 0) throw IOException("createSubprocess failed for $cmd")
            return PtySession(masterFd, pid[0])
        }

        private fun defaultEnv(cwd: File): Array<String> = arrayOf(
            "TERM=xterm-256color",
            "HOME=${cwd.path}",
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin",
        )
    }
}
