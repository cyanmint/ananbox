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

## Standalone Server

Ananbox includes a standalone server that can run containers on a separate Linux machine and stream graphics, audio, and input over TCP to connected clients.

### Building the Server

The server is built separately from the Android app. You need to build it on a Linux system with the following dependencies:
- CMake 3.22+
- Boost (filesystem, log, system, thread, program_options)
- EGL and OpenGL ES libraries
- talloc library (or it will be built from source)

```bash
cd app/src/main/cpp
mkdir build && cd build
cmake -DBUILD_ANANBOX_SERVER=ON ..
make ananbox-server
```

This will build both the `ananbox-server` binary and the `proot` binary needed to run containers.

### Running the Server

```bash
./ananbox-server --help

# Example: Start server on all interfaces, port 5558
# The proot binary is built alongside the server
./ananbox-server -a 0.0.0.0 -p 5558 -w 1920 -h 1080 -r /path/to/rootfs -P ./proot
```

#### Server Options:
- `-a, --address <ip>`: Listen address (default: 0.0.0.0)
- `-p, --port <port>`: Listen port (default: 5558)
- `-w, --width <pixels>`: Display width (default: 1280)
- `-h, --height <pixels>`: Display height (default: 720)
- `-d, --dpi <dpi>`: Display DPI (default: 160)
- `-r, --rootfs <path>`: Path to rootfs directory
- `-P, --proot <path>`: Path to proot binary (built with the server)
- `-v, --verbose`: Enable verbose logging

### Connecting from the App

1. Open Settings in the app
2. Enable "Connect to Remote Server"
3. Enter the server's IP address and port
4. Click "Connect Now" or start the app

The app will connect to the remote server and display the streamed content. Touch events are sent back to the server.

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

## Recent Changes

### Bug Fixes
- Fixed blank black screen issue by ensuring required directories (`tmp` and `rootfs/mnt/user/0`) exist before container starts
- Improved symlink handling during rootfs extraction with better validation
- Fixed symlink creation to properly handle relative paths

### New Features
- Added `LogViewActivity` for viewing logs in-app
- Added log export functionality with diagnostic information
- Added verbose mode toggle in settings
- Added standalone server for running containers remotely
- Added remote client mode to connect to external servers
- Added TCP streaming protocol for graphics, audio, and input

## Preview

![demo](https://github.com/Ananbox/ananbox/assets/6512977/2c63d517-5bf2-48bb-ac71-42aa809cffed)

