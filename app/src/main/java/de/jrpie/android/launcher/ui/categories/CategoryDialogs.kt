package de.jrpie.android.launcher.ui.categories

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.snackbar.Snackbar
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.AppFilter
import de.jrpie.android.launcher.apps.AppFilter.Companion.AppSetVisibility
import de.jrpie.android.launcher.apps.FavoriteCategories
import de.jrpie.android.launcher.apps.FavoriteCategory
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.ui.styledAlertDialogBuilder

/*
 * Dialogs to edit the categories of favorite apps, see [FavoriteCategories].
 */

private fun categories(): FavoriteCategories = LauncherPreferences.apps().favoriteCategories()

private fun store(categories: FavoriteCategories) {
    LauncherPreferences.apps().favoriteCategories(categories)
}

/**
 * The favorite apps the user can see, sorted by name.
 */
fun getVisibleFavorites(context: Context): List<AbstractDetailedAppInfo> {
    val apps = (context.applicationContext as Application).apps.value ?: return emptyList()
    return AppFilter(
        context, "",
        favoritesVisibility = AppSetVisibility.EXCLUSIVE,
        hiddenVisibility = AppSetVisibility.HIDDEN,
        privateSpaceVisibility = AppSetVisibility.VISIBLE
    ).filterVisible(apps)
}

/**
 * Lets the user choose the category of the favorite [app], or create a new one for it.
 */
fun showCategoryPicker(context: Context, app: AbstractDetailedAppInfo) {
    val categories = categories()
    val rawInfo = app.getRawInfo()
    val items = listOf(context.getString(R.string.dialog_select_category_none))
        .plus(categories.categories.map { it.name })
        .toTypedArray<CharSequence>()
    val current = categories.categoryOf(rawInfo)
    val checked = if (current == null) 0 else categories.categories.indexOf(current) + 1

    styledAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.dialog_select_category_title, app.getCustomLabel(context)))
        .setSingleChoiceItems(items, checked) { dialog, which ->
            store(categories().assign(rawInfo, categories.categories.getOrNull(which - 1)?.id))
            dialog.dismiss()
        }
        .setNeutralButton(R.string.dialog_category_new) { _, _ ->
            createCategory(context) { category ->
                store(categories().assign(rawInfo, category.id))
            }
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .show()
}

/**
 * Asks for the name of a new category and adds it.
 */
fun createCategory(context: Context, onCreated: (FavoriteCategory) -> Unit = {}) {
    showCategoryNameDialog(context, R.string.dialog_category_new, "") { name ->
        val (categories, category) = categories().add(name)
        store(categories)
        onCreated(category)
    }
}

fun renameCategory(context: Context, id: Int) {
    val category = categories().byId(id) ?: return
    showCategoryNameDialog(context, R.string.dialog_category_rename, category.name) { name ->
        store(categories().rename(id, name))
    }
}

/**
 * Deletes a category. Its apps stay favorites.
 * A snackbar shown on [view] (above [anchor], if set) allows to undo this.
 */
fun deleteCategory(view: View, id: Int, anchor: View? = null) {
    val before = categories()
    val category = before.byId(id) ?: return
    val index = before.categories.indexOf(category)
    store(before.remove(id))

    Snackbar.make(
        view,
        view.context.getString(R.string.snackbar_category_deleted, category.name),
        Snackbar.LENGTH_LONG
    ).setAction(R.string.undo) {
        store(categories().restore(index, category))
    }.setAnchorView(anchor).show()
}

private fun showCategoryNameDialog(
    context: Context,
    title: Int,
    name: String,
    onConfirm: (String) -> Unit
) {
    val dialog = styledAlertDialogBuilder(context)
        .setTitle(title)
        .setView(R.layout.dialog_category_name)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_ok) { dialogInterface, _ ->
            (dialogInterface as AlertDialog).findViewById<EditText>(R.id.dialog_category_name_edit_text)
                ?.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(onConfirm)
        }
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()

    val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    fun updateOk(text: CharSequence?) {
        ok.isEnabled = !text.isNullOrBlank()
        // the themed buttons have no disabled color
        ok.alpha = if (ok.isEnabled) 1f else 0.4f
    }
    dialog.findViewById<EditText>(R.id.dialog_category_name_edit_text)?.apply {
        setText(name)
        setSelection(text.length)
        updateOk(text)
        doAfterTextChanged { updateOk(it) }
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && ok.isEnabled) {
                ok.performClick()
            }
            actionId == EditorInfo.IME_ACTION_DONE
        }
        requestFocus()
    }
}

/**
 * Lets the user choose which favorites are in the category [id].
 * Apps that are in another category are marked as such and moved when chosen.
 */
fun showCategoryAppsDialog(context: Context, id: Int) {
    val category = categories().byId(id) ?: return
    val title = context.getString(R.string.dialog_category_apps_title, category.name)
    val favorites = getVisibleFavorites(context)
    if (favorites.isEmpty()) {
        styledAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(R.string.dialog_category_apps_no_favorites)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
        return
    }

    val categories = categories()
    val dimmed = ColorUtils.setAlphaComponent(context.textColor(), 0x99)
    val labels = favorites.map { app ->
        val label = SpannableStringBuilder(app.getCustomLabel(context))
        // apps in another category show it below their name
        categories.categoryOf(app.getRawInfo())?.takeIf { it.id != id }?.let { other ->
            val start = label.length
            label.append("\n").append(other.name)
            label.setSpan(
                ForegroundColorSpan(dimmed), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            label.setSpan(
                RelativeSizeSpan(0.75f), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        label
    }.toTypedArray<CharSequence>()
    val checked = favorites.map { it.getRawInfo() in category.apps }.toBooleanArray()

    styledAlertDialogBuilder(context)
        .setTitle(title)
        .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
            checked[which] = isChecked
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_ok) { _, _ ->
            val selected = favorites.filterIndexed { i, _ -> checked[i] }
                .map { it.getRawInfo() }.toSet()
            store(categories().setApps(id, selected, favorites.map { it.getRawInfo() }.toSet()))
        }
        .show()
}

/**
 * Menu for a category heading in the app list.
 */
fun showCategoryMenu(context: Context, anchor: View, category: FavoriteCategory) {
    PopupMenu(context, anchor).apply {
        menu.add(R.string.favorite_categories_choose_apps).setOnMenuItemClickListener {
            showCategoryAppsDialog(context, category.id)
            true
        }
        menu.add(R.string.dialog_category_rename).setOnMenuItemClickListener {
            renameCategory(context, category.id)
            true
        }
        menu.add(R.string.dialog_category_delete).setOnMenuItemClickListener {
            deleteCategory(anchor, category.id)
            true
        }
    }.show()
}

private fun Context.textColor(): Int {
    val attributes = obtainStyledAttributes(intArrayOf(android.R.attr.textColor))
    return try {
        attributes.getColor(0, Color.WHITE)
    } finally {
        attributes.recycle()
    }
}
