package de.jrpie.android.launcher.ui.list

import android.os.Build
import android.os.Bundle
import android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
import android.window.OnBackInvokedDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.apps.AppFilter.Companion.AppSetVisibility
import de.jrpie.android.launcher.databinding.ActivityListBinding
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.ui.list.apps.showGridDialog
import kotlinx.coroutines.launch

/**
 * The [AppListActivity] is used to view all apps and edit their settings.
 * The activity itself can also be chosen to be launched as an action.
 *
 * When the app list is opened from the home screen, it is shown in the home screen's
 * window instead, see [AppListOverlay].
 */
class AppListActivity : AbstractListActivity() {
    override val intention = Companion.Intention.VIEW
    private lateinit var binding: ActivityListBinding
    private lateinit var header: AppListHeader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gridDialog = intent.getBooleanExtra(KEY_GRID_DIALOG, false)

        if (LauncherPreferences.functionality().searchAutoOpenKeyboard() && !gridDialog) {
            // Let the system show the keyboard as soon as this window gains focus.
            // Otherwise it only appears after SearchView has set up its input connection
            // and sent a show request of its own, one round trip later.
            // Since API 28 this requires a focused text field when the window gains focus,
            // which openKeyboardIfEnabled() takes care of.
            window.setSoftInputMode(
                (window.attributes.softInputMode and SOFT_INPUT_MASK_STATE.inv())
                        or SOFT_INPUT_STATE_VISIBLE
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY
            ) {
                finish()
            }
        }

        // Initialise layout
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        useSoftInputResizeWorkaround(binding.listContainer)

        unlockPrivateSpaceIfExclusive(this, this)
        header = AppListHeader(this, binding, this) { finish() }

        if (gridDialog && savedInstanceState == null) {
            showGridDialog(this, favoritesVisibility == AppSetVisibility.EXCLUSIVE) { finish() }
        }

        val onThemeChanged = (this.applicationContext as Application).onThemeChanged
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                onThemeChanged.collect {
                    recreate()
                }
            }
        }
    }

    override fun updateTitle() {
        header.updateTitle()
    }

    override fun adjustLayout() {
        updateTitle()
    }
}
