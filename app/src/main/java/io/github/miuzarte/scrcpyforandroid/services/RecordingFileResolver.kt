package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import java.io.File
import java.time.LocalDateTime

object RecordingFileResolver {
    fun resolve(
        options: ClientOptions,
        sessionInfo: Scrcpy.Session.SessionInfo,
        now: LocalDateTime = LocalDateTime.now(),
    ): File {
        val resolvedName = RecordFilenameTemplate.resolve(options.recordFilename, sessionInfo, now)
        val fileName = ensureExtension(
            sanitizeFileName(resolvedName).ifBlank {
                throw IllegalArgumentException("Recording filename resolved to blank")
            },
            options.recordFormat,
        )
        val directory = PublicDirs.recordDirectory()
        ensureDirectory(directory)
        return uniqueFile(directory, fileName)
    }

    private fun ensureExtension(
        fileName: String,
        format: ClientOptions.RecordFormat,
    ): String {
        if (fileName.contains('.')) return fileName
        val extension = when (format) {
            ClientOptions.RecordFormat.M4A -> "m4a"
            ClientOptions.RecordFormat.AAC -> "aac"
            ClientOptions.RecordFormat.WAV -> "wav"
            else -> "mp4"
        }
        return "$fileName.$extension"
    }

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "").trim()

    private fun ensureDirectory(directory: File) {
        if (directory.exists()) return
        if (!directory.mkdirs()) {
            throw IllegalStateException("Unable to create directory: ${directory.absolutePath}")
        }
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var index = 0
        var candidate = File(directory, fileName)
        while (candidate.exists()) {
            index += 1
            val suffix = " ($index)"
            candidate = if (extension.isBlank()) {
                File(directory, baseName + suffix)
            } else {
                File(directory, "$baseName$suffix.$extension")
            }
        }
        return candidate
    }
}
