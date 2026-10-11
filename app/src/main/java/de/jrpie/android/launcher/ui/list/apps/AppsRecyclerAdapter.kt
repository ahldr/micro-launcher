package de.jrpie.android.launcher.ui.list.apps

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.AppFilter
import de.jrpie.android.launcher.apps.AppInfo
import de.jrpie.android.launcher.apps.DetailedAppInfo
import de.jrpie.android.launcher.getAppShortcuts
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.AppNameFormat
import de.jrpie.android.launcher.preferences.list.ListLayout
import de.jrpie.android.launcher.ui.list.AbstractListActivity
import de.jrpie.android.launcher.ui.transformMonochrome

/**
 * A [RecyclerView] (efficient scrollable list) containing all apps on the users device.
 * The apps details are represented by [AppInfo].
 *
 * @param activity - the activity this is in
 * @param lifecycleOwner - scope of the observer on the app list (e.g. the fragment's view lifecycle)
 * @param intention - why the list is displayed ("view", "pick")
 * @param forGesture - the action which an app is chosen for (when the intention is "pick")
 */
class AppsRecyclerAdapter(
    val activity: Activity,
    lifecycleOwner: LifecycleOwner,
    val root: View,
    private val intention: AbstractListActivity.Companion.Intention = AbstractListActivity.Companion.Intention.VIEW,
    private val forGesture: String? = "",
    private var appFilter: AppFilter = AppFilter(activity, ""),
    private val layout: ListLayout,
    private val nameFormat: AppNameFormat
) :
    ListAdapter<AbstractDetailedAppInfo, AppsRecyclerAdapter.ViewHolder>(diffCallback(activity)) {


    private val apps = (activity.applicationContext as Application).apps
    private val theme = LauncherPreferences.theme()
    private val colorTheme = theme.colorTheme()
    private val grayscale = colorTheme.monochromeIcons()

    // temporarily disable auto launch
    var disableAutoLaunch: Boolean = false

    init {
        apps.observe(lifecycleOwner) {
            updateAppsList()
        }
        updateAppsList()
    }


    companion object {
        private fun diffCallback(context: Context) =
            object : DiffUtil.ItemCallback<AbstractDetailedAppInfo>() {
                override fun areItemsTheSame(
                    oldItem: AbstractDetailedAppInfo,
                    newItem: AbstractDetailedAppInfo
                ): Boolean = oldItem.getRawInfo() == newItem.getRawInfo()

                // Icons are recreated when apps reload, so a different icon instance
                // must count as changed content.
                override fun areContentsTheSame(
                    oldItem: AbstractDetailedAppInfo,
                    newItem: AbstractDetailedAppInfo
                ): Boolean = oldItem.getCustomLabel(context) == newItem.getCustomLabel(context) &&
                        oldItem.getIcon(context) === newItem.getIcon(context)
            }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        var textView: TextView = itemView.findViewById(R.id.list_apps_row_name)
        var img: ImageView = itemView.findViewById(R.id.list_apps_row_icon)

        override fun onClick(v: View) {
            val rect = Rect()
            img.getGlobalVisibleRect(rect)
            selectItem(bindingAdapterPosition, rect)
        }

        init {
            itemView.setOnClickListener(this)
        }
    }


    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        var appLabel = getItem(i).getCustomLabel(activity)

        val appIcon = getItem(i).getIcon(activity)

        viewHolder.img.transformMonochrome(grayscale, colorTheme)
        viewHolder.img.setImageDrawable(appIcon.constantState?.newDrawable() ?: appIcon)

        if (layout.useBadgedText) {
            appLabel = activity.packageManager.getUserBadgedLabel(
                appLabel,
                getItem(i).getUser(activity)
            ).toString()
        }
        viewHolder.textView.text = nameFormat.format(appLabel)


        // decide when to show the options popup menu about
        if (intention == AbstractListActivity.Companion.Intention.VIEW) {
            viewHolder.textView.setOnLongClickListener {
                showOptionsPopup(
                    viewHolder,
                    getItem(i)
                )
            }
            viewHolder.img.setOnLongClickListener {
                showOptionsPopup(
                    viewHolder,
                    getItem(i)
                )
            }
            // ensure onClicks are actually caught
            viewHolder.textView.setOnClickListener { viewHolder.onClick(viewHolder.textView) }
            viewHolder.img.setOnClickListener { viewHolder.onClick(viewHolder.img) }
        }
    }

    @Suppress("SameReturnValue")
    private fun showOptionsPopup(
        viewHolder: ViewHolder,
        appInfo: AbstractDetailedAppInfo
    ): Boolean {

        /* TODO:
            This popup menu needs to be replaced by a custom solution.
            The following should be implemented, but are not possible using android.widget.PopupMenu:
                - display a separator before shortcuts
                - show static shortcuts in one line (small icons, no text)
                - add long press handler to bind shortcuts to gestures
         */
        val popup = PopupMenu(activity, viewHolder.img)
        popup.inflate(R.menu.menu_app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (LauncherPreferences.list().layout() != ListLayout.TEXT) {
                popup.setForceShowIcon(true)
            }
        }

        if (!appInfo.isRemovable()) {
            popup.menu.findItem(R.id.app_menu_delete).isVisible = false
        }

        if (appInfo !is DetailedAppInfo) {
            popup.menu.findItem(R.id.app_menu_info).isVisible = false
        }

        if (LauncherPreferences.apps().hidden()?.contains(appInfo.getRawInfo()) == true) {
            popup.menu.findItem(R.id.app_menu_hidden).apply {
                setTitle(R.string.list_app_hidden_remove)
                setIcon(R.drawable.baseline_visibility_24)
            }

        }

        if (LauncherPreferences.apps().favorites()?.contains(appInfo.getRawInfo()) == true) {
            popup.menu.findItem(R.id.app_menu_favorite).apply {
                setTitle(R.string.list_app_favorite_remove)
                setIcon(R.drawable.baseline_favorite_24)
            }
        }

        // shortcuts
        if (appInfo is DetailedAppInfo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val iconSize = (24 * activity.resources.displayMetrics.density).toInt()
            val launcherApps =
                activity.getSystemService(Service.LAUNCHER_APPS_SERVICE) as LauncherApps
            getAppShortcuts(appInfo.getRawInfo(), activity)
                .forEach { shortcutInfo ->
                    val shortcutIcon =
                        launcherApps.getShortcutBadgedIconDrawable(shortcutInfo, 0).apply {
                            transformMonochrome(grayscale, colorTheme)
                        }
                    val fixedIcon = object : DrawableWrapper(shortcutIcon) {
                        override fun getIntrinsicWidth(): Int = iconSize
                        override fun getIntrinsicHeight(): Int = iconSize
                    }
                    popup.menu.add(shortcutInfo.shortLabel).apply {
                        icon = fixedIcon
                        setOnMenuItemClickListener {
                            launcherApps.startShortcut(shortcutInfo, null, null)
                            true
                        }
                    }
                }
        }

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.app_menu_delete -> {
                    appInfo.getRawInfo().uninstall(activity); true
                }

                R.id.app_menu_info -> {
                    (appInfo.getRawInfo() as? AppInfo)?.openSettings(activity); true
                }

                R.id.app_menu_favorite -> {
                    appInfo.getRawInfo().toggleFavorite(); true
                }

                R.id.app_menu_hidden -> {
                    appInfo.getRawInfo().toggleHidden(root); true
                }

                R.id.app_menu_rename -> {
                    appInfo.showRenameDialog(activity); true
                }

                R.id.app_menu_change_icon -> {
                    appInfo.showCustomIconPickerDialog(activity); true
                }

                else -> false
            }
        }

        popup.show()
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: View = inflater.inflate(layout.layoutResource, parent, false)
        val viewHolder = ViewHolder(view)
        return viewHolder
    }

    fun selectItem(pos: Int, rect: Rect = Rect()) {
        val appInfo = currentList.getOrNull(pos) ?: return
        when (intention) {
            AbstractListActivity.Companion.Intention.VIEW -> {
                appInfo.getAction().invoke(activity, rect)
            }

            AbstractListActivity.Companion.Intention.PICK -> {
                activity.finish()
                forGesture ?: return
                val gesture = Gesture.byId(forGesture) ?: return
                Action.setActionForGesture(gesture, appInfo.getAction())
            }
        }
    }

    /**
     * @param onCommitted called on the main thread once the new list is displayed
     */
    fun updateAppsList(triggerAutoLaunch: Boolean = false, onCommitted: () -> Unit = {}) {
        val newList = apps.value?.let { appFilter(it) } ?: emptyList()

        // the commit callback runs after the new list was applied, so currentList is up to date
        submitList(newList) {
            if (triggerAutoLaunch &&
                currentList.size == 1
                && intention == AbstractListActivity.Companion.Intention.VIEW
                && !disableAutoLaunch
                && LauncherPreferences.functionality().searchAutoLaunch()
            ) {
                val app = currentList[0]
                app.getAction().invoke(activity)

                val inputMethodManager =
                    activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                // use the token of an attached view; a new View has no window token
                inputMethodManager.hideSoftInputFromWindow(root.windowToken, 0)

                // clear the query so returning to the drawer doesn't re-trigger auto-launch
                appFilter.query = ""
                root.findViewById<androidx.appcompat.widget.SearchView>(R.id.list_apps_searchview)
                    ?.setQuery("", false)
            }
            onCommitted()
        }
    }

    /**
     * The function [setSearchString] is used to search elements within this [RecyclerView].
     */
    fun setSearchString(search: String, onCommitted: () -> Unit = {}) {
        appFilter.query = search
        updateAppsList(true, onCommitted)
    }

    fun setFavoritesVisibility(v: AppFilter.Companion.AppSetVisibility) {
        appFilter.favoritesVisibility = v
        updateAppsList()
    }
}
