package io.github.miuzarte.scrcpyforandroid.container

import android.content.Context
import java.io.File

/**
 * Manages multiple independent container profiles. Each profile has its own
 * rootfs directory and its own settings (e.g. proot command), so different
 * profiles can run completely different Linux root filesystems /
 * configurations. This replaces the old adb "connected device" concept as
 * the primary thing shown/selected on the main tab.
 */
object ContainerProfileManager {
    private const val PREFS_NAME = "container_profiles"
    private const val KEY_PROFILE_LIST = "profile_list"
    private const val KEY_CURRENT_PROFILE = "current_profile"
    const val DEFAULT_PROFILE = "default"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun listProfiles(context: Context): List<String> {
        val stored = prefs(context).getString(KEY_PROFILE_LIST, null)
        val names = stored?.split("\u0001")?.filter { it.isNotBlank() } ?: emptyList()
        return if (names.isEmpty()) listOf(DEFAULT_PROFILE) else names
    }

    private fun saveProfiles(context: Context, names: List<String>) {
        prefs(context).edit()
            .putString(KEY_PROFILE_LIST, names.joinToString("\u0001"))
            .apply()
    }

    fun currentProfile(context: Context): String {
        val current = prefs(context).getString(KEY_CURRENT_PROFILE, null)
        val profiles = listProfiles(context)
        return if (current != null && profiles.contains(current)) current else profiles.first()
    }

    fun setCurrentProfile(context: Context, name: String) {
        prefs(context).edit().putString(KEY_CURRENT_PROFILE, name).apply()
    }

    fun addProfile(context: Context, name: String): Boolean {
        val sanitized = name.trim()
        if (sanitized.isBlank() || sanitized.contains("/") || sanitized.contains("..")) return false
        val profiles = listProfiles(context)
        if (profiles.contains(sanitized)) return false
        saveProfiles(context, profiles + sanitized)
        return true
    }

    fun renameProfile(context: Context, oldName: String, newName: String): Boolean {
        val sanitized = newName.trim()
        if (sanitized.isBlank() || sanitized.contains("/") || sanitized.contains("..")) return false
        val profiles = listProfiles(context)
        if (!profiles.contains(oldName) || profiles.contains(sanitized)) return false
        profileDir(context, oldName).renameTo(profileDir(context, sanitized))
        saveProfiles(context, profiles.map { if (it == oldName) sanitized else it })
        if (currentProfile(context) == oldName) setCurrentProfile(context, sanitized)
        return true
    }

    fun removeProfile(context: Context, name: String) {
        val profiles = listProfiles(context).filter { it != name }
        saveProfiles(context, profiles.ifEmpty { listOf(DEFAULT_PROFILE) })
        profileDir(context, name).deleteRecursively()
        if (currentProfile(context) == name) {
            setCurrentProfile(context, listProfiles(context).first())
        }
    }

    /** Base directory for the given profile, e.g. files/container-profiles/<name>/ */
    fun profileDir(context: Context, name: String = currentProfile(context)): File {
        return File(File(context.filesDir, "container-profiles"), name)
    }

    fun rootfsDir(context: Context, name: String = currentProfile(context)): File {
        return File(profileDir(context, name), "rootfs")
    }

    fun hasRootfs(context: Context, name: String = currentProfile(context)): Boolean {
        return rootfsDir(context, name).exists() && rootfsDir(context, name).list()?.isNotEmpty() == true
    }

    private fun settingsPrefs(context: Context, name: String) =
        context.getSharedPreferences("container_profile_$name", Context.MODE_PRIVATE)

    fun getProotCommand(context: Context, name: String = currentProfile(context)): String? {
        return settingsPrefs(context, name).getString("proot_command", null)
    }

    fun setProotCommand(context: Context, name: String, command: String) {
        settingsPrefs(context, name).edit().putString("proot_command", command).apply()
    }

    fun defaultProotCommand(context: Context, name: String = currentProfile(context)): String {
        val proot = context.applicationInfo.nativeLibraryDir + "/libproot.so"
        val rootfs = rootfsDir(context, name).path
        return "$proot --link2symlink -0 -r $rootfs -b /dev -b /proc -b /sys -w / /system/bin/sh"
    }
}
