package xyz.yoav.gyroid.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import xyz.yoav.gyroid.R

/** Inflates the live-wallpaper preferences. Values are written to the default
 *  SharedPreferences and observed live by the running wallpaper engine. */
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_prefs, rootKey)
    }
}
