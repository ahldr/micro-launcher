package de.jrpie.android.launcher.preferences.list

import android.content.Context
import android.util.TypedValue
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Size and spacing of an app list shown as a grid ([ListLayout.isGrid]).
 * The app list and the favorites each have their own, see
 * [de.jrpie.android.launcher.preferences.LauncherPreferences.list] and
 * [de.jrpie.android.launcher.preferences.LauncherPreferences.favorites].
 *
 * Like the rest of the app list, sizes are in sp, so they follow the system font size.
 * Only [sidePadding] is in dp.
 */
@Serializable
data class GridSpec(
    /**
     * Number of columns in portrait orientation,
     * or [AUTO] to fit as many columns of [ListLayout.autoColumnWidth] as possible.
     */
    val columns: Int = AUTO,
    val iconSize: Int = 40,
    val labelSize: Int = 11,
    /** Space between the grid and the left and right edges of the screen. */
    val sidePadding: Int = 16,
    /** Space between two rows. */
    val rowSpacing: Int = 10,
) {

    /** Returns a copy with every value in its allowed range. */
    fun coerced(): GridSpec {
        return GridSpec(
            columns = if (columns == AUTO) AUTO else columns.coerceIn(COLUMNS),
            iconSize = iconSize.coerceIn(ICON_SIZE),
            labelSize = labelSize.coerceIn(LABEL_SIZE),
            sidePadding = sidePadding.coerceIn(SIDE_PADDING),
            rowSpacing = rowSpacing.coerceIn(ROW_SPACING),
        )
    }

    /**
     * The number of columns to show in [layout] at the current screen width.
     *
     * A fixed number of [columns] is meant for portrait orientation. On a wider screen
     * (e.g. in landscape) columns are added, such that each keeps the same width.
     */
    fun spanCount(context: Context, layout: ListLayout): Int {
        val metrics = context.resources.displayMetrics
        return spanCount(
            metrics.widthPixels,
            metrics.heightPixels,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, layout.autoColumnWidth, metrics)
        )
    }

    /**
     * @param autoColumnWidth width of a column in pixels if [columns] is [AUTO]
     */
    fun spanCount(screenWidth: Int, screenHeight: Int, autoColumnWidth: Float): Int {
        if (columns == AUTO) {
            if (autoColumnWidth <= 0f) {
                return 1
            }
            return max(1, (screenWidth / autoColumnWidth).toInt())
        }
        val portraitWidth = min(screenWidth, screenHeight)
        if (portraitWidth <= 0) {
            return columns
        }
        return max(columns, (columns * screenWidth / portraitWidth.toFloat()).roundToInt())
    }

    companion object {
        const val AUTO = 0

        val COLUMNS = 2..10
        val ICON_SIZE = 20..80
        val LABEL_SIZE = 8..18
        val SIDE_PADDING = 0..64
        val ROW_SPACING = 0..48
    }
}
