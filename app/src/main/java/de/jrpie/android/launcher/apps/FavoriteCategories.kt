package de.jrpie.android.launcher.apps

import kotlinx.serialization.Serializable

/**
 * A named group of favorite apps, e.g. "Messaging".
 */
@Serializable
data class FavoriteCategory(
    val id: Int,
    val name: String,
    val apps: Set<AbstractAppInfo> = emptySet(),
)

/**
 * The categories favorite apps are grouped into, in the order they are shown.
 * Every app is in at most one category. Favorites that are in none are shown last.
 *
 * Instances are immutable: every modification returns a new instance,
 * which has to be stored in
 * [de.jrpie.android.launcher.preferences.LauncherPreferences.apps] to take effect.
 */
@Serializable
data class FavoriteCategories(
    val categories: List<FavoriteCategory> = emptyList(),
) {
    data class Section<T>(
        /** null for the apps that are in no category */
        val category: FavoriteCategory?,
        val items: List<T>,
    )

    fun isEmpty(): Boolean = categories.isEmpty()

    fun byId(id: Int): FavoriteCategory? = categories.firstOrNull { it.id == id }

    fun categoryOf(app: AbstractAppInfo): FavoriteCategory? =
        categories.firstOrNull { app in it.apps }

    /** Appends a new, empty category. */
    fun add(name: String): Pair<FavoriteCategories, FavoriteCategory> {
        val category = FavoriteCategory((categories.maxOfOrNull { it.id } ?: 0) + 1, name)
        return Pair(copy(categories = categories + category), category)
    }

    fun rename(id: Int, name: String): FavoriteCategories =
        copy(categories = categories.map { if (it.id == id) it.copy(name = name) else it })

    fun remove(id: Int): FavoriteCategories =
        copy(categories = categories.filterNot { it.id == id })

    /**
     * Inserts [category] at [index], e.g. to undo [remove].
     * Its apps are removed from the other categories.
     */
    fun restore(index: Int, category: FavoriteCategory): FavoriteCategories {
        val list = categories
            .filterNot { it.id == category.id }
            .map { it.copy(apps = it.apps - category.apps) }
            .toMutableList()
        list.add(index.coerceIn(0, list.size), category)
        return copy(categories = list)
    }

    /** Moves the category at index [from] to index [to]. */
    fun move(from: Int, to: Int): FavoriteCategories {
        if (from !in categories.indices || to !in categories.indices || from == to) {
            return this
        }
        val list = categories.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(categories = list)
    }

    /**
     * Puts [app] into the category [id], removing it from any other category.
     * If [id] is null, the app is removed from all categories.
     */
    fun assign(app: AbstractAppInfo, id: Int?): FavoriteCategories =
        copy(categories = categories.map {
            if (it.id == id) it.copy(apps = it.apps + app) else it.copy(apps = it.apps - app)
        })

    /**
     * Sets which of [candidates] are in the category [id]: exactly those in [selected].
     * Selected apps are removed from other categories.
     * Apps that are not among [candidates] are left as they are.
     */
    fun setApps(
        id: Int,
        selected: Set<AbstractAppInfo>,
        candidates: Set<AbstractAppInfo>
    ): FavoriteCategories =
        copy(categories = categories.map {
            if (it.id == id) {
                it.copy(apps = it.apps - candidates + selected)
            } else {
                it.copy(apps = it.apps - selected)
            }
        })

    /**
     * Splits [items] into sections, one per category that contains any of them,
     * in the order of the categories, followed by the items that are in no category.
     * The order of [items] is kept within each section.
     *
     * If none of [items] is in a category, a single section without a category is returned.
     */
    fun <T> group(items: List<T>, key: (T) -> AbstractAppInfo): List<Section<T>> {
        if (categories.isEmpty()) {
            return listOf(Section(null, items))
        }
        val indexOf = HashMap<AbstractAppInfo, Int>()
        categories.forEachIndexed { i, category ->
            // (putIfAbsent requires API 24)
            category.apps.forEach { indexOf.getOrPut(it) { i } }
        }
        val buckets = List(categories.size + 1) { mutableListOf<T>() }
        items.forEach { buckets[indexOf[key(it)] ?: categories.size].add(it) }

        val sections = categories.indices
            .filter { buckets[it].isNotEmpty() }
            .map { Section(categories[it], buckets[it]) }
        val uncategorized = buckets[categories.size]
        return when {
            sections.isEmpty() -> listOf(Section(null, uncategorized))
            uncategorized.isEmpty() -> sections
            else -> sections + Section(null, uncategorized)
        }
    }
}
