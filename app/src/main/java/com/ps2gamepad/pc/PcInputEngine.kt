package com.ps2gamepad.pc

import com.ps2gamepad.bluetooth.KeyCodes
import com.ps2gamepad.bluetooth.PcReports
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.truncate

/** Where PC Mode reports go (HID keyboard ID 2 / mouse ID 3). False = dropped (not connected). */
interface PcReportSink {
    fun keyboard(report: ByteArray, eventUptimeMs: Long): Boolean
    fun mouse(report: ByteArray, eventUptimeMs: Long): Boolean
}

/** What is currently held, for the UI / diagnostics. */
data class PcInputState(val keys: List<Int> = emptyList(), val modifiers: Int = 0, val mouseButtons: Int = 0) {
    val idle: Boolean get() = keys.isEmpty() && modifiers == 0 && mouseButtons == 0
}

/**
 * Keyboard + mouse state machine for PC Mode.
 *
 * Every on-screen control is a "source" (its id). A source holds a set of atoms (keys, modifier
 * bits, mouse buttons). Atoms are reference-counted across sources, so two controls mapped to the
 * same key (e.g. Sprint and RT both = Left Shift) never release each other. Reports are sent only on
 * transitions: a held key is reported once (key-down) and once more when released (key-up); Windows
 * does the auto-repeat itself, exactly like a real keyboard.
 *
 * Mouse movement is accumulated with sub-pixel precision and flushed as relative motion.
 * Thread-safe (touch on the main thread, gyro on the sensor thread, ticks on a coroutine).
 */
class PcInputEngine(private val sink: PcReportSink, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Held(val keys: Set<Int>, val mods: Int, val mouse: Int)

    private val lock = Any()
    private val held = HashMap<String, Held>()
    private val keyCount = LinkedHashMap<Int, Int>()     // insertion order = press order
    private val modCount = IntArray(8)
    private val mouseCount = IntArray(5)

    private var lastKeyboard: ByteArray? = null          // null = host state unknown -> next one is sent
    private var lastMouseButtons = -1
    private var accX = 0f
    private var accY = 0f

    private val _state = MutableStateFlow(PcInputState())
    val state: StateFlow<PcInputState> = _state.asStateFlow()

    @Volatile var keyboardReports = 0L; private set
    @Volatile var mouseReports = 0L; private set

    /** Press [action] for [source]. KEY and MOUSE only (wheel/gyro actions are handled by the caller). */
    fun press(source: String, action: PcAction, t: Long = clock()) = synchronized(lock) {
        val h = when (action.type) {
            PcActionType.KEY -> {
                val keyMod = if (KeyCodes.isModifier(action.key)) KeyCodes.modifierBit(action.key) else 0
                Held(if (keyMod == 0 && action.key != 0) setOf(action.key) else emptySet(), action.modifiers or keyMod, 0)
            }
            PcActionType.MOUSE -> Held(emptySet(), 0, action.mouse.bit)
            else -> return@synchronized
        }
        apply(source, h, t)
    }

    fun release(source: String, t: Long = clock()) = synchronized(lock) { apply(source, null, t) }

    /** Stick-to-keys: the exact set of key usages [source] holds now (e.g. {W, D} for up-right). */
    fun setHeldKeys(source: String, usages: Set<Int>, t: Long = clock()) = synchronized(lock) {
        var mods = 0
        val keys = HashSet<Int>()
        for (u in usages) if (KeyCodes.isModifier(u)) mods = mods or KeyCodes.modifierBit(u) else keys += u
        apply(source, if (keys.isEmpty() && mods == 0) null else Held(keys, mods, 0), t)
    }

    /** Adds relative movement (mouse counts, fractional allowed). Sent on [flushMotion]. */
    fun addMotion(dx: Float, dy: Float) = synchronized(lock) {
        if (dx.isFinite() && dy.isFinite()) { accX += dx; accY += dy }
    }

    /** Sends the whole-count part of the accumulated movement (remainder kept). */
    fun flushMotion(t: Long = clock()) = synchronized(lock) { sendMotion(t) }

    /** One wheel step (+ = up). */
    fun wheel(steps: Int, t: Long = clock()) = synchronized(lock) {
        if (steps == 0) return@synchronized
        sendMotion(t)
        sendMouse(currentMouseButtons(), 0, 0, steps, t)
    }

    /** Releases every key and mouse button and tells the host (always sent, even if it looks idle). */
    fun releaseAll(t: Long = clock()) = synchronized(lock) {
        held.clear(); keyCount.clear(); modCount.fill(0); mouseCount.fill(0)
        accX = 0f; accY = 0f
        lastKeyboard = null; lastMouseButtons = -1
        sendKeyboardIfChanged(t)
        sendMouse(0, 0, 0, 0, t)
        publish()
    }

    /** Host gone: forget what it had (full state is sent after reconnect). */
    fun onHostLost() = synchronized(lock) { lastKeyboard = null; lastMouseButtons = -1; accX = 0f; accY = 0f }

    // ------------------------------------------------------------------ internals (lock held)

    private fun apply(source: String, next: Held?, t: Long) {
        val prev = held[source]
        if (prev == next) return
        val prevMods = currentMods(); val prevMouse = currentMouseButtons()
        prev?.let { remove(it) }
        // Combination press (e.g. Ctrl+E): modifiers go out first, then the key, as a real keyboard would.
        if (next != null) {
            add(next.copy(keys = emptySet()))
            if (next.keys.isNotEmpty() && currentMods() != prevMods) sendKeyboardIfChanged(t)
            for (k in next.keys) keyCount[k] = (keyCount[k] ?: 0) + 1
            held[source] = next
        } else held.remove(source)
        // Combination release: the key goes up before its modifiers.
        if (prev != null && next == null && prev.keys.isNotEmpty() && prev.mods != 0) {
            val modsNow = currentMods()
            if (modsNow != prevMods) {
                // temporarily keep the old modifiers for one report with the key already released
                sendKeyboard(PcReports.keyboard(prevMods, activeKeys()), t)
            }
        }
        sendKeyboardIfChanged(t)
        if (currentMouseButtons() != prevMouse) { sendMotion(t); sendMouse(currentMouseButtons(), 0, 0, 0, t) }
        publish()
    }

    private fun add(h: Held) {
        for (k in h.keys) keyCount[k] = (keyCount[k] ?: 0) + 1
        for (b in 0 until 8) if (h.mods and (1 shl b) != 0) modCount[b]++
        for (b in 0 until 5) if (h.mouse and (1 shl b) != 0) mouseCount[b]++
    }

    private fun remove(h: Held) {
        for (k in h.keys) { val c = (keyCount[k] ?: 0) - 1; if (c <= 0) keyCount.remove(k) else keyCount[k] = c }
        for (b in 0 until 8) if (h.mods and (1 shl b) != 0 && modCount[b] > 0) modCount[b]--
        for (b in 0 until 5) if (h.mouse and (1 shl b) != 0 && mouseCount[b] > 0) mouseCount[b]--
    }

    private fun currentMods(): Int { var m = 0; for (b in 0 until 8) if (modCount[b] > 0) m = m or (1 shl b); return m }
    private fun currentMouseButtons(): Int { var m = 0; for (b in 0 until 5) if (mouseCount[b] > 0) m = m or (1 shl b); return m }
    private fun activeKeys(): List<Int> = keyCount.keys.toList()

    private fun sendKeyboardIfChanged(t: Long) {
        val r = PcReports.keyboard(currentMods(), activeKeys())
        if (lastKeyboard?.contentEquals(r) == true) return
        sendKeyboard(r, t)
    }

    private fun sendKeyboard(r: ByteArray, t: Long) {
        if (sink.keyboard(r, t)) { lastKeyboard = r; keyboardReports++ } else lastKeyboard = null
    }

    private fun sendMotion(t: Long) {
        val ix = truncate(accX).toInt().coerceIn(-PcReports.MAX_MOVE, PcReports.MAX_MOVE)
        val iy = truncate(accY).toInt().coerceIn(-PcReports.MAX_MOVE, PcReports.MAX_MOVE)
        if (ix == 0 && iy == 0) return
        accX -= ix; accY -= iy
        if (abs(accX) > 1f) accX = 0f // clamped overflow is dropped, never carried
        if (abs(accY) > 1f) accY = 0f
        sendMouse(currentMouseButtons(), ix, iy, 0, t)
    }

    private fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int, t: Long) {
        if (dx == 0 && dy == 0 && wheel == 0 && buttons == lastMouseButtons) return
        if (sink.mouse(PcReports.mouse(buttons, dx, dy, wheel), t)) { lastMouseButtons = buttons; mouseReports++ }
        else lastMouseButtons = -1 // unknown: the next report carries the full button state
    }

    private fun publish() { _state.value = PcInputState(activeKeys(), currentMods(), currentMouseButtons()) }
}
