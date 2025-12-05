package com.github.ananbox

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import kotlin.concurrent.thread

@Suppress("DEPRECATION")
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        private const val ROOTFS_INSTALL_REQUEST_CODE = 100
        
        // Unix permission bit for owner execute (binary 0b001_000_000, octal 0100, decimal 64)
        private const val OWNER_EXECUTE_PERMISSION = 0b001_000_000
        
        // Embedded server process (shared across activities)
        @Volatile
        private var embeddedServerProcess: Process? = null
        
        // Lock for synchronizing server start/stop
        private val serverLock = Any()
        
        // Connection mode constants
        const val MODE_LOCAL_JNI = "local_jni"
        const val MODE_LOCAL_SERVER = "local_server"
        const val MODE_REMOTE_LEGACY = "remote_legacy"
        const val MODE_REMOTE_SCRCPY = "remote_scrcpy"
        
        fun isServerRunning(): Boolean {
            return embeddedServerProcess?.isAlive == true
        }
        
        /**
         * Ensures libanbox.so and libproot.so are linked/copied to internal directory
         * Returns paths to the internal copies
         */
        private fun ensureServerBinaries(context: Context): Pair<String, String>? {
            try {
                val appInfo = context.applicationInfo
                val filesDir = context.filesDir
                val binDir = File(filesDir, "bin")
                binDir.mkdirs()
                
                val srcServerPath = appInfo.nativeLibraryDir + "/libanbox.so"
                val srcProotPath = appInfo.nativeLibraryDir + "/libproot.so"
                val destServerPath = File(binDir, "libanbox.so")
                val destProotPath = File(binDir, "libproot.so")
                
                // Copy/link server binary if needed
                val srcServerFile = File(srcServerPath)
                if (srcServerFile.exists() && (!destServerPath.exists() || destServerPath.length() != srcServerFile.length())) {
                    srcServerFile.copyTo(destServerPath, overwrite = true)
                    destServerPath.setExecutable(true, true)
                    Log.i(TAG, "Copied libanbox.so to ${destServerPath.absolutePath}")
                }
                
                // Copy/link proot binary if needed
                val srcProotFile = File(srcProotPath)
                if (srcProotFile.exists() && (!destProotPath.exists() || destProotPath.length() != srcProotFile.length())) {
                    srcProotFile.copyTo(destProotPath, overwrite = true)
                    destProotPath.setExecutable(true, true)
                    Log.i(TAG, "Copied libproot.so to ${destProotPath.absolutePath}")
                }
                
                return Pair(destServerPath.absolutePath, destProotPath.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ensure server binaries", e)
                return null
            }
        }
        
        fun startEmbeddedServer(context: Context): Boolean {
            synchronized(serverLock) {
                if (isServerRunning()) {
                    Log.i(TAG, "Embedded server already running")
                    return true
                }
                
                try {
                    val filesDir = context.filesDir
                    val basePath = filesDir.absolutePath
                    
                    // Ensure binaries are in internal directory
                    val binaries = ensureServerBinaries(context)
                    if (binaries == null) {
                        Log.e(TAG, "Failed to setup server binaries")
                        return false
                    }
                    val (serverPath, prootPath) = binaries
                    
                    // Use internal tmp directory for proot
                    val prootTmpDir = File(filesDir, "tmp")
                    prootTmpDir.mkdirs()
                    
                    val localServerAddress = getLocalServerAddress(context)
                    val localPort = getLocalServerPort(context)
                    val localAdbAddress = getLocalAdbAddress(context)
                    val localAdbPort = getLocalAdbPort(context)
                    
                    // Ensure the server binary has execute permission
                    val serverFile = File(serverPath)
                    if (serverFile.exists() && !serverFile.canExecute()) {
                        serverFile.setExecutable(true, true)
                    }
                    
                    // Use default display dimensions for background server
                    val defaultWidth = 1280
                    val defaultHeight = 720
                    val defaultDpi = 160
                    
                    val command = mutableListOf(
                        serverPath,
                        "-b", basePath,
                        "-P", prootPath,
                        "-a", localServerAddress,
                        "-p", localPort.toString(),
                        "-w", defaultWidth.toString(),
                        "-h", defaultHeight.toString(),
                        "-d", defaultDpi.toString(),
                        "-A", localAdbAddress,
                        "-D", localAdbPort.toString(),
                        "-t", prootTmpDir.absolutePath
                    )
                    
                    Log.i(TAG, "Starting embedded server: ${command.joinToString(" ")}")
                    
                    val processBuilder = ProcessBuilder(command)
                    processBuilder.environment()["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
                    processBuilder.redirectErrorStream(true)
                    
                    embeddedServerProcess = processBuilder.start()
                    
                    // Start a background thread to read server output with proper error handling
                    thread {
                        try {
                            val serverLogFile = File(filesDir, "server.log")
                            embeddedServerProcess?.inputStream?.bufferedReader()?.use { reader ->
                                serverLogFile.bufferedWriter().use { logWriter ->
                                    reader.forEachLine { line ->
                                        Log.i(TAG, "Server: $line")
                                        logWriter.write(line)
                                        logWriter.newLine()
                                        logWriter.flush()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading/writing server output", e)
                        }
                    }
                    
                    Log.i(TAG, "Embedded server started on $localServerAddress:$localPort")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start embedded server", e)
                    return false
                }
            }
        }
        
        fun stopEmbeddedServer() {
            synchronized(serverLock) {
                embeddedServerProcess?.let { process ->
                    Log.i(TAG, "Stopping embedded server...")
                    process.destroyForcibly()
                    try {
                        // Wait up to 5 seconds for process to terminate
                        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.w(TAG, "Interrupted while waiting for server to stop")
                    }
                    embeddedServerProcess = null
                }
            }
        }
        
        fun isVerboseModeEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.settings_verbose_key), false)
        }
        
        fun getConnectionMode(context: Context): String {
            // Use saved connection mode preference
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_connection_mode_key), MODE_LOCAL_JNI)
                ?: MODE_LOCAL_JNI
        }
        
        fun isLocalMode(context: Context): Boolean {
            val mode = getConnectionMode(context)
            return mode == MODE_LOCAL_JNI || mode == MODE_LOCAL_SERVER
        }
        
        fun isRemoteMode(context: Context): Boolean {
            val mode = getConnectionMode(context)
            return mode == MODE_REMOTE_LEGACY || mode == MODE_REMOTE_SCRCPY
        }
        
        fun isScrcpyMode(context: Context): Boolean {
            return getConnectionMode(context) == MODE_REMOTE_SCRCPY
        }
        
        fun isEmbeddedServerMode(context: Context): Boolean {
            return getConnectionMode(context) == MODE_LOCAL_SERVER
        }
        
        fun getRemoteAddress(context: Context): String {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_streaming_address_key), 
                          context.getString(R.string.settings_remote_streaming_address_default)) 
                ?: context.getString(R.string.settings_remote_streaming_address_default)
            // Parse address from address:port format
            return if (addressPort.contains(":")) {
                addressPort.substringBefore(":")
            } else {
                addressPort
            }
        }
        
        fun getRemotePort(context: Context): Int {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_streaming_address_key),
                          context.getString(R.string.settings_remote_streaming_address_default))
                ?: context.getString(R.string.settings_remote_streaming_address_default)
            // Parse port from address:port format
            return try {
                if (addressPort.contains(":")) {
                    addressPort.substringAfter(":").toInt()
                } else {
                    15558  // Default port
                }
            } catch (e: NumberFormatException) {
                15558
            }
        }
        
        fun getAdbPort(context: Context): Int {
            return getRemoteAdbPort(context)
        }
        
        fun getRemoteAdbAddress(context: Context): String {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_adb_address_key),
                          context.getString(R.string.settings_remote_adb_address_default))
                ?: context.getString(R.string.settings_remote_adb_address_default)
            // Parse address from address:port format
            return if (addressPort.contains(":")) {
                addressPort.substringBefore(":")
            } else {
                addressPort
            }
        }
        
        fun getRemoteAdbPort(context: Context): Int {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_adb_address_key),
                          context.getString(R.string.settings_remote_adb_address_default))
                ?: context.getString(R.string.settings_remote_adb_address_default)
            // Parse port from address:port format
            return try {
                if (addressPort.contains(":")) {
                    addressPort.substringAfter(":").toInt()
                } else {
                    15555  // Default port
                }
            } catch (e: NumberFormatException) {
                15555
            }
        }
        
        fun getLocalServerAddress(context: Context): String {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_local_server_address_key),
                          context.getString(R.string.settings_local_server_address_default))
                ?: context.getString(R.string.settings_local_server_address_default)
            // Parse address from address:port format
            return if (addressPort.contains(":")) {
                addressPort.substringBefore(":")
            } else {
                "127.0.0.1"  // Default local address
            }
        }
        
        fun getLocalServerPort(context: Context): Int {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_local_server_address_key),
                          context.getString(R.string.settings_local_server_address_default))
                ?: context.getString(R.string.settings_local_server_address_default)
            // Parse port from address:port format
            return try {
                if (addressPort.contains(":")) {
                    addressPort.substringAfter(":").toInt()
                } else {
                    15558  // Default port
                }
            } catch (e: NumberFormatException) {
                15558
            }
        }
        
        fun getLocalAdbAddress(context: Context): String {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_local_adb_address_key),
                          context.getString(R.string.settings_local_adb_address_default))
                ?: context.getString(R.string.settings_local_adb_address_default)
            // Parse address from address:port format
            return if (addressPort.isEmpty()) {
                ""  // Disabled
            } else if (addressPort.contains(":")) {
                addressPort.substringBefore(":")
            } else {
                "127.0.0.1"  // Default local address
            }
        }
        
        fun getLocalAdbPort(context: Context): Int {
            val addressPort = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_local_adb_address_key),
                          context.getString(R.string.settings_local_adb_address_default))
                ?: context.getString(R.string.settings_local_adb_address_default)
            // Parse port from address:port format (empty = disabled)
            return try {
                if (addressPort.isEmpty()) {
                    0  // Disabled
                } else if (addressPort.contains(":")) {
                    addressPort.substringAfter(":").toInt()
                } else {
                    15555  // Default port
                }
            } catch (e: NumberFormatException) {
                15555
            }
        }
        
        fun getBaseDir(context: Context): String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_basedir_key),
                          context.getString(R.string.settings_basedir_default))
                ?: context.getString(R.string.settings_basedir_default)
        }
    }

    class SettingsFragment: PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Local mode options
            val localJniOption = preferenceScreen.findPreference<Preference>(getString(R.string.settings_local_jni_key))
            val shutdownJniOption = preferenceScreen.findPreference<Preference>(getString(R.string.settings_shutdown_jni_key))
            val localServerToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_local_server_key))
            
            // Remote mode options (now click actions, not toggles)
            val remoteStreamingOption = preferenceScreen.findPreference<Preference>(getString(R.string.settings_remote_streaming_key))
            val remoteScrcpyOption = preferenceScreen.findPreference<Preference>(getString(R.string.settings_remote_scrcpy_key))
            
            // Local settings
            val localServerAddress = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_local_server_address_key))
            val localAdbAddress = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_local_adb_address_key))
            val basedir = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_basedir_key))
            
            // Remote settings
            val remoteStreamingAddress = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_remote_streaming_address_key))
            val remoteAdbAddress = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_remote_adb_address_key))
            
            // Settings group
            @Suppress("UNUSED_VARIABLE")
            val verboseMode = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_verbose_key))
            val viewLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_logs_key))
            val exportLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_export_logs_key))
            val clearLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_clear_logs_key))
            val about = preferenceScreen.findPreference<Preference>(getString(R.string.settings_about_key))

            // Handle local JNI option - on click, connect immediately
            localJniOption?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                // If local server is running, stop it before switching modes
                if (localServerToggle?.isChecked == true) {
                    stopEmbeddedServer()
                }
                // Disable local server toggle
                localServerToggle?.isChecked = false
                
                // Save connection mode preference
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putString(getString(R.string.settings_connection_mode_key), MODE_LOCAL_JNI)
                    .apply()
                
                // Start container via JNI immediately
                Toast.makeText(activity, getString(R.string.jni_container_starting), Toast.LENGTH_SHORT).show()
                startActivity(Intent(activity, MainActivity::class.java))
                true
            }
            
            // Handle shutdown JNI container option
            shutdownJniOption?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Anbox.stopRuntime()
                Anbox.stopContainer()
                Toast.makeText(activity, getString(R.string.jni_container_stopped), Toast.LENGTH_SHORT).show()
                true
            }
            
            // Handle local server toggle - starts/stops embedded server in background
            localServerToggle?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    val context = requireContext()
                    val baseDir = getBaseDir(context)
                    val rootfsDir = File(baseDir, "rootfs")
                    
                    // Check if rootfs exists, if not prompt to install
                    if (!rootfsDir.exists()) {
                        // Show rom installer dialog
                        AlertDialog.Builder(context)
                            .setTitle(R.string.rom_installer_title)
                            .setMessage(R.string.rom_installer_message)
                            .setPositiveButton(R.string.rom_installer_install) { _, _ ->
                                // Launch file picker for rootfs
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                        "application/gzip",
                                        "application/x-gzip",
                                        "application/x-compressed-tar"
                                    ))
                                }
                                startActivityForResult(intent, ROOTFS_INSTALL_REQUEST_CODE)
                            }
                            .setNegativeButton(R.string.cancel) { _, _ ->
                                // Disable the toggle since rootfs is not available
                                localServerToggle?.isChecked = false
                            }
                            .setCancelable(false)
                            .show()
                        return@OnPreferenceChangeListener false  // Don't toggle yet
                    }
                    
                    // Save connection mode preference
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putString(getString(R.string.settings_connection_mode_key), MODE_LOCAL_SERVER)
                        .apply()
                    
                    // Actually start the embedded server in background
                    Toast.makeText(activity, getString(R.string.embedded_server_starting), Toast.LENGTH_SHORT).show()
                    thread {
                        val success = startEmbeddedServer(context)
                        activity?.runOnUiThread {
                            if (success) {
                                val port = getLocalServerPort(context)
                                Toast.makeText(activity, getString(R.string.embedded_server_started, port), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, getString(R.string.embedded_server_failed, "Failed to start"), Toast.LENGTH_LONG).show()
                                localServerToggle?.isChecked = false
                            }
                        }
                    }
                } else {
                    // Stop server
                    stopEmbeddedServer()
                    Toast.makeText(activity, getString(R.string.embedded_server_stopped), Toast.LENGTH_SHORT).show()
                }
                true
            }
            
            // Handle remote streaming option - click to connect
            remoteStreamingOption?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                // If local server is running, stop it before switching modes
                if (localServerToggle?.isChecked == true) {
                    stopEmbeddedServer()
                }
                // Disable local server toggle
                localServerToggle?.isChecked = false
                
                // Save connection mode preference
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putString(getString(R.string.settings_connection_mode_key), MODE_REMOTE_LEGACY)
                    .apply()
                
                // Start streaming connection
                startActivity(Intent(activity, MainActivity::class.java))
                true
            }
            
            // Handle remote scrcpy option - click to connect
            remoteScrcpyOption?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                // If local server is running, stop it before switching modes
                if (localServerToggle?.isChecked == true) {
                    stopEmbeddedServer()
                }
                // Disable local server toggle
                localServerToggle?.isChecked = false
                
                // Save connection mode preference
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putString(getString(R.string.settings_connection_mode_key), MODE_REMOTE_SCRCPY)
                    .apply()
                
                // Start scrcpy connection
                startActivity(Intent(activity, MainActivity::class.java))
                true
            }

            // View logs
            viewLogs?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(Intent(activity, LogViewActivity::class.java))
                true
            }

            // Export logs
            exportLogs?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                exportLogFiles()
                true
            }
            
            // Clear logs
            clearLogs?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showClearLogsConfirmation()
                true
            }
            
            // About dialog
            about?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showAboutDialog()
                true
            }
            
            // Update local server address summary
            localServerAddress?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_local_server_address_summary)
                } else {
                    pref.text
                }
            }
            
            // Update local ADB address summary
            localAdbAddress?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_local_adb_address_summary)
                } else {
                    pref.text
                }
            }
            
            // Update basedir summary
            basedir?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_basedir_summary)
                } else {
                    pref.text
                }
            }
            
            // Update remote streaming address summary with current value
            remoteStreamingAddress?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_remote_streaming_address_summary)
                } else {
                    pref.text
                }
            }
            
            // Update remote ADB address summary with current value
            remoteAdbAddress?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_remote_adb_address_summary)
                } else {
                    pref.text
                }
            }
        }
        
        private fun showClearLogsConfirmation() {
            val context = activity ?: return
            AlertDialog.Builder(context)
                .setTitle(R.string.settings_clear_logs_confirm_title)
                .setMessage(R.string.settings_clear_logs_confirm_message)
                .setPositiveButton(R.string.ok) { _, _ ->
                    clearLogFiles()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        
        private fun clearLogFiles() {
            val context = activity ?: return
            val filesDir = context.filesDir
            
            try {
                var cleared = false
                
                // Clear proot.log
                val prootLogFile = File(filesDir, "proot.log")
                if (prootLogFile.exists() && prootLogFile.delete()) {
                    cleared = true
                }
                
                // Clear container.log
                val containerLogFile = File(filesDir, "container.log")
                if (containerLogFile.exists() && containerLogFile.delete()) {
                    cleared = true
                }
                
                // Clear server.log
                val serverLogFile = File(filesDir, "server.log")
                if (serverLogFile.exists() && serverLogFile.delete()) {
                    cleared = true
                }
                
                // Clear system.log from rootfs
                val systemLogFile = File(filesDir, "rootfs/data/system.log")
                if (systemLogFile.exists() && systemLogFile.delete()) {
                    cleared = true
                }
                
                if (cleared) {
                    Toast.makeText(context, R.string.settings_clear_logs_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.settings_logs_empty, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logs", e)
                Toast.makeText(context, R.string.settings_clear_logs_failed, Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun showAboutDialog() {
            val context = activity ?: return
            
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                "Unknown"
            }
            
            AlertDialog.Builder(context)
                .setTitle(R.string.about_dialog_title)
                .setMessage(getString(R.string.about_dialog_message, versionName))
                .setPositiveButton(R.string.ok, null)
                .show()
        }

        private fun exportLogFiles() {
            val context = activity ?: return
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir
            
            try {
                // Create a tarball with all diagnostic logs
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val tarGzFile = File(cacheDir, "ananbox_logs_$timestamp.tar.gz")
                
                FileOutputStream(tarGzFile).use { fos ->
                    GZIPOutputStream(fos).use { gzos ->
                        TarArchiveOutputStream(gzos).use { tarOs ->
                            tarOs.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                            
                            // Add proot.log
                            addFileToTar(tarOs, File(filesDir, "proot.log"), "proot.log")
                            
                            // Add system.log from rootfs
                            addFileToTar(tarOs, File(filesDir, "rootfs/data/system.log"), "system.log")
                            
                            // Add container output if available
                            addFileToTar(tarOs, File(filesDir, "container.log"), "container.log")
                            
                            // Add embedded server log if available
                            addFileToTar(tarOs, File(filesDir, "server.log"), "server.log")
                            
                            // Add logcat output
                            val logcatContent = collectLogcat()
                            addStringToTar(tarOs, logcatContent, "logcat.txt")
                            
                            // Add process list
                            val psOutput = collectProcessList()
                            addStringToTar(tarOs, psOutput, "processes.txt")
                            
                            // Add device info
                            val deviceInfo = collectDeviceInfo()
                            addStringToTar(tarOs, deviceInfo, "device_info.txt")
                            
                            // Add dmesg if available (requires root or specific permissions)
                            val dmesgOutput = collectDmesg()
                            if (dmesgOutput.isNotEmpty()) {
                                addStringToTar(tarOs, dmesgOutput, "dmesg.txt")
                            }
                            
                            // Add settings info
                            val verboseEnabled = isVerboseModeEnabled(context)
                            val connectionMode = getConnectionMode(context)
                            val settingsInfo = StringBuilder()
                            settingsInfo.append("Verbose mode: $verboseEnabled\n")
                            settingsInfo.append("Connection mode: $connectionMode\n")
                            settingsInfo.append("Remote address: ${getRemoteAddress(context)}\n")
                            settingsInfo.append("Remote ADB port: ${getRemoteAdbPort(context)}\n")
                            settingsInfo.append("Local server port: ${getLocalServerPort(context)}\n")
                            settingsInfo.append("Local ADB port: ${getLocalAdbPort(context)}\n")
                            settingsInfo.append("Base directory: ${getBaseDir(context)}\n")
                            addStringToTar(tarOs, settingsInfo.toString(), "settings.txt")
                            
                            tarOs.finish()
                        }
                    }
                }
                
                // Share the tarball
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tarGzFile
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gzip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Ananbox Diagnostic Logs - $timestamp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Export Logs"))
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs", e)
                // Fall back to simple text sharing
                exportLogsAsText()
            }
        }
        
        private fun addFileToTar(tarOs: TarArchiveOutputStream, file: File, entryName: String) {
            if (file.exists() && file.isFile) {
                try {
                    val entry = TarArchiveEntry(file, entryName)
                    tarOs.putArchiveEntry(entry)
                    file.inputStream().use { fis ->
                        fis.copyTo(tarOs)
                    }
                    tarOs.closeArchiveEntry()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add file to tar: ${file.path}", e)
                }
            }
        }
        
        private fun addStringToTar(tarOs: TarArchiveOutputStream, content: String, entryName: String) {
            if (content.isNotEmpty()) {
                try {
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    val entry = TarArchiveEntry(entryName)
                    entry.size = bytes.size.toLong()
                    tarOs.putArchiveEntry(entry)
                    tarOs.write(bytes)
                    tarOs.closeArchiveEntry()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add string to tar: $entryName", e)
                }
            }
        }
        
        private fun collectLogcat(): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "*:V"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                output.append("=== Logcat Output ===\n")
                output.append("Timestamp: ${Date()}\n\n")
                reader.useLines { lines ->
                    lines.forEach { line ->
                        output.append(line).append("\n")
                    }
                }
                process.waitFor()
                output.toString()
            } catch (e: Exception) {
                "Failed to collect logcat: ${e.message}\n"
            }
        }
        
        private fun collectProcessList(): String {
            val output = StringBuilder()
            output.append("=== Process List ===\n")
            output.append("Timestamp: ${Date()}\n\n")
            
            // Get all processes visible to this app
            try {
                val process = Runtime.getRuntime().exec(arrayOf("ps", "-A"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                reader.useLines { lines ->
                    lines.forEach { line ->
                        output.append(line).append("\n")
                    }
                }
                process.waitFor()
            } catch (e: Exception) {
                output.append("Failed to get process list: ${e.message}\n")
            }
            
            // Also try to get processes inside the container
            output.append("\n=== Container Processes ===\n")
            try {
                val context = activity ?: return output.toString()
                val rootfsDir = File(context.filesDir, "rootfs")
                if (rootfsDir.exists()) {
                    // Try reading /proc inside rootfs if available
                    val procDir = File(rootfsDir, "proc")
                    if (procDir.exists() && procDir.isDirectory) {
                        procDir.listFiles()?.filter { it.name.matches(Regex("\\d+")) }?.forEach { pidDir ->
                            try {
                                val cmdlineFile = File(pidDir, "cmdline")
                                if (cmdlineFile.exists()) {
                                    val cmdline = cmdlineFile.readText().replace('\u0000', ' ').trim()
                                    if (cmdline.isNotEmpty()) {
                                        output.append("PID ${pidDir.name}: $cmdline\n")
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore individual process read errors
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                output.append("Failed to get container processes: ${e.message}\n")
            }
            
            return output.toString()
        }
        
        private fun collectDeviceInfo(): String {
            val output = StringBuilder()
            output.append("=== Device Information ===\n")
            output.append("Timestamp: ${Date()}\n\n")
            output.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
            output.append("Android Version: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n")
            output.append("Build: ${android.os.Build.DISPLAY}\n")
            output.append("Board: ${android.os.Build.BOARD}\n")
            output.append("Hardware: ${android.os.Build.HARDWARE}\n")
            output.append("CPU ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}\n")
            
            // Memory info
            try {
                val activityManager = activity?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memInfo)
                output.append("\nMemory:\n")
                output.append("  Available: ${memInfo.availMem / (1024 * 1024)} MB\n")
                output.append("  Total: ${memInfo.totalMem / (1024 * 1024)} MB\n")
                output.append("  Low Memory: ${memInfo.lowMemory}\n")
            } catch (e: Exception) {
                output.append("Failed to get memory info: ${e.message}\n")
            }
            
            // Storage info
            try {
                val context = activity ?: return output.toString()
                val filesDir = context.filesDir
                output.append("\nStorage:\n")
                output.append("  Files Dir: ${filesDir.absolutePath}\n")
                output.append("  Free Space: ${filesDir.freeSpace / (1024 * 1024)} MB\n")
                output.append("  Total Space: ${filesDir.totalSpace / (1024 * 1024)} MB\n")
            } catch (e: Exception) {
                output.append("Failed to get storage info: ${e.message}\n")
            }
            
            return output.toString()
        }
        
        private fun collectDmesg(): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("dmesg"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                output.append("=== Kernel Log (dmesg) ===\n")
                output.append("Timestamp: ${Date()}\n\n")
                reader.useLines { lines ->
                    lines.forEach { line ->
                        output.append(line).append("\n")
                    }
                }
                process.waitFor()
                output.toString()
            } catch (e: Exception) {
                // dmesg often requires root, so we silently fail
                ""
            }
        }
        
        private fun exportLogsAsText() {
            val context = activity ?: return
            val filesDir = context.filesDir
            
            val logContent = StringBuilder()
            logContent.append("=== Ananbox Diagnostic Report ===\n")
            logContent.append("Generated: ${Date()}\n\n")
            
            // Add device info
            logContent.append(collectDeviceInfo())
            logContent.append("\n")
            
            // Add process list
            logContent.append(collectProcessList())
            logContent.append("\n")
            
            // Add proot.log
            val prootLogFile = File(filesDir, "proot.log")
            if (prootLogFile.exists()) {
                logContent.append("=== proot.log ===\n")
                try {
                    logContent.append(prootLogFile.readText())
                } catch (e: Exception) {
                    logContent.append("Error reading proot.log: ${e.message}")
                }
                logContent.append("\n\n")
            }
            
            // Add system.log
            val systemLogFile = File(filesDir, "rootfs/data/system.log")
            if (systemLogFile.exists()) {
                logContent.append("=== system.log ===\n")
                try {
                    logContent.append(systemLogFile.readText())
                } catch (e: Exception) {
                    logContent.append("Error reading system.log: ${e.message}")
                }
                logContent.append("\n\n")
            }
            
            // Add server.log
            val serverLogFile = File(filesDir, "server.log")
            if (serverLogFile.exists()) {
                logContent.append("=== server.log ===\n")
                try {
                    logContent.append(serverLogFile.readText())
                } catch (e: Exception) {
                    logContent.append("Error reading server.log: ${e.message}")
                }
                logContent.append("\n\n")
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ananbox Diagnostic Report")
                putExtra(Intent.EXTRA_TEXT, logContent.toString())
            }
            startActivity(Intent.createChooser(shareIntent, "Export Logs"))
        }
        
        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == ROOTFS_INSTALL_REQUEST_CODE) {
                if (resultCode != Activity.RESULT_OK || data == null) {
                    // User cancelled, disable local server toggle
                    val localServerToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_local_server_key))
                    localServerToggle?.isChecked = false
                    return
                }
                val uri = data.data
                if (uri != null) {
                    val context = requireContext()
                    val baseDir = getBaseDir(context)
                    
                    val progressDialog = ProgressDialog(context).apply {
                        setTitle(getString(R.string.rom_installer_extracting_title))
                        setMessage(getString(R.string.rom_installer_extracting_msg))
                        setProgressStyle(ProgressDialog.STYLE_SPINNER)
                        setCanceledOnTouchOutside(false)
                        show()
                    }
                    
                    thread {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val destDir = File(baseDir)
                                destDir.mkdirs()
                                extractTarGz(inputStream, destDir)
                                inputStream.close()
                                ensureRequiredDirectories(destDir)
                                
                                // Now start the embedded server after extraction
                                val success = startEmbeddedServer(context)
                                
                                activity?.runOnUiThread {
                                    progressDialog.dismiss()
                                    // Now enable the toggle and save preference
                                    val localServerToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_local_server_key))
                                    localServerToggle?.isChecked = true
                                    
                                    PreferenceManager.getDefaultSharedPreferences(context)
                                        .edit()
                                        .putString(getString(R.string.settings_connection_mode_key), MODE_LOCAL_SERVER)
                                        .apply()
                                    
                                    if (success) {
                                        val port = getLocalServerPort(context)
                                        Toast.makeText(context, getString(R.string.embedded_server_started, port), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, getString(R.string.embedded_server_failed, "Failed to start"), Toast.LENGTH_LONG).show()
                                        localServerToggle?.isChecked = false
                                    }
                                }
                            } else {
                                activity?.runOnUiThread {
                                    progressDialog.dismiss()
                                    Toast.makeText(context, getString(R.string.rootfs_open_failed), Toast.LENGTH_SHORT).show()
                                    val localServerToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_local_server_key))
                                    localServerToggle?.isChecked = false
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to extract rootfs", e)
                            activity?.runOnUiThread {
                                progressDialog.dismiss()
                                Toast.makeText(context, getString(R.string.rootfs_extract_failed), Toast.LENGTH_LONG).show()
                                val localServerToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_local_server_key))
                                localServerToggle?.isChecked = false
                            }
                        }
                    }
                }
            }
        }
        
        private fun ensureRequiredDirectories(baseDir: File) {
            // Create tmp directory for proot (PROOT_TMP_DIR)
            val tmpDir = File(baseDir, "tmp")
            if (!tmpDir.exists()) {
                tmpDir.mkdirs()
                Log.i(TAG, "Created tmp directory: ${tmpDir.absolutePath}")
            }
            
            // Create mnt/user/0 directory for storage binding
            val mntUserDir = File(baseDir, "rootfs/mnt/user/0")
            if (!mntUserDir.exists()) {
                mntUserDir.mkdirs()
                Log.i(TAG, "Created mnt/user/0 directory: ${mntUserDir.absolutePath}")
            }
        }
        
        private fun extractTarGz(inputStream: java.io.InputStream, destDir: File) {
            // Extract to rootfs subdirectory since tar.gz contains /system directly
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
                                val isAbsolute = linkTarget.startsWith("/")
                                val wouldEscape = if (isAbsolute) {
                                    // Allow absolute symlinks: proot virtualizes the filesystem,
                                    // so absolute symlinks inside the rootfs resolve within the
                                    // proot environment and cannot escape to the host filesystem.
                                    false
                                } else {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setTitle(R.string.title_settings)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> {
                finish()
            }
        }
        return true
    }
}