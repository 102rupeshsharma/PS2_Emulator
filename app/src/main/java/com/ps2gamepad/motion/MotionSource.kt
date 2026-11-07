package com.ps2gamepad.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Display

/** Which motion sensors the phone has. */
data class MotionStatus(val hasGyroscope: Boolean, val hasAccelerometer: Boolean) {
    val usable: Boolean get() = hasGyroscope && hasAccelerometer
}

/** Raw motion samples. The app's only sensor access; the ViewModel decides when it runs. */
interface MotionSource {
    val status: MotionStatus
    /** Starts delivering samples on a background thread. Returns false if the sensors are missing. */
    fun start(onSample: (MotionSample) -> Unit): Boolean
    fun stop()
}

/**
 * Gyroscope + accelerometer at ~100 Hz on a dedicated thread (100 Hz needs no special permission;
 * Android 12+ only restricts rates above 200 Hz). Stopped whenever the gyro stick is not in use, to
 * save battery. The display rotation is attached to every sample, so a flipped landscape works.
 */
class AndroidMotionSource(context: Context) : MotionSource {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val display: Display? = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    private val gyro: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    override val status = MotionStatus(gyro != null, accel != null)

    @Synchronized
    override fun start(onSample: (MotionSample) -> Unit): Boolean {
        if (listener != null) return true
        val g = gyro ?: return false
        val a = accel ?: return false
        val manager = sm ?: return false
        val t = HandlerThread("motion").apply { start() }
        var rotation = display?.rotation ?: 0
        var lastRotationCheck = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.timestamp - lastRotationCheck > 500_000_000L) { // re-read rotation twice a second
                    rotation = display?.rotation ?: rotation
                    lastRotationCheck = e.timestamp
                }
                val kind = if (e.sensor.type == Sensor.TYPE_GYROSCOPE) SensorKind.GYROSCOPE else SensorKind.ACCELEROMETER
                onSample(MotionSample(kind, e.values[0], e.values[1], e.values[2], e.timestamp, rotation))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val h = Handler(t.looper)
        // gyroscope at 200 Hz (the most allowed without an extra permission): smoother aiming
        manager.registerListener(l, g, GYRO_SAMPLING_US, h)
        manager.registerListener(l, a, SAMPLING_US, h)
        thread = t
        listener = l
        return true
    }

    @Synchronized
    override fun stop() {
        listener?.let { sm?.unregisterListener(it) }
        listener = null
        thread?.quitSafely()
        thread = null
    }

    private companion object {
        const val SAMPLING_US = 10_000
        const val GYRO_SAMPLING_US = 5_000
    }
}
