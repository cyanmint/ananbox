package com.cyanmint.anbox

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cyanmint.anbox.ui.AnanboxTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import java.io.File
import kotlin.concurrent.thread

/**
 * Main entry point.
 *
 * The launcher UI is built with Jetpack Compose + Miuix and laid out as a
 * bottom-[NavigationBar] tabbed shell (Container / Console / Files / Settings),
 * closely mirroring the tabbed design of ScrcpyForAndroid (Apache-2.0, see
 * /NOTICE.md and Settings -> About). The emulated screen is never created
 * automatically; the renderer/container only start when the user taps "Start"
 * on the Container tab.
 *
 * The tabbed Compose UI is hosted inside a [ComposeView] that lives on top of
 * a plain [FrameLayout] root. When the container starts, the ComposeView is
 * removed and the native [SurfaceView] is added into the same FrameLayout,
 * exactly like the old launcherPanel/fab View-based flow.
 */
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private lateinit var rootFrame: FrameLayout
    private lateinit var composeView: ComposeView

    private lateinit var mSurfaceView: SurfaceView
    private lateinit var mProotCommand: String

    private val profiles = mutableStateListOf<String>()
    private var currentProfile by mutableStateOf(ProfileManager.DEFAULT_PROFILE)
    private var importProgress by mutableStateOf(false)

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

    private val receiver = BinderReceiver()
    private val handlerThread = HandlerThread("BinderReceiverThread")
    private var containerStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlerThread.start()
        ContextCompat.registerReceiver(
            this, receiver,
            android.content.IntentFilter("com.cyanmint.anbox.BINDER"),
            null, Handler(handlerThread.looper),
            ContextCompat.RECEIVER_EXPORTED,
        )

        rootFrame = FrameLayout(this)
        composeView = ComposeView(this)
        rootFrame.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(rootFrame)

        refreshProfiles()

        composeView.setContent {
            AnanboxTheme {
                AnanboxApp(
                    profiles = profiles,
                    currentProfile = currentProfile,
                    importProgress = importProgress,
                    rootDir = filesDir,
                    onSelectProfile = { name ->
                        ProfileManager.setCurrentProfile(this, name)
                        currentProfile = name
                    },
                    onAddProfile = { name ->
                        if (ProfileManager.addProfile(this, name)) {
                            ProfileManager.setCurrentProfile(this, name)
                            refreshProfiles()
                        }
                    },
                    onRemoveProfile = {
                        ProfileManager.removeProfile(this, currentProfile)
                        refreshProfiles()
                    },
                    onStart = { onStartClicked() },
                    onShutdown = { onShutdown() },
                    onImportRom = { uri, profile -> importRomTo(uri, profile) },
                    onOpenFile = { file -> openInternalFile(this, file) },
                )
            }
        }
    }

    private fun refreshProfiles() {
        val list = ProfileManager.listProfiles(this)
        profiles.clear()
        profiles.addAll(list)
        currentProfile = ProfileManager.currentProfile(this)
    }

    private fun onShutdown() {
        try {
            Anbox.stopRuntime()
            Anbox.stopContainer()
        } catch (e: Exception) {
            // container was never started
        }
        finishAffinity()
    }

    private fun importRomTo(uri: Uri, profile: String) {
        importProgress = true
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
                importProgress = false
                refreshProfiles()
            }
        }
    }

    private fun onStartClicked() {
        val profile = ProfileManager.currentProfile(this)
        if (!ProfileManager.hasRootfs(this, profile)) {
            // No rootfs installed yet; nothing to boot.
            return
        }

        mProotCommand = ProfileManager.getProotCommand(this, profile)
            ?: ProfileManager.defaultProotCommand(this, profile)
        Anbox.setPath(ProfileManager.profileDir(this, profile).path)

        // Remove the Compose launcher panel and hand the FrameLayout over to
        // the native render surface, matching the old launcherPanel/fab flow.
        rootFrame.removeView(composeView)

        mSurfaceView = SurfaceView(this)
        mSurfaceView.holder.addCallback(mSurfaceCallback)
        rootFrame.addView(mSurfaceView, 0)
        mSurfaceView.setOnTouchListener(Anbox)

        val fab = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            contentDescription = getString(R.string.action_settings)
            setOnClickListener { onShutdown() }
        }
        val fabParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            val margin = (8 * resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }
        rootFrame.addView(fab, fabParams)

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
            refreshProfiles()
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

private enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    Container(R.string.tab_container, MiuixIcons.Home),
    Console(R.string.tab_console, MiuixIcons.ListView),
    Files(R.string.tab_files, MiuixIcons.Folder),
    Settings(R.string.tab_settings, MiuixIcons.Settings),
}

@Composable
private fun AnanboxApp(
    profiles: List<String>,
    currentProfile: String,
    importProgress: Boolean,
    rootDir: File,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onRemoveProfile: () -> Unit,
    onStart: () -> Unit,
    onShutdown: () -> Unit,
    onImportRom: (Uri, String) -> Unit,
    onOpenFile: (File) -> Unit,
) {
    val context = LocalContext.current
    val tabs = remember { MainTab.entries }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Container.ordinal) }

    val containerScroll = MiuixScrollBehavior()
    val consoleScroll = MiuixScrollBehavior()
    val filesScroll = MiuixScrollBehavior()
    val settingsScroll = MiuixScrollBehavior()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = tab.icon,
                        label = context.getString(tab.labelRes),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (tabs[selectedTab]) {
                MainTab.Container -> ContainerScreen(
                    profiles = profiles,
                    currentProfile = currentProfile,
                    scrollBehavior = containerScroll,
                    bottomPadding = padding,
                    onSelectProfile = onSelectProfile,
                    onAddProfile = onAddProfile,
                    onRemoveProfile = onRemoveProfile,
                    onStart = onStart,
                )
                MainTab.Console -> ConsoleScreen(
                    scrollBehavior = consoleScroll,
                    bottomPadding = padding,
                )
                MainTab.Files -> FileBrowserScreen(
                    rootDir = rootDir,
                    scrollBehavior = filesScroll,
                    bottomPadding = padding,
                    onOpenFile = onOpenFile,
                )
                MainTab.Settings -> SettingsScreen(
                    scrollBehavior = settingsScroll,
                    bottomPadding = padding,
                    onShutdown = onShutdown,
                    importRom = onImportRom,
                    importProgress = importProgress,
                )
            }
        }
    }
}
