package com.y0av.gyroidwall

/**
 * Live-tunable rendering parameters shared between the wallpaper engine (writer)
 * and the GL render thread (reader). Every field is [Volatile] so reads on the
 * render thread always observe the latest value without locking.
 */
class RenderConfig {
    @Volatile var speed: Float = 1.0f
    @Volatile var parallax: Float = 1.0f
    @Volatile var thickness: Float = 0.055f
    @Volatile var paletteIndex: Int = 0
    @Volatile var maxSteps: Int = 56
    @Volatile var renderScale: Float = 0.65f
    @Volatile var fpsCap: Int = 60

    /** Smoothed, normalized tilt in [-1, 1] written by the sensor listener. */
    @Volatile var tiltX: Float = 0f
    @Volatile var tiltY: Float = 0f
}
