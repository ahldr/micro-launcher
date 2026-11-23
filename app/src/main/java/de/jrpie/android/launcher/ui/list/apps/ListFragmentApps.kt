package de.jrpie.android.launcher.ui.list.apps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import de.jrpie.android.launcher.preferences.list.ListLayout
import de.jrpie.android.launcher.preferences.theme.ColorTheme
import de.jrpie.android.launcher.ui.UIObject
import de.jrpie.android.launcher.ui.closeKeyboardOnScroll
import de.jrpie.android.launcher.ui.list.AbstractListActivity
import de.jrpie.android.launcher.ui.list.AppListHost
import de.jrpie.android.launcher.ui.openKeyboardIfEnabled
import de.jrpie.android.launcher.ui.showKeyboardNow


/**
 * The [ListFragmentApps] is used as a tab in ListActivity.
 *
 * It is a list of all installed applications that can be launched.
 */
class ListFragmentApps : Fragment(), UIObject {
    private lateinit var binding: ListAppsBinding
    private lateinit var appsRecyclerAdapter: AppsRecyclerAdapter

    /**
     * The preferences that [appsRecyclerAdapter] and the layout manager were created with.
     * If any of them changes, both are created again.
     */
    private data class ListConfig(
        val layout: ListLayout,
        val reverseLayout: Boolean,
        val nameFormat: AppNameFormat,
        val colorTheme: ColorTheme,
        val monochromeIcons: Boolean,
    )

    private var listConfig: ListConfig? = null

    // Set when preferences change while the list is not visible.
    private var appsListOutdated = false

    // Set while [onShown] clears the search field, so that this doesn't launch an app.
    private var clearingQuery = false

    private val host: AppListHost?
        get() = activity as? AppListHost

    private var sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
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

        binding.listAppsSearchview.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String): Boolean {
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
                    appsRecyclerAdapter.selectItem(0)
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {

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

                appsRecyclerAdapter.setSearchString(newText, triggerAutoLaunch = !clearingQuery)
                return false
            }
        })

        binding.listAppsCheckBoxFavorites.setOnClickListener {
            val host = host ?: return@setOnClickListener
            host.favoritesVisibility =
                if (binding.listAppsCheckBoxFavorites.isChecked) {
                    AppFilter.Companion.AppSetVisibility.EXCLUSIVE
                } else {
                    AppFilter.Companion.AppSetVisibility.VISIBLE
                }
            appsRecyclerAdapter.setFavoritesVisibility(host.favoritesVisibility)
            host.updateTitle()
        }

        closeKeyboardOnScroll(requireActivity(), binding.listAppsRview)

        // The adapter is created in onStart, before the view lifecycle is started,
        // so it exists whenever this observer is called.
        (requireContext().applicationContext as Application).apps.observe(viewLifecycleOwner) {
            appsRecyclerAdapter.updateAppsListIfChanged()
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

        binding.listAppsCheckBoxFavorites.isChecked =
            host?.favoritesVisibility == AppFilter.Companion.AppSetVisibility.EXCLUSIVE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        binding.listAppsRview.layoutManager?.let {
            LauncherPreferences.list().layout().updateLayoutManager(requireContext(), it)
        }

    }


    override fun setOnClicks() {}

    override fun adjustLayout() {
        val host = host ?: return

        val colorTheme = LauncherPreferences.theme().colorTheme()
        val config = ListConfig(
            layout = LauncherPreferences.list().layout(),
            reverseLayout = LauncherPreferences.list().reverseLayout(),
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
                    layout = config.layout,
                    nameFormat = config.nameFormat
                )

            binding.listAppsRview.apply {
                layoutManager = config.layout.layoutManager(context)
                    .also {
                        if (config.reverseLayout) {
                            (it as? LinearLayoutManager)?.reverseLayout = true
                            (it as? GridLayoutManager)?.reverseLayout = true
                        }
                    }
                adapter = appsRecyclerAdapter
            }
            appsRecyclerAdapter.updateAppsList()
            appsListOutdated = false
        } else if (appsListOutdated && host.isAppListShown) {
            appsRecyclerAdapter.updateAppsList()
            appsListOutdated = false
        }

        if (host.intention == AbstractListActivity.Companion.Intention.VIEW && host.isAppListShown) {
            openKeyboardIfEnabled(requireActivity(), binding.listAppsSearchview)
        }
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

        binding.listAppsCheckBoxFavorites.isChecked =
            host.favoritesVisibility == AppFilter.Companion.AppSetVisibility.EXCLUSIVE
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
        if (visibilitiesChanged || appsListOutdated) {
            appsRecyclerAdapter.updateAppsList()
            appsListOutdated = false
        }
        binding.listAppsRview.scrollToPosition(0)
    }

}
