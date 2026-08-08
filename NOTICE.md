# Third-party credits

## Terminal / console UI

The files under `app/src/main/java/com/termux/terminal` and
`app/src/main/java/com/termux/view` (terminal emulator and `TerminalView`)
used by the in-app Console screen are copied, with light adaptation (package
of the `R` class), from:

- https://github.com/Miuzarte/ScrcpyForAndroid (Apache License 2.0)
  which in turn credits https://github.com/reapercanuk39/termux-kotlin-app
  for this Apache-2.0-licensed portion of the terminal implementation.

These files remain licensed under the Apache License, Version 2.0. See
`LICENSE-APACHE-2.0.txt` for the full license text.

## App UI design (Miuix)

The app's user interface (Main/launcher, Settings, Console and File Browser
screens) is built with Jetpack Compose and the
[Miuix](https://github.com/compose-miuix-ui/miuix) component library, and its
screen structure/layout is adapted from:

- https://github.com/Miuzarte/ScrcpyForAndroid (Apache License 2.0)

This attribution is also shown in-app under Settings → About.
