# Flick

Draw a lightning bolt to turn on the flashlight, an **M** to call Mum, or a spiral to open your music app. You choose what each shape means. Flick recognises your gestures on your Android device.

[![Download Flick for Android](assets/download-button.svg)](https://github.com/ibfavas/Flick/releases/latest/download/flick.apk)

**v2.0.0** · Android 8.0 and later · [Release notes and previous downloads](https://github.com/ibfavas/Flick/releases)

[🚀 Get started](#your-first-gesture) · [🎯 Explore features](#what-can-i-launch) · [✍️ Drawing pads](#drawing-pads) · [🧠 Recognition](#how-recognition-works) · [🎨 Customisation](#make-it-yours) · [📦 Backups](#back-up-your-gestures) · [❓ FAQ](#common-questions)

## Your first gesture

1. Open **Home → Apps**, tap **+** and draw a shape you can repeat comfortably.
2. Give it a name, pick an installed app and save it. One sample is enough to get started. You can add two more in the empty sample slots.
3. Add the Flick tile to Android's Quick Settings or place a Flick widget on your home screen. Tap it to open the drawing pad.
4. Draw the shape and lift your finger. The target icon and name appear at the top, an accent wave sweeps up the pad, and your app opens.

## What can I launch?

| Tab | What you can bind | A gesture you could try |
| --- | --- | --- |
| 📱 **Apps** | An installed app | Draw a spiral to open your music player. |
| ⚡ **Actions** | A phone control | Draw a lightning bolt to switch on the flashlight. |
| 🌐 **Webpages** | A specific URL | Draw a wave to open your local surf forecast. |
| 👤 **People** | A call to one person, with their contact photo or a custom icon | Draw an **M** to call Mum. |

<details>
<summary>⚙️ See the full list of phone actions</summary>

- **Flashlight and camera** · Turn the flashlight on, turn it off, or open the camera.
- **Media** · Play or pause playback.
- **Screen** · Lock the screen or take a screenshot with Flick's accessibility service.
- **Connectivity** · Open Android's Wi-Fi or Bluetooth settings.
- **Interruptions** · Toggle Do Not Disturb after granting Android's policy access.

</details>

## Drawing pads

Choose your pad in **Settings → Overlay → Drawing pad style**. Leave the switch off for **Full screen** to draw across the screen, or turn it on for **Card** to draw in a floating panel over the current app.

| While you draw | What happens |
| --- | --- |
| ✍️ Touch down | The corner guides and hint disappear. A clean stroke follows your finger in your accent colour. |
| ✅ Flick recognises the shape | A compact “Launching” pill shows the target. The wave animation runs for 650 ms before the action starts. |
| ↻ The first attempt misses | The stroke flashes red and clears. Draw again in the same pad. |
| ✕ The second attempt misses | The pad closes. You can also tap **X** to dismiss it yourself. |

**Haptic feedback** gives a match and a miss different vibration patterns. Switch it off in Settings to disable both vibrations.

## How recognition works

Flick compares your drawing with the samples saved for your enabled gestures. You can draw a shape in one continuous line or build it from several strokes. If you draw the same shape a little differently one-handed, save that version as a second or third sample.

**Similar shapes.** When you record a gesture that resembles an existing one, Flick warns you and lets you redraw or keep it. During recognition, a gesture must both clear the match threshold and beat the next-best gesture by a minimum margin.

**Strictness and learning.** Move **Settings → General → Match strictness** towards strict for a closer match, or towards loose for more variation. Over time, successful uses give a familiar gesture a small allowance on near misses. Your recorded samples stay available for you to review and edit.

<details>
<summary>🔬 Inside the two-pass matcher</summary>

1. **Prepare the shape.** Flick resamples the path to 96 points and normalises its size and position. It checks the drawing in both directions. Differences in orientation and stroke count can reduce the score.
2. **Compare complete paths.** The first pass compares corresponding points and also uses banded dynamic time warping (DTW). DTW lets a corner or curve line up even if it falls slightly earlier along one path.
3. **Retry an incomplete path.** If the first pass finds no match, open-ended DTW can skip up to 24 saved points at either end. Each skipped point carries a penalty. This can recover a gesture when you lift your finger a little early.
4. **Apply experience.** If ordinary recognition still misses, a previously successful gesture can qualify for a slightly lower threshold. The allowance grows over its first ten learned uses and then stops. The closest competing gesture still counts when checking ambiguity.

[Read the recogniser](app/src/main/java/com/ibfavas/flick/gesture/Recognizer.kt) · [Read the adaptive matching logic](app/src/main/java/com/ibfavas/flick/gesture/LibraryMatcher.kt)

</details>

## Your gesture library

| Page | What you'll find |
| --- | --- |
| 🏠 **Home** | Counts for saved gestures, distinct target apps and uses, plus the accessibility service status. |
| ✍️ **Gestures** | Browse the four tabs. Each row shows the drawn shape, target and sample count. Sort by creation order, A–Z or **Most used**. |
| 📊 **Stats** | See total launches and the count for each gesture, ranked by use. |
| 🕘 **History** | Look back at recent launches and their times. |

Tap a row's play button to test its gesture. Long-press the row or tap **⋮** to edit, delete, duplicate or test it. If you delete one by mistake, tap **Undo** while the message is visible.

## Make it yours

- 🎨 **Accent colour.** Choose Lime, Emerald, Cyan, Sapphire, Amethyst, Ruby, Copper or Champagne Gold. The accent carries through the dark interface, gesture strokes, widgets and the dot over the *i* in Flick.
- 🎚️ **Overlay controls.** Set background blur, opacity, idle timeout and stroke width. These controls sit together with the pad style switch.
- 📲 **Widgets and tile.** Use the round **1×1** shortcut, an invisible **2×1** tap area, or the wide **3×1** widget. On the wide widget, **Draw a gesture** opens the pad and the expand button opens Flick. The Quick Settings tile opens the pad from the notification shade.

## Back up your gestures

**Auto backup** includes your library in Android's backup and restore when backup is enabled on your device. For a copy you can save or move yourself, export the library as JSON from Settings.

An export includes gesture names, samples, targets, contact thumbnails and enabled states. Import merges the file into your library. It skips names you already have, ignoring case and surrounding spaces, and reports duplicate or invalid entries. Usage counts and launch history are outside the JSON export.

## Common questions

<details>
<summary>🟢 Why does Home say “Inactive”?</summary>

The indicator shows whether Android has connected Flick's optional accessibility service. **Lock screen** and **Take screenshot** use it. Apps, webpages and contact gestures work while the indicator is inactive. Enable it under **Settings → Permissions → Accessibility service** if you use those two actions.

</details>

<details>
<summary>✍️ Two gestures are easy to mix up. What can I change?</summary>

Try an open zigzag for one and a closed loop for the other. A visible difference in the overall shape gives the matcher more to work with than a tiny flourish at the end. If one gesture varies naturally, edit it and record another sample in an empty slot. Each gesture holds up to three samples.

</details>

<details>
<summary>📱 Where did contact gestures and Flows go in 2.0?</summary>

Create new direct calls in **People**. Existing WhatsApp bindings remain available under **Actions**. Version 2.0 removes Flows.

</details>

## Build Flick

<details>
<summary>🛠️ Local build instructions and Android versions</summary>

Use JDK 17, Android SDK platform 35 and Build Tools 35.0.0. The manual release script uses Kotlin CLI 2.0.21, the matching Compose compiler plugin, aapt2, d8 and apksigner. With the toolchain, dependencies and signing configuration in place, run:

```sh
tools/build-manual.sh
```

The signed APK is written to `build/flick-release.apk`. For a Gradle debug build, use `./gradlew assembleDebug`. Gradle release signing accepts `flickStoreFile`, `flickStorePassword`, `flickKeyAlias` and `flickKeyPassword`.

| Package | Version | Minimum SDK | Target SDK |
| --- | --- | --- | --- |
| `com.ibfavas.flick` | 2.0.0 (26) | 26 | 35 |

[Manual build script](tools/build-manual.sh)

</details>

[⬆ Back to top](#flick) · [Report an issue](https://github.com/ibfavas/Flick/issues) · [Apache License 2.0](LICENSE)
