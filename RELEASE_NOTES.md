# Release notes

## 1.6.0 (versionCode 10), 2026-11-06: one simple controller + in-controller settings

- **Single controller look:** the 5 styles (PS2 Classic, PS5, Xbox, Retro, Neon) and the Styles screen
  are removed. The controller always uses the simple PS2 button look (△ ○ ✕ □, L1/L2/R1/R2,
  START/SELECT). Saved settings from older versions still load (the old style value is ignored).
- **Settings button inside the controller:**
  - Gyro aiming on/off: ON turns the right joystick into the gyro automatically, with Recenter.
  - Opacity of all controls, size of all buttons, joystick size, and Reset.
  - Changes apply live and are saved to the loadout in use.
  - The panel blocks touches, so nothing behind it is pressed. It scrolls on short screens.
- Everything else is unchanged: loadouts, layout editor, per-control opacity in Customize, PC Mode,
  Bluetooth.
- Tests: 298 (296 passed, 2 skipped).

## 1.5.0 (versionCode 9), 2026-11-06: PC Gaming Mode

- **Mode selection** on Open Controller: Emulator Gaming (unchanged) or PC Gaming. The choice is remembered.
- **PC Gaming controller:** W/A/S/D stick, right-stick mouse, A/B/X/Y, LB/RB/LT/RT, Start/Select,
  Sprint/Crouch, left/right mouse, wheel ▲/▼, gyro pause, recenter. Defaults follow the spec table;
  everything is editable.
- **Keyboard and mouse over Bluetooth HID** (composite descriptor: gamepad unchanged + keyboard ID 2 +
  mouse ID 3). **Opt-in**, needs a one-time re-pair; emulator-only users keep the original descriptor.
- **Key handling:** transitions only (no repeats), reference-counted shared keys, Ctrl/Shift/Alt
  combinations (modifier down first, key up first), 6-key rollover. Everything is released on
  disconnect, mode switch, focus loss, background or edit mode.
- **Gyro mouse:** relative, from angular velocity, with orientation correction, bias calibration,
  dead zone, smoothing, X/Y sensitivity, inversion, rate limit, pause and recenter.
- **Custom buttons** (max 12 per profile) and **edit mode:** move, resize, opacity, rename, remap, delete,
  restore defaults (with confirmation).
- **PC profiles** (max 10): Generic PC Game, GTA V example, plus your own. Create, rename, duplicate,
  delete, restore, persist. Separate from emulator loadouts.
- **Fixed during development:** the mouse ticker woke the CPU every 8 ms even when idle (it now
  sleeps until the stick moves); a default PC layout overlap (Wheel ▲ / Start); the top bar covering
  LT/RT; the store-repair step could drop new fields (it now copies the stored data).
- Tests: 299 (297 passed, 2 skipped). **Not yet tested on Windows.**

## 1.4.0 (versionCode 8), 2026-11-05: loadouts

- **Loadouts: one saved setup per game, up to 5** (plus the built-in Default). Each loadout keeps
  its own button positions and sizes, opacity (all and per control), style, stick settings and gyro.
- **"Choose a loadout" every time you open the controller:** cards with a live preview, "LAST USED"
  badge, Play, and Delete (with confirmation).
- **Create:** type a name → *Create & arrange buttons*. It starts as a copy of your current setup and
  opens the controller in Edit mode; Save stores the layout in that loadout. Changes made later in
  Customize, Styles or Gyro are saved to the loadout in use.
- At 5 loadouts the "New loadout" card is hidden, and the Profiles screen's New/Duplicate are disabled
  ("Limit: 5 loadouts"). Deleting one frees a slot.
- **Fixed (found by tests):** a loadout named like a built-in preset ("God of War") became "God of War 2".
  Names now only need to be unique among your loadouts.
- Built on the existing profile system: same storage, same HID report, no re-pairing.
- Tests: 256 (254 passed, 2 skipped).

## 1.3.0 (versionCode 7), 2026-11-05: gyro right stick

- **360° gyroscope right stick.** Phone tilt drives the RIGHT analog stick continuously in every direction.
  It is position-based like a real stick: a held tilt keeps the stick displaced (the camera keeps
  turning), and returning to neutral centres it. It is not angular-velocity / mouse-like.
  - Sensor fusion: gyroscope (fast) + accelerometer (anchors "down"), so the neutral cannot drift.
    Accelerometer samples taken while shaking get less weight.
  - Tilt is measured relative to the calibrated hold. Any holding angle works (flat, 45°, upright).
  - Mapping reuses the stick maths: radial dead zone without a jump, sensitivity, max tilt angle,
    circular clamp (|v| ≤ 1, direction kept), smoothing, invert X/Y.
  - Recenter (instant) and Calibrate (1.5 s average). The neutral is saved and shared by all profiles.
  - Sensors run only on the controller (gyro on) or the gyro test screen, with the app visible.
    Otherwise they are off, and a centred right stick is sent.
  - Jitter filter: changes under 2 HID counts are not sent; centre and full deflection are always sent.
- **New Gyro screen:** live circle (the value in the HID report), X/Y, HID values, magnitude, direction,
  tilt angles, sensor status and rate, and all settings.
- **Controller:** with the gyro on, the touch right stick is replaced by a GYRO indicator (tap = recenter),
  plus a Recenter chip. The HID descriptor and report format are unchanged, so no re-pairing is needed.
- **Fixed (found by a screenshot):** a held tilt was not re-sent after an input reset (screen change, reconnect).
- Tests: 245 (243 passed, 2 skipped). Gyro maths, fusion, engine and ViewModel integration are tested
  with simulated sensors; nothing is verified on the phone yet.

## 1.2.0 (versionCode 6), 2026-11-05: premium styles

- **5 controller styles:** PS2 Classic, PS5 Style (two-tone white, CREATE/OPTIONS), Xbox Style
  (Y B A X, LB/LT/RB/RT, VIEW/MENU, LS/RS), Retro (coloured X A B Y), Neon (glowing outlines).
  New **Styles** screen with live previews. Styles only change the look: each position always
  sends the same HID button (the bottom face button is HID button 2 whether it shows ✕ or A).
- **Opacity per control** (D-pad, face buttons, every shoulder button, sticks, L3/R3, Start/Select),
  on top of the opacity for all controls. Set it in Customize, or for the selected control in Edit layout.
  A control can't go below 10 % (an invisible control gets hit by accident).
- **Premium home screen:** brand header, status card with the one action needed, live preview of your
  controller style, tiles for Styles / Customize / More.
- **Removed:** the testing Logs button and screen. "Restore my phone's original Bluetooth name" moved to More.
- Tests: 211 (209 passed, 2 skipped), including "every style sends the same HID buttons".

## 1.1.0 (versionCode 5), 2026-11-05: simple mode

After the first real test on the POCO F6 (HID registration works on Android 16):
- **New simple home screen:** one status in plain words and the one button needed now.
  Advanced screens are under "More".
- **Automatic setup:** opening the app (or turning Bluetooth on while it is open) registers the gamepad
  automatically, and connects to the known laptop 1.5 s later if the stack didn't already.
- **The PC sees "PS2 Gamepad":** the app renames the phone's Bluetooth name (the name Windows shows)
  and remembers the original. "Restore phone name" is on the Logs screen.
- **Logs button (testing):** full log with Copy / Clear. It is in its own file so it is easy to remove later.
- **Fixed:** earbuds were offered as a "host" to connect to; only computers are listed now.
- **Fixed (found by tests):** a pending auto-connect could fire again after a later disconnect.
- Tests: 201 (199 passed, 2 skipped).

## 1.0.0 (versionCode 4), 2026-11-05: release candidate

> **Release candidate, not a verified release.** All automated tests pass and the release build
> runs on the Android emulator, but nothing has been tested on the POCO F6, Windows 11 or PCSX2 yet.
> Run TEST_REPORT.md §4 before relying on it.

APKs (`release/`):
- `PS2BluetoothGamepad-1.0.0-release.apk`: signed, R8-shrunk, 2.5 MB
- `PS2BluetoothGamepad-1.0.0-debug.apk`: debug key, logcat output, 27 MB

### What's in it
- Bluetooth HID gamepad (Android 9+): 12 buttons, 8-way D-pad, two analog sticks; descriptor
  unchanged since Phase 1, so no re-pairing is needed.
- DualShock 2-style full-screen landscape controller with full multi-touch.
- Customization, layout editor, 6 built-in profiles + your own, per-stick analog settings,
  centre calibration with a live visualizer.
- Dashboard with connection timer, last connection, battery and active profile.
- Diagnostics with measured report generation time, send rate, touch→send time, CPU, heap and battery.

### Changes since 0.3.0 (Phase 4)
- **Fixed (critical):** a failed "release" report could leave a button held on the PC; it is now retried with the current state.
- **Fixed (critical):** Bluetooth profile proxies leaked on every Bluetooth off/on, and a late callback could crash the app after exit.
- **Fixed (high):** the stack reconnected to the PC after the user had tapped Disconnect.
- **Fixed (high):** a setting changed right after launch was lost if the app closed quickly.
- Fixed: the floating stick jumped when a second finger took over; layout-editor drafts could be reset.
- Security/privacy: no cloud backup or device transfer of settings (they contain the PC's Bluetooth address);
  release builds write nothing to logcat.
- Release build: signing config (key outside the repository), R8 shrinking, explicit keep rules for saved settings.
- App renamed "PS2 Bluetooth Gamepad". The Bluetooth service name Windows sees is unchanged.
- Tests: 181 automated (unit, Robolectric UI, lifecycle integration, stress, benchmark).
  Full list in BUG_REPORT.md.

### Known limitations
- The app must stay in the foreground (Android unregisters background HID apps); it re-registers on return.
- L2/R2 digital; 8-bit sticks with circular range; no vibration.
- Only one HID app per phone at a time.
- Auto-reconnect is attempted but unverified.
- Game profiles are unverified starting points.
- Debug and release use different signing keys: uninstall one before installing the other.

### Upgrading
- From 0.2.0/0.3.0 debug builds to the release build: **uninstall first** (signature change).
  Windows pairing stays valid.

## 0.3.0: Phase 3
Customization, layout editor, profiles, DataStore persistence, advanced analog, calibration, dashboard, measurements.

## 0.2.0: Phase 2
Full touch controller, multi-touch, report pacing, interruption safety, diagnostics.

## 0.1: Phase 1 (included in 0.2.0)
HID registration, pairing, connection lifecycle, test buttons.
