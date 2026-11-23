package de.jrpie.android.launcher.ui.list.apps

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.actions.ShortcutAction
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.AppFilter
import de.jrpie.android.launcher.apps.AppInfo
import de.jrpie.android.launcher.apps.DetailedAppInfo
import de.jrpie.android.launcher.getAppShortcuts
import de.jrpie.android.launcher.isDefaultHomeScreen
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
 * @param intention - why the list is displayed ("view", "pick")
 * @param forGesture - the action which an app is chosen for (when the intention is "pick")
 */
@SuppressLint("NotifyDataSetChanged")
class AppsRecyclerAdapter(
    val activity: AppCompatActivity,
    val root: View,
    private val intention: AbstractListActivity.Companion.Intention = AbstractListActivity.Companion.Intention.VIEW,
    private val forGesture: String? = "",
    private var appFilter: AppFilter = AppFilter(activity, ""),
    private val layout: ListLayout,
    private val nameFormat: AppNameFormat
) :
    RecyclerView.Adapter<AppsRecyclerAdapter.ViewHolder>() {


    private val apps = (activity.applicationContext as Application).apps
    private val appsListDisplayed: MutableList<AbstractDetailedAppInfo> = mutableListOf()
    private val theme = LauncherPreferences.theme()
    private val colorTheme = theme.colorTheme()
    private val grayscale = colorTheme.monochromeIcons()

    // temporarily disable auto launch
    var disableAutoLaunch: Boolean = false

    init {
        // LiveData delivers the current list once the activity is started,
        // so populating the list here as well would build it twice.
        apps.observe(this.activity as AppCompatActivity) {
            updateAppsList()
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
        var appLabel = appsListDisplayed[i].getCustomLabel(activity)

        val appIcon = appsListDisplayed[i].getIcon(activity)

        viewHolder.img.transformMonochrome(grayscale, colorTheme)
        viewHolder.img.setImageDrawable(appIcon?.constantState?.newDrawable() ?: appIcon)

        if (layout.useBadgedText) {
            appLabel = activity.packageManager.getUserBadgedLabel(
                appLabel,
                appsListDisplayed[i].getUser(activity)
            ).toString()
        }
        viewHolder.textView.text = nameFormat.format(appLabel)


        // decide when to show the options popup menu about
        if (intention == AbstractListActivity.Companion.Intention.VIEW) {
            viewHolder.textView.setOnLongClickListener {
                showOptionsPopup(
                    viewHolder,
                    appsListDisplayed[i]
                )
            }
            viewHolder.img.setOnLongClickListener {
                showOptionsPopup(
                    viewHolder,
                    appsListDisplayed[i]
                )
            }
            // ensure onClicks are actually caught
            viewHolder.textView.setOnClickListener { viewHolder.onClick(viewHolder.textView) }
            viewHolder.img.setOnClickListener { viewHolder.onClick(viewHolder.img) }
        } else if (intention == AbstractListActivity.Companion.Intention.PICK) {
            // long click shows the app's shortcuts, such that one of them can be bound instead
            viewHolder.itemView.setOnLongClickListener {
                appsListDisplayed.getOrNull(viewHolder.bindingAdapterPosition)
                    ?.let { showShortcutPicker(viewHolder, it) } ?: false
            }
        }
    }

    @Suppress("SameReturnValue")
    private fun showOptionsPopup(
        viewHolder: ViewHolder,
        appInfo: AbstractDetailedAppInfo
    ): Boolean {
        AppContextMenu(appInfo, activity).show(viewHolder.textView)
        return true
    }


    /**
     * Shows the shortcuts of [appInfo] (e.g. "New tab" of a browser).
     * Picking one binds it to [forGesture] instead of the app itself.
     */
    private fun showShortcutPicker(
        viewHolder: ViewHolder,
        appInfo: AbstractDetailedAppInfo
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || appInfo !is DetailedAppInfo) {
            return false
        }
        val gesture = forGesture?.let { Gesture.byId(it) } ?: return false

        if (getAppShortcuts(appInfo.getRawInfo(), activity).isEmpty()) {
            val message = if (isDefaultHomeScreen(activity)) {
                activity.getString(
                    R.string.toast_app_has_no_shortcuts,
                    appInfo.getCustomLabel(activity)
                )
            } else {
                activity.getString(R.string.toast_shortcuts_not_default_home)
            }
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            return true
        }

        AppContextMenu(appInfo, activity) { shortcut ->
            ShortcutAction.bindToGesture(activity, shortcut, gesture)
            activity.finish()
        }.show(viewHolder.textView)
        return true
    }

    override fun getItemCount(): Int {
        return appsListDisplayed.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: View = inflater.inflate(layout.layoutResource, parent, false)
        val viewHolder = ViewHolder(view)
        return viewHolder
    }

    fun selectItem(pos: Int, rect: Rect = Rect()) {
        val appInfo = appsListDisplayed.getOrNull(pos) ?: return
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

    fun updateAppsList(triggerAutoLaunch: Boolean = false) {
        appsListDisplayed.clear()
        apps.value?.let { appsListDisplayed.addAll(appFilter(it)) }

        if (triggerAutoLaunch &&
            appsListDisplayed.size == 1
            && intention == AbstractListActivity.Companion.Intention.VIEW
            && !disableAutoLaunch
            && LauncherPreferences.functionality().searchAutoLaunch()
        ) {
            val app = appsListDisplayed[0]
            app.getAction().invoke(activity)

            val inputMethodManager =
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(View(activity).windowToken, 0)
        }

        notifyDataSetChanged()
    }

    /**
     * The function [setSearchString] is used to search elements within this [RecyclerView].
     */
    fun setSearchString(search: String) {
        appFilter.query = search
        updateAppsList(true)

    }

    fun setFavoritesVisibility(v: AppFilter.Companion.AppSetVisibility) {
        appFilter.favoritesVisibility = v
        updateAppsList()
    }
}
