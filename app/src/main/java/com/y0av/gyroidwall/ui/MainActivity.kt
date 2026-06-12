package com.y0av.gyroidwall.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.y0av.gyroidwall.GyroidWallpaperService
import com.y0av.gyroidwall.R
import com.y0av.gyroidwall.settings.WallpaperSettingsActivity

/** Small launcher screen: apply the wallpaper or open its settings. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<Button>(R.id.applyButton).setOnClickListener { applyWallpaper() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, WallpaperSettingsActivity::class.java))
        }
    }

    private fun applyWallpaper() {
        // Deep-link straight to the preview for our wallpaper.
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, GyroidWallpaperService::class.java)
        )
        try {
            startActivity(direct)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to the generic chooser.
        }
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.apply_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
