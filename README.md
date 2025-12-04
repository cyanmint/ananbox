# Ananbox

**Another rootless Android container on android**

Ananbox is a fork version of Anbox, with some modifications to get it run on Android rootlessly. And proot is now used to enforce the storage isolation.

## Status
WIP. The container can boot, but still buggy.

Most of the Android security features are missing becuase the current implementation of binder inside the container.

### Supported System Component
- Binder
- Graphics (forked from anbox)
- Wifi Simulation (still buggy)

### Supported Host Android version
Android 11, 12

(More version is coming soon.., theoretically it can support android 7.0 and newer)

### Supported Architecture
- x86_64
- arm64

## Feature
- FOSS (Both the app & the internal ROM), you can build everything from source, and everything is under your control.
- Customizable, you can customize both the app and the internal ROM.
- **Log Viewer**: View proot and system logs directly in the app
- **Log Export**: Export diagnostic logs as a compressed tarball for debugging
- **Verbose Mode**: Enable detailed logging for troubleshooting

## How to use

Build or Download the app and the rootfs.tar.gz of corresponding architecture. The app provides the option to import the ROM the first time you boot. 

Click the bottom-right button to launch the Settings Activity, where you can:
- Start/restart the container
- Shutdown the container gracefully
- View logs (proot.log and system.log)
- Export logs for debugging
- Toggle verbose mode for detailed logging

## Debug

System.log is under `<internal storage>/rootfs/data`, proot.log is under `<internal storage>`

### Using the Log Viewer

1. Open Settings from the floating action button
2. Click "View Logs" to see proot.log and system.log in-app
3. Use "Export Logs" to create a diagnostic tarball that includes:
   - proot.log
   - system.log
   - logcat output
   - device information
   - process list

### Verbose Mode

Enable verbose mode in Settings to get detailed device information and timestamps in the log viewer.

## Building

The project uses Gradle for building. C++ warnings from third-party code are suppressed during compilation.

```bash
./gradlew assembleDebug
```

## Recent Changes

### Bug Fixes
- Fixed blank black screen issue by ensuring required directories (`tmp` and `rootfs/mnt/user/0`) exist before container starts
- Improved symlink handling during rootfs extraction with better validation
- Fixed symlink creation to properly handle relative paths

### New Features
- Added `LogViewActivity` for viewing logs in-app
- Added log export functionality with diagnostic information
- Added verbose mode toggle in settings

## Preview

![demo](https://github.com/Ananbox/ananbox/assets/6512977/2c63d517-5bf2-48bb-ac71-42aa809cffed)

