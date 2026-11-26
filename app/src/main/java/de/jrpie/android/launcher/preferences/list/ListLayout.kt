package de.jrpie.android.launcher.preferences.list

import de.jrpie.android.launcher.R

// TODO: move this to de.jrpie.android.launcher.ui.list.apps ?
/**
 * How apps are shown in the app list.
 * Values are stored in preferences, see @array/settings_list_layout_values.
 */
@Suppress("unused")
enum class ListLayout(
    val layoutResource: Int,
    val useBadgedText: Boolean,
    /** Apps are shown in a grid, sized according to a [GridSpec]. */
    val isGrid: Boolean,
    val showsIcons: Boolean,
    val showsLabels: Boolean,
    /** Width of a column (in sp) if the number of columns is [GridSpec.AUTO]. */
    val autoColumnWidth: Float = 0f,
) {
    DEFAULT(
        R.layout.list_apps_row,
        useBadgedText = false,
        isGrid = false,
        showsIcons = true,
        showsLabels = true,
    ),
    TEXT(
        R.layout.list_apps_row_variant_text,
        useBadgedText = true,
        isGrid = false,
        showsIcons = false,
        showsLabels = true,
    ),
    GRID(
        R.layout.list_apps_row_variant_grid,
        useBadgedText = false,
        isGrid = true,
        showsIcons = true,
        showsLabels = true,
        autoColumnWidth = 90f,
    ),
    GRID_ONLY_ICONS(
        R.layout.list_apps_row_variant_grid_only_icons,
        useBadgedText = false,
        isGrid = true,
        showsIcons = true,
        showsLabels = false,
        autoColumnWidth = 55f,
    ),
    ;

    /** The grid layout that differs from this one only in whether labels are shown. */
    fun withLabels(labels: Boolean): ListLayout {
        return if (!isGrid) this else if (labels) GRID else GRID_ONLY_ICONS
    }
}
