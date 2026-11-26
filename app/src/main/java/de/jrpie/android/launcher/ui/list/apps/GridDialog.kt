package de.jrpie.android.launcher.ui.list.apps

import android.app.Activity
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatSeekBar
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.GridSetting
import de.jrpie.android.launcher.ui.styledAlertDialogBuilder

/**
 * Changes the [GridSetting]s of the app list or the favorites.
 * Every change is stored right away, so the list behind the dialog previews it.
 */
fun showGridDialog(activity: Activity, favorites: Boolean, onDismiss: () -> Unit) {
    val builder = styledAlertDialogBuilder(activity)
    val context = builder.context
    val padding = (20 * context.resources.displayMetrics.density).toInt()
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding / 2, padding, 0)
    }

    for (setting in GridSetting.entries) {
        val label = TextView(context)
        fun showValue(value: Int) {
            label.text = context.getString(
                R.string.grid_setting_value,
                context.getString(setting.label),
                if (setting == GridSetting.COLUMNS && value == 0) {
                    context.getString(R.string.grid_columns_auto)
                } else {
                    value.toString()
                }
            )
        }
        showValue(setting.get(favorites))
        content.addView(label)
        content.addView(AppCompatSeekBar(context).apply {
            max = setting.max - setting.min
            progress = setting.get(favorites) - setting.min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    setting.set(favorites, progress + setting.min)
                    showValue(progress + setting.min)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        })
    }

    if (favorites) {
        content.addView(AppCompatCheckBox(context).apply {
            setText(R.string.grid_hide_labels)
            isChecked = LauncherPreferences.list().favoritesHideLabels()
            setOnCheckedChangeListener { _, checked ->
                LauncherPreferences.list().favoritesHideLabels(checked)
            }
        })
    }

    builder.setTitle(if (favorites) R.string.settings_list_grid_favorites else R.string.settings_list_grid)
        .setView(content)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener { onDismiss() }
        .create().apply {
            // keep the list visible
            window?.setGravity(Gravity.BOTTOM)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }.show()
}
