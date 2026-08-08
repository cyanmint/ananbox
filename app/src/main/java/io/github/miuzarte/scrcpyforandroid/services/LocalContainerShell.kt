package io.github.miuzarte.scrcpyforandroid.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Runs shell commands and file transfers against this app's own internal
 * storage (/data/user/&lt;userId&gt;/com.cyanmint.anbox/files) instead of a
 * remote adb-connected device. Used by the terminal and file manager
 * features, which browse/operate on this app's own container rootfs rather
 * than a paired device.
 */
object LocalContainerShell {
    /** Root directory that all "remote" paths used by the file manager are resolved against. */
    val rootDir: File
        get() = AppRuntime.context.filesDir.apply { mkdirs() }

    /** Resolves a "remote" (container-rooted) path to a local [File]. */
    fun resolve(path: String): File {
        val relative = path.trimStart('/')
        return if (relative.isBlank()) rootDir else File(rootDir, relative)
    }

    suspend fun shell(command: String): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IOException(output.ifBlank { "command failed ($exitCode)" })
        }
        output
    }

    suspend fun ensureConnectionResponsive() {
        // No-op: this is a local shell, always available.
    }

    /** Opens a persistent, interactive local shell process rooted at [rootDir]. */
    fun openShellProcess(): Process {
        return ProcessBuilder("/system/bin/sh")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
    }

    suspend fun push(input: InputStream, remotePath: String) = withContext(Dispatchers.IO) {
        val target = resolve(remotePath)
        target.parentFile?.mkdirs()
        target.outputStream().use { output -> input.copyTo(output) }
        Unit
    }

    suspend fun pull(remotePath: String, output: OutputStream) = withContext(Dispatchers.IO) {
        resolve(remotePath).inputStream().use { input -> input.copyTo(output) }
        Unit
    }

    fun destroy(process: Process) {
        runCatching { process.destroyForcibly() }
    }

    fun waitFor(process: Process, timeoutMs: Long): Boolean {
        return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
