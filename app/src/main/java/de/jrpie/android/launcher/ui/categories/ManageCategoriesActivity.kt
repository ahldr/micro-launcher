package de.jrpie.android.launcher.ui.categories

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.apps.FavoriteCategory
import de.jrpie.android.launcher.databinding.ActivityManageCategoriesBinding
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.ui.UIObjectActivity

/**
 * Lists the categories of favorite apps (see [de.jrpie.android.launcher.apps.FavoriteCategories])
 * and allows to add, rename, delete and reorder them and to choose their apps.
 */
class ManageCategoriesActivity : UIObjectActivity() {
    private lateinit var binding: ActivityManageCategoriesBinding
    private lateinit var adapter: CategoriesAdapter

    private val sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, prefKey ->
            if (prefKey == LauncherPreferences.apps().keys().favoriteCategories()
                || prefKey == LauncherPreferences.apps().keys().favorites()
                || prefKey == LauncherPreferences.apps().keys().customNames()
            ) {
                adapter.reload()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityManageCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val touchHelper = ItemTouchHelper(ReorderCallback())
        adapter = CategoriesAdapter { touchHelper.startDrag(it) }
        binding.manageCategoriesRecycler.apply {
            layoutManager = LinearLayoutManager(this@ManageCategoriesActivity)
            adapter = this@ManageCategoriesActivity.adapter
        }
        touchHelper.attachToRecyclerView(binding.manageCategoriesRecycler)

        binding.manageCategoriesAdd.setOnClickListener {
            createCategory(this) { category ->
                // A new category is empty, so let the user choose its apps right away.
                showCategoryAppsDialog(this, category.id)
            }
        }

        // the names of the apps are only known once they are loaded
        (applicationContext as Application).apps.observe(this) { adapter.reload() }
    }

    override fun onStart() {
        super.onStart()
        LauncherPreferences.getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        adapter.reload()
    }

    override fun onStop() {
        LauncherPreferences.getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener)
        super.onStop()
    }

    override fun setOnClicks() {
        binding.manageCategoriesClose.setOnClickListener { finish() }
    }

    private inner class CategoriesAdapter(
        private val startDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {
        private var categories: MutableList<FavoriteCategory> = mutableListOf()

        // names of the favorites in each category, by id
        private var appNames: Map<Int, List<String>> = emptyMap()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val handle: ImageView = itemView.findViewById(R.id.list_categories_row_handle)
            val name: TextView = itemView.findViewById(R.id.list_categories_row_name)
            val info: TextView = itemView.findViewById(R.id.list_categories_row_info)
            val rename: ImageView = itemView.findViewById(R.id.list_categories_row_rename)
            val delete: ImageView = itemView.findViewById(R.id.list_categories_row_delete)

            // the background while not being dragged
            var background: Drawable? = null

            val category: FavoriteCategory?
                get() = categories.getOrNull(bindingAdapterPosition)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun reload() {
            categories = LauncherPreferences.apps().favoriteCategories().categories.toMutableList()
            val favorites = getVisibleFavorites(this@ManageCategoriesActivity)
            appNames = categories.associate { category ->
                category.id to favorites
                    .filter { it.getRawInfo() in category.apps }
                    .map { it.getCustomLabel(this@ManageCategoriesActivity) }
            }
            notifyDataSetChanged()
            binding.manageCategoriesEmpty.isVisible = categories.isEmpty()
        }

        fun move(from: Int, to: Int) {
            if (from !in categories.indices || to !in categories.indices) {
                return
            }
            categories.add(to, categories.removeAt(from))
            notifyItemMoved(from, to)
        }

        /** Stores the order the categories were dragged into. */
        fun saveOrder() {
            val order = categories.map { it.id }
            val stored = LauncherPreferences.apps().favoriteCategories()
            val reordered = stored.copy(
                categories = stored.categories.sortedBy { order.indexOf(it.id) }
            )
            if (reordered != stored) {
                LauncherPreferences.apps().favoriteCategories(reordered)
            }
        }

        override fun getItemCount(): Int = categories.size

        @SuppressLint("ClickableViewAccessibility")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_categories_row, parent, false)
            return ViewHolder(view).also { holder ->
                holder.background = view.background
                holder.handle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        startDrag(holder)
                    }
                    false
                }
                view.setOnClickListener {
                    holder.category?.let { showCategoryAppsDialog(view.context, it.id) }
                }
                holder.rename.setOnClickListener {
                    holder.category?.let { renameCategory(view.context, it.id) }
                }
                holder.delete.setOnClickListener {
                    holder.category?.let {
                        deleteCategory(binding.root, it.id, anchor = binding.manageCategoriesAdd)
                    }
                }
                // reordering without dragging, e.g. with TalkBack
                ViewCompat.addAccessibilityAction(
                    view, getString(R.string.content_description_move_up)
                ) { _, _ ->
                    moveAndSave(holder.bindingAdapterPosition, holder.bindingAdapterPosition - 1)
                }
                ViewCompat.addAccessibilityAction(
                    view, getString(R.string.content_description_move_down)
                ) { _, _ ->
                    moveAndSave(holder.bindingAdapterPosition, holder.bindingAdapterPosition + 1)
                }
            }
        }

        private fun moveAndSave(from: Int, to: Int): Boolean {
            if (from !in categories.indices || to !in categories.indices) {
                return false
            }
            move(from, to)
            saveOrder()
            return true
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.name.text = category.name
            val names = appNames[category.id] ?: emptyList()
            holder.info.text = if (names.isEmpty()) {
                getString(R.string.favorite_categories_no_apps)
            } else {
                resources.getQuantityString(
                    R.plurals.favorite_categories_number_of_apps, names.size, names.size
                ) + " · " + names.joinToString(", ")
            }
        }
    }

    /**
     * Reorders categories by dragging (long press or the handle).
     */
    private inner class ReorderCallback :
        ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                // lift the row off the list
                viewHolder?.itemView?.background = cardBackgroundColor().toDrawable()
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.background = (viewHolder as? CategoriesAdapter.ViewHolder)?.background
            adapter.saveOrder()
        }
    }

    private fun cardBackgroundColor(): Int {
        val attributes = obtainStyledAttributes(intArrayOf(androidx.cardview.R.attr.cardBackgroundColor))
        return try {
            attributes.getColor(0, Color.DKGRAY)
        } finally {
            attributes.recycle()
        }
    }
}
