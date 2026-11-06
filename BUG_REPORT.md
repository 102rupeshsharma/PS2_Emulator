# Bug report

Every bug found during development, including the Phase 4 independent code audit.
"Verified" means the regression test passes, or (where marked) a check on the Android emulator.
**No fix has been verified on the POCO F6 or Windows yet**: hardware-dependent fixes are marked so.

Severity follows the Phase 4 scale (Critical / High / Medium / Low).

## Fixed

| ID | Sev. | Bug | How found | Root cause | Fix | Regression test | Verified |
|---|---|---|---|---|---|---|---|
| B13 | Critical | A failed "button released" report could leave the button **held on the PC** until the next input | Audit H2 | The sender counted a `sendReport()` failure and moved on; the input manager already believed the host had the new state | On failure, the **current** state is re-queued (if nothing newer is queued): up to 5 retries, 15 ms apart | None automated: needs a real `BluetoothHidDevice` that fails. Covered by manual test H-STUCK | Code review only. **Hardware: pending** |
| B12 | Critical | Possible **crash** (`RejectedExecutionException`) and one leaked profile proxy per Bluetooth off/on cycle | Audit H1 | The proxy was dropped without `closeProfileProxy`. The old instance rebinds when Bluetooth returns and its callbacks reached a shut-down executor after the app closed | Every proxy is tracked and closed (BT off, replacement, close). Callbacks after close are ignored. Requests are not duplicated | `LifecycleIntegrationTest.hidManagerCloseIsIdempotentAndLaterCallsAreIgnored` (partial: the rebinding itself needs hardware) | JVM ✅. Hardware: pending |
| B15 | High | After the user tapped **Disconnect**, going to the background and back reconnected to the PC anyway | Audit M2 | `onAppStatusChanged(plugged)` always called `connect(plugged)` | `register(autoConnectPlugged)`: the ViewModel passes false after a user disconnect | `MainViewModelTest.userDisconnectDisablesStackAutoConnectOnReRegister` | ✅ (mocked BT) |
| B22 | High | A setting changed right after app start was **not saved** if the app was closed before loading finished | `LifecycleIntegrationTest` | `flush()` did nothing while loading; the replayed edit waited for the debounce | flush is remembered and executed as soon as loading completes | `SettingsRepositoryTest.flushRequestedDuringLoadIsWrittenRightAfterLoad`, `LifecycleIntegrationTest.settingsSurvive…` | ✅ JVM + release build on the emulator (setting kept after force-stop) |
| B11 | High | Saved profiles could be **overwritten** by an edit made before loading finished | Code review (Phase 3) | Load result was discarded when an edit came first | Early edits are replayed on top of the loaded data | `SettingsRepositoryTest.editsBeforeLoadAreReplayedOnTopOfSavedData` | ✅ |
| B8 | High | Response curve < 0.6: moving 1 % past the dead zone **jumped** to ~17 % output | `AnalogSettingsTest` | t^0.4 is very steep near 0 | Minimum curve 0.6 (sanitised and in the slider) | `AnalogSettingsTest.curveIsMonotonicAndHasNoJumpAtDeadZone`, `curveBelowMinimumIsClamped` | ✅ |
| B1 | High | Left/right stick deflection differed by one step (−63 vs +64) | `StickMathTest` | `roundToInt` rounds .5 upwards for both signs | Round the magnitude, then reapply the sign | `StickMathTest.toAxisIsSymmetric` | ✅ |
| B5 | High | If Android dropped the HID registration just **after** the app resumed (e.g. a system dialog), nothing re-registered | Emulator observation (Phase 2) | Re-registration only happened in `onResume` | Re-register when dropped while visible; at most 3 automatic attempts | `MainViewModelTest.droppedWhileVisibleReRegistersImmediately`, `autoReRegisterLoopIsBounded` | ✅ (mocked BT) |
| B14 | Medium | `close()` raced the callback thread; a proxy arriving during close was never closed | Audit M1 | `closed` set last; cleanup on the main thread | `closed` first, cleanup runs on the callback thread, `shutdown()` | Same test as B12 | JVM ✅ |
| B16 | Medium | `sendReport()` could report success for a report dropped by a concurrent disconnect | Audit M3 | Connection checked outside the lock | Check and enqueue under the same lock; `clearConnection` also locked | `StressTest.concurrentInputAndResetsNeverCorruptState` (input side) | Code review |
| B17 | Medium | Diagnostics screen recomposed up to ~250×/s while sending | Audit M4 | Sender published stats for every report | Stats published at most every 250 ms (and on failures) | None (performance) | Code review |
| B18 | Medium | Floating stick **jumped** when a second finger took over after the first lifted | Audit L2 | Origin used the new finger's original touch-down point | Origin = the new owner's current position | None automated. Manual test C-HANDOFF | Code review. Hardware: pending |
| B6 | Medium | Phase 2 layout: right stick's touch area overlapped the face buttons by 0.02u (touches in the overlap went to the stick) | `LayoutEngineTest` (Phase 3) | Absolute stick position | Sticks anchored to the cluster edge | `LayoutEngineTest.defaultLayoutHasNoOverlapsOnAnyScreen` | ✅ |
| B7 | Medium | Sticks slid into the D-pad/face buttons, and R3 overlapped the face cluster, at uneven button/stick scales | `LayoutEngineTest` | Positions not tied to neighbour sizes | Anchoring; L3/R3 moved above the sticks | `LayoutEngineTest.moderateScalesStayOverlapFreeOnThePhone` | ✅ |
| B9 | Medium | Layout editor sometimes lost a drag | `ControllerUiTest` | Gesture callbacks read a copy refreshed only on recomposition | Callbacks read the state object directly | `ControllerUiTest.editModeBlocksHidAndMovesControls` | ✅ (Robolectric UI) |
| B4 | Medium | "Open Controller" was below the fold on the setup screen | Emulator screenshot | Layout order | Buttons moved to the title row; later a dashboard | — | ✅ emulator |
| B19 | Low | Unsaved layout-editor moves could be reset if the profile store emitted during editing | Audit L3 | `remember(w, h, profile)` | One draft per edit session | — | Code review |
| B20 | Low | Settings (incl. the PC's Bluetooth address) were eligible for cloud backup and device transfer | Audit L4 | Default backup on | `allowBackup=false` + data-extraction rules exclude everything | Manifest checked with aapt2 | ✅ |
| B21 | Low | Wrong error code for GET_REPORT of an unsupported report type | Audit L6 | Single error branch | `ERROR_RSP_INVALID_PARAM` for wrong types | — | Code review |
| B10 | Low | A new/duplicated profile appeared off-screen | `ControllerUiTest` | Appended to the end of a list | List scrolls to the active profile | `ControllerUiTest.profileDeleteThroughDialogAndSwitch` | ✅ |
| B3 | Low | Test Button labels nearly invisible (dark on dark) | Emulator screenshot | Default button content colour | Explicit content colours; white on primary | — | ✅ emulator |
| B2 | Low | Lint error: unescaped Windows path in `local.properties` | Lint | Path written with `\` | Escaped path | Lint | ✅ |
| B23 | Low | Dead code: `BluetoothManagerHelper.localName`, `GamepadState.isNeutral`, `PadColors.Led` | Audit | Left over | Removed after checking references | Build + tests | ✅ |

## Fixed in 1.1.0

| ID | Sev. | Bug | How found | Fix | Regression test |
|---|---|---|---|---|---|
| B24 | High | First real test: the laptop paired *before* HID registration rejected the gamepad connection (DISCONNECTED after ~4.5 s) | User test on POCO F6 | Not an app bug (Windows caches the services at pairing). The simple screen now tells the user to remove the phone in Windows and pair again | `SimpleStatusTest.knownPcMeansConnect` (help text) |
| B25 | Medium | Earbuds ("realme Buds T500 Pro") were listed as a PC to connect to | User screenshot | Paired list filtered to Bluetooth class COMPUTER | `AutoModeViewModelTest.earbudsAreNotOfferedAsPc` |
| B26 | Medium | An auto-connect scheduled at registration could fire after a later disconnect (duplicate connect attempt) | `MainViewModelTest` | Cancelled once connected | `MainViewModelTest.reconnectGivesUpAfterThreeAttempts` |

## Fixed in 1.3.0

| ID | Sev. | Bug | How found | Fix | Regression test |
|---|---|---|---|---|---|
| B27 | High | Gyro: after an input reset (screen change, reconnect, back press) a held, unmoving tilt was not sent again, so the right stick stayed centred until the phone moved | Screenshot of the controller during development | The jitter filter remembers the input-reset counter; any reset forces the current tilt out | `GyroViewModelTest.heldTiltIsResentAfterAnInputReset` |
| B28 | Medium | Loading saved settings dropped the gyro calibration (the repair step rebuilt the store without it) | Code review while adding the field | Neutral kept and validated (finite, non-zero) | `GyroViewModelTest.recenterIsSavedForNextTime` + codec |

## Fixed in 1.5.0 (PC Mode development)

| ID | Sev. | Bug | How found | Fix | Regression test |
|---|---|---|---|---|---|
| B29 | Medium | PC mouse ticker ran every 8 ms while the PC controller was open, even with nothing moving (battery/CPU) | Test run hung (infinite timer) | Ticker sleeps on a channel until the stick moves or a wheel button is held | `PcModeViewModelTest.wasdStickAndStickMouse` (stops when centred) |
| B30 | Medium | Default PC layout: Wheel ▲ overlapped Start on a POCO F6-sized screen | `PcProfilesTest.defaultLayoutHasNoOverlapsOnThePhone` | Positions adjusted | same test |
| B31 | Low | PC top bar covered LT/RT; "not connected" banner covered Select/Start | Screenshot review | Compact top bar; banner moved to bottom centre | Screenshot `21_pc_controller.png` |
| B32 | Medium | Store repair rebuilt the store field by field, so a new field could be silently dropped (the cause of B28) | Code review while adding PC fields | `copy()` keeps every stored field | `PcProfilesTest.pcDataPersistsAndEmulatorDataIsUntouched` |

## Open / not fixed

| ID | Sev. | Issue | Why open | Workaround |
|---|---|---|---|---|
| O1 | (Unknown, potentially Critical) | Whether the **POCO F6 accepts `registerApp()`** at all | Hardware not tested yet | None: if it fails, Bluetooth HID is impossible on that phone |
| O2 | Limitation | Android unregisters the HID app in the background (lock screen, app switch) | Platform behaviour; a foreground service could keep it | Keep the app in front (the controller keeps the screen on); it re-registers on return |
| O3 | Unknown | Auto-reconnect attempts and stack "plugged" reconnect with Windows | Unverified on hardware | Reconnect button |
| O4 | Test gap | The profile **name dialog** cannot be UI-tested on Robolectric (text field in a dialog never idles) | Harness limitation | Logic unit-tested; manual test P3.5 |
| O5 | Test gap | B13 retry and B18 hand-off have no automated test | Need real Bluetooth / tricky multi-finger timing | Manual tests H-STUCK and C-HANDOFF |
| O6 | Low | The neutral report queued in `onCleared` is cleared by `close()` before sending (audit L1) | Harmless: closing disconnects the host, and Windows releases all inputs on disconnect | — |
