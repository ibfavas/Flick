# Flick

Draw a gesture to launch apps, links, and phone actions. Flick is a fully
local Android app: no backend, no accounts, no data leaves the phone.

## Features

- **Gesture pad** — draw a gesture on a full-screen canvas. Flick recognises
  it with a from-scratch implementation of the $N multi-stroke recognizer
  (resample to 64 points, rotate to the indicative angle, scale, translate,
  path-distance scoring, matched across stroke orderings), generalising the
  $1 Unistroke Recognizer. Draw a circle, lift, and draw a line to make a
  Q: multi-stroke gestures work, and single-stroke gestures work exactly as
  before. No ML model, no dependency.
- **Gesture library** — record gestures, give them names, attach actions,
  edit, delete, and test them from the draw pad. Stored locally in a
  SQLite database on the phone (see the note in `data/Gesture.kt`: v1.0.0
  was built without Gradle, so Room's annotation processing was replaced
  with a plain SQLiteOpenHelper holding the same table and API).
  Templates are stored as strokes separated by `|`, points as `x,y`;
  single-stroke templates from earlier versions load back as one stroke.
- **Actions** — launch an app, open a URL or deep link, flashlight on/off,
  open the camera, media play/pause.
- **Invisible widget** — a fully transparent home-screen widget. Tap it and
  a transparent gesture pad opens; draw, and the matched action runs.
- **Quick Settings tile** — the Flick tile opens the same gesture pad from
  anywhere.
- **Settings** — stroke colour and width, stored in DataStore Preferences.

## How the invisible widget works

Android widgets only support taps, not stroke tracking: a widget cannot
receive finger-movement data. So the widget itself is just a transparent
tap target. Tapping it launches a transparent full-screen activity, and
the drawing happens there. In daily use it feels like drawing on the home
screen, but the drawing technically happens in that transparent activity,
not inside the widget.

## Build

Requirements: JDK 17 and the Android SDK (platform-tools,
`platforms;android-35`, `build-tools;35.0.0`).

### Standard build (Gradle)

```sh
export ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug
```

For a signed release build, pass the keystore details as Gradle
properties (never commit them):

```sh
./gradlew assembleRelease \
  -PflickStoreFile=$HOME/.config/flick/flick-release.keystore \
  -PflickStorePassword=... \
  -PflickKeyAlias=flick \
  -PflickKeyPassword=...
```

### Daemon-free manual build (used for v1.0.0)

v1.0.0 was built without Gradle because the build sandbox blocks loopback
TCP, which Gradle's daemon protocol needs (every Gradle invocation fails
with "Could not receive a message from the daemon"). The manual pipeline
uses only local tools, so it works anywhere:

```sh
# One-time: fetch the dependency jars (Compose, DataStore, coroutines)
python3 tools/resolve_deps.py tools/seeds.txt build/deps

# Build, sign and verify the release APK
bash tools/build-manual.sh
# Output: build/flick-release.apk
```

`tools/build-manual.sh` runs aapt2 (compile + link, generating `R.java`),
kotlinc 2.0.21 with the Compose compiler plugin, d8, zipalign and
apksigner. It reads the keystore passwords from
`~/.config/flick/keystore.properties` (never committed). If you build on a
machine where Gradle works, prefer the standard Gradle build; the manual
script exists as a fallback.

## Release signing

The release keystore is **not** in this repo. Keep it safe: every future
release must be signed with the same keystore, or Android will refuse to
update the installed app.

## Roadmap (not in v1)

- Multi-step Flows with conditions
- AccessibilityService-backed actions (optional, with disclosure)
- Gesture import/export
- Stroke styling (glow, dashed lines)

Done in v1.0.5: multi-stroke gestures ($N recognizer), app-picker crash
fix (dedupe by package), minimal dark UI redesign (gesture list + floating
add button, no bottom tabs).

Done in v1.0.6: the Flick brand (logo: minimal white swoosh ending in a
cyan dot on deep teal; wordmark: rounded lowercase "flick" with the dot of
the i in cyan; deep teal theme with a cyan accent), a transparent touch
pad (see-through overlay with only a very light dim, stroke outlined so
it reads over any background, close and settings buttons), smooth
animations (screen transitions, list item enter/move, press feedback,
pad fade in/out, animated settings), and optimization (cached stroke
paths in the pad, lazy lists, stable state). The launcher icon is the
new logo.

## Originality

Flick is 100% original code written for this project. It shares a concept
with other gesture launchers (draw to launch) but copies no code, assets,
branding, or resources from any other app.

## License

Flick is licensed under the Apache License 2.0. See [LICENSE](LICENSE)
for the full text.
