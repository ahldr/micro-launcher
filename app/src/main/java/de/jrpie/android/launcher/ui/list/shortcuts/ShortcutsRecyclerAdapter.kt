package de.jrpie.android.launcher.ui.list.shortcuts

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import de.jrpie.android.launcher.FilterHelper
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.DetailedAppInfo
import de.jrpie.android.launcher.getAllAppShortcuts
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.ui.transformMonochrome
import java.util.Locale

/**
 * An app shortcut together with the information needed to display it.
 */
@RequiresApi(Build.VERSION_CODES.N_MR1)
class AppShortcutItem(
    val shortcut: ShortcutInfo,
    val label: String,
    val appLabel: String,
    val icon: Drawable?
)

/**
 * Loads the app shortcuts of all apps in [apps].
 * The result is sorted by app and, within an app, in the order defined by the app.
 *
 * This performs IPC calls and loads icons, hence it should not be called on the main thread.
 */
@RequiresApi(Build.VERSION_CODES.N_MR1)
fun loadAppShortcuts(
    context: Context,
    apps: List<AbstractDetailedAppInfo>
): List<AppShortcutItem> {
    val launcherApps = context.getSystemService(Service.LAUNCHER_APPS_SERVICE) as LauncherApps
    val appsByPackage = apps.filterIsInstance<DetailedAppInfo>()
        .groupBy { Pair(it.getRawInfo().packageName, it.getRawInfo().user) }

    return getAllAppShortcuts(context).mapNotNull { shortcut ->
        // only show shortcuts of apps that are shown in the app list
        val candidates = appsByPackage[Pair(shortcut.`package`, shortcut.userHandle.hashCode())]
            ?: return@mapNotNull null
        val app = candidates.firstOrNull {
            it.getRawInfo().activityName == shortcut.activity?.className
        } ?: candidates.first()

        val icon = try {
            launcherApps.getShortcutBadgedIconDrawable(shortcut, 0)
        } catch (_: Exception) {
            null
        } ?: app.getIcon(context)

        AppShortcutItem(
            shortcut,
            (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
            app.getCustomLabel(context),
            icon
        )
    }.sortedWith(
        compareBy(
            { it.appLabel.lowercase(Locale.ROOT) },
            { it.shortcut.`package` },
            { it.shortcut.userHandle.hashCode() },
            // static shortcuts first, then dynamic ones; each ordered by the app's ranking
            { !it.shortcut.isDeclaredInManifest },
            { it.shortcut.rank }
        )
    )
}

/**
 * Lists app shortcuts in [ListFragmentShortcuts].
 * Shortcuts can be searched by their label and by the label of their app.
 */
@RequiresApi(Build.VERSION_CODES.N_MR1)
@SuppressLint("NotifyDataSetChanged")
class ShortcutsRecyclerAdapter(
    private val context: Context,
    private val onSelect: (AppShortcutItem) -> Unit
) : RecyclerView.Adapter<ShortcutsRecyclerAdapter.ViewHolder>() {

    private var items: List<AppShortcutItem> = emptyList()
    private var displayedItems: List<AppShortcutItem> = emptyList()
    private var query: String = ""

    private val colorTheme = LauncherPreferences.theme().colorTheme()
    private val grayscale = colorTheme.monochromeIcons()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.list_shortcuts_row_icon)
        val label: TextView = itemView.findViewById(R.id.list_shortcuts_row_name)
        val appLabel: TextView = itemView.findViewById(R.id.list_shortcuts_row_app)

        init {
            itemView.setOnClickListener {
                displayedItems.getOrNull(bindingAdapterPosition)?.let(onSelect)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_shortcuts_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = displayedItems[position]
        holder.label.text = item.label
        holder.appLabel.text = item.appLabel
        // the fallback icon (the app's icon) might be shared by several shortcuts
        holder.icon.setImageDrawable(item.icon?.constantState?.newDrawable() ?: item.icon)
        holder.icon.transformMonochrome(grayscale, colorTheme)
        holder.itemView.contentDescription =
            context.getString(R.string.list_shortcuts_content_description, item.label, item.appLabel)
    }

    override fun getItemCount(): Int {
        return displayedItems.size
    }

    fun setItems(items: List<AppShortcutItem>) {
        this.items = items
        updateDisplayedItems()
    }

    fun setSearchString(query: String) {
        this.query = query
        updateDisplayedItems()
    }

    fun firstItem(): AppShortcutItem? {
        return displayedItems.firstOrNull()
    }

    private fun updateDisplayedItems() {
        displayedItems = ShortcutFilter.filter(items, query)
        notifyDataSetChanged()
    }

    private object ShortcutFilter : FilterHelper() {
        fun filter(items: List<AppShortcutItem>, query: String): List<AppShortcutItem> {
            if (query.isEmpty()) {
                return items
            }
            return items.filterByQuery(query) { listOf(it.label, it.appLabel) }
        }
    }
}
