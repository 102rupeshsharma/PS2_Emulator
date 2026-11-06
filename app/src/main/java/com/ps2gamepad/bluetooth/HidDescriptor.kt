package com.ps2gamepad.bluetooth

/**
 * HID report descriptor for a generic gamepad. Windows binds its built-in HID game controller
 * driver (HidGame / DirectInput / Raw Input) to it; no custom driver is needed.
 *
 * The descriptor is fixed from Phase 1 onwards. Windows caches it at pairing time, so changing it
 * later would force a re-pair. Phase 1 already declares all 12 buttons that Phase 2 needs.
 *
 * Input report, Report ID 1. 7 bytes on the air: the report ID plus the 6 data bytes below.
 * `BluetoothHidDevice.sendReport(device, 1, data)` adds the ID, so [REPORT_LENGTH] = 6.
 *
 *  byte 0  bit0..7  Buttons 1-8   (Square, Cross, Circle, Triangle, L1, R1, L2, R2)
 *  byte 1  bit0..3  Buttons 9-12  (Select, Start, L3, R3)
 *  byte 1  bit4..7  Hat switch    0..7 = N,NE,E,SE,S,SW,W,NW; 8 = null (centred)
 *  byte 2           X   left stick horizontal   int8 -127..127 (0 = centre, - = left)
 *  byte 3           Y   left stick vertical     int8 -127..127 (- = up)
 *  byte 4           Z   right stick horizontal  int8 -127..127
 *  byte 5           Rz  right stick vertical    int8 -127..127
 */
object HidDescriptor {
    const val REPORT_ID: Int = 1
    const val REPORT_LENGTH: Int = 6
    const val BUTTON_COUNT: Int = 12
    const val HAT_NULL: Int = 8

    const val SDP_NAME = "Bluetooth Gamepad Prototype"
    const val SDP_DESCRIPTION = "Android Bluetooth HID gamepad"
    const val SDP_PROVIDER = "PS2Gamepad"

    /** Each line is one HID item: prefix byte (tag/type/size) followed by its data bytes. */
    val REPORT_DESCRIPTOR: ByteArray = bytes(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x05,             // Usage (Game Pad)
        0xA1, 0x01,             // Collection (Application)
        0x85, REPORT_ID,        //   Report ID (1)

        // 12 buttons, 1 bit each
        0x05, 0x09,             //   Usage Page (Button)
        0x19, 0x01,             //   Usage Minimum (Button 1)
        0x29, BUTTON_COUNT,     //   Usage Maximum (Button 12)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1 bit)
        0x95, BUTTON_COUNT,     //   Report Count (12)
        0x81, 0x02,             //   Input (Data, Variable, Absolute)

        // Hat switch (D-pad), 4 bits, with a null state for "not pressed"
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x39,             //   Usage (Hat Switch)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x07,             //   Logical Maximum (7)
        0x35, 0x00,             //   Physical Minimum (0)
        0x46, 0x3B, 0x01,       //   Physical Maximum (315 degrees)
        0x65, 0x14,             //   Unit (English Rotation: degrees)
        0x75, 0x04,             //   Report Size (4 bits)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x42,             //   Input (Data, Variable, Absolute, Null State)

        // Reset the unit and physical range so they do not apply to the axes
        0x65, 0x00,             //   Unit (None)
        0x45, 0x00,             //   Physical Maximum (0) -> physical range = logical range

        // Two analog sticks: X/Y (left), Z/Rz (right), signed 8-bit
        0x09, 0x30,             //   Usage (X)
        0x09, 0x31,             //   Usage (Y)
        0x09, 0x32,             //   Usage (Z)
        0x09, 0x35,             //   Usage (Rz)
        0x15, 0x81,             //   Logical Minimum (-127)
        0x25, 0x7F,             //   Logical Maximum (127)
        0x75, 0x08,             //   Report Size (8 bits)
        0x95, 0x04,             //   Report Count (4)
        0x81, 0x02,             //   Input (Data, Variable, Absolute)

        0xC0,                   // End Collection
    )

    // ------------------------------------------------------------------ PC Mode (opt-in)

    const val KEYBOARD_REPORT_ID: Int = 2
    const val KEYBOARD_REPORT_LENGTH: Int = 8
    const val MOUSE_REPORT_ID: Int = 3
    const val MOUSE_REPORT_LENGTH: Int = 6

    /**
     * Keyboard top-level collection, Report ID 2, 8 bytes:
     *  byte 0 modifiers (bit0 LCtrl, 1 LShift, 2 LAlt, 3 LGUI, 4 RCtrl, 5 RShift, 6 RAlt, 7 RGUI)
     *  byte 1 reserved, bytes 2..7 up to six pressed key usages (Keyboard/Keypad page 0x07)
     */
    private val KEYBOARD_COLLECTION: ByteArray = bytes(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1, 0x01,             // Collection (Application)
        0x85, KEYBOARD_REPORT_ID, //   Report ID (2)
        0x05, 0x07,             //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,             //   Usage Minimum (Left Control)
        0x29, 0xE7,             //   Usage Maximum (Right GUI)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95, 0x08,             //   Report Count (8)
        0x81, 0x02,             //   Input (Data, Variable, Absolute)  - modifier bits
        0x75, 0x08,             //   Report Size (8)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x01,             //   Input (Constant)                  - reserved byte
        0x75, 0x08,             //   Report Size (8)
        0x95, 0x06,             //   Report Count (6)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x65,             //   Logical Maximum (101)
        0x05, 0x07,             //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,             //   Usage Minimum (0)
        0x29, 0x65,             //   Usage Maximum (101)
        0x81, 0x00,             //   Input (Data, Array)               - key array
        0xC0,                   // End Collection
    )

    /**
     * Mouse top-level collection, Report ID 3, 6 bytes:
     *  byte 0 buttons (bit0 left, 1 right, 2 middle, 3 back, 4 forward), bytes 1-2 X, 3-4 Y
     *  (int16 little endian, RELATIVE), byte 5 wheel (int8, relative)
     */
    private val MOUSE_COLLECTION: ByteArray = bytes(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x02,             // Usage (Mouse)
        0xA1, 0x01,             // Collection (Application)
        0x85, MOUSE_REPORT_ID,  //   Report ID (3)
        0x09, 0x01,             //   Usage (Pointer)
        0xA1, 0x00,             //   Collection (Physical)
        0x05, 0x09,             //     Usage Page (Button)
        0x19, 0x01,             //     Usage Minimum (1)
        0x29, 0x05,             //     Usage Maximum (5)
        0x15, 0x00,             //     Logical Minimum (0)
        0x25, 0x01,             //     Logical Maximum (1)
        0x75, 0x01,             //     Report Size (1)
        0x95, 0x05,             //     Report Count (5)
        0x81, 0x02,             //     Input (Data, Variable, Absolute)
        0x75, 0x03,             //     Report Size (3)
        0x95, 0x01,             //     Report Count (1)
        0x81, 0x01,             //     Input (Constant)                - padding
        0x05, 0x01,             //     Usage Page (Generic Desktop)
        0x09, 0x30,             //     Usage (X)
        0x09, 0x31,             //     Usage (Y)
        0x16, 0x01, 0x80,       //     Logical Minimum (-32767)
        0x26, 0xFF, 0x7F,       //     Logical Maximum (32767)
        0x75, 0x10,             //     Report Size (16)
        0x95, 0x02,             //     Report Count (2)
        0x81, 0x06,             //     Input (Data, Variable, Relative)
        0x09, 0x38,             //     Usage (Wheel)
        0x15, 0x81,             //     Logical Minimum (-127)
        0x25, 0x7F,             //     Logical Maximum (127)
        0x75, 0x08,             //     Report Size (8)
        0x95, 0x01,             //     Report Count (1)
        0x81, 0x06,             //     Input (Data, Variable, Relative)
        0xC0,                   //   End Collection
        0xC0,                   // End Collection
    )

    /**
     * Composite descriptor for PC Mode: the UNCHANGED gamepad collection (Report ID 1) followed by
     * keyboard (2) and mouse (3). Windows creates a game controller, a keyboard and a mouse from it.
     * Opt-in only: changing descriptors needs a one-time re-pair on Windows.
     */
    val COMPOSITE_DESCRIPTOR: ByteArray = REPORT_DESCRIPTOR + KEYBOARD_COLLECTION + MOUSE_COLLECTION

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}
