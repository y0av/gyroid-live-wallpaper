package com.y0av.gyroidwall.gl

import android.opengl.GLES30
import android.util.Log
import android.view.Surface
import com.y0av.gyroidwall.RenderConfig

/**
 * Dedicated render thread that owns the EGL context and draws the gyroid shader
 * to [surface]. It parks itself (zero frames, zero GPU) whenever the wallpaper
 * is not visible, and tears the GL context down cleanly when shut down.
 */
class WallpaperRenderThread(
    private val surface: Surface,
    private val config: RenderConfig
) : Thread("GyroidRender") {

    private val lock = Object()
    @Volatile private var running = true
    @Volatile private var visible = false

    private var egl: EglCore? = null
    private var program = 0
    private var vao = 0

    private var uResolution = -1
    private var uTime = -1
    private var uTilt = -1
    private var uSpeed = -1
    private var uParallax = -1
    private var uThickness = -1
    private var uPalette = -1
    private var uMaxSteps = -1

    private var startNanos = 0L
    private var lastFrameNanos = 0L
    private var vpWidth = 0
    private var vpHeight = 0

    fun setVisible(value: Boolean) {
        synchronized(lock) {
            visible = value
            lock.notifyAll()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
    }

    override fun run() {
        try {
            initGl()
            startNanos = System.nanoTime()
            while (true) {
                synchronized(lock) {
                    while (running && !visible) lock.wait()
                    if (!running) return
                }
                drawFrame()
                pace()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Render thread stopped on error", t)
        } finally {
            releaseGl()
        }
    }

    private fun initGl() {
        val core = EglCore(surface)
        core.makeCurrent()
        egl = core

        program = GlUtil.buildProgram(Shaders.VERTEX, Shaders.FRAGMENT)
        GLES30.glUseProgram(program)
        uResolution = GLES30.glGetUniformLocation(program, "uResolution")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uTilt = GLES30.glGetUniformLocation(program, "uTilt")
        uSpeed = GLES30.glGetUniformLocation(program, "uSpeed")
        uParallax = GLES30.glGetUniformLocation(program, "uParallax")
        uThickness = GLES30.glGetUniformLocation(program, "uThickness")
        uPalette = GLES30.glGetUniformLocation(program, "uPalette")
        uMaxSteps = GLES30.glGetUniformLocation(program, "uMaxSteps")

        // A bound VAO is required for attribute-less draws on some ES3 drivers.
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vao = vaos[0]
        GLES30.glBindVertexArray(vao)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    private fun drawFrame() {
        val core = egl ?: return

        // Track the live surface size (it changes when render-scale is adjusted).
        val w = core.surfaceWidth()
        val h = core.surfaceHeight()
        if (w > 0 && h > 0 && (w != vpWidth || h != vpHeight)) {
            vpWidth = w
            vpHeight = h
            GLES30.glViewport(0, 0, w, h)
        }
        if (vpWidth == 0 || vpHeight == 0) return

        val now = System.nanoTime()
        val time = (now - startNanos) / 1_000_000_000.0f

        GLES30.glUseProgram(program)
        GLES30.glUniform2f(uResolution, vpWidth.toFloat(), vpHeight.toFloat())
        GLES30.glUniform1f(uTime, time)
        GLES30.glUniform2f(uTilt, config.tiltX, config.tiltY)
        GLES30.glUniform1f(uSpeed, config.speed)
        GLES30.glUniform1f(uParallax, config.parallax)
        GLES30.glUniform1f(uThickness, config.thickness)
        GLES30.glUniform1i(uPalette, config.paletteIndex)
        GLES30.glUniform1i(uMaxSteps, config.maxSteps.coerceIn(1, 110))

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        core.setPresentationTime(now)
        core.swapBuffers()
    }

    /** Caps the frame rate. eglSwapBuffers already blocks on vsync; this throttles
     *  high-refresh panels (and 30fps mode) without busy-waiting. */
    private fun pace() {
        val cap = config.fpsCap
        if (cap <= 0) {
            lastFrameNanos = System.nanoTime()
            return
        }
        val frameNs = 1_000_000_000L / cap
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            val sleep = frameNs - (now - lastFrameNanos)
            if (sleep > 0) {
                try {
                    sleep(sleep / 1_000_000L, (sleep % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
        lastFrameNanos = System.nanoTime()
    }

    private fun releaseGl() {
        try {
            if (vao != 0) {
                GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
                vao = 0
            }
            if (program != 0) {
                GLES30.glDeleteProgram(program)
                program = 0
            }
        } catch (_: Throwable) {
            // context may already be gone; ignore
        }
        egl?.release()
        egl = null
    }

    companion object {
        private const val TAG = "GyroidRender"
    }
}
