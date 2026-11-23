package de.jrpie.android.launcher.ui.list

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.apps.AppFilter
import de.jrpie.android.launcher.databinding.ActivitySelectActionBinding
import de.jrpie.android.launcher.ui.list.apps.ListFragmentApps
import de.jrpie.android.launcher.ui.list.other.ListFragmentOther
import de.jrpie.android.launcher.ui.list.shortcuts.ListFragmentShortcuts


/**
 * The [SelectActionActivity] is used to select an action
 * (i.e. an app, an app shortcut or one of [de.jrpie.android.launcher.actions.LauncherAction])
 */
class SelectActionActivity : AbstractListActivity() {
    override val intention = AbstractListActivity.Companion.Intention.PICK
    private lateinit var binding: ActivitySelectActionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialise layout
        binding = ActivitySelectActionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        useSoftInputResizeWorkaround(binding.selectContainer)

        val sectionsPagerAdapter = ListSectionsPagerAdapter(this)
        binding.selectActionViewpager.apply {
            adapter = sectionsPagerAdapter
        }
        TabLayoutMediator(
            binding.selectActionTabs,
            binding.selectActionViewpager
        ) { tab, position ->
            tab.text = sectionsPagerAdapter.getPageTitle(position)
        }.attach()
    }

    override fun setOnClicks() {
        binding.selectActionClose.setOnClickListener { finish() }
    }

    companion object {
        fun selectAction(context: Context, gesture: Gesture) {
            val intent = Intent(context, SelectActionActivity::class.java)
            intent.putExtra(KEY_HIDDEN_VISIBILITY, AppFilter.Companion.AppSetVisibility.VISIBLE)
            intent.putExtra(KEY_FOR_GESTURE, gesture.id) // for which action we choose the app
            context.startActivity(intent)
        }
    }
}

class ListSectionsPagerAdapter(val activity: SelectActionActivity) :
    FragmentStateAdapter(activity) {

    private enum class Page(val title: Int) {
        APPS(R.string.list_tab_app),
        SHORTCUTS(R.string.list_tab_shortcuts),
        OTHER(R.string.list_tab_other),
    }

    // App shortcuts are only supported since Android 7.1
    private val pages = listOfNotNull(
        Page.APPS,
        Page.SHORTCUTS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 },
        Page.OTHER
    )

    override fun createFragment(position: Int): Fragment {
        return when (pages.getOrNull(position)) {
            Page.APPS -> ListFragmentApps()
            Page.SHORTCUTS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                ListFragmentShortcuts()
            } else {
                Fragment()
            }

            Page.OTHER -> ListFragmentOther()
            null -> Fragment()
        }
    }

    fun getPageTitle(position: Int): CharSequence {
        return activity.resources.getString(pages[position].title)
    }

    override fun getItemCount(): Int {
        return pages.size
    }
}
