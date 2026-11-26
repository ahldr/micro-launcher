package de.jrpie.android.launcher.ui.list.apps

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.apps.AppFilter
import de.jrpie.android.launcher.databinding.ListAppsBinding
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.list.AppNameFormat
import de.jrpie.android.launcher.preferences.theme.ColorTheme
import de.jrpie.android.launcher.ui.UIObject
import de.jrpie.android.launcher.ui.closeKeyboardOnScroll
import de.jrpie.android.launcher.ui.list.AbstractListActivity
import de.jrpie.android.launcher.ui.list.AppListHost
import de.jrpie.android.launcher.ui.openKeyboardIfEnabled
import de.jrpie.android.launcher.ui.showKeyboardNow
import kotlin.math.roundToInt


/**
 * The [ListFragmentApps] is used as a tab in ListActivity.
 *
 * It is a list of all installed applications that can be launched.
 *
 * If [AppListHost.customizeGrid] is set, it instead previews changes to the grid
 * made with a [GridEditor].
 */
class ListFragmentApps : Fragment(), UIObject {
    private lateinit var binding: ListAppsBinding
    private lateinit var appsRecyclerAdapter: AppsRecyclerAdapter

    /**
     * The preferences that [appsRecyclerAdapter] and the layout manager were created with.
     * If any of them changes, both are created again.
     */
    private data class ListConfig(
        val appList: ListPresentation,
        val favorites: ListPresentation,
        val nameFormat: AppNameFormat,
        val colorTheme: ColorTheme,
        val monochromeIcons: Boolean,
    )

    private var listConfig: ListConfig? = null

    // Set when preferences change while the list is not visible.
    private var appsListOutdated = false

    // Set while [onShown] clears the search field, so that this doesn't launch an app.
    private var clearingQuery = false

    // Only set if the grid is being customized.
    private var gridEditor: GridEditor? = null

    private val host: AppListHost?
        get() = activity as? AppListHost

    private val favoritesOnly: Boolean
        get() = host?.favoritesVisibility == AppFilter.Companion.AppSetVisibility.EXCLUSIVE

    // The adapter is only created in [adjustLayout] (onStart), but the listeners set up in
    // [onViewCreated] can be called earlier: when the activity is recreated, restoring the
    // search field's text calls onQueryTextChange. The new adapter then picks up that text.
    private val hasAdapter: Boolean
        get() = ::appsRecyclerAdapter.isInitialized

    private var sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (hasAdapter
                && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                && host?.isAppListShown != false
            ) {
                appsRecyclerAdapter.updateAppsList()
            } else {
                appsListOutdated = true
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ListAppsBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // improve performance (since content changes don't change the layout size)
        binding.listAppsRview.setHasFixedSize(true)

        if (host?.customizeGrid == true) {
            binding.listAppsSearchBar.visibility = View.GONE
            binding.listAppsGridEditor.root.visibility = View.VISIBLE
            gridEditor = GridEditor(
                requireContext(),
                binding.listAppsGridEditor,
                favorites = favoritesOnly,
                onPreview = { applyPresentation() },
                onFinish = { activity?.finish() }
            )
        }

        binding.listAppsSearchview.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String): Boolean {
                if (!hasAdapter) {
                    return true
                }
                appsRecyclerAdapter.setSearchString(query)

                if (LauncherPreferences.functionality().searchWeb()) {
                    val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        activity?.startActivity(i)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            requireContext(),
                            R.string.toast_activity_not_found_search_web,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } else {
                    appsRecyclerAdapter.selectFirstItem()
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (!hasAdapter) {
                    return false
                }

                if (newText == " " &&
                    !appsRecyclerAdapter.disableAutoLaunch &&
                    host?.intention == AbstractListActivity.Companion.Intention.VIEW &&
                    LauncherPreferences.functionality().searchAutoLaunch()
                ) {
                    appsRecyclerAdapter.disableAutoLaunch = true
                    binding.listAppsSearchview.apply {
                        queryHint = context.getString(R.string.list_apps_search_hint_no_auto_launch)
                        setQuery("", false)
                    }
                    return false
                }

                // Never auto launch from a list that isn't visible: restoring the search
                // field's text after the home screen was recreated also ends up here.
                appsRecyclerAdapter.setSearchString(
                    newText,
                    triggerAutoLaunch = !clearingQuery && host?.isAppListShown != false
                )
                return false
            }
        })

        binding.listAppsCheckBoxFavorites.setOnClickListener {
            val host = host ?: return@setOnClickListener
            if (!hasAdapter) {
                return@setOnClickListener
            }
            host.favoritesVisibility =
                if (binding.listAppsCheckBoxFavorites.isChecked) {
                    AppFilter.Companion.AppSetVisibility.EXCLUSIVE
                } else {
                    AppFilter.Companion.AppSetVisibility.VISIBLE
                }
            // the favorites can have a layout of their own
            applyPresentation()
            appsRecyclerAdapter.setFavoritesVisibility(host.favoritesVisibility)
            host.updateTitle()
        }

        closeKeyboardOnScroll(requireActivity(), binding.listAppsRview)

        // Only called once the view lifecycle is started, after onStart created the adapter.
        (requireContext().applicationContext as Application).apps.observe(viewLifecycleOwner) {
            if (hasAdapter) {
                appsRecyclerAdapter.updateAppsListIfChanged()
            }
        }

        LauncherPreferences.getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
    }

    override fun onDestroyView() {
        LauncherPreferences.getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener)
        // a new view needs a new adapter
        listConfig = null
        super.onDestroyView()
    }

    override fun onStart() {
        super<Fragment>.onStart()
        super<UIObject>.onStart()

        binding.listAppsCheckBoxFavorites.isChecked = favoritesOnly
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // the number of columns depends on the width of the screen
        applyPresentation()
    }


    override fun setOnClicks() {}

    override fun adjustLayout() {
        val host = host ?: return

        val colorTheme = LauncherPreferences.theme().colorTheme()
        val config = ListConfig(
            appList = ListPresentation.forAppList(),
            favorites = ListPresentation.forFavorites(),
            nameFormat = LauncherPreferences.list().appNameFormat(),
            colorTheme = colorTheme,
            monochromeIcons = colorTheme.monochromeIcons(),
        )

        if (config != listConfig) {
            listConfig = config
            appsRecyclerAdapter =
                AppsRecyclerAdapter(
                    requireActivity() as AppCompatActivity, binding.root,
                    host.intention, host.forGesture,
                    appFilter = AppFilter(
                        requireContext(),
                        binding.listAppsSearchview.query.toString(),
                        favoritesVisibility = host.favoritesVisibility,
                        privateSpaceVisibility = host.privateSpaceVisibility,
                        hiddenVisibility = host.hiddenVisibility
                    ),
                    presentation = currentPresentation(),
                    nameFormat = config.nameFormat,
                    previewOnly = gridEditor != null,
                )
            appsRecyclerAdapter.onListChanged = ::onListChanged

            applyPresentation(newLayoutManager = true)
            binding.listAppsRview.adapter = appsRecyclerAdapter
            appsRecyclerAdapter.updateAppsList()
            appsListOutdated = false
        } else if (appsListOutdated && host.isAppListShown) {
            appsRecyclerAdapter.updateAppsList()
            appsListOutdated = false
        }

        if (host.intention == AbstractListActivity.Companion.Intention.VIEW
            && host.isAppListShown
            && gridEditor == null
        ) {
            openKeyboardIfEnabled(requireActivity(), binding.listAppsSearchview)
        }
    }

    /**
     * How the list looks right now: like the favorites or the app list,
     * or like the grid that is being customized.
     */
    private fun currentPresentation(): ListPresentation {
        gridEditor?.let { return it.presentation }
        val config = listConfig
        return when {
            config == null -> ListPresentation.load(favoritesOnly)
            favoritesOnly -> config.favorites
            else -> config.appList
        }
    }

    /**
     * Sets up the layout manager and the adapter for [currentPresentation].
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun applyPresentation(newLayoutManager: Boolean = false) {
        if (!hasAdapter) {
            return
        }
        val presentation = currentPresentation()
        val recyclerView = binding.listAppsRview
        val context = requireContext()
        val layoutManager = recyclerView.layoutManager

        if (presentation.layout.isGrid) {
            val spanCount = presentation.grid.spanCount(context, presentation.layout)
            if (!newLayoutManager
                && layoutManager is GridLayoutManager
                && layoutManager.reverseLayout == presentation.reverse
            ) {
                if (layoutManager.spanCount != spanCount) {
                    layoutManager.spanCount = spanCount
                    // headings are aligned with the columns
                    appsRecyclerAdapter.notifyDataSetChanged()
                }
            } else {
                recyclerView.layoutManager = GridLayoutManager(context, spanCount).also {
                    it.reverseLayout = presentation.reverse
                    // headings take up a whole row
                    it.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return if (appsRecyclerAdapter.isHeader(position)) it.spanCount else 1
                        }
                    }
                }
            }
        } else if (newLayoutManager
            || layoutManager !is LinearLayoutManager
            || layoutManager is GridLayoutManager
            || layoutManager.reverseLayout != presentation.reverse
        ) {
            recyclerView.layoutManager = LinearLayoutManager(context).also {
                it.reverseLayout = presentation.reverse
            }
        }

        val sideMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            if (presentation.layout.isGrid) presentation.grid.sidePadding.toFloat() else LIST_SIDE_MARGIN_DP,
            resources.displayMetrics
        ).roundToInt()
        (recyclerView.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            if (it.leftMargin != sideMargin || it.rightMargin != sideMargin) {
                it.leftMargin = sideMargin
                it.rightMargin = sideMargin
                it.marginStart = sideMargin
                it.marginEnd = sideMargin
                recyclerView.layoutParams = it
            }
        }

        appsRecyclerAdapter.presentation = presentation
    }

    private fun onListChanged(appsShown: Int) {
        val host = host ?: return
        binding.listAppsEmpty.isVisible = appsShown == 0
                && favoritesOnly
                && host.intention == AbstractListActivity.Companion.Intention.VIEW
                && gridEditor == null
                && binding.listAppsSearchview.query.isEmpty()
    }

    /**
     * Called by [de.jrpie.android.launcher.ui.list.AppListOverlay] each time the home screen
     * shows the list: resets it to how a freshly opened list looks and opens the keyboard.
     */
    fun onShown() {
        val host = host ?: return
        if (!::appsRecyclerAdapter.isInitialized) {
            return
        }

        binding.listAppsCheckBoxFavorites.isChecked = favoritesOnly
        appsRecyclerAdapter.disableAutoLaunch = false
        binding.listAppsSearchview.queryHint = getString(R.string.list_apps_search_hint)
        // before focusing it: changing the text restarts the keyboard's input connection
        clearingQuery = true
        binding.listAppsSearchview.setQuery("", false)
        clearingQuery = false

        // Request the keyboard before doing anything else, it takes the longest to appear.
        if (host.intention == AbstractListActivity.Companion.Intention.VIEW
            && LauncherPreferences.functionality().searchAutoOpenKeyboard()
        ) {
            binding.listAppsSearchview.showKeyboardNow()
        }

        val visibilitiesChanged = appsRecyclerAdapter.setVisibilities(
            host.favoritesVisibility, host.hiddenVisibility, host.privateSpaceVisibility
        )
        // the favorites can have a layout of their own
        applyPresentation()
        if (visibilitiesChanged || appsListOutdated) {
            appsRecyclerAdapter.updateAppsList()
            appsListOutdated = false
        }
        binding.listAppsRview.scrollToPosition(0)
    }

    companion object {
        // see list_apps.xml
        private const val LIST_SIDE_MARGIN_DP = 16f
    }
}
