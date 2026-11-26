package de.jrpie.android.launcher.preferences.list

import androidx.core.content.edit
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.preferences.LauncherPreferences

/**
 * Size and spacing of the app list when it is shown as a grid.
 * The app list and the favorites have their own values.
 */
enum class GridSetting(val label: Int, val min: Int, val max: Int, val default: Int) {
    /** 0: as many as fit the screen */
    COLUMNS(R.string.grid_columns, 0, 10, 0),
    ICON_SIZE(R.string.grid_icon_size, 20, 80, 40), // sp
    PADDING(R.string.grid_padding, 0, 64, 16), // dp, between the grid and the screen's edges
    SPACING(R.string.grid_spacing, 0, 48, 10), // sp, between rows
    ;

    private fun key(favorites: Boolean): String {
        return (if (favorites) "list.grid_favorites." else "list.grid.") + name.lowercase()
    }

    fun get(favorites: Boolean): Int {
        return LauncherPreferences.getSharedPreferences().getInt(key(favorites), default)
    }

    fun set(favorites: Boolean, value: Int) {
        LauncherPreferences.getSharedPreferences().edit { putInt(key(favorites), value) }
    }

    companion object {
        fun load(favorites: Boolean): Map<GridSetting, Int> {
            return entries.associateWith { it.get(favorites) }
        }
    }
}
