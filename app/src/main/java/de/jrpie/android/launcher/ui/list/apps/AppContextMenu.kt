package de.jrpie.android.launcher.ui.list.apps

import android.app.Service
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.actions.ShortcutAction
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.AppInfo
import de.jrpie.android.launcher.apps.DetailedAppInfo
import de.jrpie.android.launcher.apps.loadShortcutBadgedIcon
import de.jrpie.android.launcher.databinding.AppContextMenuBinding
import de.jrpie.android.launcher.getAppShortcuts
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.ListLayout
import de.jrpie.android.launcher.ui.setTooltipAndContentDescription
import de.jrpie.android.launcher.ui.transformMonochrome
import de.jrpie.android.launcher.ui.settings.DialogBindToGesture

/**
 * Context menu of an app, showing actions (rename, hide, ...) and the app's shortcuts.
 *
 * @param onPickShortcut if set, the menu is used to pick one of the app's shortcuts,
 *  e.g. to bind it to a gesture: only the shortcuts are shown
 *  and tapping one calls [onPickShortcut] instead of launching it.
 */
class AppContextMenu(
    val appInfo: AbstractDetailedAppInfo,
    val activity: AppCompatActivity,
    private val onPickShortcut: ((ShortcutInfo) -> Unit)? = null
) : PopupWindow(activity) {
    var binding =
        (activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).let {
            AppContextMenuBinding.inflate(it)
        }

    init {
        contentView = binding.root
        width = LinearLayout.LayoutParams.WRAP_CONTENT
        height = LinearLayout.LayoutParams.WRAP_CONTENT
        isFocusable = true
        inputMethodMode = INPUT_METHOD_NOT_NEEDED
        isOutsideTouchable = true
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                if (isShowing) {
                    dismiss()
                }
                activity.lifecycle.removeObserver(this)
            }
        })

        if (onPickShortcut != null) {
            binding.appMenuTop.isVisible = false
            binding.appMenuTopSpace.isVisible = false
        }

        binding.appMenuTopInfo.apply {
            setTooltipAndContentDescription(R.string.list_app_info)
            visibility = if (appInfo is DetailedAppInfo) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            setOnClickListener { _ ->
                (appInfo.getRawInfo() as? AppInfo)?.openSettings(activity)
                dismiss()
            }
        }

        binding.appMenuTopDelete.apply {
            setTooltipAndContentDescription(R.string.list_app_delete)
            visibility = if (appInfo.isRemovable()) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            setOnClickListener { _ ->
                appInfo.getRawInfo().uninstall(activity)
                dismiss()
            }
        }
        binding.appMenuTopFavorites.apply {
            if (LauncherPreferences.apps().favorites()?.contains(appInfo.getRawInfo()) == true) {
                setTooltipAndContentDescription(R.string.list_app_favorite_remove)
                setImageDrawable(
                    ResourcesCompat.getDrawable(
                        activity.resources,
                        R.drawable.baseline_favorite_24,
                        activity.theme
                    )
                )
            } else {
                setTooltipAndContentDescription(R.string.list_app_favorite_add)
            }

            setOnClickListener { _ ->
                appInfo.getRawInfo().toggleFavorite()
                dismiss()
            }
        }

        // only favorites are grouped by category
        binding.appMenuTopCategory.apply {
            setTooltipAndContentDescription(R.string.list_app_category)
            isVisible =
                LauncherPreferences.apps().favorites()?.contains(appInfo.getRawInfo()) == true
            setOnClickListener { _ ->
                appInfo.showCategoryDialog(activity)
                dismiss()
            }
        }

        binding.appMenuTopHide.apply {
            if (LauncherPreferences.apps().hidden()?.contains(appInfo.getRawInfo()) == true) {
                setTooltipAndContentDescription(R.string.list_app_hidden_remove)
                setImageDrawable(
                    ResourcesCompat.getDrawable(
                        activity.resources,
                        R.drawable.baseline_visibility_24,
                        activity.theme
                    )
                )
            } else {
                setTooltipAndContentDescription(R.string.list_app_hidden_add)
            }

            setOnClickListener { _ ->
                appInfo.getRawInfo().toggleHidden(binding.root)
                dismiss()
            }
        }
        binding.appMenuTopRename.apply {
            setTooltipAndContentDescription(R.string.list_app_rename)
            setOnClickListener { _ ->
                appInfo.showRenameDialog(activity)
            }
        }

        val colorTheme = LauncherPreferences.theme().colorTheme()
        val grayscale = colorTheme.monochromeIcons()
        val showIcons = LauncherPreferences.list().layout() != ListLayout.TEXT

        // shortcuts
        if (appInfo is DetailedAppInfo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val iconSize = (24 * activity.resources.displayMetrics.density).toInt()
            val launcherApps =
                activity.getSystemService(Service.LAUNCHER_APPS_SERVICE) as LauncherApps
            getAppShortcuts(appInfo.getRawInfo(), activity)
                .forEach { shortcutInfo ->
                    val shortcutView = activity.layoutInflater.inflate(
                        R.layout.app_context_menu_shortcut,
                        binding.appMenuShortcuts,
                        false
                    )
                    if (showIcons) {
                        val shortcutIcon =
                            loadShortcutBadgedIcon(activity, shortcutInfo)?.apply {
                                transformMonochrome(grayscale, colorTheme)
                            }
                        val fixedIcon = object : DrawableWrapper(shortcutIcon) {
                            override fun getIntrinsicWidth(): Int = iconSize
                            override fun getIntrinsicHeight(): Int = iconSize
                        }
                        shortcutView.findViewById<ImageView>(R.id.app_menu_shortcut_icon).apply {
                            setImageDrawable(fixedIcon)
                        }
                    } else {
                        shortcutView.findViewById<ImageView>(R.id.app_menu_shortcut_icon).isVisible = false
                    }

                    binding.appMenuShortcuts.addView(shortcutView)
                    shortcutView.findViewById<TextView>(R.id.app_menu_shortcut_label).text =
                        shortcutInfo.shortLabel

                    shortcutView.setOnClickListener { _ ->
                        dismiss()
                        if (onPickShortcut != null) {
                            onPickShortcut(shortcutInfo)
                        } else {
                            launcherApps.startShortcut(shortcutInfo, null, null)
                        }
                    }
                    if (onPickShortcut != null) {
                        return@forEach
                    }
                    // Long click binds the shortcut to a gesture
                    ViewCompat.replaceAccessibilityAction(
                        shortcutView,
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                        activity.getString(R.string.pin_shortcut_button_bind),
                        null
                    )
                    shortcutView.setOnLongClickListener { _ ->
                        dismiss()
                        DialogBindToGesture(activity) { gesture ->
                            ShortcutAction.bindToGesture(activity, shortcutInfo, gesture)
                            Toast.makeText(
                                activity,
                                activity.getString(
                                    R.string.toast_shortcut_bound,
                                    shortcutInfo.shortLabel ?: shortcutInfo.longLabel ?: "",
                                    gesture.getLabel(activity)
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    }
                }
        }
    }

    fun show(view: View?) {
        if (view == null) {
            showAtLocation(binding.root, Gravity.NO_GRAVITY, 0, 0)
        } else {
            showAsDropDown(view)
        }
    }
}