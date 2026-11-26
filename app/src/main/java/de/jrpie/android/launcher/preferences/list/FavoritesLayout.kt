package de.jrpie.android.launcher.preferences.list

/**
 * Layout of the favorite apps, which can differ from the [ListLayout] of the app list,
 * e.g. to show only icons there.
 * Values are stored in preferences, see @array/settings_favorites_layout_values.
 */
@Suppress("unused")
enum class FavoritesLayout(private val layout: ListLayout?) {
    SAME_AS_APP_LIST(null),
    DEFAULT(ListLayout.DEFAULT),
    TEXT(ListLayout.TEXT),
    GRID(ListLayout.GRID),
    GRID_ONLY_ICONS(ListLayout.GRID_ONLY_ICONS),
    ;

    fun resolve(appListLayout: ListLayout): ListLayout {
        return layout ?: appListLayout
    }

    companion object {
        fun of(layout: ListLayout): FavoritesLayout {
            return entries.first { it.layout == layout }
        }
    }
}
