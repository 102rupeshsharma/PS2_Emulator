# PC Gaming Mode: technical assessment (before implementation)

## Existing architecture (as found)

| Layer | Implementation |
|---|---|
| UI | Single Activity, Jetpack Compose, `Screen` enum navigation in `MainViewModel` |
| Emulator controller | `ui/controller/*` (layout engine, 5 styles, loadouts, edit mode), `ControllerInputManager` = single source of gamepad state |
| Transport | Android **BluetoothHidDevice** (no Windows software). `HidDeviceManager`: proxy, `registerApp`, callbacks thread, sender thread (4 ms pacing, coalescing, retry) |
| HID report | One **gamepad** top-level collection, Report ID 1, 6 bytes: 12 buttons, hat, X/Y/Z/Rz int8 |
| Windows view | Generic HID game controller (joy.cpl, PCSX2 via SDL "SDL-0"), DirectInput, **not** XInput |
| Sensors | `motion/` gyroscope + accelerometer fusion (gyro right stick, emulator only) |
| Persistence | DataStore JSON (`ProfileStore`): emulator profiles/loadouts, gyro neutral, last connection |
| Tests | 256 JVM tests (unit, Robolectric UI, lifecycle, stress, gyro) |

Verified on hardware by the user: HID registration on POCO F6 (Android 16), Windows pairing,
joy.cpl, PCSX2 (manual binding). Gyro right stick and loadouts: not yet confirmed on the phone.

## Known limitations relevant to PC Mode

- The app sends **only gamepad reports** today. PC games that need keyboard and mouse get nothing usable.
- The gamepad is **DirectInput**, not XInput. Many PC games ignore it (Steam Input can translate).
- Android keeps exactly **one** HID registration per phone. Windows caches the HID descriptor at pairing.

## Decision: Approach A, a composite Bluetooth HID (gamepad + keyboard + mouse)

`registerApp()` accepts any report descriptor. One descriptor can declare three top-level collections
with report IDs: **1 gamepad (byte-for-byte unchanged)**, **2 keyboard**, **3 mouse**. Windows creates
one device per collection (the standard composite-HID mechanism also used by keyboard + touchpad
combos). This keeps the existing transport, threads and pairing, needs **no Windows software**, and
the gamepad report is unchanged.

Why not Approach B (Windows companion app)? It would add a second transport (RFCOMM or Wi-Fi), a
Windows program to install and start, and a second connection to manage, all to reach the same
result (`SendInput`). It stays the documented fallback if composite HID fails on the real PC.

Protecting the working gamepad (the "do not replace without proof" rule):
- The composite descriptor is **opt-in** ("Enable PC input"). Until the user enables it, the app
  registers the **exact current gamepad-only descriptor**, so Emulator Mode is bit-for-bit as before.
- Enabling or disabling it re-registers the app. Windows must **remove and re-pair the phone once**,
  because it caches descriptors. The app says so.
- Report ID 1 is identical in both descriptors, so emulator input (and PCSX2 bindings) keep working.
- Not yet proven on hardware: Windows' keyboard/mouse handling of this descriptor. It is marked NOT TESTED.

## PC Mode design (separate from Emulator Mode)

- `pc/` package: `PcModel` (controls, actions, profiles), `PcInputEngine` (key and mouse state with
  reference counts, transitions only, no repeats, release-all), `GyroMouseEngine` (angular velocity → relative
  mouse, bias calibration, dead zone, smoothing, rate limit), `PcProfileManager`.
- Reports: keyboard (ID 2, 8 bytes: modifiers + 6 keys) and mouse (ID 3, 6 bytes: 5 buttons,
  int16 X/Y relative, int8 wheel). The sender queue never merges keyboard reports, and sums mouse
  movement instead of dropping it.
- UI: Mode selection → Emulator (existing loadout picker, unchanged) or PC controller (play/edit,
  custom buttons) + PC profiles and settings.
- Storage: separate `pc` section of the settings document. Emulator profiles are never read or written by PC code.

## Files

Modified: `HidDescriptor.kt`, `HidDeviceManager.kt`, `ReportQueue.kt`, `ControllerProfile.kt`
(store fields), `MainViewModel.kt`, `MainScreen.kt` (routes), `SimpleHomeScreen.kt` / `DashboardScreen.kt`
(Open Controller → mode selection), test fakes.
New: `bluetooth/PcReports.kt`, `pc/PcModel.kt`, `pc/PcInputEngine.kt`, `pc/GyroMouse.kt`,
`ui/ModeSelectScreen.kt`, `ui/pc/PcControllerScreen.kt`, `ui/pc/PcProfilesScreen.kt`, tests.
