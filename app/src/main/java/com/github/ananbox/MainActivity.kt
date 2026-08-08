package com.github.ananbox

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.system.Os
import android.text.InputType
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.ananbox.databinding.ActivityMainBinding
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val READ_REQUEST_CODE = 2
    private val PREFS_NAME = "ananbox"
    private val PREF_PROOT_COMMAND = "proot_command"
    private lateinit var mSurfaceView: SurfaceView
    private lateinit var mProotCommand: String
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
                Anbox.startContainer(mProotCommand)
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
    private val receiver = BinderReceiver()
    private val handlerThread = HandlerThread("BinderReceiverThread")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlerThread.start()
        ContextCompat.registerReceiver(this ,receiver,
            IntentFilter("com.github.ananbox.BINDER"),
            null, Handler(handlerThread.looper),
            ContextCompat.RECEIVER_EXPORTED)

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
                                    setType("application/x-tar")
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

        promptProotCommand()
    }

    private fun defaultProotCommand(): String {
        val proot = applicationContext.applicationInfo.nativeLibraryDir + "/libproot.so"
        val rootfs = File(filesDir, "rootfs").path
        return "$proot --link2symlink -0 -r $rootfs -b /dev -b /proc -b /sys -w / /system/bin/sh"
    }

    private fun promptProotCommand() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(prefs.getString(PREF_PROOT_COMMAND, defaultProotCommand()))
        }
        AlertDialog.Builder(this)
            .apply {
                setTitle(getString(R.string.proot_command_title))
                setMessage(getString(R.string.proot_command_message))
                setView(input)
                setPositiveButton(R.string.proot_command_start) { dialogInterface: DialogInterface, i: Int ->
                    val cmd = input.text.toString()
                    prefs.edit().putString(PREF_PROOT_COMMAND, cmd).apply()
                    mProotCommand = cmd
                    startContainerUi()
                }
                setNegativeButton(R.string.cancel) { dialogInterface: DialogInterface, i: Int ->
                    finishAffinity()
                    exitProcess(0)
                }
                setCancelable(false)
                show()
            }
    }

    private fun startContainerUi() {
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
        unregisterReceiver(receiver)
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
                    val romFile = File(filesDir, "rootfs.tar")
                    val rootfsDir = File(filesDir, "rootfs")
                    val tmpDir = File(filesDir, "tmp")
                    val inputStream = contentResolver.openInputStream(uri)
                    val outputStream = romFile.outputStream()
                    if (inputStream != null) {
                        inputStream.copyTo(outputStream)
                        outputStream.close()
                        inputStream.close()
                        extractRootfs(romFile, rootfsDir)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ParcelConstructor.getBroadcastIntent("local")
                            ParcelConstructor.getBroadcastIntent("binder")
                        }
                        val codeFile = File(rootfsDir, "trans_code")
                        codeFile.writeText(Anbox.getBroadcastIntentTransactionCode().toString())

                        progressDialog.dismiss()
                        romFile.delete()
                        tmpDir.mkdir()
                        runOnUiThread() { recreate() }
                    }
                }
            }
        }
    }

    private fun extractRootfs(tarFile: File, destDir: File) {
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