# PC Gaming Mode (1.5.0)

PC Mode turns the phone into a **Bluetooth keyboard + mouse** with an on-screen layout for PC games:
a W/A/S/D movement stick, gyro or stick mouse aiming, mouse buttons, and your own custom buttons
per game. Emulator Mode is unchanged.

> **Status:** implemented and covered by automated tests with simulated Bluetooth. **Not yet tested
> on Windows.** Whether Windows accepts the keyboard and mouse parts is the first thing to verify (§6).

## 1. How it talks to Windows (architecture)

No Windows software is needed. The phone uses the existing Bluetooth HID connection and adds
two more HID "devices" to it:

| Report ID | Device | Contents | Windows sees |
|---|---|---|---|
| 1 | Gamepad (unchanged) | 12 buttons, hat, 2 sticks | Game controller (joy.cpl, PCSX2) |
| 2 | Keyboard | modifiers + up to 6 keys | Keyboard |
| 3 | Mouse | 5 buttons, relative X/Y, wheel | Mouse |

This combined ("composite") mode is **opt-in**. Until you enable PC input, the phone registers exactly
as before (gamepad only), so nothing changes for emulator users.

## 2. One-time setup

1. Open the app → **Open Controller** → **PC Gaming** → **Enable PC input** (or: PC profiles &
   settings → PC input → Enabled).
2. **On the laptop:** Settings → Bluetooth & devices → the phone (**PS2 Gamepad**) → **Remove device**.
   Windows remembers the old device type, so this re-pair is required **once**.
3. On the phone, tap **Make phone visible to PC** on the home screen. On the laptop: **Add device →
   Bluetooth → PS2 Gamepad**, then confirm the code on both screens.
4. Windows installs a game controller, a keyboard and a mouse (Device Manager → Keyboards / Mice
   and other pointing devices / Human Interface Devices).
5. Emulator Mode keeps working afterwards (same gamepad report). PCSX2 bindings should stay valid;
   re-check them once after re-pairing.

To go back to gamepad only, switch PC input off and re-pair once more.

## 3. Using PC Mode

**Open Controller → PC Gaming** (the last mode used is marked; you can switch any time with ◀).

| Control | Default |
|---|---|
| Left stick | W / A / S / D (diagonals = two keys, e.g. W+D) |
| Right stick | Mouse movement (speed proportional to how far you push) |
| A / B / X / Y | Space / E / R / F |
| LB / RB | Q / G |
| LT / RT | Left Ctrl / Left Shift |
| Start / Select | Esc / Tab |
| Sprint / Crouch | Left Shift / C |
| LMB / RMB | Left / right mouse button (hold = drag) |
| ▲ / ▼ | Mouse wheel (holding repeats) |
| Gyro chip | Pause / resume gyro mouse (the controller stays connected) |
| ⟳ | Gyro recenter |

These are starting points. Change everything per game (§4).

Keys are sent as **key-down when pressed and key-up when released**, never repeated by the app
(Windows auto-repeats a held key like a real keyboard). Everything is released automatically when you
leave the controller, switch modes, pull down the notification shade, lock the phone, edit the
layout, or the connection drops.

## 4. Custom buttons and editing

Tap **Edit** (top bar). Nothing is sent to the PC while editing.

- **Move:** drag any control.
- **Edit:** tap a control. In the side panel you can set:
  - Name.
  - Action: **Key** (with optional **Ctrl / Shift / Alt** for combinations like Ctrl+E), **Mouse**
    (left, right, middle, back, forward), **Wheel ▲/▼**, **Gyro on/off**, **Recenter**.
  - Size and opacity.
  - For the movement stick: its four keys.
- **+ Button:** adds a custom button (max **12** per profile). Then name it and pick its key.
- **Delete:** custom buttons only (default controls can be remapped, resized, made faint or moved).
- **Restore default layout:** asks for confirmation first.
- **Done:** leave Edit. Everything is saved immediately to the profile in use.

## 5. Gyro mouse

Turn or tilt the phone to move the mouse **relatively**, like a real mouse. A still phone means a
still cursor.

| Phone movement | Mouse |
|---|---|
| Turn left / right, or roll left / right | Left / right |
| Tilt the top edge away from you | Up |
| Tilt the top edge towards you | Down |

- **Calibrate:** PC profiles & settings → *Calibrate (keep still)*, then lay the phone still for
  1.5 s. This measures the gyroscope's drift and removes it.
- **Recenter (⟳):** restarts movement from the current hold.
- **Settings** (per profile): on/off, mouse sensitivity, X and Y sensitivity, dead zone (°/s),
  smoothing, invert X/Y. Also right-stick mouse speed and the movement-key threshold.
- Sensors run only while the PC controller is shown, gyro is on and not paused.

## 6. Profiles

**PC profiles & settings** (tap the profile name in the top bar).
- Built-in: *Generic PC Game*, *GTA V (example)* (adds Reload, Map, Phone).
- **Create** (copy of the one in use), **Rename**, **Duplicate**, **Use**, **Delete** (asks),
  **Restore** built-ins (asks). Max **10** profiles.
- Each profile keeps its controls, keys, custom buttons, positions, sizes, opacity, gyro and mouse settings.
- PC profiles are separate from the emulator loadouts.

## 7. Limitations

- **Not tested on Windows yet.** If Windows shows no keyboard/mouse after re-pairing, report it.
  The fallback plan is a small Windows companion app (Approach B), not built.
- Up to **6 normal keys** at once (plus all modifiers), the standard HID keyboard limit.
- PC Mode sends keyboard/mouse. It does **not** emulate an Xbox (XInput) controller. Games that only
  accept Xbox pads need Steam Input or Emulator Mode with the generic gamepad.
- Gyro mouse feel (sensitivity scale) is tuned on paper; adjust it in settings.
- The app must stay in the foreground (Android drops HID apps in the background).
