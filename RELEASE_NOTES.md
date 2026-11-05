# Release notes

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
