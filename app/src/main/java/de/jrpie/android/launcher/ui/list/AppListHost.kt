package de.jrpie.android.launcher.ui.list

import de.jrpie.android.launcher.apps.AppFilter.Companion.AppSetVisibility

/**
 * The activity a [de.jrpie.android.launcher.ui.list.apps.ListFragmentApps] is shown in:
 * an [AbstractListActivity], or the home screen when it shows the app list in its own window
 * (see [AppListOverlay]).
 */
interface AppListHost {
    val intention: AbstractListActivity.Companion.Intention
    val forGesture: String?
    var favoritesVisibility: AppSetVisibility
    val privateSpaceVisibility: AppSetVisibility
    val hiddenVisibility: AppSetVisibility

    /**
     * Whether the list is visible to the user.
     * Only false while the app list on the home screen is closed.
     */
    val isAppListShown: Boolean
        get() = true

    fun updateTitle() {}
}

/**
 * An activity that can show the app list in its own window, see [AppListOverlay].
 */
interface AppListOverlayHost {
    /**
     * Shows the app list in this activity's window.
     * Returns false if that is not possible right now; the caller then starts [AppListActivity].
     */
    fun showAppList(
        favoritesVisibility: AppSetVisibility,
        hiddenVisibility: AppSetVisibility,
        privateSpaceVisibility: AppSetVisibility
    ): Boolean

    /**
     * Plays [animation] (0 for none) if the app list was just opened by [showAppList],
     * and returns true. Returns false otherwise.
     */
    fun startAppListOpenAnimation(animation: Int): Boolean
}
