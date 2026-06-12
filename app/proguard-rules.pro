# Keep the WallpaperService and settings Activity — referenced from the manifest
# and the wallpaper.xml metadata by name, so R8 must not rename/remove them.
-keep class com.y0av.gyroidwall.GyroidWallpaperService { *; }
-keep class com.y0av.gyroidwall.settings.WallpaperSettingsActivity { *; }

# Preferences are inflated from XML by name.
-keep class * extends androidx.preference.Preference { *; }
