# G9 — Gallery on 9 keys

A gallery for Android phones with a physical keypad/D-pad and no touchscreen. It uses the look and
the keys of the [Nine suite](https://git.sr.ht/~timofurrer/nine) (L9, C9, P9, M9): one column on
black, a numbered `*` menu, and a hint bar at the bottom. It is a sister app of
[TG9](https://github.com/NineTailsFlip/tg9), Telegram on 9 keys.

## What works

- Every photo and video, newest first under day headers. Each row shows a thumbnail, the time,
  and the folder, size and dimensions. The list follows MediaStore live, so a photo you just took
  shows up without leaving the screen.
- `0` opens the folders (Camera, Screenshots, Download, TG9...), most recently added-to first,
  then the trash.
- Full screen: Up/Down step through the list. A photo zooms with OK and pans with the D-pad.
  A video plays with OK and seeks 10 s with Left/Right. `#` shows the details: name, date, size,
  folder, path. GIFs and animated WebP play.
- `#` in a list marks items, and `*` → Mark this day marks a whole day. `*` shares them or
  moves them to the trash.
- Nothing is deleted outright. Items go to Android's trash, which keeps them for 30 days, and
  Android asks for confirmation every time. From the trash you can restore them or delete them
  for good.
- G9 opens photos and videos that other apps hand over, and it registers as the phone's gallery
  app.

Keys are listed in the app itself: Settings → About → Key mapping.

## Building

You need JDK 17+ and the Android SDK (platform 34, build-tools 34).

```bash
git clone --recursive https://github.com/NineTailsFlip/g9.git
cd g9
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`, about 3 MB. Tests:
`./gradlew testDebugUnitTest`.

## Layout

- `app/` — G9 itself (`dev.g9`): the list (`BrowseActivity`), folders, the full-screen viewer,
  and sharing and the trash (`Actions`).
- `nine-core/` — a git submodule ([nine-core](https://github.com/NineTailsFlip/nine-core)):
  the Nine suite's `core/` and the gallery `kit/` shared with TG9. After a change there, update
  the pointer with `git -C nine-core pull` and commit it.

## Requirements and limits

- Android 14+ (minSdk 34), the same as the Nine suite.
- Not done yet: rotating or editing photos, setting a wallpaper, search.

## License

GPL-3.0-or-later (see `LICENSE`), because it includes the Nine suite's `core/`, which is GPL-3.0.
