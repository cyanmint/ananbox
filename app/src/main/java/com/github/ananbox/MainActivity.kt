@file:Suppress("DEPRECATION")
package com.github.ananbox

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.ananbox.databinding.ActivityMainBinding
import com.github.ananbox.scrcpy.ScrcpyClient
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
    private var isRemoteMode = false
    private var isEmbeddedServerMode = false
    private var isScrcpyMode = false
    private var remoteAddress = ""
    private var remotePort = 5558
    private var scrcpyPort = 27183
    private var streamingClient: StreamingClient? = null
    private var scrcpyClient: ScrcpyClient? = null
    @Volatile
    private var currentBitmap: Bitmap? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var embeddedServerProcess: Process? = null
    
    private val mSurfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (isRemoteMode) {
                if (isScrcpyMode) {
                    // Scrcpy mode: use scrcpy client for video decoding
                    connectViaScrcpy(holder)
                } else {
                    // Remote mode: connect to streaming server
                    connectToRemoteServer(holder)
                }
            } else if (isEmbeddedServerMode) {
                // Embedded server mode: start local server then connect
                startEmbeddedServerAndConnect(holder)
            } else {
                // JNI mode: start local container directly
                startLocalContainerJni(holder)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.i(
                TAG,
                "surfaceChanged: " + mSurfaceView.width + "x" + mSurfaceView.height
            )
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (isScrcpyMode) {
                scrcpyClient?.disconnect()
            } else if (isRemoteMode || isEmbeddedServerMode) {
                streamingClient?.disconnect()
            } else {
                Anbox.destroySurface()
            }
            Log.i(TAG, "surfaceDestroyed!")
        }
    }
    
    private fun startLocalContainerJni(holder: SurfaceHolder) {
        val surface = holder.surface
        val windowManager = windowManager
        val defaultDisplay = windowManager.defaultDisplay
        val displayMetrics = DisplayMetrics()
        defaultDisplay.getRealMetrics(displayMetrics)
        val dpi = displayMetrics.densityDpi
        Log.i(TAG, "Runtime initializing (JNI mode)..")
        if(Anbox.initRuntime(mSurfaceView.width, mSurfaceView.height, dpi)) {
            Anbox.createSurface(surface)
            // Create required directories before starting runtime
            ensureRequiredDirectories()
            Anbox.startRuntime()
            Anbox.startContainer(applicationContext.applicationInfo.nativeLibraryDir + "/libproot.so")
        }
        else {
            Anbox.createSurface(surface)
        }
    }
    
    private fun startEmbeddedServerAndConnect(holder: SurfaceHolder) {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val dpi = displayMetrics.densityDpi
        
        val localServerAddress = SettingsActivity.getLocalServerAddress(this)
        val localPort = SettingsActivity.getLocalServerPort(this)
        val localAdbAddress = SettingsActivity.getLocalAdbAddress(this)
        val localAdbPort = SettingsActivity.getLocalAdbPort(this)
        
        Log.i(TAG, "Starting embedded server on $localServerAddress:$localPort (ADB: $localAdbAddress:$localAdbPort)")
        
        // Ensure required directories exist
        ensureRequiredDirectories()
        
        // Start the embedded server process
        thread {
            try {
                // Use libanbox.so which is built as a PIE executable that can run standalone
                val serverPath = applicationInfo.nativeLibraryDir + "/libanbox.so"
                val prootPath = applicationInfo.nativeLibraryDir + "/libproot.so"
                val basePath = filesDir.absolutePath
                
                // Ensure the server binary has execute permission
                val serverFile = File(serverPath)
                if (serverFile.exists() && !serverFile.canExecute()) {
                    serverFile.setExecutable(true, true)
                    Log.i(TAG, "Set execute permission on $serverPath")
                }
                
                // Use nativeLibraryDir for PROOT_TMP_DIR since it's mounted with exec permission
                // The app's filesDir is mounted with noexec, preventing proot's loader from being executed
                val prootTmpDir = applicationInfo.nativeLibraryDir
                
                val command = mutableListOf(
                    serverPath,
                    "-b", basePath,
                    "-P", prootPath,
                    "-a", localServerAddress,
                    "-p", localPort.toString(),
                    "-w", mSurfaceView.width.toString(),
                    "-h", mSurfaceView.height.toString(),
                    "-d", dpi.toString(),
                    "-A", localAdbAddress,
                    "-D", localAdbPort.toString(),
                    "-t", prootTmpDir  // Explicitly pass tmp dir with exec permission
                )
                
                Log.i(TAG, "Executing: ${command.joinToString(" ")}")
                
                val processBuilder = ProcessBuilder(command)
                processBuilder.environment()["PROOT_TMP_DIR"] = prootTmpDir
                processBuilder.redirectErrorStream(true)
                
                embeddedServerProcess = processBuilder.start()
                
                mainHandler.post {
                    Toast.makeText(this@MainActivity, 
                        getString(R.string.embedded_server_started, localPort),
                        Toast.LENGTH_SHORT).show()
                }
                
                // Wait a moment for the server to start, then connect
                Thread.sleep(2000)
                
                mainHandler.post {
                    remoteAddress = localServerAddress
                    remotePort = localPort
                    connectToRemoteServer(holder)
                }
                
                // Read server output in background and save to log file
                val serverLogFile = File(filesDir, "server.log")
                val logWriter = serverLogFile.bufferedWriter()
                val reader = embeddedServerProcess?.inputStream?.bufferedReader()
                try {
                    reader?.forEachLine { line ->
                        Log.i(TAG, "Server: $line")
                        logWriter.write(line)
                        logWriter.newLine()
                        logWriter.flush()
                    }
                } finally {
                    logWriter.close()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start embedded server", e)
                mainHandler.post {
                    Toast.makeText(this@MainActivity, 
                        getString(R.string.embedded_server_failed, e.message),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun stopEmbeddedServer() {
        embeddedServerProcess?.let { process ->
            Log.i(TAG, "Stopping embedded server...")
            process.destroy()
            embeddedServerProcess = null
        }
    }
    
    private fun connectViaScrcpy(holder: SurfaceHolder) {
        Log.i(TAG, "Connecting via scrcpy to $remoteAddress:$scrcpyPort")
        
        Toast.makeText(this, 
            getString(R.string.scrcpy_connecting, remoteAddress, scrcpyPort.toString()),
            Toast.LENGTH_SHORT).show()
        
        scrcpyClient = ScrcpyClient(remoteAddress, scrcpyPort)
        
        scrcpyClient?.onConnected = { deviceName, width, height ->
            mainHandler.post {
                Toast.makeText(this@MainActivity,
                    getString(R.string.scrcpy_connected, deviceName, width, height),
                    Toast.LENGTH_SHORT).show()
            }
        }
        
        scrcpyClient?.onDisconnected = { _ ->
            mainHandler.post {
                Toast.makeText(this@MainActivity,
                    getString(R.string.scrcpy_disconnected),
                    Toast.LENGTH_SHORT).show()
            }
        }
        
        scrcpyClient?.onError = { error ->
            mainHandler.post {
                Toast.makeText(this@MainActivity,
                    getString(R.string.scrcpy_error, error),
                    Toast.LENGTH_LONG).show()
            }
        }
        
        scrcpyClient?.connect(holder.surface)
    }
    
    private val scrcpyTouchListener = View.OnTouchListener { _, event ->
        val client = scrcpyClient ?: return@OnTouchListener true
        
        val maskedAction = event.action and MotionEvent.ACTION_MASK
        val action = when (maskedAction) {
            MotionEvent.ACTION_DOWN -> 0  // AMOTION_EVENT_ACTION_DOWN
            MotionEvent.ACTION_UP -> 1    // AMOTION_EVENT_ACTION_UP
            MotionEvent.ACTION_MOVE -> 2  // AMOTION_EVENT_ACTION_MOVE
            MotionEvent.ACTION_POINTER_DOWN -> 5  // AMOTION_EVENT_ACTION_POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> 6    // AMOTION_EVENT_ACTION_POINTER_UP
            else -> return@OnTouchListener true
        }
        
        // Handle multi-touch
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            val x = event.getX(i).toInt()
            val y = event.getY(i).toInt()
            
            if (maskedAction == MotionEvent.ACTION_MOVE) {
                client.sendTouchEvent(x, y, 2, pointerId)
            } else if (i == event.actionIndex) {
                client.sendTouchEvent(x, y, action, pointerId)
            }
        }
        
        true
    }
    
    private fun connectToRemoteServer(holder: SurfaceHolder) {
        Log.i(TAG, "Connecting to remote server: $remoteAddress:$remotePort")
        
        
        val displayMetrics = DisplayMetrics()
        
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        
        val dpi = displayMetrics.densityDpi
        
        streamingClient = StreamingClient()
        streamingClient?.setListener(object : StreamingClient.Listener {
            override fun onConnected(width: Int, height: Int, dpi: Int) {
                mainHandler.post {
                    Toast.makeText(this@MainActivity, 
                        getString(R.string.remote_connected), 
                        Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onDisconnected(reason: String?) {
                mainHandler.post {
                    Toast.makeText(this@MainActivity, 
                        getString(R.string.remote_disconnected), 
                        Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onFrame(bitmap: Bitmap) {
                currentBitmap = bitmap
                mainHandler.post {
                    drawBitmapToSurface(holder, bitmap)
                }
            }
            
            override fun onAudioData(data: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int) {
                // TODO: Play audio data
            }
            
            override fun onDisplayConfigChanged(width: Int, height: Int, dpi: Int) {
                Log.i(TAG, "Display config changed: ${width}x${height}@${dpi}dpi")
            }
            
            override fun onError(error: String) {
                mainHandler.post {
                    Toast.makeText(this@MainActivity, 
                        getString(R.string.remote_connection_failed, error), 
                        Toast.LENGTH_LONG).show()
                }
            }
        })
        
        thread {
            val connected = streamingClient?.connect(
                remoteAddress, 
                remotePort, 
                mSurfaceView.width, 
                mSurfaceView.height, 
                dpi
            ) ?: false
            
            if (!connected) {
                mainHandler.post {
                    Toast.makeText(this@MainActivity, 
                        getString(R.string.remote_connection_failed, "Connection failed"), 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun drawBitmapToSurface(holder: SurfaceHolder, bitmap: Bitmap) {
        try {
            val canvas: Canvas? = holder.lockCanvas()
            canvas?.let {
                it.drawBitmap(bitmap, 0f, 0f, null)
                holder.unlockCanvasAndPost(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to draw frame: ${e.message}")
        }
    }
    
    private val remoteTouchListener = View.OnTouchListener { _, event ->
        val client = streamingClient ?: return@OnTouchListener true
        
        val maskedAction = event.action and MotionEvent.ACTION_MASK
        val action = when (maskedAction) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_UP -> 1
            MotionEvent.ACTION_MOVE -> 2
            MotionEvent.ACTION_POINTER_DOWN -> 0
            MotionEvent.ACTION_POINTER_UP -> 1
            else -> return@OnTouchListener true
        }
        
        // Handle multi-touch
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            val x = event.getX(i).toInt()
            val y = event.getY(i).toInt()
            
            if (maskedAction == MotionEvent.ACTION_MOVE) {
                client.sendTouchEvent(x, y, pointerId, 2)
            } else if (i == event.actionIndex) {
                client.sendTouchEvent(x, y, pointerId, action)
            }
        }
        
        true
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
        
        // Check connection mode from settings
        val connectionMode = SettingsActivity.getConnectionMode(this)
        Log.i(TAG, "Connection mode: $connectionMode")
        
        when (connectionMode) {
            SettingsActivity.MODE_LOCAL_JNI -> {
                isRemoteMode = false
                isEmbeddedServerMode = false
                isScrcpyMode = false
                Log.i(TAG, "Local JNI mode")
            }
            SettingsActivity.MODE_LOCAL_SERVER -> {
                isRemoteMode = false
                isEmbeddedServerMode = true
                isScrcpyMode = false
                Log.i(TAG, "Local embedded server mode")
            }
            SettingsActivity.MODE_REMOTE_LEGACY -> {
                isRemoteMode = true
                isEmbeddedServerMode = false
                isScrcpyMode = false
                remoteAddress = SettingsActivity.getRemoteAddress(this)
                remotePort = SettingsActivity.getRemotePort(this)
                Log.i(TAG, "Remote legacy mode: $remoteAddress:$remotePort")
            }
            SettingsActivity.MODE_REMOTE_SCRCPY -> {
                isRemoteMode = true
                isEmbeddedServerMode = false
                isScrcpyMode = true
                remoteAddress = SettingsActivity.getRemoteAddress(this)
                scrcpyPort = SettingsActivity.getAdbPort(this)
                Log.i(TAG, "Remote scrcpy mode: $remoteAddress:$scrcpyPort")
            }
        }
        
        // For local mode, check if rootfs exists
        if (!isRemoteMode && !File(filesDir, "rootfs").exists()) {
            AlertDialog.Builder(this)
                .apply {
                    setTitle(getString(R.string.rom_installer_title))
                    setMessage(getString(R.string.rom_installer_message))
                    setPositiveButton(R.string.rom_installer_install) { _, _ ->
                        
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
                    setNegativeButton(R.string.cancel) { _, _ ->
                        finishAffinity()
                        exitProcess(0)
                    }
                    setCancelable(false)
                    show()
                }
            return
        }

        if (!isRemoteMode && !isEmbeddedServerMode) {
            Anbox.setPath(filesDir.path)
        }

        mSurfaceView = SurfaceView(this)
        mSurfaceView.getHolder().addCallback(mSurfaceCallback)
        binding.root.addView(mSurfaceView, 0)

        // Set touch listener based on mode
        if (isScrcpyMode) {
            mSurfaceView.setOnTouchListener(scrcpyTouchListener)
        } else if (isRemoteMode || isEmbeddedServerMode) {
            mSurfaceView.setOnTouchListener(remoteTouchListener)
        } else {
            mSurfaceView.setOnTouchListener(Anbox)
        }
        
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
        if (isRemoteMode || isEmbeddedServerMode) {
            streamingClient?.disconnect()
            stopEmbeddedServer()
        } else {
            Anbox.stopRuntime()
        }
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
            if (!tmpDir.mkdirs()) {
                Log.e(TAG, "Failed to create tmp directory: ${tmpDir.absolutePath}")
                throw RuntimeException("Failed to create required tmp directory")
            }
            Log.i(TAG, "Created tmp directory: ${tmpDir.absolutePath}")
        }
        
        // Create mnt/user/0 directory for storage binding
        val mntUserDir = File(filesDir, "rootfs/mnt/user/0")
        if (!mntUserDir.exists()) {
            if (!mntUserDir.mkdirs()) {
                Log.e(TAG, "Failed to create mnt/user/0 directory: ${mntUserDir.absolutePath}")
                throw RuntimeException("Failed to create required mnt/user/0 directory")
            }
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
                                var currentPath: File? = destFile.parentFile
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
                                    // Delete existing file/symlink if it exists using Files.deleteIfExists for reliability
                                    java.nio.file.Files.deleteIfExists(destFile.toPath())
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