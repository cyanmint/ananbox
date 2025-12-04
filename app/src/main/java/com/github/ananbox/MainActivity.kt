package com.github.ananbox

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.ananbox.databinding.ActivityMainBinding
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import kotlin.concurrent.thread
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val READ_REQUEST_CODE = 2
    private lateinit var mSurfaceView: SurfaceView
    private val mSurfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            val surface = holder.surface
            val windowManager = windowManager
            val defaultDisplay = windowManager.defaultDisplay
            val displayMetrics = DisplayMetrics()
            defaultDisplay.getRealMetrics(displayMetrics)
            val dpi = displayMetrics.densityDpi
            Log.i(TAG, "Runtime initializing..")
            if(Anbox.initRuntime(mSurfaceView.width, mSurfaceView.height, dpi)) {
                Anbox.createSurface(surface)
                Anbox.startRuntime()
                // Create required directories before starting container
                ensureRequiredDirectories()
                Anbox.startContainer(applicationContext.applicationInfo.nativeLibraryDir + "/libproot.so")
            }
            else {
                Anbox.createSurface(surface)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.i(
                TAG,
                "surfaceChanged: " + mSurfaceView.width + "x" + mSurfaceView.height
            )
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
//            Renderer.removeWindow(holder.surface)
            Anbox.destroySurface()
            Log.i(TAG, "surfaceDestroyed!")
        }
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!File(filesDir, "rootfs").exists()) {
            AlertDialog.Builder(this)
                .apply {
                    setTitle(getString(R.string.rom_installer_title))
                    setMessage(getString(R.string.rom_installer_message))
                    setPositiveButton(R.string.rom_installer_install) { dialogInterface: DialogInterface, i: Int ->
                        startActivityForResult(
                            Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    setType("*/*")
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                        "application/gzip",
                                        "application/x-gzip",
                                        "application/x-compressed-tar"
                                    ))
                                },
                            READ_REQUEST_CODE
                        )
                    }
                    setNegativeButton(R.string.cancel) { dialogInterface: DialogInterface, i: Int ->
                        finishAffinity()
                        exitProcess(0)
                    }
                    setCancelable(false)
                    show()
                }
            return
        }

        Anbox.setPath(filesDir.path)

        mSurfaceView = SurfaceView(this)
        mSurfaceView.getHolder().addCallback(mSurfaceCallback)
        binding.root.addView(mSurfaceView, 0)

        // put in onResume?
        mSurfaceView.setOnTouchListener(Anbox)
        binding.fab.setOnClickListener {
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1);
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Anbox.stopRuntime()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == READ_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK || data == null) {
                finishAffinity()
                return
            }
            val uri = data.data
            if (uri != null) {
                val progressDialog = ProgressDialog(this).apply {
                    setTitle(getString(R.string.rom_installer_extracting_title))
                    setMessage(getString(R.string.rom_installer_extracting_msg))
                    setProgressStyle(ProgressDialog.STYLE_SPINNER)
                    setCanceledOnTouchOutside(false)
                    show()
                }
                thread {
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        extractTarGz(inputStream, filesDir)
                        inputStream.close()
                        // Create required directories after extraction
                        ensureRequiredDirectories()
                        progressDialog.dismiss()
                        runOnUiThread() { recreate() }
                    }
                }
            }
        }
    }

    private fun ensureRequiredDirectories() {
        // Create tmp directory for proot (PROOT_TMP_DIR)
        val tmpDir = File(filesDir, "tmp")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
            Log.i(TAG, "Created tmp directory: ${tmpDir.absolutePath}")
        }
        
        // Create mnt/user/0 directory for storage binding
        val mntUserDir = File(filesDir, "rootfs/mnt/user/0")
        if (!mntUserDir.exists()) {
            mntUserDir.mkdirs()
            Log.i(TAG, "Created mnt/user/0 directory: ${mntUserDir.absolutePath}")
        }
    }

    private companion object {
        // Unix permission bit for owner execute (octal 0100)
        private const val OWNER_EXECUTE_PERMISSION = 0b001_000_000
    }

    private fun extractTarGz(inputStream: java.io.InputStream, destDir: File) {
        // Extract to rootfs subdirectory since tar.gz contains /system directly
        // (not /rootfs/system like the old 7z format)
        val rootfsDir = File(destDir, "rootfs")
        rootfsDir.mkdirs()
        val rootfsCanonicalPath = rootfsDir.canonicalPath

        GZIPInputStream(BufferedInputStream(inputStream)).use { gzipInputStream ->
            TarArchiveInputStream(gzipInputStream).use { tarInputStream ->
                var entry = tarInputStream.nextTarEntry
                while (entry != null) {
                    val destFile = File(rootfsDir, entry.name)
                    
                    // Validate path to prevent path traversal attacks
                    if (!destFile.canonicalPath.startsWith(rootfsCanonicalPath)) {
                        Log.w(TAG, "Skipping entry outside destination: ${entry.name}")
                        entry = tarInputStream.nextTarEntry
                        continue
                    }
                    
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        if (entry.isSymbolicLink) {
                            // Handle symbolic links with validation
                            val linkTarget = entry.linkName
                            
                            // Validate symlink target to prevent escape attacks
                            // We check if the target would escape the rootfs when resolved
                            val isAbsolute = linkTarget.startsWith("/")
                            val wouldEscape = if (isAbsolute) {
                                // Absolute symlinks should stay within rootfs 
                                // (they will be resolved relative to the proot environment anyway)
                                false  // Allow absolute symlinks as proot handles them
                            } else {
                                // For relative symlinks, check if resolving them escapes rootfs
                                var currentPath = destFile.parentFile
                                for (component in linkTarget.split("/")) {
                                    when (component) {
                                        ".." -> currentPath = currentPath?.parentFile
                                        ".", "" -> { /* ignore */ }
                                        else -> currentPath = currentPath?.let { File(it, component) }
                                    }
                                }
                                currentPath?.let { 
                                    !it.absolutePath.startsWith(rootfsCanonicalPath) 
                                } ?: true
                            }
                            
                            if (wouldEscape) {
                                Log.w(TAG, "Skipping unsafe symlink: ${destFile.path} -> $linkTarget")
                            } else {
                                try {
                                    // Delete existing file/symlink if it exists
                                    if (destFile.exists() || java.nio.file.Files.isSymbolicLink(destFile.toPath())) {
                                        destFile.delete()
                                    }
                                    java.nio.file.Files.createSymbolicLink(
                                        destFile.toPath(),
                                        java.nio.file.Paths.get(linkTarget)
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to create symlink: ${destFile.path} -> $linkTarget: ${e.message}")
                                }
                            }
                        } else {
                            FileOutputStream(destFile).use { fos ->
                                tarInputStream.copyTo(fos)
                            }
                            // Preserve file permissions (owner execute only)
                            val mode = entry.mode
                            if (mode and OWNER_EXECUTE_PERMISSION != 0) {
                                destFile.setExecutable(true, true)
                            }
                        }
                    }
                    entry = tarInputStream.nextTarEntry
                }
            }
        }
    }
}