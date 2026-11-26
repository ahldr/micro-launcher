package de.jrpie.android.launcher.ui.list.apps

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.actions.ShortcutAction
import de.jrpie.android.launcher.apps.AbstractAppInfo
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.AppFilter
import de.jrpie.android.launcher.apps.AppInfo
import de.jrpie.android.launcher.apps.DetailedAppInfo
import de.jrpie.android.launcher.getAppShortcuts
import de.jrpie.android.launcher.isDefaultHomeScreen
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.AppNameFormat
import de.jrpie.android.launcher.preferences.list.GridSetting
import de.jrpie.android.launcher.preferences.list.ListLayout
import de.jrpie.android.launcher.ui.list.AbstractListActivity
import de.jrpie.android.launcher.ui.transformMonochrome
import kotlin.math.min

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
    private val nameFormat: AppNameFormat,
    private val grid: Map<GridSetting, Int>,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    private val apps = (activity.applicationContext as Application).apps
    private val appsListDisplayed: MutableList<AbstractDetailedAppInfo> = mutableListOf()

    // [appsListDisplayed], with headings (Strings) for the categories of favorites
    private val rows: MutableList<Any> = mutableListOf()
    private var categories: Map<AbstractAppInfo, String> = emptyMap()
    private val theme = LauncherPreferences.theme()
    private val colorTheme = theme.colorTheme()
    private val grayscale = colorTheme.monochromeIcons()

    // The installed apps the list was last built from.
    private var appsSource: List<AbstractDetailedAppInfo>? = null

    // [appsSource] sorted and filtered by visibility, before applying the search query.
    // Kept so that typing only has to run the query filter.
    private var appsVisible: List<AbstractDetailedAppInfo> = emptyList()

    // temporarily disable auto launch
    var disableAutoLaunch: Boolean = false


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


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, i: Int) {
        val row = rows[i]
        if (row is String) {
            holder.itemView.findViewById<TextView>(R.id.list_apps_header_name).text =
                nameFormat.format(row)
            if (intention == AbstractListActivity.Companion.Intention.VIEW) {
                holder.itemView.setOnLongClickListener {
                    showCategoryRenameDialog(activity, row)
                    true
                }
            }
            return
        }
        val app = row as AbstractDetailedAppInfo
        val viewHolder = holder as ViewHolder
        var appLabel = app.getCustomLabel(activity)

        val appIcon = app.getIcon(activity)

        viewHolder.img.transformMonochrome(grayscale, colorTheme)
        viewHolder.img.setImageDrawable(appIcon?.constantState?.newDrawable() ?: appIcon)

        if (layout.useBadgedText) {
            appLabel = activity.packageManager.getUserBadgedLabel(
                appLabel,
                app.getUser(activity)
            ).toString()
        }
        viewHolder.textView.text = nameFormat.format(appLabel)


        // decide when to show the options popup menu about
        if (intention == AbstractListActivity.Companion.Intention.VIEW) {
            viewHolder.textView.setOnLongClickListener {
                showOptionsPopup(viewHolder, app)
            }
            viewHolder.img.setOnLongClickListener {
                showOptionsPopup(viewHolder, app)
            }
            // ensure onClicks are actually caught
            viewHolder.textView.setOnClickListener { viewHolder.onClick(viewHolder.textView) }
            viewHolder.img.setOnClickListener { viewHolder.onClick(viewHolder.img) }
        } else if (intention == AbstractListActivity.Companion.Intention.PICK) {
            // long click shows the app's shortcuts, such that one of them can be bound instead
            viewHolder.itemView.setOnLongClickListener {
                (rows.getOrNull(viewHolder.bindingAdapterPosition) as? AbstractDetailedAppInfo)
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
        return rows.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (isHeader(position)) VIEW_TYPE_HEADER else VIEW_TYPE_APP
    }

    fun isHeader(position: Int): Boolean {
        return rows.getOrNull(position) is String
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == VIEW_TYPE_HEADER) {
            return object : RecyclerView.ViewHolder(
                inflater.inflate(R.layout.list_apps_row_header, parent, false)
            ) {}
        }
        val view: View = inflater.inflate(layout.layoutResource, parent, false)
        val viewHolder = ViewHolder(view)
        if (layout.isGrid) {
            val metrics = parent.resources.displayMetrics
            fun sp(value: Int) = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value.toFloat(), metrics
            ).toInt()
            // the icon must fit into its column
            val columns = ((parent as RecyclerView).layoutManager as GridLayoutManager).spanCount
            var iconSize = sp(grid.getValue(GridSetting.ICON_SIZE))
            if (parent.width > 0) {
                iconSize = min(iconSize, parent.width / columns - sp(10))
            }
            viewHolder.img.layoutParams.width = iconSize
            viewHolder.img.layoutParams.height = iconSize
            (view.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = sp(grid.getValue(GridSetting.SPACING)) / 2
                bottomMargin = topMargin
            }
        }
        return viewHolder
    }

    /** Selects the first app, skipping headings. */
    fun selectFirstItem() {
        selectItem(rows.indexOfFirst { it !is String })
    }

    fun selectItem(pos: Int, rect: Rect = Rect()) {
        val appInfo = rows.getOrNull(pos) as? AbstractDetailedAppInfo ?: return
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
     * Rebuilds the list from the installed apps and the current preferences.
     */
    fun updateAppsList() {
        appsSource = apps.value
        appsVisible = appsSource?.let { appFilter.filterVisible(it) } ?: emptyList()
        categories = LauncherPreferences.apps().categories() ?: emptyMap()
        showMatchingApps(false)
    }

    /**
     * Rebuilds the list only if the installed apps changed since it was last built.
     */
    fun updateAppsListIfChanged() {
        if (apps.value !== appsSource) {
            updateAppsList()
        }
    }

    private fun showMatchingApps(triggerAutoLaunch: Boolean) {
        appsListDisplayed.clear()
        appsListDisplayed.addAll(appFilter.applyQuery(appsVisible))
        buildRows()

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
     * Favorites are grouped by their category (sorted by name), with a heading for each.
     * Favorites without a category are shown at the top.
     */
    private fun buildRows() {
        rows.clear()
        if (appFilter.favoritesVisibility != AppFilter.Companion.AppSetVisibility.EXCLUSIVE) {
            rows.addAll(appsListDisplayed)
            return
        }
        // A reversed list is laid out bottom up, so headings have to follow their apps.
        val reverse = LauncherPreferences.list().reverseLayout()
        val groups = appsListDisplayed.groupBy { categories[it.getRawInfo()] }
        if (!reverse) groups[null]?.let { rows.addAll(it) }
        for (category in groups.keys.filterNotNull().sortedBy { it.lowercase() }) {
            if (!reverse) rows.add(category)
            rows.addAll(groups.getValue(category))
            if (reverse) rows.add(category)
        }
        if (reverse) groups[null]?.let { rows.addAll(it) }
    }

    /**
     * The function [setSearchString] is used to search elements within this [RecyclerView].
     */
    fun setSearchString(search: String, triggerAutoLaunch: Boolean = true) {
        appFilter.query = search
        showMatchingApps(triggerAutoLaunch)
    }

    /**
     * Changes which apps are shown. Returns true if anything changed;
     * the list is then out of date until [updateAppsList] is called.
     */
    fun setVisibilities(
        favorites: AppFilter.Companion.AppSetVisibility,
        hidden: AppFilter.Companion.AppSetVisibility,
        privateSpace: AppFilter.Companion.AppSetVisibility
    ): Boolean {
        if (appFilter.favoritesVisibility == favorites
            && appFilter.hiddenVisibility == hidden
            && appFilter.privateSpaceVisibility == privateSpace
        ) {
            return false
        }
        appFilter.favoritesVisibility = favorites
        appFilter.hiddenVisibility = hidden
        appFilter.privateSpaceVisibility = privateSpace
        return true
    }

    fun setFavoritesVisibility(v: AppFilter.Companion.AppSetVisibility) {
        appFilter.favoritesVisibility = v
        updateAppsList()
    }

    companion object {
        private const val VIEW_TYPE_APP = 0
        private const val VIEW_TYPE_HEADER = 1
    }
}
