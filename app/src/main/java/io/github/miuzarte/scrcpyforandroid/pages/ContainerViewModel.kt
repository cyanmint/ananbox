package io.github.miuzarte.scrcpyforandroid.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.miuzarte.scrcpyforandroid.container.ContainerProfileManager
import io.github.miuzarte.scrcpyforandroid.container.ContainerRomImporter
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class ContainerState(
    val profiles: List<String> = listOf(ContainerProfileManager.DEFAULT_PROFILE),
    val activeProfile: String = ContainerProfileManager.DEFAULT_PROFILE,
    val hasRootfs: Boolean = false,
    val importing: Boolean = false,
)

/**
 * Backs the "Container" section of the main tab: profile selection/editing,
 * the active profile / rootfs status display, and rootfs import.
 */
class ContainerViewModel: ViewModel() {

    private val context get() = AppRuntime.context

    private val _state = MutableStateFlow(refreshedState())
    val state: StateFlow<ContainerState> = _state.asStateFlow()

    private fun refreshedState(): ContainerState {
        val profile = ContainerProfileManager.currentProfile(context)
        return ContainerState(
            profiles = ContainerProfileManager.listProfiles(context),
            activeProfile = profile,
            hasRootfs = ContainerProfileManager.hasRootfs(context, profile),
        )
    }

    fun refresh() {
        _state.value = refreshedState()
    }

    fun selectProfile(name: String) {
        ContainerProfileManager.setCurrentProfile(context, name)
        refresh()
    }

    fun addProfile(name: String): Boolean {
        val added = ContainerProfileManager.addProfile(context, name)
        if (added) {
            ContainerProfileManager.setCurrentProfile(context, name)
            refresh()
        }
        return added
    }

    fun renameActiveProfile(newName: String): Boolean {
        val renamed = ContainerProfileManager.renameProfile(context, _state.value.activeProfile, newName)
        if (renamed) refresh()
        return renamed
    }

    fun removeProfile(name: String) {
        ContainerProfileManager.removeProfile(context, name)
        refresh()
    }

    fun prootCommand(): String {
        val profile = _state.value.activeProfile
        return ContainerProfileManager.getProotCommand(context, profile)
            ?: ContainerProfileManager.defaultProotCommand(context, profile)
    }

    fun setProotCommand(command: String) {
        ContainerProfileManager.setProotCommand(context, _state.value.activeProfile, command)
    }

    /** Imports a rootfs tarball (from [openInput]) into [targetProfile]. */
    fun importRootfs(targetProfile: String, openInput: () -> InputStream?, onDone: (Boolean) -> Unit) {
        _state.value = _state.value.copy(importing = true)
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCatching {
                val input = openInput() ?: throw IllegalStateException("cannot open selected file")
                val profileDir = ContainerProfileManager.profileDir(context, targetProfile)
                profileDir.mkdirs()
                val tarFile = File(profileDir, "rootfs.tar")
                input.use { stream -> tarFile.outputStream().use { output -> stream.copyTo(output) } }
                val rootfsDir = ContainerProfileManager.rootfsDir(context, targetProfile)
                ContainerRomImporter.extractRootfs(tarFile, rootfsDir)
                tarFile.delete()
            }.isSuccess
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(importing = false)
                refresh()
                onDone(success)
            }
        }
    }
}
