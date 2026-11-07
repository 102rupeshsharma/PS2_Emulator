# PadLink: your phone as a Bluetooth controller

**PadLink** turns an Android phone into a Bluetooth controller for a Windows PC: a gamepad for PCSX2 and
other emulators, a keyboard + mouse for PC games, gyro aiming, loadouts per game, and a soft light or
black theme. No PC software and no driver needed.

## 📲 Install on your phone

1. Open this page on your Android phone and tap the **Download** link at the [bottom of this page](#-download).
2. Open the downloaded `PadLink.apk` (from the notification or the Downloads folder).
3. If asked, allow **"Install unknown apps"** for your browser or file manager, then tap **Install**.
   Android or Play Protect may warn that the app is from an unknown developer, because it isn't
   from the Play Store. Choose **Install anyway**.
4. Open **PadLink** and follow the one instruction on the screen:
   - **Allow** Bluetooth access, then the app sets itself up and renames the phone to **"PadLink"**.
   - First time: tap **Make phone visible to PC**, then on the laptop go to Settings → Bluetooth & devices →
     Add device → Bluetooth → **PadLink**, and confirm the code on both screens.
   - Already paired with an older version? It keeps working. Windows may still show the old name
     "PS2 Gamepad" until you remove the phone and pair it again.
   - Next times: just open the app with Bluetooth on (phone and laptop); it connects by itself.
   - When it says **Connected ✓**, tap **Open Controller**.
   - If the phone was paired with the laptop *before*, remove it in Windows Bluetooth settings first and pair again.

Requires Android 9 or newer with Bluetooth.

### Screens

| Home | Settings inside the controller |
|---|---|
| ![Home](docs/screenshots/02_home_connected.png) | ![Controller settings](docs/screenshots/08_controller_settings.png) |
| **Mode selection** | **PC Gaming controller** |
| ![Modes](docs/screenshots/20_mode_select.png) | ![PC controller](docs/screenshots/21_pc_controller.png) |
| **Loadouts (one per game)** | **Controller with gyro** |
| ![Loadouts](docs/screenshots/07_loadouts.png) | ![Controller gyro](docs/screenshots/06_controller_gyro.png) |

SHA-256: `e4e74c2ce6506fc52f2d3b234228e3c5f56ace0714c9dd788a6689b8fecb03e4`

---

PadLink is an Android app that turns a phone into a **Bluetooth HID controller** with a touch
layout you can rearrange freely. Windows sees a standard game controller (and, if you switch on
PC input, a keyboard and mouse), so PCSX2, other emulators and PC games can use it with no driver
and no PC software.

**Version 2.0.0.** 311 automated tests (309 passed, 2 skipped).
- Confirmed on real hardware by the developer (POCO F6 + Windows 11): registration, pairing,
  Windows game controller test (`joy.cpl`), PCSX2 with manual binding.
- Not yet confirmed on hardware: gyro aiming, loadouts, PC Mode (keyboard/mouse), the soft-UI
  themes and the battery saver. See [TEST_REPORT.md](TEST_REPORT.md).

### 🔋 Battery and heat

**Battery saver** is ON by default (controller → Settings). While a controller is open it:
- limits the screen to **60 Hz** instead of 90/120 Hz, which halves display and GPU work;
- **dims the screen**, since you look at the PC, not the phone;
- sends Bluetooth reports at **125 Hz** instead of 250 Hz (adds at most 4 ms).

The app also has no background polling: the Bluetooth sender, the PC mouse ticker and the motion
sensors sleep completely when idle, and the sensors run only while gyro aiming is on. For the
lowest power use on an AMOLED phone, also switch on the **Black theme** (black pixels are off).

| Document | Contents |
|---|---|
| [INSTALLATION.md](INSTALLATION.md) | Install the APK, debug vs release builds, building from source |
| [BLUETOOTH_SETUP.md](BLUETOOTH_SETUP.md) | Compatibility findings, pairing with Windows, joy.cpl, troubleshooting |
| [PCSX2_CONFIGURATION.md](PCSX2_CONFIGURATION.md) | Button/axis mapping and PCSX2 binding |
| [TEST_REPORT.md](TEST_REPORT.md) | Automated results, what is mocked, the hardware test procedure |
| [BUG_REPORT.md](BUG_REPORT.md) | Every bug found, its root cause, fix and regression test |
| [RELEASE_NOTES.md](RELEASE_NOTES.md) | 1.0.0 contents and known limitations |
| [DEVELOPMENT_PROGRESS.md](DEVELOPMENT_PROGRESS.md) | Phase-by-phase history and remaining work |

## Features

- **Bluetooth HID gamepad** using Android's public `BluetoothHidDevice` API (Android 9+):
  12 buttons, D-pad (hat switch) and two analog sticks. One fixed report, so the PC never needs re-pairing.
- **DualShock 2 layout:** D-pad, △ ○ ✕ □, L1 L2 R1 R2, Start, Select, L3, R3 and two analog sticks.
  Full multi-touch with per-finger tracking.
- **Never-stuck inputs:** everything is released on lift, cancel, app pause, focus loss,
  notification shade, back, profile switch, layout editing, Bluetooth off and connection loss.
  A failed report is retried with the current state.
- **Two modes:** *Emulator Gaming* (unchanged PS2 controller) and **PC Gaming**: Bluetooth keyboard +
  mouse with a W/A/S/D stick, gyro mouse aiming, mouse buttons, wheel, custom buttons (key combos too)
  and PC game profiles. See [PC_MODE.md](PC_MODE.md). Windows test pending.
- **Loadouts (up to 5):** one saved setup per game. Each keeps its own button layout, sizes,
  opacity, sticks and gyro. Open Controller asks which loadout to play; create one, arrange the
  buttons, Save, and next time just pick it.
- **Gyro right stick (360°):** tilt the phone to move the right stick like a real analog stick:
  any direction, any intermediate angle, held tilt = held stick, back to neutral = centre.
  Gyroscope + accelerometer fusion (no drift), Recenter / Calibrate, sensitivity, max tilt angle,
  dead zone, smoothing, invert X/Y, and a live test screen showing exactly what is sent.
- **Settings inside the controller** (Settings button): gyro on/off (on = the right joystick
  becomes the gyro), opacity of all controls, size of all buttons, joystick size.
- **Customization:** button size, stick size, opacity of all controls **and of each control**, controller opacity,
  face-button spacing, 4 backgrounds, labels, haptics.
- **Layout editor:** drag, resize and reset controls; overlapping drops are refused; layouts adapt to any screen size.
- **Profiles:** Standard PS2, God of War, God of War II, GTA San Andreas, NFS Most Wanted, Custom,
  plus your own (create, rename, duplicate, delete, restore). Profiles never change the button mapping.
- **Analog settings per stick:** sensitivity, dead zone, max range, response curve, invert Y,
  fixed/floating centre, centre calibration with a live X/Y visualizer.
- **Dashboard:** connection status and timer, last connection, active profile, phone battery, quick access to every screen.
- **Diagnostics:** every input live, the last HID report, measured report rate, report generation time,
  touch→send time, app CPU %, heap, session battery change.

## Technology

Kotlin 2.1 · Jetpack Compose (BOM 2025.05) · Material 3 · coroutines/StateFlow · MVVM ·
Jetpack DataStore + kotlinx-serialization · AGP 8.13 · minSdk 28, targetSdk 35 · R8 for release.
Tests: JUnit 4, kotlinx-coroutines-test, Robolectric 4.14 + Compose UI test.

## Architecture

```
 Touch (Compose) ──► per-control pointer tracking (ui/controller/PointerTracking.kt)
        │                 D-pad · face cluster · sticks (StickMath) · buttons
        ▼
 ControllerInputManager  ── single source of truth for the controller state, locked,
        │                   dedups, releaseAll() + reset epoch invalidates stale fingers
        ▼  HidReportManager.encode()  (6-byte report, Report ID 1)
 HidDeviceManager        ── BluetoothHidDevice proxy · registerApp · callbacks (own thread)
        │                   sender thread: 4 ms pacing, axis-only coalescing, retry on failure
        ▼
   Android Bluetooth stack ──► Windows HID game controller ──► PCSX2 (SDL / DInput)

 MainViewModel  ── connects UI, input manager, HID state machine (HidStateReducer), lifecycle
                   (pause/stop/focus → release), reconnect, profiles (SettingsRepository → DataStore)
```

Source layout: `bluetooth/` (HID layer), `controller/` (input manager, stick maths, layout engine),
`model/`, `settings/` (profiles, persistence), `permissions/`, `ui/` (screens), `ui/controller/` (controller UI).

## Quick start

1. Install the APK from the [Download](#-download) link below.
2. Open PadLink → allow Bluetooth access → **Make phone visible to PC**.
3. Windows → Bluetooth → Add device → pick the phone → confirm the code.
4. Check `joy.cpl`, then **Open Controller** and bind it in PCSX2 ([PCSX2_CONFIGURATION.md](PCSX2_CONFIGURATION.md)).

## Known limitations

- **Keep the app in the foreground.** Android unregisters HID apps that go to the background;
  the app re-registers when you return. The controller screen keeps the display on.
- L2/R2 are digital (no pressure). Sticks are 8-bit with a circular range (diagonal ≈ ±90 per axis).
- No vibration (the HID report has no output/rumble report).
- Only one app on the phone can be a HID device at a time.
- Earbuds on the phone play phone audio only; PC game audio is not forwarded.
- Game profiles are tuning starting points, not tested presets.

## 📥 Download

### **[⬇️ Download PadLink (v2.0.0, 2.7 MB)](https://raw.githubusercontent.com/102rupeshsharma/PS2_Emulator/main/download/PadLink.apk)**

Tap the link on your Android phone, open the downloaded file and tap **Install**. It installs over an
older version, and your loadouts and pairing are kept.

## ❤️ Support this project

PadLink is free. If it helped you and you'd like to say thanks, a small donation by **UPI** is welcome.

**Scan with any UPI app** (Google Pay, PhonePe, Paytm, BHIM…):

<img src="docs/support/upi-qr.png" alt="UPI QR code: Rupesh Sharma, 102rupeshsharma@okicici" width="260">

Or pay to a UPI ID (open your UPI app → **Pay to UPI ID** → paste → any amount → send):

```
102rupeshsharma@okicici
```
```
7483124898@superyes
```

Thank you! 🙏
