package com.cyanmint.anbox

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val READ_REQUEST_CODE = 2
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val shutdown = preferenceScreen.findPreference<Preference>(getString(R.string.settings_shutdown_key))
            shutdown?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                activity?.finishAffinity()
                try {
                    Anbox.stopRuntime()
                    Anbox.stopContainer()
                } catch (e: Exception) {
                    // container was never started
                }
                true
            }

            val importRom = preferenceScreen.findPreference<Preference>(getString(R.string.settings_import_rom_key))
            importRom?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                (activity as? SettingsActivity)?.pickRomFile()
                true
            }

            val prootCommand = preferenceScreen.findPreference<Preference>(getString(R.string.settings_proot_command_key))
            prootCommand?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                (activity as? SettingsActivity)?.promptProotCommand()
                true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.title_settings)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }

    fun pickRomFile() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                setType("application/x-tar")
            },
            READ_REQUEST_CODE
        )
    }

    fun promptProotCommand() {
        val profile = ProfileManager.currentProfile(this)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(
                ProfileManager.getProotCommand(this@SettingsActivity, profile)
                    ?: ProfileManager.defaultProotCommand(this@SettingsActivity, profile)
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.proot_command_title)
            .setMessage(getString(R.string.proot_command_message, profile))
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                ProfileManager.setProotCommand(this, profile, input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != READ_REQUEST_CODE) return
        if (resultCode != Activity.RESULT_OK || data == null) return
        val uri = data.data ?: return
        promptImportDestination(uri)
    }

    private fun promptImportDestination(uri: android.net.Uri) {
        val profiles = ProfileManager.listProfiles(this).toTypedArray()
        var selected = ProfileManager.currentProfile(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.rom_installer_destination_title)
            .setSingleChoiceItems(profiles, profiles.indexOf(selected)) { _, which ->
                selected = profiles[which]
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                importRomTo(uri, selected)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importRomTo(uri: android.net.Uri, profile: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle(getString(R.string.rom_installer_extracting_title))
            setMessage(getString(R.string.rom_installer_extracting_msg))
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
            setCanceledOnTouchOutside(false)
            show()
        }
        thread {
            val profileDir = ProfileManager.profileDir(this, profile)
            profileDir.mkdirs()
            val romFile = File(profileDir, "rootfs.tar")
            val rootfsDir = ProfileManager.rootfsDir(this, profile)
            val tmpDir = File(profileDir, "tmp")
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                romFile.outputStream().use { output -> inputStream.copyTo(output) }
                inputStream.close()
                RomImporter.extractRootfs(romFile, rootfsDir)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ParcelConstructor.getBroadcastIntent("local")
                    ParcelConstructor.getBroadcastIntent("binder")
                }
                val codeFile = File(rootfsDir, "trans_code")
                codeFile.writeText(Anbox.getBroadcastIntentTransactionCode().toString())

                romFile.delete()
                tmpDir.mkdir()
            }
            runOnUiThread {
                progressDialog.dismiss()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
        }
        return true
    }
}
