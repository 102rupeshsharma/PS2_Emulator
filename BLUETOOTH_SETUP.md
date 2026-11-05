# Bluetooth setup: POCO F6 ↔ Windows 11

## 1. Compatibility investigation (Phase 1)

| Question | Finding | How it is verified on the device |
|---|---|---|
| Android version / min API | `BluetoothHidDevice` was added in API 28. The app's minSdk is 28. The POCO F6 shipped with Android 14 and is updated to Android 15 (HyperOS 2) / Android 16 (HyperOS 3) depending on region. | Exact version, build and API level are printed in the app's **Compatibility** card and log at start-up. |
| API availability | Public SDK API, obtained with `BluetoothAdapter.getProfileProxy(ctx, listener, BluetoothProfile.HID_DEVICE)` from `BluetoothManager.adapter`. | Log line *"HID Device profile proxy connected"*. |
| Does the F6 expose the HID Device profile? | **Unknown until tested on the device.** The profile is optional in AOSP (`profile_supported_hidd`). Most Qualcomm phones enable it. Xiaomi documents Bluetooth HID support for its POCO range, but no public report confirms the *device* role on the F6. | If the profile is missing, `getProfileProxy` returns false or never connects. The app then shows *"HID Device profile not supported"* / *"did not respond"* and stops. |
| Custom HID descriptor | `registerApp()` accepts any report descriptor through `BluetoothHidDeviceAppSdpSettings`. | Registration callback `onAppStatusChanged(registered=true)`. |
| Permissions | Android 12+: `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` (runtime, "Nearby devices"). Android 9–11: install-time `BLUETOOTH`. No location, no `BLUETOOTH_SCAN`. | Permission card on the main screen. |
| Simultaneous earbuds (A2DP) + HID | Supported by Bluetooth Classic: the phone keeps two separate links, A2DP source → earbuds and HID device → laptop. Both share one radio, so bandwidth and latency can be affected, especially with high-bitrate codecs (LDAC). | Manual Test 7. |
| Manufacturer restrictions | 1) Android (AOSP `HidDeviceService`) **unregisters a HID app that leaves the foreground**. 2) Only **one** HID app can be registered at a time. 3) Xiaomi battery saver may kill background apps. This does not matter here because the app must be in the foreground anyway. | Log shows *"registration was dropped by the system, registering again"* when returning to the app. |

**If registration fails on the F6** (profile missing or `registerApp()` returning false), the
phone cannot act as a Bluetooth HID gamepad with this approach. Phase 2 would then not work on
this phone, and a different transport (USB, Wi-Fi + PC receiver) would be needed. Please report
the log lines if this happens.

## 2. First pairing

Order matters: Windows reads the HID service record **at pairing time**.

1. **If the phone was ever paired with this laptop before**, remove it on both sides:
   - Windows: Settings → Bluetooth & devices → Devices → phone → *Remove device*.
   - Phone: Settings → Bluetooth → laptop → *Unpair*.
2. Phone: open the app → **Connection** → **Grant permission** → **Register HID Device**.
   Wait for *HID registration successful*.
3. Tap **Make discoverable** → *Allow*. The phone is visible for 5 minutes.
   (While the system dialog is open, Android may briefly drop the registration. The app
   registers again automatically when the dialog closes. Check that the status shows *Registered*.)
4. Windows: Settings → Bluetooth & devices → **Add device** → **Bluetooth** → choose the phone.
5. Confirm the pairing code on **both** devices.
6. Within a few seconds the app should show **Connected to <LAPTOP-NAME>**.
   If it stays on *Waiting for pairing*, use **Connect <laptop>** under *Paired hosts*.
7. Verify: `Win + R` → `joy.cpl`. The device appears (Windows usually lists it under the phone's
   Bluetooth name). **Properties** → *Test* tab shows 12 buttons, a POV hat, X/Y axis, Z axis and Z rotation.

## 3. Reconnecting later

- Open the app → Connection → **Register HID Device**. The stack may reconnect to the last
  host on its own (Android reports it as the "plugged" device). If it doesn't, tap
  **Reconnect <laptop>** or **Connect <laptop>** under Paired hosts.
- After an unexpected drop the app makes 3 reconnect attempts (after 2 s, 5 s and 10 s), but only
  if you did not tap Disconnect yourself. Whether these attempts succeed with Windows is **not verified yet**.
- **After restarting the phone:** open the app, register, then Reconnect. Pairing is kept.
- **After restarting the laptop:** register on the phone, then use Reconnect. Windows usually
  accepts the phone's connection without any action.
- Do **not** pair again. Re-pairing is needed only if the HID descriptor changes, and it has
  not changed between Phase 1 and Phase 2.

## 4. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| *HID Device profile not supported* / *did not respond* | The phone's Bluetooth stack does not expose the HID Device role. No app-side fix. |
| *registerApp() returned false* | Another app holds the HID registration (e.g. a Bluetooth keyboard app). Force-stop it, or toggle Bluetooth off/on. |
| Windows pairs but shows only "Phone"/audio, no controller | Windows cached an old service record. Remove the pairing on both sides and pair again **while registered**. |
| Connected, but joy.cpl shows nothing | Unplug/replug isn't possible. Toggle laptop Bluetooth off/on, or remove and re-pair. Check Device Manager → *Human Interface Devices* for "Bluetooth HID Device". |
| Disconnects when switching apps | Expected: Android drops HID registration in the background. Return to the app and it reconnects. |
| Inputs lag while earbuds play | Shared radio. Try an SBC/AAC codec instead of LDAC (Developer options → Bluetooth audio codec), or pause audio. |
| Release build shows no logcat output | By design: release builds keep the log on screen only (device names are not written to the system log). Use the debug APK for logcat. |
| Stuck button on PC | Should not happen: inputs are released on any interruption. If it does, tap **Reset All Inputs** in Diagnostics, then report the steps. |

## 5. Earbuds

Earbuds connected to the **phone** play the **phone's** audio. Windows/PCSX2 game audio is
**not** forwarded to them. Phase 1/2 only test that both links can coexist.
To test (Test 7): start music in another app, then switch back to this app. The music keeps
playing in the background, while this app must be in the foreground for the HID link.
