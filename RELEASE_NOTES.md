# Release notes

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
