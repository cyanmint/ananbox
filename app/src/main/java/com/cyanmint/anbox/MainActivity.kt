package com.cyanmint.anbox

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cyanmint.anbox.databinding.ActivityMainBinding

/**
 * Main entry point.
 *
 * On launch only a profile selector and a set of buttons (Start, Settings,
 * Console, File Browser) are shown - the emulated screen is never created
 * automatically. The renderer/container are only started once the user
 * explicitly taps "Start"; settings (including the proot command, ROM
 * import, etc.) can be freely adjusted before that.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

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
            if (Anbox.initRuntime(mSurfaceView.width, mSurfaceView.height, dpi)) {
                Anbox.createSurface(surface)
                Anbox.startRuntime()
                Anbox.startContainer(mProotCommand)
            } else {
                Anbox.createSurface(surface)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.i(TAG, "surfaceChanged: " + mSurfaceView.width + "x" + mSurfaceView.height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Anbox.destroySurface()
            Log.i(TAG, "surfaceDestroyed!")
        }
    }

    private lateinit var binding: ActivityMainBinding
    private val receiver = BinderReceiver()
    private val handlerThread = HandlerThread("BinderReceiverThread")
    private var containerStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlerThread.start()
        ContextCompat.registerReceiver(
            this, receiver,
            IntentFilter("com.cyanmint.anbox.BINDER"),
            null, Handler(handlerThread.looper),
            ContextCompat.RECEIVER_EXPORTED
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLauncherPanel()
    }

    private fun setupLauncherPanel() {
        refreshProfileSpinner()

        binding.profileSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long
                ) {
                    val profiles = ProfileManager.listProfiles(this@MainActivity)
                    if (position in profiles.indices) {
                        ProfileManager.setCurrentProfile(this@MainActivity, profiles[position])
                        updateStatusText()
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        binding.addProfileButton.setOnClickListener { promptAddProfile() }
        binding.removeProfileButton.setOnClickListener { promptRemoveProfile() }

        binding.startButton.setOnClickListener { onStartClicked() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
        }
        binding.consoleButton.setOnClickListener {
            startActivity(Intent(applicationContext, ConsoleActivity::class.java))
        }
        binding.fileBrowserButton.setOnClickListener {
            startActivity(Intent(applicationContext, FileBrowserActivity::class.java))
        }

        updateStatusText()
    }

    private fun refreshProfileSpinner() {
        val profiles = ProfileManager.listProfiles(this)
        binding.profileSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, profiles)
        val current = ProfileManager.currentProfile(this)
        val index = profiles.indexOf(current).coerceAtLeast(0)
        binding.profileSpinner.setSelection(index)
    }

    private fun updateStatusText() {
        val profile = ProfileManager.currentProfile(this)
        val hasRootfs = ProfileManager.hasRootfs(this, profile)
        binding.statusText.text = if (hasRootfs) {
            getString(R.string.profile_rootfs_ready, profile)
        } else {
            getString(R.string.profile_rootfs_missing, profile)
        }
    }

    private fun promptAddProfile() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_add)
            .setView(input)
            .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                val name = input.text.toString().trim()
                if (ProfileManager.addProfile(this, name)) {
                    ProfileManager.setCurrentProfile(this, name)
                    refreshProfileSpinner()
                    updateStatusText()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptRemoveProfile() {
        val current = ProfileManager.currentProfile(this)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_remove))
            .setMessage(getString(R.string.profile_remove_confirm, current))
            .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                ProfileManager.removeProfile(this, current)
                refreshProfileSpinner()
                updateStatusText()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onStartClicked() {
        val profile = ProfileManager.currentProfile(this)
        if (!ProfileManager.hasRootfs(this, profile)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.rom_installer_title)
                .setMessage(getString(R.string.rom_missing_go_to_settings))
                .setPositiveButton(R.string.action_settings) { _: DialogInterface, _: Int ->
                    startActivity(Intent(applicationContext, SettingsActivity::class.java))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        mProotCommand = ProfileManager.getProotCommand(this, profile)
            ?: ProfileManager.defaultProotCommand(this, profile)
        Anbox.setPath(ProfileManager.profileDir(this, profile).path)

        binding.launcherPanel.visibility = android.view.View.GONE
        binding.fab.visibility = android.view.View.VISIBLE

        mSurfaceView = SurfaceView(this)
        mSurfaceView.getHolder().addCallback(mSurfaceCallback)
        binding.root.addView(mSurfaceView, 0)

        mSurfaceView.setOnTouchListener(Anbox)
        binding.fab.setOnClickListener {
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
        }
        containerStarted = true

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        if (!containerStarted) {
            refreshProfileSpinner()
            updateStatusText()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (containerStarted) {
            Anbox.stopRuntime()
        }
        unregisterReceiver(receiver)
    }
}
