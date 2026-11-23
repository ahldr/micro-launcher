package de.jrpie.android.launcher.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.actions.LauncherAction
import de.jrpie.android.launcher.apps.AppFilter.Companion.AppSetVisibility
import de.jrpie.android.launcher.databinding.ActivityHomeBinding
import de.jrpie.android.launcher.openTutorial
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.ui.list.AbstractListActivity
import de.jrpie.android.launcher.ui.list.AppListHost
import de.jrpie.android.launcher.ui.list.AppListOverlay
import de.jrpie.android.launcher.ui.list.AppListOverlayHost
import de.jrpie.android.launcher.ui.util.LauncherGestureActivity


/**
 * [HomeActivity] is the actual application launcher.
 * It displays widgets (usually just the clock)
 * and listens for gestures.
 *
 * It also shows the app list in its own window, see [AppListOverlay].
 */
class HomeActivity : UIObject, LauncherGestureActivity(), AppListHost, AppListOverlayHost {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var appListOverlay: AppListOverlay

    // [AppListHost] for the app list shown in this window
    override val intention = AbstractListActivity.Companion.Intention.VIEW
    override val forGesture: String? = null
    override var favoritesVisibility = AppSetVisibility.VISIBLE
    override var hiddenVisibility = AppSetVisibility.HIDDEN
    override var privateSpaceVisibility = AppSetVisibility.VISIBLE
    override val isAppListShown: Boolean
        get() = ::appListOverlay.isInitialized && appListOverlay.isShown

    // When the app list was last opened or closed, see [handleBack].
    private var appListToggledAt = 0L

    private var sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, prefKey ->
            // The app list shown in this window has to pick up theme changes as well.
            if (prefKey?.startsWith("display.") == true || prefKey?.startsWith("theme.") == true) {
                recreate()
            } else if (prefKey?.startsWith("action.") == true) {
                updateSettingsFallbackButtonVisibility()
            } else if (prefKey == LauncherPreferences.widgets().keys().widgets()) {
                binding.homeWidgetContainer.updateWidgets(
                    this@HomeActivity,
                    LauncherPreferences.widgets().widgets()
                )
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super<LauncherGestureActivity>.onCreate(savedInstanceState)
        super<UIObject>.onCreate()

        // Initialise layout
        binding = ActivityHomeBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.buttonFallbackSettings.setOnClickListener {
            LauncherAction.SETTINGS.invoke(this)
        }

        appListOverlay = AppListOverlay(this, this, binding.homeAppList) { shown ->
            appListToggledAt = SystemClock.uptimeMillis()
            // While the keyboard is shown, the window may be resized. Hide the widgets so that
            // they can't be seen jumping around behind the list.
            if (shown) {
                binding.homeWidgetContainer.visibility = View.INVISIBLE
            } else {
                showWidgetsOnceKeyboardIsHidden()
            }
            updateSettingsFallbackButtonVisibility()
        }
        useSoftInputResizeWorkaround(binding.homeAppList.listContainer)
    }

    /**
     * Shows the widgets again after the app list was closed. The window can stay resized
     * until the keyboard has disappeared, so wait for that (or at most [WIDGETS_SHOW_TIMEOUT_MS]).
     */
    private fun showWidgetsOnceKeyboardIsHidden() {
        val root = binding.root
        val showWidgets = object : ViewTreeObserver.OnGlobalLayoutListener, Runnable {
            override fun onGlobalLayout() {
                if (ViewCompat.getRootWindowInsets(root)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) != true
                ) {
                    run()
                }
            }

            override fun run() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                root.removeCallbacks(this)
                if (!isAppListShown) {
                    binding.homeWidgetContainer.visibility = View.VISIBLE
                }
            }
        }
        if (ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
            root.viewTreeObserver.addOnGlobalLayoutListener(showWidgets)
            root.postDelayed(showWidgets, WIDGETS_SHOW_TIMEOUT_MS)
        } else {
            showWidgets.run()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The home button was pressed. It used to close [AppListActivity] as well.
        appListOverlay.hide(animate = false)
    }

    override fun onStart() {
        super<LauncherGestureActivity>.onStart()
        super<UIObject>.onStart()

        // If the tutorial was not finished, start it
        if (!LauncherPreferences.internal().started()) {
            openTutorial(this)
        }

        LauncherPreferences.getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(sharedPreferencesListener)

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && LauncherPreferences.display().hideNavigationBar()) {
            hideNavigationBar()
        }
    }

    private fun updateSettingsFallbackButtonVisibility() {
        // If µLauncher settings can not be reached from any action bound to an enabled gesture,
        // show the fallback button.
        binding.buttonFallbackSettings.visibility = if (
            !isAppListShown && !Gesture.entries.any { g ->
                g.isEnabled() && Action.forGesture(g)?.canReachSettings() == true
            }
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun getTheme(): Resources.Theme {
        return modifyTheme(super.getTheme())
    }

    override fun onPause() {
        try {
            (application as Application).appWidgetHost.stopListening()
        } catch (e: Exception) {
            // Throws a NullPointerException on Android 12 an earlier, see #172
            e.printStackTrace()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        updateSettingsFallbackButtonVisibility()

        binding.homeWidgetContainer.updateWidgets(
            this@HomeActivity,
            LauncherPreferences.widgets().widgets()
        )

        (application as Application).appWidgetHost.startListening()
    }


    override fun onDestroy() {
        LauncherPreferences.getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener)
        super.onDestroy()
    }

    override fun handleBack() {
        // One press of the back button must not open and close the list (or the other way
        // round), which shows as a flicker. Ignore back right after the list was toggled.
        if (SystemClock.uptimeMillis() - appListToggledAt < BACK_DEBOUNCE_MS) {
            return
        }
        if (isAppListShown) {
            appListOverlay.hide()
        } else {
            Gesture.BACK(this)
        }
    }

    override fun gesturesEnabled(): Boolean {
        return !isAppListShown
    }

    override fun showAppList(
        favoritesVisibility: AppSetVisibility,
        hiddenVisibility: AppSetVisibility,
        privateSpaceVisibility: AppSetVisibility
    ): Boolean {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            return false
        }
        this.favoritesVisibility = favoritesVisibility
        this.hiddenVisibility = hiddenVisibility
        this.privateSpaceVisibility = privateSpaceVisibility
        appListOverlay.show()
        return true
    }

    override fun startAppListOpenAnimation(animation: Int): Boolean {
        return appListOverlay.startOpenAnimation(animation)
    }

    override fun updateTitle() {
        appListOverlay.updateTitle()
    }

    override fun getRootView(): View {
        return binding.root
    }

    override fun isHomeScreen(): Boolean {
        return true
    }

    companion object {
        private const val WIDGETS_SHOW_TIMEOUT_MS = 500L
        private const val BACK_DEBOUNCE_MS = 300L
    }
}
