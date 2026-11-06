package com.ps2gamepad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ps2gamepad.bluetooth.HidController
import com.ps2gamepad.bluetooth.SenderStats
import com.ps2gamepad.controller.ControllerInputManager
import com.ps2gamepad.controller.LayoutEngine
import com.ps2gamepad.model.Appearance
import com.ps2gamepad.model.ControlId
import com.ps2gamepad.model.ControllerProfile
import com.ps2gamepad.model.LastConnection
import com.ps2gamepad.model.Placement
import com.ps2gamepad.model.ProfileStore
import com.ps2gamepad.model.Stick
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.settings.InMemoryTextStorage
import com.ps2gamepad.settings.ProfileManager
import com.ps2gamepad.settings.SettingsRepository
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ConnectionState
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.HidState
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.PermissionState
import com.ps2gamepad.model.RegistrationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { HOME, MODE_SELECT, PC_CONTROLLER, PC_PROFILES, LOADOUTS, GYRO, DASHBOARD, CONNECTION, CONTROLLER, DIAGNOSTICS, CUSTOMIZE, PROFILES, CALIBRATION }

/** A controller session, for measured (not estimated) battery and duration figures. */
data class Session(val startUptimeMs: Long, val startBattery: Int?)

/**
 * Glue between the Bluetooth layer, the input manager and the UI. Holds no Android framework
 * objects, so it can be unit tested with a fake [HidController].
 */
class MainViewModel(
    private val hid: HidController,
    val bluetoothState: StateFlow<BluetoothState>,
    private val bondedHostsProvider: () -> List<HostDevice>,
    val log: DiagnosticLog,
    val compatibility: List<String>,
    uptimeMs: () -> Long,
    private val reconnectDelaysMs: List<Long> = listOf(2_000, 5_000, 10_000),
    private val cleanup: () -> Unit = {},
    settingsRepository: SettingsRepository? = null,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    val batteryPercent: () -> Int? = { null },
    private val newId: () -> String = { java.util.UUID.randomUUID().toString().take(8) },
    private val phoneNameControl: com.ps2gamepad.bluetooth.PhoneNameControl? = null,
    private val autoConnectDelayMs: Long = 1_500,
    private val motionSource: com.ps2gamepad.motion.MotionSource? = null,
    /** Where the PC Mode mouse ticker runs (tests pass their dispatcher). */
    private val pcTickContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default,
) : ViewModel() {

    private val uptime = uptimeMs
    val settings: SettingsRepository = settingsRepository ?: SettingsRepository(InMemoryTextStorage(), viewModelScope)
    val profiles: StateFlow<ProfileStore> = settings.store
    val activeProfile: StateFlow<ControllerProfile> =
        settings.store.map { it.active }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.store.value.active)

    val hidState: StateFlow<HidState> = hid.state
    val senderStats: StateFlow<SenderStats> = hid.stats

    val input = ControllerInputManager(sink = { report, t -> hid.sendReport(report, t) }, clock = uptimeMs)
    val gamepad: StateFlow<GamepadState> = input.state

    private val _permission = MutableStateFlow(PermissionState.REQUIRED)
    val permission: StateFlow<PermissionState> = _permission.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _bondedHosts = MutableStateFlow<List<HostDevice>>(emptyList())
    val bondedHosts: StateFlow<List<HostDevice>> = _bondedHosts.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    /** Layout editor active: controls are not interactive and send nothing. */
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _connectedSince = MutableStateFlow<Long?>(null)
    /** Uptime (ms) when the current connection was confirmed by the stack; null when not connected. */
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun uptimeNow(): Long = uptime()

    // ------------------------------------------------------------------ gyroscope right stick

    val gyroStatus: com.ps2gamepad.motion.MotionStatus = motionSource?.status ?: com.ps2gamepad.motion.MotionStatus(false, false)
    private val _gyroReading = MutableStateFlow(com.ps2gamepad.motion.GyroReading.NONE)
    /** Latest gyro reading (tilt, stick vector, status) for the gyro screen. */
    val gyroReading: StateFlow<com.ps2gamepad.motion.GyroReading> = _gyroReading.asStateFlow()
    private val _gyroSending = MutableStateFlow(false)
    /** True while the gyro drives the right stick that goes to the PC. */
    val gyroSending: StateFlow<Boolean> = _gyroSending.asStateFlow()
    @Volatile private var gyroEngine: com.ps2gamepad.motion.GyroEngine? = null
    private var windowFocused = true
    /** Last right-stick axes forwarded from the gyro (jitter filter). */
    @Volatile private var lastGyroAxes = 0 to 0
    /** Input reset counter seen at the last gyro forward: a reset (screen change, reconnect...) forces a resend. */
    @Volatile private var lastGyroEpoch = -1

    fun updateGyro(f: (com.ps2gamepad.model.GyroSettings) -> com.ps2gamepad.model.GyroSettings) {
        settings.update { s -> ProfileManager.updateActive(s) { it.copy(gyro = f(it.gyro)) } }
        refreshMotion()
    }

    /** Use the current hold as neutral right now. */
    fun gyroRecenter() { gyroEngine?.requestRecenter(); log.add("Gyro: recentred") }

    // ------------------------------------------------------------------ PC Gaming Mode

    private val pcSink = object : com.ps2gamepad.pc.PcReportSink {
        // Keyboard/mouse reports exist only with the PC input (composite) descriptor.
        override fun keyboard(report: ByteArray, eventUptimeMs: Long) =
            profiles.value.pcInputEnabled && hid.sendInputReport(com.ps2gamepad.bluetooth.HidDescriptor.KEYBOARD_REPORT_ID, report, eventUptimeMs)
        override fun mouse(report: ByteArray, eventUptimeMs: Long) =
            profiles.value.pcInputEnabled && hid.sendInputReport(com.ps2gamepad.bluetooth.HidDescriptor.MOUSE_REPORT_ID, report, eventUptimeMs)
    }
    val pcInput = com.ps2gamepad.pc.PcInputEngine(pcSink, uptimeMs)

    private val _pcEditMode = MutableStateFlow(false)
    val pcEditMode: StateFlow<Boolean> = _pcEditMode.asStateFlow()
    private val _pcGyroPaused = MutableStateFlow(false)
    /** Gyro mouse temporarily stopped by the on-screen toggle (controller stays connected). */
    val pcGyroPaused: StateFlow<Boolean> = _pcGyroPaused.asStateFlow()
    private val _pcGyroRunning = MutableStateFlow(false)
    val pcGyroRunning: StateFlow<Boolean> = _pcGyroRunning.asStateFlow()
    private val _pcResetEpoch = MutableStateFlow(0)
    /** Bumped by every PC release-all: controls drop fingers that were down (no stale presses). */
    val pcResetEpoch: StateFlow<Int> = _pcResetEpoch.asStateFlow()
    @Volatile private var gyroMouse: com.ps2gamepad.pc.GyroMouseEngine? = null
    /** Right-stick mouse speed (counts/s), applied by the ticker. */
    @Volatile private var pcStickVelocity = 0f to 0f
    @Volatile private var pcWheelHeld = 0
    private var pcTickJob: Job? = null
    /** Wakes the mouse ticker when the right stick moves or a wheel button is pressed. */
    private val pcWake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    val pcStore: com.ps2gamepad.pc.PcStore get() = profiles.value.pc

    fun updatePc(f: (com.ps2gamepad.pc.PcStore) -> com.ps2gamepad.pc.PcStore) {
        settings.update { it.copy(pc = f(it.pc)) }
        refreshMotion()
    }

    /** Mode selection: remembered, then opens that mode. */
    fun chooseMode(mode: com.ps2gamepad.model.GamingMode) {
        settings.update { it.copy(lastMode = mode) }
        pcReleaseAll("mode switch")
        navigate(if (mode == com.ps2gamepad.model.GamingMode.EMULATOR) Screen.LOADOUTS else Screen.PC_CONTROLLER)
    }

    /**
     * Turns PC input (keyboard + mouse HID) on/off. The descriptor changes, so the app re-registers;
     * Windows must remove the phone and pair again once (it caches descriptors).
     */
    fun setPcInputEnabled(on: Boolean) {
        if (on == profiles.value.pcInputEnabled) return
        pcReleaseAll("PC input " + if (on) "enabled" else "disabled")
        settings.update { it.copy(pcInputEnabled = on) }
        log.add(if (on) "PC input enabled: re-registering as gamepad + keyboard + mouse. Remove the phone in Windows Bluetooth and pair again once."
                else "PC input disabled: re-registering as gamepad only. Re-pair once in Windows.")
        val reg = hidState.value.registration
        if (reg == RegistrationState.Registered || reg == RegistrationState.Registering) {
            hid.unregister()
            wantRegistered = true
            autoRegisterAttempts = 0
            registerInternal()
        }
        refreshMotion()
    }

    /** A PC control was pressed (touch down). */
    fun pcPress(c: com.ps2gamepad.pc.PcControl, t: Long = uptime()) {
        when (c.action.type) {
            com.ps2gamepad.pc.PcActionType.KEY, com.ps2gamepad.pc.PcActionType.MOUSE -> pcInput.press(c.id, c.action, t)
            com.ps2gamepad.pc.PcActionType.WHEEL_UP -> { pcInput.wheel(1, t); pcWheelHeld = 1; wheelRepeatAt = uptime() + WHEEL_REPEAT_DELAY_MS; pcWake.trySend(Unit) }
            com.ps2gamepad.pc.PcActionType.WHEEL_DOWN -> { pcInput.wheel(-1, t); pcWheelHeld = -1; wheelRepeatAt = uptime() + WHEEL_REPEAT_DELAY_MS; pcWake.trySend(Unit) }
            com.ps2gamepad.pc.PcActionType.GYRO_TOGGLE -> togglePcGyro()
            com.ps2gamepad.pc.PcActionType.GYRO_RECENTER -> pcGyroRecenter()
        }
    }

    /** A PC control was released (touch up / cancel). */
    fun pcRelease(c: com.ps2gamepad.pc.PcControl, t: Long = uptime()) {
        pcInput.release(c.id, t)
        if (c.action.type == com.ps2gamepad.pc.PcActionType.WHEEL_UP || c.action.type == com.ps2gamepad.pc.PcActionType.WHEEL_DOWN) pcWheelHeld = 0
    }

    /** Left stick: the movement keys currently held. */
    fun pcMoveKeys(c: com.ps2gamepad.pc.PcControl, usages: Set<Int>, t: Long = uptime()) = pcInput.setHeldKeys(c.id, usages, t)

    /** Right stick: deflection (-1..1) -> mouse speed. */
    fun pcMouseStick(x: Float, y: Float) {
        val speed = pcStore.active.mouse.stickMouseSpeed
        pcStickVelocity = (x * speed) to (y * speed)
        if (x != 0f || y != 0f) pcWake.trySend(Unit)
    }

    fun togglePcGyro() {
        _pcGyroPaused.value = !_pcGyroPaused.value
        log.add("Gyro mouse " + if (_pcGyroPaused.value) "paused" else "resumed")
        refreshMotion()
    }

    fun pcGyroRecenter() { gyroMouse?.requestRecenter(); log.add("Gyro mouse: recentred") }
    fun pcGyroCalibrate() { gyroMouse?.requestCalibration(); log.add("Gyro mouse: calibrating, keep the phone still") }

    fun setPcEditMode(on: Boolean) {
        pcReleaseAll("PC edit mode")
        _pcEditMode.value = on
        refreshMotion()
        refreshPcTicker()
    }

    /** Releases every PC key/mouse button and stops movement; controls drop their fingers. */
    fun pcReleaseAll(reason: String) {
        pcStickVelocity = 0f to 0f
        pcWheelHeld = 0
        val wasActive = !pcInput.state.value.idle
        pcInput.releaseAll(uptime())
        _pcResetEpoch.value = _pcResetEpoch.value + 1
        if (wasActive) log.add("PC input released ($reason)")
    }

    private var wheelRepeatAt = 0L

    /** Mouse ticker: stick mouse movement, wheel auto-repeat. Runs only while the PC controller is shown. */
    private fun refreshPcTicker() {
        val run = _screen.value == Screen.PC_CONTROLLER && !_pcEditMode.value
        if (run && pcTickJob == null) {
            pcTickJob = viewModelScope.launch(pcTickContext) {
                var last = uptime()
                while (true) {
                    // Idle (stick centred, no wheel held): sleep until woken, no CPU or battery use.
                    if (pcStickVelocity.first == 0f && pcStickVelocity.second == 0f && pcWheelHeld == 0) {
                        pcInput.flushMotion(uptime())
                        pcWake.receive()
                        last = uptime()
                    }
                    delay(PC_TICK_MS)
                    val now = uptime()
                    val dt = ((now - last).coerceIn(0, 50)) / 1000f
                    last = now
                    val (vx, vy) = pcStickVelocity
                    if (vx != 0f || vy != 0f) pcInput.addMotion(vx * dt, vy * dt)
                    pcInput.flushMotion(now)
                    val w = pcWheelHeld
                    if (w != 0 && now >= wheelRepeatAt) { pcInput.wheel(w, now); wheelRepeatAt = now + WHEEL_REPEAT_MS }
                }
            }
        } else if (!run && pcTickJob != null) {
            pcTickJob?.cancel(); pcTickJob = null
        }
    }

    /** Sensor thread: gyro mouse. */
    private fun onPcMotionSample(m: com.ps2gamepad.pc.GyroMouseEngine, sample: com.ps2gamepad.motion.MotionSample) {
        if (m !== gyroMouse) return
        val (dx, dy) = m.onSample(sample) ?: return
        if (dx == 0f && dy == 0f) return
        pcInput.addMotion(dx, dy)
        pcInput.flushMotion(uptime())
    }

    /** Average the hold for 1.5 s (keep still) and use it as neutral. */
    fun gyroCalibrate() { gyroEngine?.requestCalibration(); log.add("Gyro: calibrating, hold still") }

    /** Window focus is back (after a dialog, the notification shade...). */
    fun onFocusGained() { windowFocused = true; refreshMotion() }

    /**
     * Starts/stops the sensors and decides whether the gyro drives the right stick. The sensors run
     * only on the controller (gyro on) and on the gyro test screen, with the app visible and focused.
     * Whenever the gyro stops driving the stick, a centred right stick is sent.
     */
    fun refreshMotion() {
        val source = motionSource ?: return
        val store = settings.store.value
        val gyro = store.active.gyro
        val screen = _screen.value
        val visible = inForeground && windowFocused
        val usable = source.status.usable
        // Emulator gyro right stick (rules unchanged)
        val send = gyro.enabled && visible && usable &&
            ((screen == Screen.CONTROLLER && !_editMode.value) || screen == Screen.GYRO)
        val runStick = usable && visible && (send || screen == Screen.GYRO)
        // PC Mode gyro mouse
        val pcMouse = store.pc.active.mouse
        val runMouse = usable && visible && screen == Screen.PC_CONTROLLER && !_pcEditMode.value &&
            pcMouse.gyroEnabled && !_pcGyroPaused.value && store.pcInputEnabled

        if (_gyroSending.value && !send) {
            _gyroSending.value = false
            input.setStick(Stick.RIGHT, com.ps2gamepad.controller.StickVector.ZERO)
            lastGyroAxes = 0 to 0
        }
        val want = when { runStick -> MotionConsumer.STICK; runMouse -> MotionConsumer.MOUSE; else -> MotionConsumer.NONE }
        if (want != motionConsumer) {
            if (motionConsumer != MotionConsumer.NONE) {
                source.stop()
                gyroEngine = null
                gyroMouse = null
                _gyroReading.value = com.ps2gamepad.motion.GyroReading.NONE
            }
            motionConsumer = want
            when (want) {
                MotionConsumer.STICK -> {
                    val engine = com.ps2gamepad.motion.GyroEngine(store.gyroNeutral)
                    engine.settings = gyro
                    gyroEngine = engine
                    if (!source.start { sample -> onMotionSample(engine, sample) }) { gyroEngine = null; motionConsumer = MotionConsumer.NONE; log.add("Gyro: sensors not available") }
                }
                MotionConsumer.MOUSE -> {
                    val m = com.ps2gamepad.pc.GyroMouseEngine()
                    m.settings = pcMouse
                    gyroMouse = m
                    if (!source.start { sample -> onPcMotionSample(m, sample) }) { gyroMouse = null; motionConsumer = MotionConsumer.NONE; log.add("Gyro mouse: sensors not available") }
                }
                MotionConsumer.NONE -> Unit
            }
        }
        gyroEngine?.settings = gyro
        gyroMouse?.settings = pcMouse
        _pcGyroRunning.value = gyroMouse != null
        if (send && !_gyroSending.value) lastGyroAxes = Int.MIN_VALUE to Int.MIN_VALUE // force the first value out
        _gyroSending.value = send && gyroEngine != null
    }

    private enum class MotionConsumer { NONE, STICK, MOUSE }
    @Volatile private var motionConsumer = MotionConsumer.NONE

    /** Sensor thread. */
    private fun onMotionSample(engine: com.ps2gamepad.motion.GyroEngine, sample: com.ps2gamepad.motion.MotionSample) {
        if (engine !== gyroEngine) return // stopped meanwhile
        val reading = engine.onSample(sample) { neutral ->
            viewModelScope.launch { settings.update { it.copy(gyroNeutral = neutral) } }
        } ?: return
        _gyroReading.value = reading
        if (!_gyroSending.value) return
        val ax = com.ps2gamepad.controller.StickMath.toAxis(reading.stick.x)
        val ay = com.ps2gamepad.controller.StickMath.toAxis(reading.stick.y)
        val (lx, ly) = lastGyroAxes
        val epoch = input.resetEpoch.value
        // Sensor noise makes the last bit flicker: send only real movement (>= 2 counts), and
        // always send centre and full deflection exactly. After any input reset (which centred the
        // right stick) the current tilt is sent again even if the phone did not move.
        val changed = lx == Int.MIN_VALUE || epoch != lastGyroEpoch ||
            com.ps2gamepad.controller.StickMath.axisChangedSignificantly(lx, ax, GYRO_AXIS_THRESHOLD) ||
            com.ps2gamepad.controller.StickMath.axisChangedSignificantly(ly, ay, GYRO_AXIS_THRESHOLD)
        if (changed) {
            lastGyroAxes = ax to ay
            lastGyroEpoch = epoch
            input.setStick(Stick.RIGHT, reading.stick)
        }
    }

    // ------------------------------------------------------------------ simple mode (auto start / connect / name)

    private val _phoneName = MutableStateFlow<String?>(null)
    /** The phone's Bluetooth name as the PC sees it. */
    val phoneName: StateFlow<String?> = _phoneName.asStateFlow()
    /** True after the user explicitly tapped Unregister: then nothing starts automatically. */
    private var userUnregistered = false
    private var autoConnectJob: Job? = null

    /** Laptops/PCs paired with the phone (earbuds and other devices are hidden). */
    val computerHosts: List<HostDevice> get() = _bondedHosts.value.filter { it.isComputer }

    /** The PC to connect to: the last one used, else the only paired computer. */
    val knownPc: HostDevice?
        get() = lastHost
            ?: profiles.value.lastConnection?.let { lc -> lc.address?.let { HostDevice(it, lc.hostName) } }
            ?: computerHosts.singleOrNull()

    /**
     * Called whenever the app comes to the foreground (and after permissions are granted): names the
     * phone "PS2 Gamepad" and registers the gamepad, so the user only has to open the app.
     */
    fun autoStart() {
        if (permission.value != PermissionState.GRANTED || bluetoothState.value != BluetoothState.ENABLED) return
        applyPhoneName()
        if (userUnregistered) return
        wantRegistered = true
        if (hidState.value.registration == RegistrationState.NotRegistered) {
            log.add("Auto start: registering gamepad")
            registerInternal()
        }
    }

    fun applyPhoneName() {
        val c = phoneNameControl ?: return
        val before = c.current()
        if (before != com.ps2gamepad.bluetooth.PhoneNameControl.GAMEPAD_NAME) {
            val ok = c.applyGamepadName()
            log.add(if (ok) "Phone Bluetooth name changed from \"$before\" to \"${com.ps2gamepad.bluetooth.PhoneNameControl.GAMEPAD_NAME}\""
                    else "Could not rename the phone (still \"$before\")")
        }
        _phoneName.value = c.current() ?: before
    }

    fun restorePhoneName() {
        val c = phoneNameControl ?: return
        val original = c.originalName
        log.add(if (c.restoreOriginal()) "Phone name restored to \"$original\"" else "Could not restore the phone name")
        _phoneName.value = c.current()
    }

    fun connectKnownPc() {
        val pc = knownPc ?: run { log.add("No paired laptop known: pair from the laptop first"); return }
        connect(pc)
    }

    /** After registering, connect to the known PC automatically unless the user disconnected. */
    private fun scheduleAutoConnect() {
        autoConnectJob?.cancel()
        if (userDisconnected) return
        autoConnectJob = viewModelScope.launch {
            delay(autoConnectDelayMs) // give the stack's own reconnect a chance first
            val st = hidState.value
            if (st.isConnected || st.connection is ConnectionState.Connecting || userDisconnected) return@launch
            if (st.registration != RegistrationState.Registered) return@launch
            val pc = knownPc ?: return@launch
            log.add("Auto connect to ${pc.displayName}")
            hid.connect(pc.address)
        }
    }

    /** True while the user wants the HID app registered (survives stack-initiated unregistration). */
    private var wantRegistered = false
    private var userDisconnected = false
    private var lastHost: HostDevice? = null
    private var reconnectJob: Job? = null
    private var inForeground = false
    /** Automatic re-registrations since the last user action or successful connection (loop guard). */
    private var autoRegisterAttempts = 0
    private var previous: HidState = hid.state.value

    init {
        compatibility.forEach { log.add(it) }
        viewModelScope.launch { hid.state.collect(::onHidState) }
        viewModelScope.launch { bluetoothState.collect(::onBluetoothState) }
    }

    // ------------------------------------------------------------------ inputs from the Activity

    fun onPermissionsChanged(granted: Boolean) {
        val newState = if (granted) PermissionState.GRANTED else PermissionState.REQUIRED
        if (newState != _permission.value) log.add("Bluetooth permissions: ${if (granted) "granted" else "required"}")
        _permission.value = newState
        if (granted) refreshBondedHosts()
    }

    /** Activity resumed. The stack unregisters HID apps that leave the foreground, so restore it. */
    fun onForeground() {
        inForeground = true
        refreshMotion()
        refreshBondedHosts()
        if (wantRegistered && hidState.value.registration == RegistrationState.NotRegistered) {
            log.add("Back in foreground: HID registration was dropped by the system, registering again")
            autoRegister()
        }
    }

    /** Activity stopped (not visible). Android drops HID registrations of background apps. */
    fun onBackground() {
        inForeground = false
        if (_screen.value == Screen.PC_CONTROLLER || !pcInput.state.value.idle) pcReleaseAll("app in background")
        refreshMotion()
        input.releaseAll()
    }

    /** Activity paused / lost window focus: never leave buttons held on the host. */
    fun onInterrupted(reason: String) {
        windowFocused = false
        if (_screen.value == Screen.PC_CONTROLLER || !pcInput.state.value.idle) pcReleaseAll(reason)
        refreshMotion()
        if (input.state.value != GamepadState.NEUTRAL || input.activePointers.value > 0) {
            log.add("Input interrupted ($reason): all inputs released")
        }
        input.releaseAll()
    }

    // ------------------------------------------------------------------ user actions

    fun register() {
        userUnregistered = false
        wantRegistered = true
        autoRegisterAttempts = 0
        registerInternal()
    }

    private fun autoRegister() {
        if (autoRegisterAttempts >= MAX_AUTO_REGISTER) {
            log.add("Not re-registering automatically: dropped $autoRegisterAttempts times in a row. Tap Register HID Device.")
            wantRegistered = false
            return
        }
        autoRegisterAttempts++
        registerInternal()
    }

    private fun registerInternal() {
        when {
            bluetoothState.value != BluetoothState.ENABLED -> log.add("Cannot register: ${bluetoothState.value.label}")
            permission.value != PermissionState.GRANTED -> log.add("Cannot register: Bluetooth permission required")
            else -> { log.add("Registering HID gamepad..."); hid.register(autoConnectPlugged = !userDisconnected, composite = profiles.value.pcInputEnabled) }
        }
    }

    fun unregister() {
        userUnregistered = true
        autoConnectJob?.cancel()
        wantRegistered = false
        userDisconnected = true
        reconnectJob?.cancel()
        input.releaseAll()
        log.add("Unregistering HID gamepad")
        hid.unregister()
    }

    fun connect(host: HostDevice) {
        userDisconnected = false
        lastHost = host
        log.add("Connecting to ${host.displayName}")
        hid.connect(host.address)
    }

    fun disconnect() {
        userDisconnected = true
        autoConnectJob?.cancel()
        reconnectJob?.cancel()
        input.releaseAll()
        hid.disconnect()
    }

    fun refreshBondedHosts() {
        if (permission.value == PermissionState.GRANTED) _bondedHosts.value = bondedHostsProvider()
    }

    /** Phase 1 diagnostic buttons: toggle HID buttons 1-3. */
    fun toggleTestButton(index: Int) {
        val button = when (index) { 1 -> ControllerButton.SQUARE; 2 -> ControllerButton.CROSS; 3 -> ControllerButton.CIRCLE; else -> return }
        val pressed = !input.state.value.isPressed(button)
        input.setButton(button, pressed)
        log.add("Test Button $index (HID button ${button.hidButton}) ${if (pressed) "pressed" else "released"}" + sentSuffix())
    }

    fun releaseAllInputs() {
        input.releaseAll()
        log.add("Release All: neutral report" + sentSuffix())
    }

    private fun sentSuffix() = if (hidState.value.isConnected) "" else " (not sent: no host connected)"

    // ------------------------------------------------------------------ customisation & profiles

    fun updateAppearance(f: (Appearance) -> Appearance) =
        settings.update { s -> ProfileManager.updateActive(s) { it.copy(appearance = f(it.appearance)) } }

    fun updateStick(stick: Stick, f: (StickSettings) -> StickSettings) = settings.update { s ->
        ProfileManager.updateActive(s) { if (stick == Stick.LEFT) it.copy(left = f(it.left)) else it.copy(right = f(it.right)) }
    }

    /** Opacity of one control (1 = fully visible); stored per profile. */
    fun setControlOpacity(id: ControlId, value: Float) = updateAppearance {
        it.copy(controlOpacity = if (value >= 0.995f) it.controlOpacity - id else it.controlOpacity + (id to value))
    }

    fun savePlacements(placements: Map<ControlId, Placement>) =
        settings.update { s -> ProfileManager.updateActive(s) { it.copy(placements = placements) } }

    /** Layout editor "Save": positions/sizes of the built-in controls plus the user's extra buttons. */
    fun saveLayout(placements: Map<ControlId, Placement>, extras: List<com.ps2gamepad.model.ExtraButton>) =
        settings.update { s -> ProfileManager.updateActive(s) { it.copy(placements = placements, extraButtons = extras) } }

    fun resetLayout() {
        settings.update { s -> ProfileManager.updateActive(s) { it.copy(placements = emptyMap()) } }
        log.add("Layout reset to default positions")
    }

    fun resetAppearance() = settings.update { s -> ProfileManager.updateActive(s) { it.copy(appearance = Appearance()) } }

    fun switchProfile(id: String) {
        input.releaseAll()
        settings.update { ProfileManager.switchTo(it, id) }
        refreshMotion()
    }
    fun createProfile(name: String) {
        if (!ProfileManager.canCreateLoadout(profiles.value)) { log.add(LOADOUT_LIMIT_MESSAGE); return }
        settings.update { ProfileManager.create(it, name, newId) }
    }
    fun duplicateProfile(id: String) {
        if (!ProfileManager.canCreateLoadout(profiles.value)) { log.add(LOADOUT_LIMIT_MESSAGE); return }
        settings.update { ProfileManager.duplicate(it, id, newId) }
    }

    // ------------------------------------------------------------------ loadouts (per-game setups)

    /** The user's own loadouts (max [ProfileManager.MAX_LOADOUTS]). */
    val loadouts: List<ControllerProfile> get() = ProfileManager.loadouts(profiles.value)
    val canCreateLoadout: Boolean get() = ProfileManager.canCreateLoadout(profiles.value)

    /** "Open Controller" always goes through the loadout picker. */
    /** "Open Controller" asks for the gaming mode first (Emulator -> loadout picker, PC -> PC controller). */
    fun openController() = navigate(Screen.MODE_SELECT)

    /** Play with [id]: make it active and open the controller. */
    fun playLoadout(id: String) {
        switchProfile(id)
        navigate(Screen.CONTROLLER)
    }

    /**
     * Creates a loadout named [name] from the current setup, makes it active and opens the
     * controller in Edit mode so the buttons can be arranged right away. Returns false at the limit.
     */
    fun createLoadout(name: String): Boolean {
        val before = profiles.value
        if (!ProfileManager.canCreateLoadout(before)) { log.add(LOADOUT_LIMIT_MESSAGE); return false }
        settings.update { ProfileManager.createFrom(it, name, it.activeId, newId) }
        log.add("Loadout \"${profiles.value.active.name}\" created")
        navigate(Screen.CONTROLLER)
        setEditMode(true)
        refreshMotion()
        return true
    }

    fun deleteLoadout(id: String) {
        val name = profiles.value.profiles.firstOrNull { it.id == id }?.name
        settings.update { ProfileManager.delete(it, id) }
        log.add("Loadout \"$name\" deleted")
        refreshMotion()
    }
    fun renameProfile(id: String, name: String) = settings.update { ProfileManager.rename(it, id, name) }
    fun deleteProfile(id: String) = settings.update { ProfileManager.delete(it, id) }
    fun restoreProfileDefaults(id: String) = settings.update { ProfileManager.restoreDefaults(it, id) }

    /** Stores the thumb's natural resting point (fraction of travel) as the fixed-mode centre. */
    fun calibrateCenter(stick: Stick, offsetX: Float, offsetY: Float) {
        updateStick(stick) { it.copy(centerOffsetX = offsetX, centerOffsetY = offsetY) }
        log.add("Calibrated $stick stick centre: (%.2f, %.2f)".format(offsetX, offsetY))
    }

    fun setEditMode(on: Boolean) {
        input.releaseAll() // nothing may stay held while controls move
        _editMode.value = on
        refreshMotion()
    }

    /** Overlapping controls in the active layout for a screen of [w] x [h] dp. */
    fun layoutOverlaps(w: Float, h: Float) = activeProfile.value.let {
        LayoutEngine.overlaps(LayoutEngine.resolve(w, h, it.appearance, it.placements))
    }

    fun reconnect() {
        val host = lastHost ?: profiles.value.lastConnection?.let { lc -> lc.address?.let { HostDevice(it, lc.hostName) } }
        if (host == null) { log.add("Reconnect: no previous host known"); return }
        connect(host)
    }

    val lastKnownHostName: String? get() = lastHost?.displayName ?: profiles.value.lastConnection?.hostName

    fun navigate(to: Screen) {
        if (to != _screen.value) input.releaseAll()
        if (to != _screen.value && (to == Screen.PC_CONTROLLER || _screen.value == Screen.PC_CONTROLLER)) pcReleaseAll("screen change")
        if (to != Screen.PC_CONTROLLER) _pcEditMode.value = false
        if (to != Screen.CONTROLLER) _editMode.value = false
        if (to == Screen.CONTROLLER && _session.value == null) _session.value = Session(uptime(), batteryPercent())
        _screen.value = to
        refreshMotion()
        refreshPcTicker()
    }

    fun resetSession() { _session.value = Session(uptime(), batteryPercent()) }

    /** Persist pending settings now (app going to the background). */
    fun flushSettings() = settings.flush()

    // ------------------------------------------------------------------ state reactions

    private fun onBluetoothState(s: BluetoothState) {
        log.add("Adapter: ${s.label}")
        when (s) {
            BluetoothState.TURNING_OFF, BluetoothState.DISABLED -> {
                reconnectJob?.cancel()
                input.releaseAll()
                hid.onBluetoothOff()
            }
            BluetoothState.ENABLED -> {
                refreshBondedHosts()
                if (inForeground) autoStart()
                if (wantRegistered && hidState.value.registration == RegistrationState.NotRegistered) {
                    log.add("Bluetooth back on: registering again")
                    autoRegister()
                }
            }
            else -> Unit
        }
    }

    private fun onHidState(s: HidState) {
        val prev = previous
        previous = s
        if (prev.registration != s.registration) log.add("Registration: ${s.registration.label}")
        if (prev.connection != s.connection) log.add("Connection: ${s.connection.label}")

        if (s.registration is RegistrationState.Failed) wantRegistered = false
        if (prev.registration != RegistrationState.Registered && s.registration == RegistrationState.Registered && !s.isConnected) {
            scheduleAutoConnect()
        }
        // Registration dropped by the system while we are visible (e.g. a dialog briefly took the
        // foreground). In the background we wait for onForeground() instead.
        if (prev.registration == RegistrationState.Registered && s.registration == RegistrationState.NotRegistered &&
            wantRegistered && inForeground && bluetoothState.value == BluetoothState.ENABLED
        ) {
            log.add("HID registration dropped by the system, registering again")
            autoRegister()
        }

        val wasConnected = prev.connection is ConnectionState.Connected
        if (s.isConnected && !wasConnected) {
            autoConnectJob?.cancel() // connected: a pending auto-connect must not fire later
            _connectedSince.value = uptime()
            s.connectedHost?.let { h ->
                settings.update { ProfileManager.recordConnection(it, LastConnection(h.displayName, wallClockMs(), h.address)) }
            }
            reconnectJob?.cancel()
            userDisconnected = false
            autoRegisterAttempts = 0
            lastHost = s.connectedHost
            // Fresh baseline for the host (HidDeviceManager also queues one).
            input.releaseAll()
            if (s.isConnected && profiles.value.pcInputEnabled && _screen.value == Screen.PC_CONTROLLER) pcReleaseAll("connected")
        } else if (wasConnected && !s.isConnected) {
            _connectedSince.value = null
            // Stop, clear touches, reset state. The sink already drops reports while disconnected.
            input.releaseAll()
            input.onHostLost()
            pcReleaseAll("connection lost")
            pcInput.onHostLost()
            log.add("WARNING: host connection lost - inputs reset")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        val host = lastHost ?: return
        if (userDisconnected || !wantRegistered) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for ((i, d) in reconnectDelaysMs.withIndex()) {
                delay(d)
                val st = hidState.value
                if (st.isConnected || !wantRegistered || userDisconnected) return@launch
                if (st.registration != RegistrationState.Registered || bluetoothState.value != BluetoothState.ENABLED) continue
                log.add("Auto-reconnect attempt ${i + 1}/${reconnectDelaysMs.size} to ${host.displayName}")
                hid.connect(host.address)
            }
        }
    }

    companion object {
        const val PC_TICK_MS = 8L
        const val WHEEL_REPEAT_DELAY_MS = 350L
        const val WHEEL_REPEAT_MS = 110L
        const val LOADOUT_LIMIT_MESSAGE = "You already have 5 loadouts. Delete one to create a new one."
        const val MAX_AUTO_REGISTER = 3
        /** Gyro jitter filter: right-stick change (HID counts) needed before a new report. */
        const val GYRO_AXIS_THRESHOLD = 2
    }

    override fun onCleared() {
        pcTickJob?.cancel()
        pcInput.releaseAll()
        motionSource?.stop()
        gyroEngine = null
        reconnectJob?.cancel()
        input.releaseAll()
        cleanup()
        hid.close()
    }
}
