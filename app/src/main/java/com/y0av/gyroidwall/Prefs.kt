package com.y0av.gyroidwall

import android.content.SharedPreferences

/**
 * Bridges the SharedPreferences written by the settings screen to a [RenderConfig].
 * Keys here must match the keys in res/xml/settings_prefs.xml.
 */
object Prefs {
    const val KEY_PALETTE = "palette"
    const val KEY_THICKNESS = "thickness"
    const val KEY_SPEED = "speed"
    const val KEY_PARALLAX = "parallax"
    const val KEY_QUALITY = "quality"
    const val KEY_FPS = "fps"

    fun applyTo(config: RenderConfig, p: SharedPreferences) {
        config.paletteIndex = (p.getString(KEY_PALETTE, "0") ?: "0").toIntOrNull() ?: 0

        // Slider 5..90 -> shell half-thickness 0.02..0.105 (thin filigree -> chunky tubes).
        val thicknessSlider = p.getInt(KEY_THICKNESS, 40)
        config.thickness = 0.015f + thicknessSlider * 0.001f

        // Slider 0..200 -> 0.0..2.0x animation speed.
        config.speed = p.getInt(KEY_SPEED, 100) / 100f

        // Slider 0..150 -> 0.0..1.5x tilt parallax.
        config.parallax = p.getInt(KEY_PARALLAX, 100) / 100f

        when ((p.getString(KEY_QUALITY, "1") ?: "1").toIntOrNull() ?: 1) {
            0 -> { config.maxSteps = 40; config.renderScale = 0.50f }   // battery saver
            2 -> { config.maxSteps = 72; config.renderScale = 0.80f }   // high
            else -> { config.maxSteps = 56; config.renderScale = 0.65f } // balanced
        }

        config.fpsCap = (p.getString(KEY_FPS, "60") ?: "60").toIntOrNull() ?: 60
    }
}
