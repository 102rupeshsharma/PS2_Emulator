package com.ps2gamepad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.core.content.edit

/**
 * Windows shows a Bluetooth gamepad under the PHONE's Bluetooth name (not the app name).
 * This renames the phone to [GAMEPAD_NAME] so the PC lists "PadLink", and remembers the
 * original name so it can be restored. Names used by older versions ([LEGACY_NAMES]) are treated
 * as gamepad names, never saved as the phone's own name.
 */
interface PhoneNameControl {
    fun current(): String?
    /** Sets the gamepad name if it is not already set. Returns true when the name is (now) the gamepad name. */
    fun applyGamepadName(): Boolean
    /** Restores the name the phone had before; returns true on success. */
    fun restoreOriginal(): Boolean
    val originalName: String?

    companion object {
        const val GAMEPAD_NAME = "PadLink"
        /** Bluetooth names set by earlier versions (1.0 - 1.9 used "PS2 Gamepad"). */
        val LEGACY_NAMES = setOf("PS2 Gamepad")
    }
}

@SuppressLint("MissingPermission") // only called after BLUETOOTH_CONNECT is granted; SecurityException is caught
class AdapterPhoneName(context: Context, private val adapter: () -> BluetoothAdapter?) : PhoneNameControl {
    private val prefs = context.applicationContext.getSharedPreferences("phone_name", Context.MODE_PRIVATE)

    override val originalName: String? get() = prefs.getString(KEY_ORIGINAL, null)

    override fun current(): String? = try { adapter()?.name } catch (e: SecurityException) { null }

    override fun applyGamepadName(): Boolean = try {
        val a = adapter() ?: return false
        val now = a.name
        if (now == PhoneNameControl.GAMEPAD_NAME) true
        else {
            if (originalName == null && !now.isNullOrBlank() && now !in PhoneNameControl.LEGACY_NAMES) prefs.edit { putString(KEY_ORIGINAL, now) }
            a.setName(PhoneNameControl.GAMEPAD_NAME)
        }
    } catch (e: SecurityException) { false }

    override fun restoreOriginal(): Boolean = try {
        val o = originalName ?: return false
        val ok = adapter()?.setName(o) == true
        if (ok) prefs.edit { remove(KEY_ORIGINAL) }
        ok
    } catch (e: SecurityException) { false }

    private companion object { const val KEY_ORIGINAL = "original" }
}
