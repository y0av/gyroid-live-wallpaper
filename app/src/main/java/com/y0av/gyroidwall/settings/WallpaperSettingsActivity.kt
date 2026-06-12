package com.y0av.gyroidwall.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.y0av.gyroidwall.R

/**
 * Hosts [SettingsFragment]. Referenced from res/xml/wallpaper.xml so the system
 * wallpaper picker can open it, and also reachable from the launcher screen.
 *
 * Handles Android 16's mandatory edge-to-edge by applying system-bar insets as
 * padding. Predictive back works out of the box (no onBackPressed override).
 */
class WallpaperSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
