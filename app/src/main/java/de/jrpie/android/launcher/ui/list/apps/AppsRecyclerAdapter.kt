package de.jrpie.android.launcher.ui.list.apps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
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
import de.jrpie.android.launcher.apps.FavoriteCategories
import de.jrpie.android.launcher.apps.FavoriteCategory
import de.jrpie.android.launcher.getAppShortcuts
import de.jrpie.android.launcher.isDefaultHomeScreen
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.AppNameFormat
import de.jrpie.android.launcher.preferences.list.GridSpec
import de.jrpie.android.launcher.preferences.list.ListLayout
import de.jrpie.android.launcher.ui.categories.showCategoryMenu
import de.jrpie.android.launcher.ui.list.AbstractListActivity
import de.jrpie.android.launcher.ui.transformMonochrome
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A [RecyclerView] (efficient scrollable list) containing all apps on the users device.
 * The apps details are represented by [AppInfo].
 *
 * If [ListPresentation.groupByCategory] is set, apps are shown under a heading
 * for each of their [FavoriteCategories].
 *
 * @param activity - the activity this is in
 * @param intention - why the list is displayed ("view", "pick")
 * @param forGesture - the action which an app is chosen for (when the intention is "pick")
 * @param previewOnly - the list only shows what it looks like (see [GridEditor]):
 *  apps can't be launched or edited
 */
@SuppressLint("NotifyDataSetChanged")
class AppsRecyclerAdapter(
    val activity: AppCompatActivity,
    val root: View,
    private val intention: AbstractListActivity.Companion.Intention = AbstractListActivity.Companion.Intention.VIEW,
    private val forGesture: String? = "",
    private var appFilter: AppFilter = AppFilter(activity, ""),
    presentation: ListPresentation,
    private val nameFormat: AppNameFormat,
    private val previewOnly: Boolean = false,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row
    private class AppRow(val app: AbstractDetailedAppInfo) : Row

    /** Heading of [category], or of the apps in no category if [category] is null. */
    private class HeaderRow(val category: FavoriteCategory?, val topmost: Boolean) : Row

    private val apps = (activity.applicationContext as Application).apps
    private val rows: MutableList<Row> = mutableListOf()
    private val theme = LauncherPreferences.theme()
    private val colorTheme = theme.colorTheme()
    private val grayscale = colorTheme.monochromeIcons()

    // The installed apps the list was last built from.
    private var appsSource: List<AbstractDetailedAppInfo>? = null

    // [appsSource] sorted and filtered by visibility, before applying the search query.
    // Kept so that typing only has to run the query filter.
    private var appsVisible: List<AbstractDetailedAppInfo> = emptyList()

    // [appsVisible] filtered by the search query.
    private var appsMatching: List<AbstractDetailedAppInfo> = emptyList()

    private var categories = FavoriteCategories()

    private var recyclerView: RecyclerView? = null

    private val headerTextColor: Int
    private val headerRuleColor: Int

    // temporarily disable auto launch
    var disableAutoLaunch: Boolean = false

    /** Called whenever the list was rebuilt, with the number of apps it shows. */
    var onListChanged: ((Int) -> Unit)? = null

    var presentation: ListPresentation = presentation
        set(value) {
            if (field == value) {
                return
            }
            field = value
            gridMetrics = GridMetrics(activity, value.grid)
            buildRows()
            notifyDataSetChanged()
        }

    private var gridMetrics = GridMetrics(activity, presentation.grid)

    init {
        val attributes = activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColor))
        val textColor = try {
            attributes.getColor(0, Color.WHITE)
        } finally {
            attributes.recycle()
        }
        headerTextColor = ColorUtils.setAlphaComponent(textColor, HEADER_TEXT_ALPHA)
        headerRuleColor = ColorUtils.setAlphaComponent(textColor, HEADER_RULE_ALPHA)
    }

    /**
     * Sizes from a [GridSpec], in pixels.
     */
    class GridMetrics(context: Context, spec: GridSpec) {
        val iconSize = context.sp(spec.iconSize.toFloat())
        val labelSize = spec.labelSize.toFloat()
        val rowMargin = context.sp(spec.rowSpacing / 2f)
        val cellMargin = context.sp(GRID_CELL_MARGIN_SP)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        var textView: TextView = itemView.findViewById(R.id.list_apps_row_name)
        var img: ImageView = itemView.findViewById(R.id.list_apps_row_icon)

        // the sizes this view has (only for grid layouts)
        private var appliedGrid: GridMetrics? = null

        override fun onClick(v: View) {
            val rect = Rect()
            img.getGlobalVisibleRect(rect)
            selectItem(bindingAdapterPosition, rect)
        }

        init {
            if (!previewOnly) {
                itemView.setOnClickListener(this)
            }
        }

        // where to show menus
        val anchor: View
            get() = if (textView.isVisible) textView else img

        fun applyGrid(metrics: GridMetrics) {
            if (appliedGrid === metrics) {
                return
            }
            appliedGrid = metrics
            (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                topMargin = metrics.rowMargin
                bottomMargin = metrics.rowMargin
                leftMargin = metrics.cellMargin
                rightMargin = metrics.cellMargin
            }
            (img.layoutParams as? ConstraintLayout.LayoutParams)?.let {
                it.matchConstraintMaxWidth = metrics.iconSize
                img.layoutParams = it
            }
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.labelSize)
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.list_apps_header_label)

        init {
            label.setTextColor(headerTextColor)
            itemView.findViewById<View>(R.id.list_apps_header_rule)
                .setBackgroundColor(headerRuleColor)
            ViewCompat.setAccessibilityHeading(itemView, true)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is HeaderRow -> VIEW_TYPE_HEADER
            // views of different layouts must not be recycled for one another
            is AppRow -> VIEW_TYPE_APP + presentation.layout.ordinal
        }
    }

    fun isHeader(position: Int): Boolean {
        return rows.getOrNull(position) is HeaderRow
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is AppRow -> bindApp(holder as ViewHolder, row.app)
            is HeaderRow -> bindHeader(holder as HeaderViewHolder, row)
        }
    }

    private fun bindApp(viewHolder: ViewHolder, app: AbstractDetailedAppInfo) {
        if (presentation.layout.isGrid) {
            viewHolder.applyGrid(gridMetrics)
        }

        var appLabel = app.getCustomLabel(activity)

        val appIcon = app.getIcon(activity)

        viewHolder.img.transformMonochrome(grayscale, colorTheme)
        viewHolder.img.setImageDrawable(appIcon?.constantState?.newDrawable() ?: appIcon)

        if (presentation.layout.useBadgedText) {
            appLabel = activity.packageManager.getUserBadgedLabel(
                appLabel,
                app.getUser(activity)
            ).toString()
        }
        viewHolder.textView.text = nameFormat.format(appLabel)

        if (previewOnly) {
            return
        }

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
                (rows.getOrNull(viewHolder.bindingAdapterPosition) as? AppRow)
                    ?.let { showShortcutPicker(viewHolder, it.app) } ?: false
            }
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, row: HeaderRow) {
        val name = row.category?.name
            ?: activity.getString(R.string.list_favorites_category_other)
        holder.label.text = nameFormat.format(name)

        // line up with the icons (or names) of the apps below
        val inset = headerInset()
        holder.itemView.setPaddingRelative(
            inset,
            activity.dp(if (row.topmost) HEADER_PADDING_TOP_FIRST_DP else HEADER_PADDING_TOP_DP),
            inset,
            activity.dp(HEADER_PADDING_BOTTOM_DP)
        )

        val category = row.category
        if (category != null && !previewOnly
            && intention == AbstractListActivity.Companion.Intention.VIEW
        ) {
            holder.itemView.setOnLongClickListener {
                showCategoryMenu(activity, holder.label, category)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.isLongClickable = false
        }
    }

    /**
     * Horizontal space before a heading, such that it is aligned with the apps below.
     */
    private fun headerInset(): Int {
        val layout = presentation.layout
        if (!layout.isGrid) {
            // see the margins and paddings of the layouts
            return if (layout == ListLayout.TEXT) {
                activity.sp(5f) + activity.dp(20f)
            } else {
                activity.sp(15f)
            }
        }
        val recyclerView = recyclerView ?: return 0
        val spanCount = (recyclerView.layoutManager as? GridLayoutManager)?.spanCount ?: return 0
        val cellWidth =
            (recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight) / spanCount.toFloat()
        val iconWidth = min(
            gridMetrics.iconSize.toFloat(),
            cellWidth - 2 * gridMetrics.cellMargin
        )
        return max(0f, (cellWidth - iconWidth) / 2f).roundToInt()
    }

    @Suppress("SameReturnValue")
    private fun showOptionsPopup(
        viewHolder: ViewHolder,
        appInfo: AbstractDetailedAppInfo
    ): Boolean {
        AppContextMenu(appInfo, activity, showIcons = presentation.layout.showsIcons)
            .show(viewHolder.anchor)
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

        AppContextMenu(
            appInfo,
            activity,
            showIcons = presentation.layout.showsIcons
        ) { shortcut ->
            ShortcutAction.bindToGesture(activity, shortcut, gesture)
            activity.finish()
        }.show(viewHolder.anchor)
        return true
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == VIEW_TYPE_HEADER) {
            return HeaderViewHolder(
                inflater.inflate(R.layout.list_apps_row_header, parent, false)
            )
        }
        val layout = ListLayout.entries[viewType - VIEW_TYPE_APP]
        return ViewHolder(inflater.inflate(layout.layoutResource, parent, false))
    }

    fun selectItem(pos: Int, rect: Rect = Rect()) {
        if (previewOnly) {
            return
        }
        val appInfo = (rows.getOrNull(pos) as? AppRow)?.app ?: return
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
     * Selects the first app of the list (headings are skipped).
     */
    fun selectFirstItem() {
        selectItem(rows.indexOfFirst { it is AppRow })
    }

    /**
     * Rebuilds the list from the installed apps and the current preferences.
     */
    fun updateAppsList() {
        appsSource = apps.value
        appsVisible = appsSource?.let { appFilter.filterVisible(it) } ?: emptyList()
        if (previewOnly && appsVisible.isEmpty()
            && appFilter.favoritesVisibility == AppFilter.Companion.AppSetVisibility.EXCLUSIVE
        ) {
            // There are no favorites yet to preview their layout with. Use the other apps.
            appsVisible = appsSource?.let {
                AppFilter(
                    activity, "",
                    favoritesVisibility = AppFilter.Companion.AppSetVisibility.VISIBLE,
                    hiddenVisibility = appFilter.hiddenVisibility,
                    privateSpaceVisibility = appFilter.privateSpaceVisibility
                ).filterVisible(it)
            } ?: emptyList()
        }
        categories = LauncherPreferences.apps().favoriteCategories()
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
        appsMatching = appFilter.applyQuery(appsVisible)
        buildRows()

        if (triggerAutoLaunch &&
            appsMatching.size == 1
            && intention == AbstractListActivity.Companion.Intention.VIEW
            && !disableAutoLaunch
            && !previewOnly
            && LauncherPreferences.functionality().searchAutoLaunch()
        ) {
            val app = appsMatching[0]
            app.getAction().invoke(activity)

            val inputMethodManager =
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(View(activity).windowToken, 0)
        }

        notifyDataSetChanged()
        onListChanged?.invoke(appsMatching.size)
    }

    /**
     * Builds [rows] from [appsMatching]: if they are grouped by category,
     * each group gets a heading above it.
     */
    private fun buildRows() {
        rows.clear()
        val sections = if (presentation.groupByCategory) {
            categories.group(appsMatching) { it.getRawInfo() }
        } else {
            listOf(FavoriteCategories.Section(null, appsMatching))
        }
        if (sections.size == 1 && sections[0].category == null) {
            appsMatching.mapTo(rows) { AppRow(it) }
            return
        }

        // A reversed list is laid out from the bottom up,
        // so headings have to follow their apps to end up above them.
        val reverse = presentation.reverse
        val topmost = if (reverse) sections.lastIndex else 0
        sections.forEachIndexed { index, section ->
            val header = HeaderRow(section.category, index == topmost)
            if (!reverse) {
                rows.add(header)
            }
            section.items.mapTo(rows) { AppRow(it) }
            if (reverse) {
                rows.add(header)
            }
        }
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
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APP = 1

        private const val GRID_CELL_MARGIN_SP = 5f

        private const val HEADER_PADDING_TOP_FIRST_DP = 4f
        private const val HEADER_PADDING_TOP_DP = 20f
        private const val HEADER_PADDING_BOTTOM_DP = 6f
        private const val HEADER_TEXT_ALPHA = 0x9e
        private const val HEADER_RULE_ALPHA = 0x33
    }
}

private fun Context.sp(value: Float): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
        .roundToInt()
}

private fun Context.dp(value: Float): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
        .roundToInt()
}
