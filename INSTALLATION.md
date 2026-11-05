# Installation

## APKs (version 1.0.0, versionCode 4)

| File | Use | Size | SHA-256 |
|---|---|---|---|
| `release/PS2BluetoothGamepad-1.0.0-release.apk` | Normal use: R8-shrunk, signed with the project release key | 2.5 MB | `d5cc736080bc12632ffff45fad04a61ac65064398cb2e0a85ee49c06f4dfef91` |
| `release/PS2BluetoothGamepad-1.0.0-debug.apk` | Troubleshooting: same features, logs to logcat, debug key | 27 MB | `9d668f1bf4c7a1b17d7a4041ae393e003e3bd13e881361e5cb03fe63c40b9bbe` |

Both use the package `com.ps2gamepad` (only one app can hold the HID registration).
**Debug and release are signed with different keys:** to switch between them, uninstall the
installed one first (your profiles are deleted with it).

Requirements: Android 9 (API 28) or newer, with Bluetooth Classic.

## Install with USB (recommended)

1. On the POCO F6: Settings → About phone → tap **OS version** 7 times.
2. Settings → Additional settings → Developer options: enable **USB debugging** and
   **Install via USB** (HyperOS may ask for a Mi account sign-in for the latter).
3. Connect the phone, accept the "Allow USB debugging" prompt.
4. On the PC:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r release\PS2BluetoothGamepad-1.0.0-release.apk
   ```
   `-r` keeps your profiles when upgrading a release build.

## Install without a PC cable

Copy the APK to the phone, open it in the file manager and allow "Install unknown apps" for the
file manager. HyperOS may show a security scan first; that is normal for apps from outside the store.

## First start

1. The app opens on the **Dashboard**.
2. Open **Connection** → **Grant permission** ("Nearby devices": needed to register the HID
   device, connect and make the phone discoverable; no location permission is used).
3. Continue with [BLUETOOTH_SETUP.md](BLUETOOTH_SETUP.md).

## Building from source

Requirements: Android SDK 35, JDK 17+ (Android Studio's JBR works).

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest      # 181 JVM tests (unit, Robolectric UI, stress)
.\gradlew.bat :app:assembleDebug          # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:assembleRelease        # app\build\outputs\apk\release\app-release.apk
.\gradlew.bat :app:testDebugUnitTest -Pscreenshots   # also renders docs\screenshots\*.png
```

### Release signing

The release signing config is read from `keystore.properties` in the project root:

```properties
storeFile=signing/release.jks
storePassword=...
keyAlias=ps2gamepad
keyPassword=...
```

`keystore.properties` and `signing/` are listed in `.gitignore` and **must never be committed or
shared**. The project folder is not a git repository yet, so take care when zipping or copying it.
**Back up both files**: without this key you cannot publish updates that install over the existing release.
Without `keystore.properties` the release APK is built unsigned.
