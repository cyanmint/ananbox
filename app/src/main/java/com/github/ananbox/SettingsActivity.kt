package com.github.ananbox

import android.app.AlertDialog
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
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        
        // Connection mode constants
        const val MODE_LOCAL_JNI = "local_jni"
        const val MODE_LOCAL_SERVER = "local_server"
        const val MODE_REMOTE_LEGACY = "remote_legacy"
        const val MODE_REMOTE_SCRCPY = "remote_scrcpy"
        
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
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_address_key), 
                          context.getString(R.string.settings_remote_address_default)) 
                ?: context.getString(R.string.settings_remote_address_default)
        }
        
        fun getRemotePort(context: Context): Int {
            val portStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_server_port_key),
                          context.getString(R.string.settings_remote_server_port_default))
                ?: context.getString(R.string.settings_remote_server_port_default)
            return try {
                portStr.toInt()
            } catch (e: NumberFormatException) {
                5558
            }
        }
        
        fun getAdbPort(context: Context): Int {
            return getRemoteAdbPort(context)
        }
        
        fun getRemoteAdbPort(context: Context): Int {
            val portStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_adb_port_key),
                          context.getString(R.string.settings_remote_adb_port_default))
                ?: context.getString(R.string.settings_remote_adb_port_default)
            return try {
                portStr.toInt()
            } catch (e: NumberFormatException) {
                5555
            }
        }
        
        fun getLocalServerPort(context: Context): Int {
            val portStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_local_port_key),
                          context.getString(R.string.settings_local_port_default))
                ?: context.getString(R.string.settings_local_port_default)
            return try {
                portStr.toInt()
            } catch (e: NumberFormatException) {
                5558
            }
        }
        
        fun getLocalAdbPort(context: Context): Int {
            val portStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_local_adb_port_key),
                          context.getString(R.string.settings_local_adb_port_default))
                ?: context.getString(R.string.settings_local_adb_port_default)
            return try {
                portStr.toInt()
            } catch (e: NumberFormatException) {
                5555
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
            val localServerToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_local_server_key))
            
            // Remote mode toggles
            val remoteStreamingToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_remote_streaming_key))
            val remoteScrcpyToggle = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_remote_scrcpy_key))
            
            // Local settings
            val localPort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_local_port_key))
            val localAdbPort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_local_adb_port_key))
            val basedir = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_basedir_key))
            
            // Remote settings
            val remoteAddress = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_remote_address_key))
            val remoteServerPort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_remote_server_port_key))
            val remoteAdbPort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_remote_adb_port_key))
            
            // Settings group
            @Suppress("UNUSED_VARIABLE")
            val verboseMode = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_verbose_key))
            val viewLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_logs_key))
            val exportLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_export_logs_key))
            val clearLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_clear_logs_key))
            val about = preferenceScreen.findPreference<Preference>(getString(R.string.settings_about_key))

            // Handle local JNI option - on click, connect immediately
            localJniOption?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                // Disable other toggles
                localServerToggle?.isChecked = false
                remoteStreamingToggle?.isChecked = false
                remoteScrcpyToggle?.isChecked = false
                
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
            
            // Handle local server toggle - runs in background
            localServerToggle?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    // Disable other toggles
                    remoteStreamingToggle?.isChecked = false
                    remoteScrcpyToggle?.isChecked = false
                    
                    // Save connection mode preference
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putString(getString(R.string.settings_connection_mode_key), MODE_LOCAL_SERVER)
                        .apply()
                    
                    // Start local server in background
                    Toast.makeText(activity, getString(R.string.embedded_server_starting), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(activity, MainActivity::class.java))
                } else {
                    // Stop server
                    Anbox.stopRuntime()
                    Anbox.stopContainer()
                    Toast.makeText(activity, getString(R.string.embedded_server_stopped), Toast.LENGTH_SHORT).show()
                }
                true
            }
            
            // Handle remote streaming toggle
            remoteStreamingToggle?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    // Disable other toggles
                    localServerToggle?.isChecked = false
                    remoteScrcpyToggle?.isChecked = false
                    
                    // Save connection mode preference
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putString(getString(R.string.settings_connection_mode_key), MODE_REMOTE_LEGACY)
                        .apply()
                    
                    // Start streaming connection
                    startActivity(Intent(activity, MainActivity::class.java))
                } else {
                    Toast.makeText(activity, getString(R.string.remote_disconnected), Toast.LENGTH_SHORT).show()
                }
                true
            }
            
            // Handle remote scrcpy toggle
            remoteScrcpyToggle?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    // Disable other toggles
                    localServerToggle?.isChecked = false
                    remoteStreamingToggle?.isChecked = false
                    
                    // Save connection mode preference
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putString(getString(R.string.settings_connection_mode_key), MODE_REMOTE_SCRCPY)
                        .apply()
                    
                    // Start scrcpy connection
                    startActivity(Intent(activity, MainActivity::class.java))
                } else {
                    Toast.makeText(activity, getString(R.string.scrcpy_disconnected), Toast.LENGTH_SHORT).show()
                }
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
            
            // Update local server port summary
            localPort?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_local_port_summary)
                } else {
                    "Port: ${pref.text}"
                }
            }
            
            // Update local ADB port summary
            localAdbPort?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_local_adb_port_summary)
                } else {
                    if (pref.text == "0") "Disabled" else "Port: ${pref.text}"
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
            
            // Update remote address summary with current value
            remoteAddress?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_remote_address_summary)
                } else {
                    pref.text
                }
            }
            
            // Update remote server port summary with current value
            remoteServerPort?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_remote_server_port_summary)
                } else {
                    "Port: ${pref.text}"
                }
            }
            
            // Update remote ADB port summary with current value
            remoteAdbPort?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_remote_adb_port_summary)
                } else {
                    "Port: ${pref.text}"
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