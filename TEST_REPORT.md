# Test report: PS2 Bluetooth Gamepad 1.0.0

Date: 2026-11-05 · Build: 1.0.0 (versionCode 4)
Release APK SHA-256 `d5cc736080bc12632ffff45fad04a61ac65064398cb2e0a85ee49c06f4dfef91`

## 0. Summary

| Kind of test | Executed | Result |
|---|---|---|
| Automated JVM tests (unit, integration, UI, stress) | Yes | **181 total: 179 passed, 0 failed, 2 skipped** |
| Android emulator (Android 16, virtual Bluetooth) | Yes, partly | Install, launch, HID registration, settings persistence (release build) ✅ |
| **POCO F6 + Windows 11 hardware** | **No** | **0 of the hardware tests done.** Procedure in §4 |
| **PCSX2 / games** | **No** | **No game is verified compatible** |
| End-to-end latency | No | Method in §3.3 |

Legend: ✅ passed (actually executed) · ⏳ not done yet · ❌ failed.
A test with a **mock** Bluetooth layer proves app logic only, never Bluetooth or Windows behaviour.

## 1. Automated tests: what is real and what is mocked

| Layer | Real | Mocked / simulated |
|---|---|---|
| Unit tests | Production code (encoder, descriptor parser, stick maths, input manager, reducer, profiles, layout) | — |
| ViewModel / stress tests | ViewModel, input manager, reducer, settings repository | `FakeHid` (mock HID layer driven by the real state reducer), in-memory storage |
| UI tests (Robolectric + Compose) | Real Compose screens, real touch injection, real multi-touch dispatch | `FakeHid`, simulated Android framework |
| Lifecycle integration | Real `MainActivity`, ViewModel factory, **DataStore**, `BluetoothManagerHelper`, `HidDeviceManager` | Robolectric **shadow** Bluetooth adapter (no radio) |

Command: `.\gradlew.bat :app:testDebugUnitTest` (clean build, 2026-11-05).

| Suite | Tests | Passed | Skipped | Covers |
|---|---|---|---|---|
| HidDescriptorTest | 7 | 7 | 0 | Descriptor parsed item by item: Game Pad, Report ID 1, 12 buttons, hat with null state, X/Y/Z/Rz −127..127, 48 input bits = 6 bytes |
| HidReportEncodingTest | 13 | 13 | 0 | Neutral `00 80 00 00 00 00`, every button bit, all 9 hat values, axis order/sign/clamp, 2000 random round-trips |
| StickMathTest | 17 | 17 | 0 | 8 directions + centre, dead zone with no jump, clamping, symmetric rounding, D-pad sectors |
| AnalogSettingsTest | 9 | 9 | 0 | Max range, response curves, invert Y, neutral under every setting, 20 000-case fuzz never leaves the HID range, face spacing geometry |
| ControllerInputManagerTest | 21 | 21 | 0 | Press/release of all inputs, 5 s hold, rapid tapping, the 7 spec multi-touch combinations, disconnect behaviour |
| ReportQueueTest | 5 | 5 | 0 | Axis coalescing; press+release never merged |
| HidStateReducerTest | 15 | 15 | 0 | Registration/connection state machine; "Connected" only from a real callback |
| MainViewModelTest | 30 | 30 | 0 | Permission/BT gating, reconnect logic, re-registration, user-disconnect rules, profiles, edit mode, connection timer, sessions |
| ProfileManagerTest | 13 | 13 | 0 | Built-ins, create/rename/duplicate/delete/switch/restore, JSON round-trip, corrupt data repair, **no button-mapping field** |
| LayoutEngineTest | 9 | 9 | 0 | No overlaps on 5 screen sizes, on-screen, scaling, relative placements, collisions |
| SettingsRepositoryTest | 6 | 6 | 0 | Debounced save, persistence across restart, edits before load, flush during load |
| ControllerUiTest | 17 | 16 | 1 | Every button, D-pad incl. diagonal and sliding, both sticks, 5-finger multi-touch, interruption, connection loss, edit mode, customization, profiles, calibration, diagnostics, error states |
| LifecycleIntegrationTest | 6 | 6 | 0 | Real Activity: launch, pause/stop/focus-loss release, DataStore persistence across recreation, adapter broadcasts, HID manager close safety |
| StressTest | 8 | 8 | 0 | 10 000 taps, long holds under load, 100 000 analog updates, 1 000 profile switches, 500 connection drops, 200 background cycles, BT toggle storm, 3 concurrent threads |
| PerformanceBenchmarkTest | 1 | 1 | 0 | Pipeline micro-benchmark (desktop JVM) |
| MiscTest | 3 | 3 | 0 | Permissions per API level, log buffer, status texts |
| ScreenshotCapture | 1 | 0 | 1 | Renders docs/screenshots; runs only with `-Pscreenshots` (skipped otherwise, by design) |
| **Total** | **181** | **179** | **2** | |

Skipped tests:
1. `ControllerUiTest.newProfileDialogOpensAndCancels`: Robolectric never becomes idle with a text field
   inside a dialog. The profile logic is unit-tested; the dialog is in manual test P3.5.
2. `ScreenshotCapture.captureScreens`: a tool, not a test; run with `-Pscreenshots`.

Lint (debug and release): 0 errors, 0 code warnings (only "newer version available" notices).

## 2. Android emulator checks (Pixel 9a image, Android 16, virtual Bluetooth: NOT the POCO F6)

| Check | Build | Result |
|---|---|---|
| Install, launch, no crash | debug 0.2.0, release 1.0.0 | ✅ |
| HID profile proxy + `registerApp()` + `registered=true` callback | debug 0.2.0, **release 1.0.0 (R8)** | ✅ |
| App reports "Bluetooth disabled" and refuses to register when the adapter is off | release 1.0.0 | ✅ |
| Setting changed (background), app force-stopped, relaunched: setting kept | **release 1.0.0 (R8)** | ✅ |
| Controller layout renders without overlap | debug | ✅ (`docs/screenshots/02_controller.png`) |
| Pairing with Windows, joy.cpl, real touch on the emulator | — | Not possible / emulator System UI kept hanging |

Robolectric-rendered screenshots of every screen: `docs/screenshots/10_dashboard.png` …
`18_connection.png` (the "Connected" state shown there is mock state).

## 3. Performance

### 3.1 Measured so far (development PC, NOT the phone)
JVM micro-benchmark (`app/build/perf/benchmark.txt`, 2 runs): stick update → processed → HID report → send queue
**0.7–1.2 µs**, button 0.6–0.7 µs, encode 0.12–0.27 µs. This is software cost only; it says
nothing about the phone, Bluetooth or Windows.

### 3.2 Measured on the phone by the app (Diagnostics screen), still to be recorded ⏳
| Metric | What exactly it measures | Value |
|---|---|---|
| Report generation avg/max | Input change → HID report encoded and queued (`System.nanoTime`) | ⏳ |
| Touch → sendReport avg/last | Android touch event timestamp → `BluetoothHidDevice.sendReport()` returned. Includes the 4 ms pacing. **Excludes** the radio and Windows | ⏳ |
| Report rate | `sendReport()` calls per second while moving a stick | ⏳ |
| Send failures | `sendReport()` returned false | ⏳ |
| App CPU % | Process CPU time / wall time (2 s windows) | ⏳ |
| Heap | Java heap in use | ⏳ |
| Battery | % at session start → now | ⏳ |

### 3.3 Not measurable by the app: end-to-end latency
Bluetooth transmission time and Windows/PCSX2 processing cannot be seen from the phone.
Repeatable method: film the phone screen and the PC monitor (joy.cpl Test tab, or a game) together
with a phone camera in **240 fps slow motion**. Tap a button 20 times; for each tap count the frames
between the finger touching (button highlights on the phone) and the joy.cpl indicator lighting
up (1 frame = 4.2 ms; add the monitor's own lag). Report the median and the worst of the 20.

## 4. Hardware test procedure: POCO F6 + Windows 11 ⏳

Fill in Result (✅/❌ + notes). Use the release APK unless you need logcat (debug APK).

### 4.1 Connection
| # | Step | Expected | Result |
|---|---|---|---|
| C1 | Install the release APK | Installs | ⏳ |
| C2 | Phone Bluetooth on, laptop Bluetooth on | Dashboard: Bluetooth enabled | ⏳ |
| C3 | **Connection → Register HID Device** (critical) | Log: proxy connected, `registered=true`; "HID registration successful" | ✅ 2026-11-05, POCO F6 (24069PC21I), Android 16 / API 36, build BP3A.250905.014 (user screenshot) |
| C4 | Make discoverable; pair from Windows (remove old pairings first) | Paired | ⏳ |
| C5 | Gamepad recognised | App "Connected to <laptop>"; joy.cpl lists the device | ✅ app shows Connected to RUPESH after a clean re-pair (user, 2026-11-05, v1.1.0); joy.cpl ✅ device listed and reacts to phone input (user, 2026-11-05). First attempt failed because the old pairing pre-dated HID registration |
| C6 | Disconnect, then Reconnect | Disconnected, then Connected; no re-pairing | ⏳ |
| C7 | Restart the phone, open the app, register, Reconnect | Connected | ⏳ |
| C8 | Restart the laptop, then register/reconnect from the phone | Connected | ⏳ |
| C9 | Turn laptop Bluetooth off for 30 s, then on | App: Disconnected + banner; then reconnects (automatically or with the button). Note which | ⏳ |
| C10 | Lock the phone 10 s, unlock | Re-registers; reconnects; no input held | ⏳ |

### 4.2 Windows (joy.cpl → Properties → Test)
| # | Check | Expected | Result |
|---|---|---|---|
| W1 | Each of the 12 buttons | The right button number lights only while held (PCSX2_CONFIGURATION.md §1) | ⏳ |
| W2 | D-pad 8 directions | POV shows the matching direction, centre when released | ⏳ |
| W3 | Left stick, right stick (all directions) | Cross-hair / Z bars move the right way; up = up | ⏳ |
| W4 | Simultaneous: stick + ✕ + L1 + R1 + other stick | All shown together | ⏳ |
| W5 | Neutral at rest | Cross-hair centred, no buttons, POV centred | ⏳ |
| H-STUCK | Hold ✕ and switch the laptop's Bluetooth off; also hold ✕ and pull down the notification shade | Nothing stays held after reconnect / immediately | ⏳ |
| C-HANDOFF | Floating stick: finger A pushes right, finger B touches the stick, A lifts | No jump of the stick value | ⏳ |

### 4.3 Earbuds
| # | Step | Expected | Result |
|---|---|---|---|
| E1 | Earbuds connected to the phone, music playing (started in another app, then back to this one) | Music plays; HID stays Connected | ⏳ |
| E2 | Play 10 min with music | No HID drop, no audio drop; note any input lag | ⏳ |
| E3 | Restart the app and repeat E1 | Same | ⏳ |

### 4.4 Customization / profiles (Phase 3)
| # | Test | Expected | Result |
|---|---|---|---|
| P3.1 | Every Customize slider, background, labels, haptics | Preview + controller update immediately | ⏳ |
| P3.2 | Edit layout: move/resize; try dropping onto another control | Moves; overlap refused; joy.cpl idle while editing | ⏳ |
| P3.3 | Force-stop and reopen; reboot the phone | Layout, profiles, active profile kept | ⏳ |
| P3.5 | Profiles: create and rename through the **dialog**, duplicate, delete, switch, restore | Works and persists | ⏳ |
| P3.6 | Per-stick sensitivity / dead zone / max range / curve / invert Y | Visible in joy.cpl; no drift at rest | ⏳ |
| P3.7 | Fixed mode + Calibrate centre | Resting thumb reads 0,0 | ⏳ |

### 4.5 PCSX2 and games
| Game | Movement | Camera | Combat / actions | Shoulders | Menus | Simultaneous | Extended play | Issues |
|---|---|---|---|---|---|---|---|---|
| God of War | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | |
| God of War II | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | |
| GTA San Andreas | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | |
| Need for Speed Most Wanted | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | |
| Shadow of the Colossus | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | |

Also check: PCSX2 detects the device (SDL) ⏳ · all bindings ⏳ · reconnect during emulation ⏳.

### 4.6 Long-duration
| Session | Actual duration | Disconnects | Stuck inputs | Missed inputs | Crashes | Battery start→end | CPU % / heap (Diagnostics) | Phone temperature | Result |
|---|---|---|---|---|---|---|---|---|---|
| 30 min | | | | | | | | | ⏳ |
| 60 min | | | | | | | | | ⏳ |
| 2 h (optional) | | | | | | | | | ⏳ |

## 4.7 Gyro right stick (1.3.0) ⏳

Setup: Gyro tile → switch ON → hold the phone as you play → **Recenter**. Keep `joy.cpl` → Properties
open on the laptop (the right stick shows as **Z axis / Z rotation**).

| # | Test | Expected | Result |
|---|---|---|---|
| G1 | Neutral: calibrate, hold still | Gyro screen X=0 Y=0; joy.cpl Z/Z-rotation centred | ⏳ |
| G2 | Tilt right / left / top edge away / top edge towards you | Only the matching axis moves; away = stick up | ⏳ |
| G3 | 4 diagonals | Both axes move together; direction label matches | ⏳ |
| G4 | Slowly circle the tilt through 360° (also 15°, 30°, 45°, 60°) | Circle indicator moves smoothly round, no jumps or snapping | ⏳ |
| G5 | Slight / moderate / maximum tilt | Magnitude grows smoothly up to 1.00 | ⏳ |
| G6 | Hold a tilt 10 s in a game | Camera keeps turning for the whole 10 s | ⏳ |
| G7 | Return to the calibrated hold | Both axes back to 0 | ⏳ |
| G8 | Rest the phone on a table 1 min; small hand tremor | Stays at 0 (dead zone), no creep | ⏳ |
| G9 | joy.cpl: all directions, diagonals, intermediate positions | Z / Z-rotation follow the phone | ⏳ |
| G10 | Regression: left stick, all buttons, D-pad, connection, with the gyro on | Unchanged; left stick + gyro at the same time | ⏳ |
| G11 | Notification shade / home / lock while tilted | Right stick centres; sensors stop; back = resumes | ⏳ |
| G12 | Settings: sensitivity, max tilt, dead zone, smoothing, invert X/Y; Calibrate (1.5 s) | Each has a visible effect; calibration kept after app restart | ⏳ |

Automated (simulated sensors, MOCK Bluetooth): GyroTiltTest (11), GyroFusionTest (5), GyroEngineTest (6),
GyroViewModelTest (10), GyroUiTest (2): all passed.

## 5. Final acceptance checklist

| # | Criterion | Status |
|---|---|---|
| 1 | Application installs | ✅ emulator · ⏳ POCO F6 |
| 2 | POCO F6 registers as Bluetooth HID gamepad | ✅ (user test 2026-11-05) |
| 3 | Windows recognises the device | ✅ joy.cpl (user test 2026-11-05) |
| 4 | All PS2 buttons work | ✅ app logic (UI tests, mocked BT) · ⏳ hardware |
| 5 | Both analog sticks work | ✅ app logic · ⏳ hardware |
| 6 | Multi-touch works | ✅ app logic (5 fingers, Robolectric) · ⏳ real touchscreen |
| 7 | Simultaneous inputs work | ✅ app logic · ⏳ hardware |
| 8 | Button releases are reliable | ✅ app logic + stress · ⏳ hardware |
| 9 | Bluetooth reconnect works | ⏳ |
| 10 | Earbud simultaneous connectivity verified | ⏳ |
| 11 | PCSX2 recognises the controller | ⏳ |
| 12 | Actual gameplay manually tested | ⏳ |
| 13 | No known reproducible critical bugs | ✅ none known (two critical audit bugs fixed; hardware unknown, see BUG_REPORT.md O1) |
| 14 | Automated regression tests pass | ✅ 179/179 executed, 2 skipped |
| 15 | Release APK builds | ✅ signed, R8, 2.5 MB |
| 16 | Documentation complete | ✅ |

**The project is not release-ready** until items 2, 3, 9, 10, 11 and 12 pass on the real hardware.
