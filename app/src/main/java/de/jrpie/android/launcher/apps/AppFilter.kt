package de.jrpie.android.launcher.apps

import android.content.Context
import de.jrpie.android.launcher.FilterHelper
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.AppAction
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.actions.ShortcutAction
import de.jrpie.android.launcher.preferences.LauncherPreferences
import java.util.Locale

class AppFilter(
    var context: Context,
    var query: String,
    favoritesVisibility: AppSetVisibility = AppSetVisibility.VISIBLE,
    hiddenVisibility: AppSetVisibility = AppSetVisibility.HIDDEN,
    privateSpaceVisibility: AppSetVisibility = AppSetVisibility.VISIBLE
): FilterHelper() {

    var favoritesVisibility: AppSetVisibility = favoritesVisibility
        set(value) {
            if (field != value) invalidate()
            field = value
        }
    var hiddenVisibility: AppSetVisibility = hiddenVisibility
        set(value) {
            if (field != value) invalidate()
            field = value
        }
    var privateSpaceVisibility: AppSetVisibility = privateSpaceVisibility
        set(value) {
            if (field != value) invalidate()
            field = value
        }

    /** An app together with its (custom) label, which is computed once per base list. */
    private data class Entry(val app: AbstractDetailedAppInfo, val label: String)

    // Cached result of the base list step (sort, visibility, bound apps), see [baseList].
    // Only accessed from the main thread.
    private var cachedInput: List<AbstractDetailedAppInfo>? = null
    private var cachedBase: List<Entry>? = null

    /**
     * Drops the cached base list. Must be called when a preference that influences the
     * base list (hidden, favorites, custom names, hide bound apps, gesture bindings, ...)
     * changes. A new `apps` list reference and changes of the visibility properties
     * invalidate the cache automatically.
     */
    fun invalidate() {
        cachedInput = null
        cachedBase = null
    }

    /**
     * Base list step: sort, visibility and bound apps. Independent of [query].
     */
    private fun baseList(apps: List<AbstractDetailedAppInfo>): List<Entry> {
        cachedBase?.let { if (cachedInput === apps) return it }

        // compute each label once instead of once per comparison
        val sorted = apps
            .map { app -> Entry(app, app.getCustomLabel(context)) }
            .sortedBy { entry -> entry.label.lowercase(Locale.ROOT) }

        val hidden = LauncherPreferences.apps().hidden() ?: setOf()
        val favorites = LauncherPreferences.apps().favorites() ?: setOf()
        val private = apps.filter { it.isPrivate() }
            .map { it.getRawInfo() }.toSet()

        var result = sorted.filter { (info, _) ->
            favoritesVisibility.predicate(favorites, info)
                    && hiddenVisibility.predicate(hidden, info)
                    && privateSpaceVisibility.predicate(private, info)
        }

        if (LauncherPreferences.apps().hideBoundApps()) {
            val boundApps = Gesture.entries
                .filter(Gesture::isEnabled)
                .mapNotNull { g -> Action.forGesture(g) }
                .mapNotNull {
                    (it as? AppAction)?.app
                        ?: (it as? ShortcutAction)?.shortcut
                }
                .toSet()
            result = result.filterNot { (info, _) -> boundApps.contains(info.getRawInfo()) }
        }

        cachedInput = apps
        cachedBase = result
        return result
    }

    operator fun invoke(apps: List<AbstractDetailedAppInfo>): List<AbstractDetailedAppInfo> {
        val base = baseList(apps)

        // query step: the only part that depends on the search string
        return if (query.isEmpty()) {
            base.map { it.app }
        } else {
            base.filterByQuery(query) { entry -> listOf(entry.label) }.map { it.app }
        }
    }

    companion object {
        enum class AppSetVisibility(
            val predicate: (set: Set<AbstractAppInfo>, AbstractDetailedAppInfo) -> Boolean
        ) {
            VISIBLE({ _, _ -> true }),
            HIDDEN({ set, appInfo -> !set.contains(appInfo.getRawInfo()) }),
            EXCLUSIVE({ set, appInfo -> set.contains(appInfo.getRawInfo()) }),
            ;
        }
    }
}