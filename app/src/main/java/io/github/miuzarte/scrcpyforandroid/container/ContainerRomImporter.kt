package io.github.miuzarte.scrcpyforandroid.container

import android.system.Os
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream

/** Extracts a rootfs tarball into a destination directory. Used for manual rootfs import. */
object ContainerRomImporter {
    private const val TAG = "ContainerRomImporter"

    fun extractRootfs(tarFile: File, destDir: File) {
        destDir.mkdirs()
        val destCanonicalPath = destDir.canonicalPath
        TarArchiveInputStream(tarFile.inputStream()).use { tarIn ->
            var entry: TarArchiveEntry? = tarIn.nextTarEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                val outCanonicalPath = outFile.canonicalPath
                if (outCanonicalPath != destCanonicalPath &&
                    !outCanonicalPath.startsWith(destCanonicalPath + File.separator)) {
                    Log.w(TAG, "Skipping tar entry outside of destination: ${entry.name}")
                    entry = tarIn.nextTarEntry
                    continue
                }
                when {
                    entry.isDirectory -> outFile.mkdirs()
                    entry.isSymbolicLink -> {
                        outFile.parentFile?.mkdirs()
                        outFile.delete()
                        try {
                            Os.symlink(entry.linkName, outFile.path)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to create symlink ${outFile.path} -> ${entry.linkName}", e)
                        }
                    }
                    else -> {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> tarIn.copyTo(out) }
                        try {
                            Os.chmod(outFile.path, entry.mode)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to chmod ${outFile.path}", e)
                        }
                    }
                }
                entry = tarIn.nextTarEntry
            }
        }
    }
}
