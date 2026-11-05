# Development progress

Last updated: 2026-11-05 · App version **1.0.0** (versionCode 4) · APKs `release/PS2BluetoothGamepad-1.0.0-{release,debug}.apk`

## Status

| Phase | Code | Automated tests | Hardware verification |
|---|---|---|---|
| 1. Bluetooth HID prototype | Done | Passing | **Pending: your POCO F6 + Windows tests** |
| 2. PS2 touch controller | Done | Passing | **Pending: your joy.cpl + PCSX2 tests** |
| 3. Customization, profiles, performance | Done (0.3.0) | Passing | **Pending: your tests** |
| 4. Audit, bug fixing, release | Done (1.0.0 RC) | 181 tests: 179 passed, 2 skipped | **Pending: your tests (TEST_REPORT.md §4)** |

**Order of work.** Phase 1's own instructions said to wait for real-world results before Phase 2.
You asked for Phase 2 to start once Phase 1 was complete, so Phase 2 was built on top of Phase 1
**before** any hardware confirmation. Neither phase meets its acceptance criteria until your tests
pass. The Phase 1 screen (status, register, pairing, Test Buttons 1–3, log) is unchanged inside
the same APK, so all Phase 1 tests can run with this build.

**Hardware dependency.** Everything depends on the POCO F6 accepting `registerApp()`. If Phase 1
Test 2 fails, stop: Phase 2 cannot work on that phone (see BLUETOOTH_SETUP.md §1).

## Phase 1: implemented

- Bluetooth adapter detection and live on/off tracking (`ACTION_STATE_CHANGED`).
- Version-specific permissions: `BLUETOOTH_CONNECT` + `BLUETOOTH_ADVERTISE` on Android 12+;
  legacy `BLUETOOTH` (maxSdk 30). No location, no scan.
- `getProfileProxy(HID_DEVICE)` → `registerApp()` with custom SDP settings (subclass = gamepad)
  and descriptor → waits for `onAppStatusChanged`. Timeouts: 5 s for the proxy, 10 s for the callback.
- Connection callbacks drive the state. **"Connected" appears only after `STATE_CONNECTED` from the stack.**
- Unregister, disconnect, connect-to-paired-host, make discoverable, reconnect to the "plugged" host.
- `onGetReport` answers with the current report. `SET_REPORT` is rejected as unsupported.
- Cleanup: the ViewModel's `onCleared` disconnects, unregisters, closes the proxy and stops threads.
- Diagnostic screen: Test Buttons 1–3 (toggle HID buttons 1–3), Release All, last report hex, log.
- Compatibility card: manufacturer, model, Android version, API level, build.

### HID descriptor decision
A single descriptor covers both phases (12 buttons + hat + X/Y/Z/Rz, report ID 1, 6 bytes).
Windows caches the descriptor at pairing, so defining it once avoids a forced re-pair between
phases. Phase 1 uses buttons 1–3. The full layout is in README.md and documented byte by byte
in `HidDescriptor.kt`.

## Phase 2: implemented

- **Controls (16 inputs):** D-pad ×4 (one control, 8-way with diagonals, sliding), Triangle,
  Circle, Cross, Square (one cluster with slight hit overlap for thumb-rolling), L1, L2, R1, R2,
  Start, Select, L3, R3. Plus two analog sticks.
- **`ControllerInputManager`:** the single source of truth for controller state. It changes state
  under a lock, sends at most one report per change, skips duplicates, and `releaseAll()` sends a
  neutral report and invalidates every active touch through a reset epoch.
- **Multi-touch:** `trackPointers` gives each control only the fingers that went down on it
  (pointer-ID tracking, no shared touch state). Cancel, removal and reset all release that control.
- **Sticks:** fixed or floating centre, radial dead zone with rescaling (no jump at its edge),
  sensitivity multiplier, saturation outside the boundary, exact (0,0) on release, first finger
  owns the stick. Sticks are independent, so both can move at once.
- **Report pacing:** the sender thread sends at most one report every 4 ms (~250/s). Axis-only
  updates are merged into the newest. Button and hat changes are never merged, so quick taps are not lost.
- **Interruption safety:** inputs are released on pause, stop, window focus loss (notification
  shade, dialogs), screen change, back press, Bluetooth off and connection loss.
- **Connection loss:** reports stop (dropped while disconnected), touches clear, state resets,
  a red banner appears, and the app auto-reconnects to the last host (3 attempts: 2 s, 5 s, 10 s).
  There is no auto-reconnect after a user disconnect.
- **Registration dropped by Android** (app in background / dialog): the app registers again on
  return to the foreground, at most 3 automatic attempts in a row.
- **Full-screen landscape:** immersive (swipe reveals bars), keeps the screen on, display-cutout
  safe, system-gesture exclusion over the controls, back must be pressed twice to leave.
- **Responsive layout:** unit `u = min(height, width/2.4)`, clusters anchored to the screen edges.
- **Diagnostic screen:** every button lamp, D-pad, both stick values, connection, last report,
  active pointer count, measured send rate and in-app latency, Reset All Inputs, and stick
  tuning (dead zone, sensitivity, floating/fixed).
- **Live overlay** on the controller ("Live" chip) to watch the state while playing.

## Verified in the Android emulator (Pixel 9a image, Android 16), not the POCO F6

- App installs and launches with no crash. Permission state is detected.
- On the emulator's virtual Bluetooth stack: proxy connected, `registerApp()` accepted,
  `onAppStatusChanged(registered=true)`, status shows *Waiting for pairing*.
- Controller layout renders without overlaps at 2424×1080 (screenshots in `docs/screenshots/`).
- Not possible on the emulator: pairing with Windows, joy.cpl, touch/multi-touch checks
  (System UI kept hanging under software rendering), the diagnostic screen screenshot.
- During the first emulator session the registration dropped after 1.2 s while the emulator's
  screen went black (the system dropped it, not the app). This led to the foreground
  auto-re-register logic.

## Known limitations

- The app must stay in the foreground: Android unregisters background HID apps.
  A foreground service would remove this limit and is a possible Phase 3 item.
- L2/R2 are digital only. Sticks are 8-bit with a circular range (diagonal ≈ ±90/axis).
- In-app latency covers touch → `sendReport()` only. Radio and Windows latency are not measurable on the phone.
- Stick settings are kept in memory only (reset on app restart).
- No vibration, no button remapping, no profiles (out of scope for Phase 2).

## Remaining work

1. Run the manual checklist in TEST_REPORT.md on the POCO F6 + Windows 11 and send back the results.
2. Fix anything those tests reveal (mapping, layout size on the F6, latency).
3. Phase 3: only after you confirm.


## Phase 3: implemented (0.3.0)

Missing before Phase 3 (from inspecting the code): only dead zone / sensitivity / floating, kept in
memory and shared by both sticks; no persistence; no profiles; fixed layout; no dashboard; no
connection duration or history.

- **Persistence:** Jetpack DataStore (Preferences) holds one JSON document (kotlinx-serialization).
  Saves are debounced (400 ms) and flushed in `onStop`. Corrupt or old data is repaired, not trusted.
  Edits made before loading finishes are replayed on top of the saved data.
- **Profiles:** Standard PS2, God of War, God of War II, GTA San Andreas, Need for Speed Most Wanted,
  Custom Profile, plus user profiles. You can create, rename, duplicate, delete (custom only),
  switch, or restore defaults. Each profile stores its layout, look and both sticks' analog settings.
  A profile has no button-mapping field (a regression test enforces this), so the HID mapping cannot change.
  The game profiles are tuning starting points and are **not verified in the games**.
- **Customization (live):** button size, stick size, button opacity, controller opacity,
  face-button spacing, background (DualShock body / dark / black / blue), labels on/off,
  haptics on/off (key tick on press only, never delaying a release), reset look, reset layout.
  A preview shows exactly what the controller will draw.
- **Layout editor:** "Edit" on the controller. Controls are drawn non-interactive, so no HID input is
  possible. Drag to move, tap and use the slider to resize (50–200 %). Drops and resizes that would
  overlap are refused (snap back), and controls are kept on screen. Save, Cancel, Defaults.
  Positions are stored as screen fractions, so layouts adapt to other screen sizes.
- **Advanced analog (per stick):** sensitivity, dead zone, max range (outer saturation),
  response curve (0.6–3), invert Y, fixed/floating, centre calibration for fixed mode.
  **Calibration screen** with a live X/Y visualizer (raw finger vs value sent, dead-zone and
  max-range rings, live HID values).
- **Dashboard (new home):** Bluetooth and controller status, registration, connected host,
  connection duration, last successful connection (persisted), active profile, phone battery,
  and quick access to Controller, Connection, Customize, Profiles, Diagnostics, Calibration.
- **Connection management:** Connected-for timer, Disconnect, **Reconnect** (to the last host,
  also after an app restart), clear error messages. Auto-reconnect after an unexpected drop is
  still *attempted* (3 tries), but it is **not verified** on hardware and not promised.
- **Performance measurement (real, on the phone):** report generation time (avg/max µs), app CPU %
  from process CPU time, heap, controller session duration and battery change, plus the Phase 2
  send rate and touch→sendReport time. Also a repeatable JVM micro-benchmark (desktop, not phone).

### Bugs found and fixed in Phase 3
| Bug | Found by | Fix |
|---|---|---|
| Phase 2 default layout: right stick's touch area overlapped the face cluster by 0.02u | New layout overlap test | Sticks anchored to the cluster edge |
| Sticks slid into the D-pad / face buttons, and R3 hit the face cluster, at uneven button/stick scales | Layout test | Sticks anchored to the cluster edge; L3/R3 moved above the sticks |
| Response curve < 0.6 made a 1 % movement past the dead zone jump to ~17 % output | Analog test | Minimum curve 0.6 |
| Layout editor lost a drag when the drag ended before a recomposition (stale state) | UI test | Gesture callbacks read the state object directly |
| New/duplicated profile was added off-screen at the bottom of the list | UI test | List scrolls to the active profile |
| Saved settings could be overwritten by an edit made before loading finished | Code review | Pending edits are replayed onto loaded data |

### Known limitations (Phase 3)
- The app still has to be in the foreground (Android drops background HID registrations).
- The profile name dialog could not be UI-tested on the JVM (Robolectric cannot idle with a text
  field in a dialog). Its logic is unit-tested, and the dialog is on the manual checklist.
- Game profiles are unverified starting points.
- Battery and CPU figures need a real session on the phone. None are reported yet.


## Phase 4: audit, testing, release (1.0.0)

- **Independent code audit** (separate reviewer, read-only) of all sources, manifest and Gradle files:
  2 high, 4 medium and 6 low findings. All high and medium findings are fixed; low ones are fixed or
  documented (BUG_REPORT.md B12–B23, O6).
- **Test suite grown to 181:**
  - Robolectric UI tests on the real Compose screens: every button, D-pad, both sticks, 5-finger
    multi-touch, edit mode, customization, profiles, calibration, error states.
  - Lifecycle integration with the real Activity and DataStore.
  - Stress tests, including concurrent threads.
  - A JVM benchmark.
  - Mocked and simulated layers are labelled in TEST_REPORT.md §1.
- **Release build:** signing config from git-ignored `keystore.properties`, R8 + resource shrinking,
  keep rules for persisted settings. Verified on the emulator: launch, HID registration, settings
  persistence after force-stop, all in the **minified release** build. Exported components and
  permissions were checked with aapt2. APK signature v2 verified.
- **Security/privacy:** minimal permissions (CONNECT, ADVERTISE; legacy BLUETOOTH ≤ API 30); only the
  launcher activity is exported (plus AndroidX's DUMP-protected profile receiver); the dynamic
  receiver is NOT_EXPORTED; no logcat in release; no backup/transfer of settings.
- **Documentation:** README, INSTALLATION, BLUETOOTH_SETUP, PCSX2_CONFIGURATION, TEST_REPORT,
  BUG_REPORT, RELEASE_NOTES, DEVELOPMENT_PROGRESS.

## Remaining work (needs you / the hardware)

1. TEST_REPORT.md §4: every hardware test (connection, joy.cpl, earbuds, PCSX2, 5 games, 30/60 min).
2. Record the on-phone measurements (TEST_REPORT.md §3.2) and optionally the 240 fps end-to-end latency (§3.3).
3. Report failures with the app's on-screen log (Connection screen) or a debug-APK logcat.
4. Possible future improvements:
   - A foreground service, so HID survives the lock screen.
   - Analog L2/R2 and rumble (both need a descriptor change and a re-pair).
   - Persisting the diagnostic log.
   - An instrumented on-device test suite.
