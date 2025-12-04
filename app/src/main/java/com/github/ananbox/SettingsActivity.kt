package com.github.ananbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
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
        
        const val CONTAINER_MODE_JNI = "jni"
        const val CONTAINER_MODE_SERVER = "server"
        
        fun isVerboseModeEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.settings_verbose_key), false)
        }
        
        fun isRemoteModeEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.settings_remote_enabled_key), false)
        }
        
        fun getRemoteAddress(context: Context): String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_address_key), 
                          context.getString(R.string.settings_remote_address_default)) 
                ?: context.getString(R.string.settings_remote_address_default)
        }
        
        fun getRemotePort(context: Context): Int {
            val portStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_remote_port_key),
                          context.getString(R.string.settings_remote_port_default))
                ?: context.getString(R.string.settings_remote_port_default)
            return try {
                portStr.toInt()
            } catch (e: NumberFormatException) {
                5558
            }
        }
        
        fun isAdbModeEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.settings_adb_enabled_key), false)
        }
        
        fun getAdbPort(context: Context): Int {
            val portStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_adb_port_key),
                          context.getString(R.string.settings_adb_port_default))
                ?: context.getString(R.string.settings_adb_port_default)
            return try {
                portStr.toInt()
            } catch (e: NumberFormatException) {
                5555
            }
        }
        
        fun getContainerMode(context: Context): String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_container_mode_key),
                          CONTAINER_MODE_JNI)
                ?: CONTAINER_MODE_JNI
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
    }

    class SettingsFragment: PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val start = preferenceScreen.findPreference<Preference>(getString(R.string.settings_start_key))
            val shutdown = preferenceScreen.findPreference<Preference>(getString(R.string.settings_shutdown_key))
            val viewLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_logs_key))
            val exportLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_export_logs_key))
            val verboseMode = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_verbose_key))
            
            // Local container settings
            val containerMode = preferenceScreen.findPreference<ListPreference>(getString(R.string.settings_container_mode_key))
            val localPort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_local_port_key))
            val localAdbPort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_local_adb_port_key))
            
            // Remote server settings
            val remoteEnabled = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_remote_enabled_key))
            val remoteAddress = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_remote_address_key))
            val remotePort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_remote_port_key))
            val remoteConnect = preferenceScreen.findPreference<Preference>(getString(R.string.settings_remote_connect_key))
            
            // ADB settings
            val adbEnabled = preferenceScreen.findPreference<SwitchPreferenceCompat>(getString(R.string.settings_adb_enabled_key))
            val adbPort = preferenceScreen.findPreference<EditTextPreference>(getString(R.string.settings_adb_port_key))
            val adbConnect = preferenceScreen.findPreference<Preference>(getString(R.string.settings_adb_connect_key))

            start?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(Intent(activity, MainActivity::class.java))
                true
            }

            shutdown?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                activity?.finishAffinity()
                Anbox.stopRuntime()
                Anbox.stopContainer()
                true
            }

            viewLogs?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(Intent(activity, LogViewActivity::class.java))
                true
            }

            exportLogs?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                exportLogFiles()
                true
            }
            
            // Update container mode summary with current value
            containerMode?.summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                pref.entry ?: getString(R.string.settings_container_mode_summary)
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
            
            // Update remote address summary with current value
            remoteAddress?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_remote_address_summary)
                } else {
                    pref.text
                }
            }
            
            // Update remote port summary with current value
            remotePort?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_remote_port_summary)
                } else {
                    "Port: ${pref.text}"
                }
            }
            
            // Update ADB port summary with current value
            adbPort?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (pref.text.isNullOrEmpty()) {
                    getString(R.string.settings_adb_port_summary)
                } else {
                    "Port: ${pref.text}"
                }
            }
            
            // Handle connect button
            remoteConnect?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val context = activity ?: return@OnPreferenceClickListener true
                val address = getRemoteAddress(context)
                val port = getRemotePort(context)
                
                Toast.makeText(context, 
                    getString(R.string.remote_connecting, address, port.toString()),
                    Toast.LENGTH_SHORT).show()
                
                // Start MainActivity which will connect to remote server
                val intent = Intent(activity, MainActivity::class.java).apply {
                    putExtra("remote_mode", true)
                    putExtra("remote_address", address)
                    putExtra("remote_port", port)
                }
                startActivity(intent)
                true
            }
            
            // Handle ADB connect button - shows connection instructions
            adbConnect?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val context = activity ?: return@OnPreferenceClickListener true
                val address = getRemoteAddress(context)
                val adbPortNum = getAdbPort(context)
                
                // Show dialog with ADB connection instructions
                val adbCommand = getString(R.string.adb_connect_command, address, adbPortNum.toString())
                val scrcpyCommand = "scrcpy -s $address:$adbPortNum"
                
                val message = getString(R.string.adb_connect_hint, address, adbPortNum.toString()) + 
                              "\n\n" + getString(R.string.scrcpy_hint, address, adbPortNum.toString())
                
                android.app.AlertDialog.Builder(context)
                    .setTitle(getString(R.string.settings_adb_connect_title))
                    .setMessage(message)
                    .setPositiveButton("Copy ADB Command") { _, _ ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("ADB Command", adbCommand)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, getString(R.string.adb_command_copied), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
                true
            }
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
                            
                            // Add verbose mode status and container mode
                            val verboseEnabled = isVerboseModeEnabled(context)
                            val containerMode = getContainerMode(context)
                            val settingsInfo = StringBuilder()
                            settingsInfo.append("Verbose mode: $verboseEnabled\n")
                            settingsInfo.append("Container mode: $containerMode\n")
                            settingsInfo.append("Local server port: ${getLocalServerPort(context)}\n")
                            settingsInfo.append("Local ADB port: ${getLocalAdbPort(context)}\n")
                            settingsInfo.append("Remote mode enabled: ${isRemoteModeEnabled(context)}\n")
                            if (isRemoteModeEnabled(context)) {
                                settingsInfo.append("Remote address: ${getRemoteAddress(context)}\n")
                                settingsInfo.append("Remote port: ${getRemotePort(context)}\n")
                                settingsInfo.append("ADB mode enabled: ${isAdbModeEnabled(context)}\n")
                                if (isAdbModeEnabled(context)) {
                                    settingsInfo.append("ADB port: ${getAdbPort(context)}\n")
                                }
                            }
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