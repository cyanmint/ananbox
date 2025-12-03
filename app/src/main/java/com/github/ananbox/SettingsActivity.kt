package com.github.ananbox

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File

class SettingsActivity : AppCompatActivity() {

    class SettingsFragment: PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val start = preferenceScreen.findPreference<Preference>(getString(R.string.settings_start_key))
            val shutdown = preferenceScreen.findPreference<Preference>(getString(R.string.settings_shutdown_key))
            val viewLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_logs_key))
            val exportLogs = preferenceScreen.findPreference<Preference>(getString(R.string.settings_export_logs_key))

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

        }

        private fun exportLogFiles() {
            val context = activity ?: return
            val filesDir = context.filesDir
            val prootLogFile = File(filesDir, "proot.log")
            val systemLogFile = File(filesDir, "system.log")

            val logContent = StringBuilder()
            
            if (prootLogFile.exists()) {
                logContent.append("=== proot.log ===\n")
                try {
                    logContent.append(prootLogFile.readText())
                } catch (e: Exception) {
                    logContent.append("Error reading proot.log: ${e.message}")
                }
                logContent.append("\n\n")
            }
            
            if (systemLogFile.exists()) {
                logContent.append("=== system.log ===\n")
                try {
                    logContent.append(systemLogFile.readText())
                } catch (e: Exception) {
                    logContent.append("Error reading system.log: ${e.message}")
                }
            }

            if (logContent.isEmpty()) {
                logContent.append("No log files found")
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ananbox Logs")
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