package de.jrpie.android.launcher.ui.list.shortcuts

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.actions.ShortcutAction
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.databinding.ListShortcutsBinding
import de.jrpie.android.launcher.isDefaultHomeScreen
import de.jrpie.android.launcher.ui.applyKeyboardSettings
import de.jrpie.android.launcher.ui.list.SelectActionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The [ListFragmentShortcuts] is used as a tab in [SelectActionActivity].
 *
 * It lists the app shortcuts (e.g. "New tab" of a browser or "Voice search" of a search app)
 * of all installed apps, such that they can be bound to a gesture.
 */
@RequiresApi(Build.VERSION_CODES.N_MR1)
class ListFragmentShortcuts : Fragment() {

    private lateinit var binding: ListShortcutsBinding
    private lateinit var shortcutsAdapter: ShortcutsRecyclerAdapter
    private var loadJob: Job? = null
    private var loaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ListShortcutsBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shortcutsAdapter = ShortcutsRecyclerAdapter(requireContext()) { select(it) }

        binding.listShortcutsRview.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = shortcutsAdapter
        }

        binding.listShortcutsSearchview.setOnQueryTextListener(object :
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                shortcutsAdapter.setSearchString(query)
                if (query.isNotBlank()) {
                    shortcutsAdapter.firstItem()?.let { select(it) }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                shortcutsAdapter.setSearchString(newText)
                updateEmptyState()
                return false
            }
        })

        applyKeyboardSettings(
            requireActivity(),
            binding.listShortcutsRview,
            binding.listShortcutsSearchview,
            openKeyboard = false
        )

        (requireActivity().application as Application).apps
            .observe(viewLifecycleOwner) { apps -> loadShortcuts(apps ?: emptyList()) }
    }

    private fun loadShortcuts(apps: List<AbstractDetailedAppInfo>) {
        val context = requireContext().applicationContext
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val shortcuts = withContext(Dispatchers.Default) {
                loadAppShortcuts(context, apps)
            }
            loaded = true
            shortcutsAdapter.setItems(shortcuts)
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        val empty = loaded && shortcutsAdapter.itemCount == 0
        binding.listShortcutsEmpty.isVisible = empty
        if (empty) {
            binding.listShortcutsEmpty.setText(
                if (isDefaultHomeScreen(requireContext())) {
                    R.string.list_shortcuts_empty
                } else {
                    R.string.list_shortcuts_not_default_home
                }
            )
        }
    }

    private fun select(item: AppShortcutItem) {
        val activity = activity as? SelectActionActivity ?: return
        val gesture = activity.forGesture?.let { Gesture.byId(it) } ?: return
        ShortcutAction.bindToGesture(activity, item.shortcut, gesture)
        activity.finish()
    }
}
