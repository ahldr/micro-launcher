package de.jrpie.android.launcher.ui.list.apps

import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.GridSpec
import de.jrpie.android.launcher.preferences.list.ListLayout

/**
 * How a list of apps is drawn.
 * The app list and the favorites ([forFavorites]) can look different.
 */
data class ListPresentation(
    val layout: ListLayout,
    /** Only used if [layout] is a grid. */
    val grid: GridSpec,
    /** Show the apps under headings, according to their [de.jrpie.android.launcher.apps.FavoriteCategories]. */
    val groupByCategory: Boolean,
    val reverse: Boolean,
) {
    companion object {
        fun load(favorites: Boolean): ListPresentation {
            return if (favorites) forFavorites() else forAppList()
        }

        fun forAppList(): ListPresentation {
            return ListPresentation(
                layout = LauncherPreferences.list().layout(),
                grid = LauncherPreferences.list().grid(),
                groupByCategory = false,
                reverse = LauncherPreferences.list().reverseLayout(),
            )
        }

        fun forFavorites(): ListPresentation {
            return ListPresentation(
                layout = LauncherPreferences.favorites().layout()
                    .resolve(LauncherPreferences.list().layout()),
                grid = favoritesGrid(),
                groupByCategory = true,
                reverse = LauncherPreferences.list().reverseLayout(),
            )
        }

        /**
         * The favorites use the grid of the app list until their own is customized.
         */
        private fun favoritesGrid(): GridSpec {
            val customized = LauncherPreferences.getSharedPreferences()
                .contains(LauncherPreferences.favorites().keys().grid())
            return if (customized) {
                LauncherPreferences.favorites().grid()
            } else {
                LauncherPreferences.list().grid()
            }
        }
    }
}
