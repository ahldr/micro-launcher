package de.jrpie.android.launcher.ui.list

import android.os.Bundle
import de.jrpie.android.launcher.apps.AppFilter
import de.jrpie.android.launcher.ui.UIObjectActivity


/**
 * This abstract class bundles common logic used in [AppListActivity] and [SelectActionActivity].
 * - [AppListActivity] is used to view all apps and edit their settings
 * - [SelectActionActivity] is used to choose an app / intent to be launched
 */
sealed class AbstractListActivity : UIObjectActivity(), AppListHost {
    // TODO: remove this
    abstract override val intention: Intention

    // TODO: this should be a view model
    override var favoritesVisibility: AppFilter.Companion.AppSetVisibility =
        AppFilter.Companion.AppSetVisibility.VISIBLE
    override var privateSpaceVisibility: AppFilter.Companion.AppSetVisibility =
        AppFilter.Companion.AppSetVisibility.VISIBLE
    override var hiddenVisibility: AppFilter.Companion.AppSetVisibility =
        AppFilter.Companion.AppSetVisibility.HIDDEN

    // TODO: only needed for [SelectActionActivity]
    override var forGesture: String? = null

    // only used by [AppListActivity]
    override var customizeGrid: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.extras?.let { bundle ->
            customizeGrid = bundle.getBoolean(KEY_CUSTOMIZE_GRID, false)
            @Suppress("deprecation") // required to support API level < 33
            favoritesVisibility = bundle.getSerializable(KEY_FAVORITES_VISIBILITY)
                    as? AppFilter.Companion.AppSetVisibility ?: favoritesVisibility
            @Suppress("deprecation") // required to support API level < 33
            privateSpaceVisibility = bundle.getSerializable(KEY_PRIVATE_SPACE_VISIBILITY)
                    as? AppFilter.Companion.AppSetVisibility ?: privateSpaceVisibility
            @Suppress("deprecation") // required to support API level < 33
            hiddenVisibility = bundle.getSerializable(KEY_HIDDEN_VISIBILITY)
                    as? AppFilter.Companion.AppSetVisibility ?: hiddenVisibility
            forGesture = bundle.getString(KEY_FOR_GESTURE)
        }
    }

    companion object {
        const val KEY_FAVORITES_VISIBILITY = "favoritesVisibility"
        const val KEY_PRIVATE_SPACE_VISIBILITY = "privateSpaceVisibility"
        const val KEY_HIDDEN_VISIBILITY = "hiddenVisibility"
        const val KEY_FOR_GESTURE = "forGesture"
        const val KEY_CUSTOMIZE_GRID = "customizeGrid"

        enum class Intention {
            VIEW, // used for [AppListActivity]
            PICK  // used for [SelectActionActivity]
        }
    }
}