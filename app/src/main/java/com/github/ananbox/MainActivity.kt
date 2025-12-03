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
                                    setType("application/gzip")
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
                    val tmpDir = File(filesDir, "tmp")
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        extractTarGz(inputStream, filesDir)
                        inputStream.close()
                        progressDialog.dismiss()
                        tmpDir.mkdir()
                        runOnUiThread() { recreate() }
                    }
                }
            }
        }
    }

    private companion object {
        // Unix permission bit for owner execute (octal 0100)
        private const val OWNER_EXECUTE_PERMISSION = 0b001_000_000
    }

    private fun extractTarGz(inputStream: java.io.InputStream, destDir: File) {
        // Extract to rootfs subdirectory since tar.gz doesn't contain the rootfs folder
        val rootfsDir = File(destDir, "rootfs")
        rootfsDir.mkdirs()

        GZIPInputStream(BufferedInputStream(inputStream)).use { gzipInputStream ->
            TarArchiveInputStream(gzipInputStream).use { tarInputStream ->
                var entry = tarInputStream.nextTarEntry
                while (entry != null) {
                    val destFile = File(rootfsDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        if (entry.isSymbolicLink) {
                            // Handle symbolic links
                            val linkTarget = entry.linkName
                            try {
                                java.nio.file.Files.createSymbolicLink(
                                    destFile.toPath(),
                                    java.nio.file.Paths.get(linkTarget)
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to create symlink: ${destFile.path} -> $linkTarget")
                            }
                        } else {
                            FileOutputStream(destFile).use { fos ->
                                tarInputStream.copyTo(fos)
                            }
                            // Preserve file permissions
                            val mode = entry.mode
                            if (mode and OWNER_EXECUTE_PERMISSION != 0) {
                                destFile.setExecutable(true, false)
                            }
                        }
                    }
                    entry = tarInputStream.nextTarEntry
                }
            }
        }
    }
}