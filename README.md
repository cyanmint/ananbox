# Ananbox

**Another rootless Android container on android**

Ananbox is a fork version of Anbox, with some modifications to get it run on Android rootlessly. And it uses proot for storage isolation and basic capbilities emulation.

## Status
WIP. The container can boot, but still buggy.

Part of the Android security features are missing becuase of the current implementation of binder inside the container.

### Supported System Component
- Binder
- Graphics (forked from anbox)
- Wifi Simulation (still buggy)

### Supported Host Android version

Android 11 and newer

### Supported Architecture
- x86_64
- arm64

## Feature
- FOSS (Both the app & the internal ROM), you can build everything from source, and everything is under your control.
- Customizable, you can customize both the app and the internal ROM. 

## How to use

Build or download the app and a rootfs.7z of the corresponding architecture. Ananbox no longer imports a ROM automatically: from the main screen, pick or create a **profile**, then go to **Settings → Import ROM** to manually select a rootfs `.tar` file and the profile to extract it into. Each profile has its own rootfs and its own container settings, so you can keep multiple independent environments side by side.

The screen/renderer is only created after you tap **Start** on the main screen; you're free to adjust settings beforehand. Use the **Console** button to get a plain `sh` shell running directly in the app's own data directory (no container/proot involved), and the **File Browser** button to browse the app's own data directory (`/data/user/<userId>/com.cyanmint.anbox`).

Click the bottom-right button while the container is running to launch the Settings Activity, where you can shutdown the container gracefully.

## Debug

**Make sure you submit these files in Github issue**

Host-side Paths (replace `<profile>` with your profile name, `default` unless renamed):

- `/data/data/com.cyanmint.anbox/files/profiles/<profile>/rootfs/data/system.log`
- `/data/data/com.cyanmint.anbox/files/profiles/<profile>/proot.log`
- `/data/data/com.cyanmint.anbox/files/profiles/<profile>/rootfs/localBroadcastIntent`
- `/data/data/com.cyanmint.anbox/files/profiles/<profile>/rootfs/binderBroadcastIntent`
- `/data/data/com.cyanmint.anbox/files/profiles/<profile>/rootfs/trans_code`

## Preview

![demo](https://github.com/Ananbox/ananbox/assets/6512977/2c63d517-5bf2-48bb-ac71-42aa809cffed)

## Credits

The Console screen's terminal emulator/view (`com.termux.terminal`, `com.termux.view`) is copied, with light adaptation, from [Miuzarte/ScrcpyForAndroid](https://github.com/Miuzarte/ScrcpyForAndroid) (Apache License 2.0), which in turn credits [reapercanuk39/termux-kotlin-app](https://github.com/reapercanuk39/termux-kotlin-app) for this portion. See `NOTICE.md` and `LICENSE-APACHE-2.0.txt`.

This fork is published under the package name `com.cyanmint.anbox` to avoid collisions with the upstream `com.github.ananbox` project, so both can be installed side by side.

