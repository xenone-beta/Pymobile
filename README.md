# Pymobile

A Python IDE for Android with its own CPython 3.14 (arm64), pip, a code editor with
highlighting and hints, a console with `input()` support and Git — in Material You.

## Features

- Editor: syntax highlighting, auto-indent, bracket/quote pairing, undo/redo, line numbers,
  completion strip, errors while you type, conservative code formatter, extra key row
- Tabs, projects (folders) in shared storage (`/storage/emulated/0/Pymobile`)
- Console as a bottom panel or full screen, ANSI colors, clickable tracebacks, Stop button
- pip manager (list / install / upgrade / uninstall, `requirements.txt`)
- Git over HTTPS with an access token (init, commit, log, pull, push, clone)
- Background run: a script keeps running as a foreground service (own process, output goes to log files,
  no keyboard input) and the app reconnects to it when you come back
- Per-file arguments and environment variables, open a file from the device, save a copy, auto-save with
  draft recovery after a crash
- Editor themes: seven built-in colour schemes, your own themes edited in the app, and `.pmt` files
  (a small JSON document: name, dark/light and the colours) that you can import and export
- "Open with Pymobile" for `.py` and `.pmt` files from other apps; PyPI search with version picker
- English and Russian

## How it works

Python runs from the official python.org Android build (`libpython3.14.so` plus the standard
library), loaded through JNI in a separate process (`:python`). Each run is one process, so
**Stop** simply kills it. The payload is not stored in the repository: `tools/fetch-python.sh`
downloads it (CI does this before building).

## Build

```
./tools/fetch-python.sh      # needs python3 and network; fills jniLibs, headers and assets
./gradlew assembleDebug
```

Requires JDK 17, Android SDK 35 and NDK/CMake 3.22.1. The APK contains arm64-v8a only.

## Limitations

- Packages must be pure Python (or have an Android wheel); sdists and native builds are not supported
- No REPL or debugger
- Background scripts have no stdin; their logs live in app storage and are replaced by the next background run
- Environment variables for a run are stored unencrypted in app-private storage
- The Git token is stored in app-private storage, not encrypted, and excluded from backups
- On Android 10 shared-storage access is limited; the app falls back to its own folder
- The first release was written without being compiled — expect to fix build errors on the first CI run

## Release signing

Set the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
and push a tag like `v0.1.0`.

MIT license.

## Theme files (.pmt)

```json
{
  "pmt": 1,
  "name": "My theme",
  "author": "you",
  "dark": true,
  "colors": { "background": "#1E1E2E", "text": "#CDD6F4", "keyword": "#CBA6F7" }
}
```

Colors are `#RGB`, `#RRGGBB` or `#AARRGGBB`. Keys: `background`, `text`, `gutter`, `gutterText`, `caret`,
`selection`, `keyword`, `builtin`, `string`, `comment`, `number`, `decorator`, `function`, `class`, `error`,
`warning`, `consoleBackground`, `consoleText`. Missing keys come from a built-in dark or light theme
(`dark` decides which).
