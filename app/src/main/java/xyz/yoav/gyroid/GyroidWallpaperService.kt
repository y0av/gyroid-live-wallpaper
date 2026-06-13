package xyz.yoav.gyroid

import android.content.SharedPreferences
import android.os.Build
import android.util.DisplayMetrics
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.preference.PreferenceManager
import xyz.yoav.gyroid.gl.WallpaperRenderThread
import xyz.yoav.gyroid.sensor.TiltProvider
import android.service.wallpaper.WallpaperService

/**
 * The live wallpaper. Each [GyroidEngine] owns one GL render thread and one tilt
 * sensor subscription, both gated on visibility so a hidden wallpaper uses no GPU,
 * no sensor, and no CPU.
 */
class GyroidWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = GyroidEngine()

    inner class GyroidEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val config = RenderConfig()
        private var renderThread: WallpaperRenderThread? = null
        private var tiltProvider: TiltProvider? = null
        private lateinit var prefs: SharedPreferences
        private var appliedScale = -1f

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs = PreferenceManager.getDefaultSharedPreferences(this@GyroidWallpaperService)
            Prefs.applyTo(config, prefs)
            prefs.registerOnSharedPreferenceChangeListener(this)

            tiltProvider = TiltProvider(this@GyroidWallpaperService) { x, y ->
                config.tiltX = x
                config.tiltY = y
            }
            setTouchEventsEnabled(false)
            applyRenderScale(surfaceHolder)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startRender(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopRender()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                if (config.parallax > 0f) tiltProvider?.start()
                renderThread?.setVisible(true)
            } else {
                tiltProvider?.stop()
                renderThread?.setVisible(false)
            }
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            tiltProvider?.stop()
            stopRender()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            Prefs.applyTo(config, prefs)

            // Keep the sensor subscription consistent with the parallax setting.
            if (isVisible) {
                if (config.parallax > 0f) {
                    tiltProvider?.start()
                } else {
                    tiltProvider?.stop()
                }
            }

            // A quality change can alter render scale, which re-sizes the surface.
            if (key == Prefs.KEY_QUALITY) {
                surfaceHolder?.let { applyRenderScale(it) }
            }
        }

        private fun startRender(holder: SurfaceHolder) {
            stopRender()
            val thread = WallpaperRenderThread(holder.surface, config)
            thread.start()
            thread.setVisible(isVisible)
            renderThread = thread
        }

        private fun stopRender() {
            renderThread?.let { thread ->
                thread.shutdown()
                try {
                    thread.join(1500)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            renderThread = null
        }

        /** Renders at a fraction of native resolution (upscaled by the compositor)
         *  to save GPU/battery. Changing the fixed size re-creates the surface. */
        private fun applyRenderScale(holder: SurfaceHolder) {
            if (config.renderScale == appliedScale) return
            appliedScale = config.renderScale
            val (nativeW, nativeH) = nativeSize()
            val w = (nativeW * config.renderScale).toInt().coerceAtLeast(1)
            val h = (nativeH * config.renderScale).toInt().coerceAtLeast(1)
            holder.setFixedSize(w, h)
        }

        private fun nativeSize(): Pair<Int, Int> {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                metrics.widthPixels to metrics.heightPixels
            }
        }
    }
}
