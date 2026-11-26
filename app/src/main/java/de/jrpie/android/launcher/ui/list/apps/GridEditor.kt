package de.jrpie.android.launcher.ui.list.apps

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.widget.SeekBar
import androidx.core.graphics.ColorUtils
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.apps.AppFilter.Companion.AppSetVisibility
import de.jrpie.android.launcher.databinding.GridEditorSliderBinding
import de.jrpie.android.launcher.databinding.ListAppsGridEditorBinding
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.FavoritesLayout
import de.jrpie.android.launcher.preferences.list.GridSpec
import de.jrpie.android.launcher.ui.list.AbstractListActivity
import de.jrpie.android.launcher.ui.list.AppListActivity
import kotlin.math.roundToInt

/**
 * Changes the [GridSpec] of the app list or, if [favorites] is set, of the favorites.
 *
 * Every change is previewed on the list itself (see [ListFragmentApps]), using [presentation].
 * Nothing is stored until the changes are saved.
 */
class GridEditor(
    private val context: Context,
    private val binding: ListAppsGridEditorBinding,
    private val favorites: Boolean,
    private val onPreview: () -> Unit,
    private val onFinish: () -> Unit,
) {
    private val initial = ListPresentation.load(favorites)

    /** The presentation with the changes made so far. */
    var presentation: ListPresentation = initial
        private set

    /**
     * A slider for one of the values of a [GridSpec].
     */
    private inner class Slider(
        val binding: GridEditorSliderBinding,
        label: Int,
        private val values: List<Int>,
        private val get: (GridSpec) -> Int,
        private val set: (GridSpec, Int) -> GridSpec,
        private val format: (Int) -> String = { it.toString() },
    ) : SeekBar.OnSeekBarChangeListener {
        init {
            binding.gridEditorSliderLabel.setText(label)
            binding.gridEditorSliderSeekbar.max = values.lastIndex
            binding.gridEditorSliderSeekbar.setOnSeekBarChangeListener(this)
        }

        fun update() {
            val value = get(presentation.grid)
            binding.gridEditorSliderSeekbar.progress =
                values.indexOf(value).takeIf { it >= 0 }
                    ?: values.indexOfFirst { it >= value }.takeIf { it >= 0 }
                            ?: values.lastIndex
            binding.gridEditorSliderValue.text = format(value)
        }

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) {
                return
            }
            val value = values[progress.coerceIn(values.indices)]
            binding.gridEditorSliderValue.text = format(value)
            if (get(presentation.grid) != value) {
                change(presentation.copy(grid = set(presentation.grid, value)))
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }

    private val columns = Slider(
        binding.gridEditorColumns,
        R.string.grid_editor_columns,
        listOf(GridSpec.AUTO) + GridSpec.COLUMNS,
        { it.columns },
        { spec, value -> spec.copy(columns = value) },
        { value ->
            if (value == GridSpec.AUTO) {
                context.getString(
                    R.string.grid_editor_columns_auto,
                    presentation.grid.spanCount(context, presentation.layout)
                )
            } else {
                value.toString()
            }
        }
    )

    private val sliders = listOf(
        columns,
        Slider(
            binding.gridEditorIconSize,
            R.string.grid_editor_icon_size,
            GridSpec.ICON_SIZE.toList(),
            { it.iconSize },
            { spec, value -> spec.copy(iconSize = value) },
        ),
        Slider(
            binding.gridEditorLabelSize,
            R.string.grid_editor_label_size,
            GridSpec.LABEL_SIZE.toList(),
            { it.labelSize },
            { spec, value -> spec.copy(labelSize = value) },
        ),
        Slider(
            binding.gridEditorSidePadding,
            R.string.grid_editor_side_padding,
            GridSpec.SIDE_PADDING.toList(),
            { it.sidePadding },
            { spec, value -> spec.copy(sidePadding = value) },
        ),
        Slider(
            binding.gridEditorRowSpacing,
            R.string.grid_editor_row_spacing,
            GridSpec.ROW_SPACING.toList(),
            { it.rowSpacing },
            { spec, value -> spec.copy(rowSpacing = value) },
        ),
    )

    init {
        showTickMarks(columns.binding.gridEditorSliderSeekbar)

        binding.gridEditorLabels.setOnCheckedChangeListener { _, checked ->
            if (checked != presentation.layout.showsLabels) {
                change(presentation.copy(layout = presentation.layout.withLabels(checked)))
            }
        }
        binding.gridEditorReset.setOnClickListener {
            change(presentation.copy(grid = GridSpec()))
        }
        binding.gridEditorCancel.setOnClickListener { onFinish() }
        binding.gridEditorSave.setOnClickListener {
            save()
            onFinish()
        }
        updateViews()
    }

    private fun change(newPresentation: ListPresentation) {
        presentation = newPresentation
        updateViews()
        onPreview()
    }

    private fun updateViews() {
        sliders.forEach { it.update() }
        binding.gridEditorLabels.isChecked = presentation.layout.showsLabels
        // the size of labels doesn't matter if there are none
        binding.gridEditorLabelSize.root.alpha = if (presentation.layout.showsLabels) 1f else 0.4f
        binding.gridEditorLabelSize.gridEditorSliderSeekbar.isEnabled =
            presentation.layout.showsLabels
    }

    private fun save() {
        if (favorites) {
            LauncherPreferences.favorites().grid(presentation.grid)
            if (presentation.layout != initial.layout) {
                LauncherPreferences.favorites().layout(FavoritesLayout.of(presentation.layout))
            }
        } else {
            LauncherPreferences.list().grid(presentation.grid)
            if (presentation.layout != initial.layout) {
                LauncherPreferences.list().layout(presentation.layout)
            }
        }
    }

    /**
     * Shows a mark for every value of [seekBar], like on a discrete slider.
     */
    private fun showTickMarks(seekBar: SeekBar) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColor))
        val color = try {
            attributes.getColor(0, Color.WHITE)
        } finally {
            attributes.recycle()
        }
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics
        ).roundToInt()
        seekBar.tickMark = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(size, size)
            setColor(ColorUtils.setAlphaComponent(color, 0x66))
        }
    }

    companion object {
        /**
         * Opens the app list (or the favorites) to customize its grid.
         */
        fun open(context: Context, favorites: Boolean) {
            val intent = Intent(context, AppListActivity::class.java)
            intent.putExtra(AbstractListActivity.KEY_CUSTOMIZE_GRID, true)
            intent.putExtra(
                AbstractListActivity.KEY_FAVORITES_VISIBILITY,
                if (favorites) AppSetVisibility.EXCLUSIVE else AppSetVisibility.VISIBLE
            )
            intent.putExtra(
                AbstractListActivity.KEY_PRIVATE_SPACE_VISIBILITY,
                if (LauncherPreferences.apps().hidePrivateSpaceApps()) {
                    AppSetVisibility.HIDDEN
                } else {
                    AppSetVisibility.VISIBLE
                }
            )
            context.startActivity(intent)
        }
    }
}
