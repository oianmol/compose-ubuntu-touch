# Compose Multiplatform on Ubuntu Touch

Run a Kotlin Compose Multiplatform app on an Ubuntu Touch phone (Lomiri, Mir). Compose can't be a Wayland client
there, so a headless Compose renderer (JVM, Skia `CanvasLayersComposeScene`) writes frames to a shared file and a small
Qt/QML host (`qmlscene`) shows them, forwards touch, and brings up Ubuntu Touch's own keyboard for text fields.

Status: proof of concept, tested on a OnePlus 8T (Ubuntu Touch 24.04, aarch64). Roughly 60 fps render, idle screen costs
nothing, memory ~130-180 MB. Details, dead ends and measurements are in [NOTES.md](NOTES.md).

## Demo

[Watch the playground running on a OnePlus 8T](docs/playground-walkthrough.mp4) (49 s, app content only, no keyboard).

## Layout

- `src/main/kotlin/` — the host: `runOnUbuntuTouch { App() }`, input method bridge, touch, frame bands
- `host/` — Qt side (`host.qml`, `bridge.py`) and run scripts that go on the phone
- `apps/playground/` — chooser home screen with demos: sign-up form, the cupcake app, animations, multi-touch ink, platform info.
  Add a `Demo(...)` to the `demos` list in `PlaygroundApp.kt` to add your own
- `apps/cupcake/` — a **Kotlin Multiplatform** sample (JetBrains' `nav_cupcake`, Apache-2.0)
  - `commonMain/` shared UI, resources, and `expect fun platformName()`
  - `jvmMain/` the `actual`, plus `main.kt` that calls `runOnUbuntuTouch`

`expect`/`actual` work: `Platform.kt` (common) declares it, `Platform.jvm.kt` supplies the JVM actual.

## Try it

Needs a Mac/Linux box with Gradle and JDK 17, and an Ubuntu Touch device with `adb` access, `qmlscene`,
PyOtherSide and a Temurin 17 aarch64 JDK in `/home/phablet/compose-spike/`.

```sh
gradle :jar :playground:fatJar          # or :cupcake:fatJar for the single sample
adb push apps/playground/build/libs/playground.jar /home/phablet/compose-spike/
adb push host /home/phablet/compose-spike/
adb shell sh /home/phablet/compose-spike/host/test-run.sh playground.jar PlaygroundMainKt 20   # guarded 20 s run
```

To get a launcher icon, copy `host/desktop/compose-playground.desktop` to `~/.local/share/applications/` on the phone. Or use `host/run-host.sh playground.jar PlaygroundMainKt` for a normal run. Paths such as `/run/user/32011` and the 1080x2277 window are
for the 8T and are hardcoded; adjust them for your device.

## Porting your own KMP app

Add a `jvm()` target, depend on the root project and `compose.desktop.linux_arm64` in `jvmMain`, and start it with
`runOnUbuntuTouch { YourApp() }`. Apps need a ViewModel store and a RESUMED lifecycle; the host provides both.

## Recording the screen

Ubuntu Touch has no screen recorder we can use over `adb`, so the host can record its own Compose content (not the keyboard
or status bar): `touch /home/phablet/compose-spike/record.on`, launch the app, then `adb pull .../compose-spike/rec` and join the
timestamped JPEGs with ffmpeg (concat demuxer, durations from the file names). Remove `record.on` to stop.

## Known gaps

System back gesture, safe-area insets, fixed window size, selection/copy-paste, GPU rendering (CPU Skia only), and the
internal Compose APIs the host uses can change between versions (pinned to Compose 1.8.1 / Kotlin 2.1.20).
Watch `dmesg -w` while testing; an earlier native memory leak made the phone reset.

MIT licensed (see LICENSE); the cupcake sample keeps its own Apache-2.0 license.
