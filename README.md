# Flick

Draw a gesture to open an app, visit a webpage, call someone or run a phone action. Flick recognises gestures on your device. It runs on Android 8.0 and later.

[![Download Flick for Android](assets/download-button.svg)](https://github.com/ibfavas/Flick/releases/latest/download/flick.apk)

**Version 2.0.0** · [Release notes and previous APKs](https://github.com/ibfavas/Flick/releases)

## Your first gesture

1. On Home, choose **Apps**, **Actions**, **Webpages** or **People**, then tap **+**.
2. Draw a gesture, name it and choose its target. You can record up to three samples. If the shape resembles another gesture, Flick lets you redraw it or keep it.
3. Add a Flick widget or the Quick Settings tile. Tap it to open the pad, then draw. When Flick recognises the gesture, the target’s icon and name appear at the top before it opens.

## What’s new in 2.0

- **Home and Gestures.** Home shows library and activity counts. Each gesture row shows the shape, target and sample count. The Bind gesture screen shows all three sample slots above the redraw and add-sample controls. Tap an empty slot to record another example. Tap play on a library row to test it. Long-press the row or open its menu to edit, test, duplicate or delete. Use **Undo** to recover a deletion.
- **Stats and History.** See how often each gesture runs, sort by **Most used**, or check recent launches and their times.
- **People.** Bind a gesture to a specific contact. Use their contact photo or choose your own. New call gestures live in People. Existing WhatsApp bindings remain available under Actions. Flows have been removed.
- **Drawing pads.** In **Settings → Overlay**, turn on **Drawing pad style** for a floating card or turn it off for full screen. Both pads use rounded corner guides and a faint dotted centre that disappear on touch. The stroke is a clean line in your accent colour. A match shows “Launching” and the target name while an accent-coloured wave sweeps up the pad for 650 ms. A miss flashes red and clears for a retry. A second miss closes the pad. Success and failure have distinct haptics when feedback is enabled.
- **Recognition.** A strictness slider controls how closely a drawing must match. Successful uses give familiar gestures a small allowance on near misses. The two-pass matcher and its bounded DTW rescue still handle single- and multi-stroke drawings.
- **Appearance and controls.** Flick uses a dark theme, a lime default accent and a new launcher icon. The accent colours the controls, gesture previews, pad and wordmark dot. Choose Lime, Emerald, Cyan, Sapphire, Amethyst, Ruby, Copper or Champagne Gold. Your selection applies to recording, testing and launching gestures. Settings groups permissions, overlay controls, general preferences and widgets. Adjust background blur, opacity, idle timeout and stroke width in Overlay. The support link is on Home and below the Widgets section in Settings.
- **Widgets.** Choose the round 1×1 shortcut, the invisible 2×1 tap area or the wide 3×1 widget. On the wide widget, **Draw a gesture** opens the pad and the expand button opens Flick.
- **Backups.** Enable **Auto backup** to include the library in Android backup and restore. For a portable copy, export JSON from Settings. Import restores gesture targets, contact thumbnails, samples and enabled states. It skips duplicate names and reports invalid entries.

## Phone actions

Gestures can control the flashlight or media playback, open the camera, lock the screen, take a screenshot, open Wi-Fi or Bluetooth settings, or toggle Do Not Disturb. Some actions need additional Android permissions. The Permissions section shows which ones are enabled.

## Build

Use JDK 17 and Android SDK platform 35 with Build Tools 35.0.0. For a Gradle debug build, run `./gradlew assembleDebug`. Gradle release signing accepts the project properties `flickStoreFile`, `flickStorePassword`, `flickKeyAlias` and `flickKeyPassword`.

The release APK can also be built with [the manual build script](tools/build-manual.sh). It uses Kotlin CLI 2.0.21, the matching Compose compiler plugin, aapt2, d8 and apksigner. With the toolchain, resolved dependencies and signing configuration in place, run:

```sh
tools/build-manual.sh
```

The signed APK is written to `build/flick-release.apk`. Package `com.ibfavas.flick`, version code 26, minimum SDK 26, target SDK 35.

[Report an issue](https://github.com/ibfavas/Flick/issues) · [Apache License 2.0](LICENSE)
