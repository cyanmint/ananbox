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

## Vendored ScrcpyForAndroid package

The files under `app/src/main/java/io/github/miuzarte/scrcpyforandroid` are
vendored as-is (with only mechanical `R`/`BuildConfig` import redirection to
this app's own generated classes, matching the pattern used for the Termux
files above) from:

- https://github.com/Miuzarte/ScrcpyForAndroid (Apache License 2.0)

Their associated string resources (`app/src/main/res/values/strings.xml` and
`app/src/main/res/values-zh/strings.xml`) are likewise copied verbatim from
the same project (only the duplicate `app_name` entry was dropped, and
`about_title` was renamed to `sfa_about_title` to avoid colliding with this
app's own string of the same name).

These files remain licensed under the Apache License, Version 2.0. See
`LICENSE-APACHE-2.0.txt` for the full license text.

Two small local compatibility shims were added by this project (not part of
the upstream vendor) to satisfy dependencies unavailable in this build
environment:

- `app/src/main/java/com/github/promeg/pinyinhelper/Pinyin.kt` — a drop-in
  replacement for the `tinypinyin` library, implemented with Android's
  built-in ICU transliteration.
- `app/src/main/java/io/github/miuzarte/scrcpyforandroid/scaffolds/BreadcrumbBarCompat.kt`
  — a stand-in for Miuix's `BreadcrumbBar`/`BreadcrumbItem`, which are not yet
  part of the published `miuix-ui` release used by this project.
