# G9 — Gallery on 9 keys

A gallery for Android phones with a keypad and no touchscreen.

[![Build](https://github.com/NineTailsFlip/g9/actions/workflows/build.yml/badge.svg)](https://github.com/NineTailsFlip/g9/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/NineTailsFlip/g9)](https://github.com/NineTailsFlip/g9/releases/latest)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)

G9 uses the look and the keys of the [Nine suite](https://git.sr.ht/~timofurrer/nine) (L9, C9,
P9, M9): one column on black, a numbered `*` menu and a hint bar. It is a sister app of
[TG9](https://github.com/NineTailsFlip/tg9), Telegram on 9 keys, and part of
[NineTailsFlip](https://github.com/NineTailsFlip).

## Features

- Every photo and video, newest first under day headers; each row shows a thumbnail, the time,
  the folder, size and dimensions. The list follows MediaStore live, so a photo you just took
  appears without leaving the screen.
- Folders (Camera, Screenshots, Download, TG9…), most recently added-to first, then the trash.
- Full screen: photos zoom and pan; videos play and seek; GIFs and animated WebP play;
  details (name, date, size, folder, path).
- Mark items (or a whole day) to share them or move them to the trash.
- Nothing is deleted outright: items go to Android's trash for 30 days, and Android asks every
  time. Restore from the trash, or delete for good.
- Opens photos and videos other apps hand over, and registers as the phone's gallery.
- Updates from GitHub Releases.

## Installation

Download the APK from [Releases](https://github.com/NineTailsFlip/g9/releases/latest) and
install it. After that G9 checks for a new release once a day and offers it; Android asks you to
confirm each update. Settings → Updates turns the check off or runs it now.

## Usage

| Key | In the list | Full screen |
|---|---|---|
| Up / Down | Move | Previous / next item |
| Left / Right | Previous / next day | Video: seek 10 s; photo: pan |
| OK | Open full screen | Photo: zoom; video: play / pause |
| `#` | Mark the item | Details |
| `0` | Folders and trash | — |
| `*` | Menu: mark this day, share, trash, settings | Menu |

The full key map is in the app: Settings → About → Key mapping.

## Building

Requirements: JDK 17+, Android SDK (platform 34, build-tools 34).

```bash
git clone --recursive https://github.com/NineTailsFlip/g9.git
cd g9
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
```

The APK (about 3 MB) is `app/build/outputs/apk/debug/app-debug.apk`. Tests:
`./gradlew testDebugUnitTest`.

Releases are built by GitHub Actions from a `v<versionName>` tag — see
[RELEASING.md](https://github.com/NineTailsFlip/.github/blob/main/RELEASING.md).

## Project structure

| Path | What's there |
|---|---|
| `app/` | G9 itself (`dev.g9`): the list (`BrowseActivity`), folders, the full-screen viewer, sharing and the trash (`Actions`) |
| `nine-core/` | Git submodule: [nine-core](https://github.com/NineTailsFlip/nine-core) — the Nine suite's `core/`, the gallery `kit/` shared with TG9, and `update/`. After a change there: `git -C nine-core pull` and commit the pointer |

## Requirements and limitations

- Android 14+ (minSdk 34), as the Nine suite.
- Not done yet: rotating or editing photos, setting a wallpaper, search.

## Contributing

Issues and pull requests are welcome; see
[CONTRIBUTING](https://github.com/NineTailsFlip/.github/blob/main/CONTRIBUTING.md).

## License

GPL-3.0-or-later — see [LICENSE](LICENSE). It includes the Nine suite's `core/`, which is
GPL-3.0.
