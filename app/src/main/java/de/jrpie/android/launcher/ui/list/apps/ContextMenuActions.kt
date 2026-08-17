package de.jrpie.android.launcher.ui.list.apps

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.apps.AbstractAppInfo
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.AppInfo
import de.jrpie.android.launcher.apps.PinnedShortcutInfo
import de.jrpie.android.launcher.getUserFromId
import de.jrpie.android.launcher.preferences.LauncherPreferences

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.jrpie.android.launcher.icons.IconEntry
import de.jrpie.android.launcher.icons.IconPackManager

private const val LOG_TAG = "AppContextMenu"

fun AppInfo.openSettings(
    context: Context,
    sourceBounds: Rect? = null,
    opts: Bundle? = null
) {
    val launcherApps = context.getSystemService(Service.LAUNCHER_APPS_SERVICE) as LauncherApps
    this.getLauncherActivityInfo(context)?.let { app ->
        launcherApps.startAppDetailsActivity(app.componentName, app.user, sourceBounds, opts)
    }
}

fun AbstractAppInfo.uninstall(activity: Activity) {
    if (this is AppInfo) {
        val packageName = this.packageName
        val userId = this.user

        Log.i(LOG_TAG, "uninstalling $this")

        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = "package:$packageName".toUri()
        getUserFromId(userId, activity).let { user ->
            intent.putExtra(Intent.EXTRA_USER, user)
        }
        activity.startActivity(intent)

    } else if (this is PinnedShortcutInfo) {
        val pinned = LauncherPreferences.apps().pinnedShortcuts() ?: mutableSetOf()
        pinned.remove(this)
        LauncherPreferences.apps().pinnedShortcuts(pinned)
    }
}

fun AbstractAppInfo.toggleFavorite() {
    val favorites: MutableSet<AbstractAppInfo> =
        LauncherPreferences.apps().favorites() ?: mutableSetOf()

    if (favorites.contains(this)) {
        favorites.remove(this)
        Log.i(LOG_TAG, "Removing $this from favorites.")
    } else {
        Log.i(LOG_TAG, "Adding $this to favorites.")
        favorites.add(this)
    }

    LauncherPreferences.apps().favorites(favorites)
}

/**
 * @param view: used to show a snackbar letting the user undo the action
 */
fun AbstractAppInfo.toggleHidden(view: View) {
    val hidden: MutableSet<AbstractAppInfo> =
        LauncherPreferences.apps().hidden() ?: mutableSetOf()
    if (hidden.contains(this)) {
        hidden.remove(this)
    } else {
        hidden.add(this)

        Snackbar.make(view, R.string.snackbar_app_hidden, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                LauncherPreferences.apps().hidden(
                    LauncherPreferences.apps().hidden().minus(this)
                )
            }.show()
    }
    LauncherPreferences.apps().hidden(hidden)
}

fun AbstractDetailedAppInfo.showRenameDialog(context: Context) {
    AlertDialog.Builder(context, R.style.AlertDialogCustom).apply {
        setTitle(context.getString(R.string.dialog_rename_title, getLabel()))
        setView(R.layout.dialog_rename_app)
        setNegativeButton(android.R.string.cancel) { d, _ -> d.cancel() }
        setPositiveButton(android.R.string.ok) { d, _ ->
            setCustomLabel(
                (d as? AlertDialog)
                    ?.findViewById<EditText>(R.id.dialog_rename_app_edit_text)
                    ?.text.toString()
            )
        }
    }.create().also { it.show() }.apply {
        val input = findViewById<EditText>(R.id.dialog_rename_app_edit_text)
        input?.setText(getCustomLabel(context))
        input?.hint = getLabel()
    }
}

private class IconPickerViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    val image: ImageView = v.findViewById(R.id.dialog_custom_icon_image)
    val name: TextView = v.findViewById(R.id.dialog_custom_icon_name)
}

private class IconPickerAdapter(
    private var items: List<IconEntry>,
    private val iconManager: IconPackManager,
    private val onItemClick: (IconEntry) -> Unit
) : RecyclerView.Adapter<IconPickerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconPickerViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.dialog_custom_icon_item, parent, false)
        return IconPickerViewHolder(v)
    }

    override fun onBindViewHolder(holder: IconPickerViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        val drawable = iconManager.getDrawableByName(item.drawableName)
        holder.image.setImageDrawable(drawable)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<IconEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}

fun AbstractDetailedAppInfo.showCustomIconPickerDialog(context: Context) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_icon_picker, null)
    val searchEdit = dialogView.findViewById<EditText>(R.id.dialog_custom_icon_search)
    val recycler = dialogView.findViewById<RecyclerView>(R.id.dialog_custom_icon_recycler)
    val resetButton = dialogView.findViewById<Button>(R.id.dialog_custom_icon_reset)

    recycler.layoutManager = GridLayoutManager(context, 4)

    val iconManager = IconPackManager.getInstance(context)
    val allIcons = iconManager.getAvailableIcons(context)

    val dialog = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        .setTitle(R.string.dialog_custom_icon_picker_title)
        .setView(dialogView)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

    val adapter = IconPickerAdapter(allIcons, iconManager) { item ->
        setCustomIcon(item.componentNameStr)
        dialog.dismiss()
    }
    recycler.adapter = adapter

    searchEdit.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val query = s?.toString()?.lowercase() ?: ""
            val filtered = if (query.isEmpty()) {
                allIcons
            } else {
                allIcons.filter { it.label.lowercase().contains(query) || it.drawableName.lowercase().contains(query) }
            }
            adapter.update(filtered)
        }
        override fun afterTextChanged(s: Editable?) {}
    })

    resetButton.setOnClickListener {
        setCustomIcon(null)
        dialog.dismiss()
    }

    dialog.show()
}


