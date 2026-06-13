package xyz.yoav.gyroid.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Turns device motion into a smoothed, normalized tilt offset in [-1, 1] on each
 * axis, suitable for steering the wallpaper's 3D camera.
 *
 * Sensor preference: TYPE_GAME_ROTATION_VECTOR (gyro-fused, smooth, drift-free,
 * no magnetometer needed) with graceful fallback to GRAVITY then ACCELEROMETER.
 * Tilt is measured relative to a baseline captured when sensing (re)starts, so
 * the effect centers on however the user is actually holding the phone.
 *
 * Register/unregister are driven by wallpaper visibility for battery safety.
 */
class TiltProvider(
    context: Context,
    private val onTilt: (Float, Float) -> Unit
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val isRotationVector = sensor?.type == Sensor.TYPE_GAME_ROTATION_VECTOR

    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)

    private var hasBaseline = false
    private var basePitch = 0f
    private var baseRoll = 0f
    private var smoothX = 0f
    private var smoothY = 0f

    private var registered = false

    fun start() {
        if (registered || sensor == null) return
        hasBaseline = false
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        registered = true
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        smoothX = 0f
        smoothY = 0f
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val pitch: Float
        val roll: Float

        if (isRotationVector) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val (axisX, axisY) = remapAxes(currentRotation())
            SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
            SensorManager.getOrientation(remapped, orientation)
            pitch = orientation[1]   // tilt forward/back
            roll = orientation[2]    // tilt left/right
        } else {
            // Low-pass the raw vector to isolate gravity, then derive tilt angles.
            val a = 0.8f
            gravity[0] = a * gravity[0] + (1 - a) * event.values[0]
            gravity[1] = a * gravity[1] + (1 - a) * event.values[1]
            gravity[2] = a * gravity[2] + (1 - a) * event.values[2]
            val gx = gravity[0]; val gy = gravity[1]; val gz = gravity[2]
            pitch = atan2(gy, sqrt(gx * gx + gz * gz))
            roll = atan2(-gx, gz)
        }

        if (!hasBaseline) {
            basePitch = pitch
            baseRoll = roll
            hasBaseline = true
        }

        val tx = ((roll - baseRoll) / MAX_TILT_RAD).coerceIn(-1f, 1f)
        val ty = ((pitch - basePitch) / MAX_TILT_RAD).coerceIn(-1f, 1f)

        // Exponential moving average removes jitter without adding much lag.
        smoothX += ALPHA * (tx - smoothX)
        smoothY += ALPHA * (ty - smoothY)
        onTilt(smoothX, smoothY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private fun currentRotation(): Int =
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    private fun remapAxes(rotation: Int): Pair<Int, Int> = when (rotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    companion object {
        /** ~28° of tilt maps to full deflection; well inside gimbal-lock range. */
        private const val MAX_TILT_RAD = 0.49f
        private const val ALPHA = 0.15f
    }
}
