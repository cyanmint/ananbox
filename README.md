# Ananbox

**Another rootless Android container on android**

[![Build Android App](https://github.com/cyanmint/ananbox/actions/workflows/build.yml/badge.svg)](https://github.com/cyanmint/ananbox/actions/workflows/build.yml)

**Version 0.2.0**

Ananbox is a fork version of Anbox, with some modifications to get it run on Android rootlessly. And proot is now used to enforce the storage isolation.

## Status
WIP. The container can boot, but still buggy.

Most of the Android security features are missing becuase the current implementation of binder inside the container.

### Supported Host Android version
Android 11, 12, 15

(More version is coming soon.., theoretically it can support android 7.0 and newer)

### Supported Architecture
- x86_64
- arm64

## Feature
- FOSS (Both the app & the internal ROM), you can build everything from source, and everything is under your control.
- Customizable, you can customize both the app and the internal ROM.
- **Self-contained**: All dependencies (anbox, boost, proot) are vendored for reproducible builds
- **Log Viewer**: View proot and system logs directly in the app
- **Log Export**: Export diagnostic logs as a compressed tarball for debugging
- **Verbose Mode**: Enable detailed logging for troubleshooting
- **Standalone Server**: Run containers on a separate machine and stream to clients
- **Remote Client Mode**: Connect to a remote Ananbox server for streaming

## How to use

Build or Download the app and the rootfs.tar.gz of corresponding architecture. The app provides the option to import the ROM the first time you boot. 

Click the bottom-right button to launch the Settings Activity, where you can:
- Start/restart the container
- Shutdown the container gracefully
- View logs (proot.log and system.log)
- Export logs for debugging
- Toggle verbose mode for detailed logging
- Configure remote server connection

## Standalone Server (Termux)

Ananbox includes a standalone server that can run containers in Termux (Android terminal emulator) and stream graphics, audio, and input over TCP to connected clients. This allows you to run the container on one Android device (or any device with Termux) and display the UI on another device using the Ananbox app.

### Building the Server for Termux

The server is built using the Android NDK toolchain, targeting Termux. You need:
- Android NDK 25.1+
- CMake 3.22+

Build using the Android NDK:

```bash
cd app/src/main/cpp

# Set up NDK environment
export ANDROID_NDK=/path/to/android-ndk
export ANDROID_ABI=arm64-v8a  # or x86_64

mkdir build && cd build
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=$ANDROID_ABI \
      -DANDROID_PLATFORM=android-24 \
      -DBUILD_ANANBOX_SERVER=ON \
      ..
make ananbox-server
```

This will build both the `ananbox-server` binary and the `proot` binary for running in Termux.

### Installing in Termux

Copy the built binaries to your Termux environment:

```bash
# On your development machine
adb push build/ananbox-server /data/local/tmp/
adb push build/proot /data/local/tmp/

# In Termux
cp /data/local/tmp/ananbox-server $PREFIX/bin/
cp /data/local/tmp/proot $PREFIX/bin/
chmod +x $PREFIX/bin/ananbox-server $PREFIX/bin/proot
```

### Running the Server in Termux

```bash
ananbox-server --help

# Example: Start server on all interfaces, port 5558
ananbox-server -a 0.0.0.0 -p 5558 -w 1920 -h 1080 -b ~/ananbox -P proot
```

#### Server Options:
- `-a, --address <ip>`: Listen address (default: 0.0.0.0)
- `-p, --port <port>`: Listen port (default: 5558)
- `-w, --width <pixels>`: Display width (default: 1280)
- `-h, --height <pixels>`: Display height (default: 720)
- `-d, --dpi <dpi>`: Display DPI (default: 160)
- `-b, --base <path>`: Base path (parent of rootfs)
- `-P, --proot <path>`: Path to proot binary
- `-A, --adb-address <ip>`: ADB listen address (default: same as --address)
- `-D, --adb-port <port>`: ADB listen port (default: 5555, 0 to disable)
- `-S, --adb-socket <path>`: ADB socket path in rootfs (default: /dev/socket/adbd)
- `-v, --verbose`: Enable verbose logging

### ADB Forwarding

The server can forward ADB connections from a TCP port to the container's ADB socket (`/dev/socket/adbd`). This allows using `adb connect` to access the container, enabling tools like scrcpy to work with remote containers.

```bash
# On the server (Termux):
ananbox-server -b ~/ananbox -P proot -D 5555

# From another device:
adb connect <server-ip>:5555
scrcpy -s <server-ip>:5555
```

### Connecting from the App

1. Open Settings in the app
2. Enable "Connect to Remote Server"
3. Enter the server's IP address and port
4. Click "Connect Now" or start the app

The app will connect to the remote server and display the streamed content. Touch events are sent back to the server.

#### Connect via ADB (scrcpy)

For better performance with tools like scrcpy:

1. Enable "Connect to Remote Server"
2. Enable "Connect via ADB (scrcpy)"
3. Configure the ADB port (default: 5555)
4. Click "Connect via ADB" to see connection instructions

This mode is ideal for using scrcpy or other ADB tools to interact with the remote container.

### Streaming Protocol

The server uses a custom TCP streaming protocol:
- **Graphics**: Raw RGBA8888 frame data
- **Audio**: PCM audio samples
- **Input**: Touch, keyboard, and mouse events
- **Control**: Handshake, ping/pong, disconnect

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

## Changelog

### Version 0.2.0

#### New Features
- **Standalone Server Mode**: Run containers on a separate machine and stream to clients
- **Remote Client Mode**: Connect to a remote Ananbox server for streaming
- **ADB Forwarding**: Forward ADB connections from TCP port to container's ADB socket
- **scrcpy Support**: Connect via ADB for better performance with scrcpy
- **Log Viewer**: View proot and system logs directly in the app
- **Log Export**: Export diagnostic logs as a compressed tarball for debugging
- **Verbose Mode**: Enable detailed logging for troubleshooting
- **Self-contained Build**: Vendored all dependencies (anbox, boost, proot) for reproducible builds

#### Bug Fixes
- Fixed embedded server mode container startup by using `PROOT_LOADER` to point to pre-built loader binary
- Fixed noexec filesystem issue by bundling proot loader in native library directory (which has exec permission)
- Use symlinks instead of copying binaries from nativeLibraryDir to filesDir/bin (saves disk space)
- Detect and recreate dangling symlinks after app upgrade
- Fixed local JNI mode container startup by properly setting `PROOT_TMP_DIR` environment variable
- Fixed blank black screen issue by ensuring required directories exist before container starts
- Improved symlink handling during rootfs extraction with better validation
- Fixed symlink creation to properly handle relative paths

#### Build Improvements
- Vendored proot source code to make the project self-contained
- Disabled proot Python extension for Android compatibility
- Suppressed excessive C/C++ warnings during compilation
- Lowered targetSdk to 28 to bypass W^X noexec restriction (like Termux on F-Droid)

### Version 0.1.0 (Original)
- Initial fork from Anbox
- Basic container functionality with proot isolation
- Graphics support via forked anbox renderer

## Preview

![demo](https://github.com/Ananbox/ananbox/assets/6512977/2c63d517-5bf2-48bb-ac71-42aa809cffed)

