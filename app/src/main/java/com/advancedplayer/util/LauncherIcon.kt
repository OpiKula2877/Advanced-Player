package com.advancedplayer.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.advancedplayer.data.ThemeMode

/**
 * Keeps the launcher icon in step with the app theme. Android cannot recolour an icon at
 * runtime, so there is one launcher alias per accent preset and light/dark combination
 * (the activity-alias entries in AndroidManifest.xml). Exactly one of them is enabled.
 */
object LauncherIcon {
    private const val PREFS = "launcher_icon"
    private const val KEY_ALIAS = "alias"

    // Same colours, in the same order, as AccentPresets in ui/theme/Theme.kt.
    private val accents = listOf(
        "green" to 0xFF1DB954L, "red" to 0xFFE53935L, "blue" to 0xFF2E77D0L, "purple" to 0xFF8E44ECL,
        "pink" to 0xFFE91E8CL, "orange" to 0xFFFF7A1AL, "yellow" to 0xFFF5C518L, "teal" to 0xFF1ABC9CL,
        "white" to 0xFFFFFFFFL, "black" to 0xFF000000L,
    )

    private fun aliasName(colour: String, dark: Boolean): String =
        "Icon${colour.replaceFirstChar { it.uppercase() }}${if (dark) "Dark" else "Light"}"

    /** Alias for the preset closest to [accent]; a custom colour gets the nearest preset's icon. */
    fun aliasFor(accent: Long, dark: Boolean): String {
        val nearest = accents.minBy { (_, c) -> distance(c, accent) }.first
        return aliasName(nearest, dark)
    }

    private fun distance(a: Long, b: Long): Long {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    /**
     * Enables the icon for the given theme and accent. Does nothing when it is already
     * active. Call from a background thread; PackageManager calls can be slow.
     */
    fun apply(context: Context, themeMode: ThemeMode, accent: Long) {
        val dark = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        val target = aliasFor(accent, dark)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ALIAS, null) == target) return

        val pm = context.packageManager
        // Enable the new icon before disabling the old one, so the launcher never shows zero.
        pm.setComponentEnabledSetting(component(context, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        accents.forEach { (colour, _) ->
            listOf(true, false).forEach { isDark ->
                val name = aliasName(colour, isDark)
                if (name != target) {
                    pm.setComponentEnabledSetting(component(context, name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
        }
        prefs.edit().putString(KEY_ALIAS, target).apply()
    }

    private fun component(context: Context, alias: String) =
        ComponentName(context.packageName, "${context.packageName}.$alias")
}
