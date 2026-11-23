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
import de.jrpie.android.launcher.ui.list.AppListActivity
import de.jrpie.android.launcher.ui.openKeyboardIfEnabled


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

    private var sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
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
                    (activity as? AbstractListActivity)?.intention
                    == AbstractListActivity.Companion.Intention.VIEW &&
                    LauncherPreferences.functionality().searchAutoLaunch()
                ) {
                    appsRecyclerAdapter.disableAutoLaunch = true
                    binding.listAppsSearchview.apply {
                        queryHint = context.getString(R.string.list_apps_search_hint_no_auto_launch)
                        setQuery("", false)
                    }
                    return false
                }

                appsRecyclerAdapter.setSearchString(newText)
                return false
            }
        })

        binding.listAppsCheckBoxFavorites.setOnClickListener {
            val listActivity = (activity as? AbstractListActivity) ?: return@setOnClickListener
            listActivity.favoritesVisibility =
                if (binding.listAppsCheckBoxFavorites.isChecked) {
                    AppFilter.Companion.AppSetVisibility.EXCLUSIVE
                } else {
                    AppFilter.Companion.AppSetVisibility.VISIBLE
                }
            appsRecyclerAdapter.setFavoritesVisibility(listActivity.favoritesVisibility)
            (activity as? AppListActivity)?.updateTitle()
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
            ((activity as? AppListActivity)?.favoritesVisibility == AppFilter.Companion.AppSetVisibility.EXCLUSIVE)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        binding.listAppsRview.layoutManager?.let {
            LauncherPreferences.list().layout().updateLayoutManager(requireContext(), it)
        }

    }


    override fun setOnClicks() {}

    override fun adjustLayout() {
        val listActivity = (activity as? AbstractListActivity) ?: return

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
                    listActivity, binding.root, listActivity.intention, listActivity.forGesture,
                    appFilter = AppFilter(
                        requireContext(),
                        binding.listAppsSearchview.query.toString(),
                        favoritesVisibility = listActivity.favoritesVisibility,
                        privateSpaceVisibility = listActivity.privateSpaceVisibility,
                        hiddenVisibility = listActivity.hiddenVisibility
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
        } else if (appsListOutdated) {
            appsRecyclerAdapter.updateAppsList()
        }
        appsListOutdated = false

        if (listActivity.intention == AbstractListActivity.Companion.Intention.VIEW) {
            openKeyboardIfEnabled(requireActivity(), binding.listAppsSearchview)
        }
    }

}
