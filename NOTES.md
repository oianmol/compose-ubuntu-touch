# Notes: Compose on Ubuntu Touch (spike log)

Question: can Compose Multiplatform (Desktop/JVM) draw a phone UI here?

Setup: Temurin 17 (aarch64) unpacked in `/home/phablet/compose-spike/`, jar built on the Mac with
`gradle fatJar` (Compose 1.8.0, Kotlin 2.1.20, `compose.desktop.linux_arm64`). Run under Lomiri's
Xwayland (`DISPLAY=:0`).

Run: `DISPLAY=:0 XDG_RUNTIME_DIR=/run/user/32011 ./jdk-17*/bin/java -Dskiko.renderApi=SOFTWARE -jar compose-spike.jar`

Results (2026-11-22):
- JDK and the Skiko arm64 library load and a window opens: yes.
- `SOFTWARE` (CPU Skia): ~67 fps frame clock with 12 animated circles. Visible window and touch not yet confirmed by eye.
- `OPENGL`: fails at start-up with
  `Failed to load hybris linker for Android SDK version 29 ... q.so: cannot allocate memory in static TLS block`.
  The JVM loads the system libEGL (libhybris) too late for its static TLS. `LD_PRELOAD` of libhybris-common,
  libEGL/libGLESv2 and `__GLX_VENDOR_LIBRARY_NAME=mesa` did not help. Preloading `q.so` itself printed nothing in 15 s.

Open: GPU rendering, multi-touch (AWT gives mouse events only), whether Compose can be a compositor (it cannot; it is an Xwayland client).

Update (2026-11-22, later): window now sized to the Xwayland screen (1080x2400, undecorated, at 0,0).
Maximized placement was ignored and gave a small top-left window. Visible and confirmed working by eye.
Full-screen CPU rendering: ~50 fps (21-52 during warm-up) vs ~67 fps in the small window, so CPU Skia is
already below 60 with a trivial scene.

GPU attempt (2026-11-22): the static TLS error is fixed with `GLIBC_TUNABLES=glibc.rtld.optional_static_tls=131072`
(4 MB is too much: the JVM cannot create threads; 128 KB to 1 MB all work). OpenGL still fails afterwards:
`glx: failed to create drisw screen` then `RenderException: Cannot create Linux GL context`, plus a harmless
`libadreno_app_profiles.so not found`. Skiko on Linux uses GLX through Xwayland, and Xwayland on this stack has no
accelerated GLX (the GPU is only reachable through libhybris EGL). Getting Skia onto the Adreno would need a native EGL
surface on a Wayland window, which Skiko's public API does not offer.

Keyboard / scaling / scrolling (2026-11-22):
- Maliit (Lomiri keyboard) runs but AWT/Xwayland has no path to it, so the spike draws its own keyboard (Osk.kt, append/backspace only).
- Density: window is 1:1 pixels, so content uses Density(21/8) (kebab.yaml GridUnit 21 px, 8 dp per grid unit).
- AWT turns touch into mouse; lists do not scroll by drag, so the list uses detectVerticalDragGestures + scrollBy (no fling).
- Wayland toolkit experiment: JBR 21.0.11 (jbr-21.0.11-linux-aarch64-b1163.125, sha512 verified) includes WLToolkit, but with
  `-Dawt.toolkit.name=WLToolkit WAYLAND_DISPLAY=wayland-0` it fails at start-up:
  `AWTError: Can't bind to the wp_viewporter interface`. Lomiri's Mir 2.9 compositor does not offer wp_viewporter, which
  WLToolkit requires. Blocked until Mir offers wp_viewporter or a workaround is found (planned to revisit). JBR removed from the phone.

## Working design: Qt host + headless Compose (2026-11-22)

`Host.kt` renders Compose with `ImageComposeScene` (no window) and writes BGRA frames to
`/run/user/32011/compose-host/frame.bin` (two buffers, slot = seq % 2). `host/host.qml` (run by `qmlscene` on
ubuntumirclient) shows them through `host/bridge.py` (PyOtherSide image provider). Touch, text and keyboard height flow
back over a Unix socket (`input.sock`). A hidden Qt `TextInput` receives Ubuntu Touch's keyboard (Maliit).

Run: build with `gradle fatJar`, push `build/libs/compose-spike.jar` and `host/` to `/home/phablet/compose-spike/`,
`chmod +x host/run-host.sh`, start "Compose Host" (desktop entry) or `host/run-host.sh`; log in `compose-spike/host.log`.

Measured: renderer ~55 fps (render 4 ms, copy ~10 ms); Qt shows ~25 fps (10 MB copy per frame through Python).
Lomiri gives the app a 1080x2277 window, so `H = 2277`. Keyboard reports its own height (817 px) and slides up.
Open: partial updates, a real text field (selection, cursor placement), packaging as a click.

## Partial frame updates (2026-11-22)

The frame is cut into 64-row bands. `Host.kt` compares each band with the previous frame (zero-copy view of Skia's pixels
through JNA) and rewrites only changed bands, each with its own sequence number in the file header (band table at offset
24, pixels from 4096, single buffer). `bridge.py` announces changed bands; `host.qml` has two image layers per band and
queues the newest update for a band that is still loading, so nothing blanks or goes stale.

Measured (headless renderer, 8T): idle screen sends nothing (0 bands/frame); copy cost 10 ms -> 2-4 ms; scrolling
changes 5-21 of 36 bands per frame, Qt loads up to ~1170 bands/s (about 30 full frames/s). Render time rose from ~4 to
~10 ms (not investigated).

A first run of this build was followed by a hard reset of the phone (journal ends at 16:38:05, boot reason
`startupmode=hard_reset`, no kernel log kept). It did not reproduce in two later runs with `adb shell dmesg -w` streaming
to the Mac, and the old build was also fine, so the cause is unknown.

## Real Compose text fields and the memory leak (2026-11-22)

The renderer now uses `CanvasLayersComposeScene` with a custom `PlatformContext` (`Host.kt`): its `textInputSession` hands the
focused field's `PlatformTextInputMethodRequest` to `Ime`, which shows Ubuntu Touch's keyboard (`show`/`hide` over the socket)
and feeds what Qt types back as `DeleteAll` + `CommitText` + `SetSelection` edit commands. Enter calls `onImeAction`; closing
the keyboard clears Compose focus. A stock `OutlinedTextField` works; verified on the 8T (keyboard appears, typing works).
Not done: selection handles, copy/paste, composing text (predictive input is off), multi-line Enter.

**Memory leak (caused hard resets of the phone):** the earlier `ImageComposeScene.render()` returned a new Skia `Image` per
frame (10 MB native) that was never closed; the JVM grew to ~9 GB, the kernel hit low memory (`lowmem_dbg` in `dmesg -w`),
the modem crashed and the phone reset. Now one reusable `Surface` is rendered into, RSS stays ~130-160 MB, the renderer exits
if RSS passes 1500 MB, and `run-host.sh` caps the heap (`-Xmx512m`). Stream `adb shell dmesg -w` to the Mac while testing.

## Running an existing app: Cupcake (2026-11-22)

`runOnUbuntuTouch { App() }` (Host.kt) runs any Compose content through the Qt host. `apps/cupcake/` is JetBrains' `nav_cupcake`
example (tag v1.8.0, Apache-2.0) with its common source and resources copied unchanged; only `main.kt` is ours. Build:
`gradle :jar :cupcake:fatJar`, push `apps/cupcake/build/libs/cupcake.jar`, then `host/run-host.sh cupcake.jar MainKt`
(or `host/test-run.sh cupcake.jar MainKt 20` for a guarded run). A desktop entry on the phone points at the first form.

What the app needed from the host: a `ViewModelStoreOwner` and a RESUMED `LifecycleOwner` (provided in `WithAppOwners`).
Its navigation library pulled Compose to 1.8.1, which moved the text-input hook from `textInputSession` to
`PlatformContext.startInputMethod`, so the plugin is now 1.8.1 and the hook follows that. Expect such internal-API drift.

20 s guarded run on the 8T: no exception, RSS 139-145 MB, 64 fps. UI on screen not yet checked by eye at commit time.
Gaps: system back gesture, status-bar/safe-area insets, window size fixed at 1080x2277, no selection/copy-paste.
