package de.jrpie.android.launcher.ui.list

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.databinding.ActivityListBinding
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.theme.Background
import de.jrpie.android.launcher.preferences.theme.ColorTheme
import de.jrpie.android.launcher.ui.list.apps.ListFragmentApps
import kotlin.math.roundToInt

private const val CLOSE_ANIMATION_DURATION_MS = 100L

/**
 * Shows the app list inside the home screen's window, instead of starting [AppListActivity].
 *
 * Starting an activity means waiting for a new window to be created, drawn and given input
 * focus before the keyboard can be requested. The home screen's window already has focus
 * and the list is already built, so the keyboard is requested as soon as the gesture is
 * detected.
 *
 * [binding] is an included [R.layout.activity_list], so the list looks like [AppListActivity].
 * [onShownChanged] is called when the list is opened or closed, so the home screen can hide
 * its own content behind it.
 */
class AppListOverlay(
    private val activity: AppCompatActivity,
    private val host: AppListHost,
    private val binding: ActivityListBinding,
    private val onShownChanged: (shown: Boolean) -> Unit,
) {
    var isShown = false
        private set

    // Set by [show], cleared by [startOpenAnimation].
    private var openAnimationPending = false

    // Whether [applyBackground] made the home screen's window blur the wallpaper.
    private var windowBlurred = false

    private val header = AppListHeader(activity, binding, host) { hide() }

    private val fragment: ListFragmentApps?
        get() = activity.supportFragmentManager
            .findFragmentById(R.id.list_fragment_container) as? ListFragmentApps

    init {
        // Don't let touches reach the home screen's gesture detection.
        binding.root.isClickable = true
        binding.root.visibility = View.GONE
    }

    fun show() {
        unlockPrivateSpaceIfExclusive(activity, host)

        binding.root.animate().cancel()
        binding.root.alpha = 1f
        binding.root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        binding.root.visibility = View.VISIBLE
        isShown = true
        openAnimationPending = true

        // Requests the keyboard, so do this first. The rest is done before the next frame anyway.
        fragment?.onShown()

        applyBackground()
        header.updateTitle()
        header.updateLockIcon()
        onShownChanged(true)
    }

    /**
     * Plays [animation] (0 for none) if the list was opened since the last call.
     * Returns false if it wasn't.
     */
    fun startOpenAnimation(animation: Int): Boolean {
        if (!openAnimationPending) {
            return false
        }
        openAnimationPending = false
        if (animation != 0) {
            binding.root.startAnimation(AnimationUtils.loadAnimation(activity, animation))
        }
        return true
    }

    fun hide(animate: Boolean = true) {
        if (!isShown) {
            return
        }
        isShown = false
        openAnimationPending = false

        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.root.windowToken, 0)
        // Otherwise clearing the focus moves it straight back to the search field.
        binding.root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.root.findFocus()?.clearFocus()

        resetBackground()
        onShownChanged(false)

        if (animate && LauncherPreferences.theme().animations()) {
            binding.root.animate()
                .alpha(0f)
                .setDuration(CLOSE_ANIMATION_DURATION_MS)
                .withEndAction {
                    binding.root.visibility = View.GONE
                    binding.root.alpha = 1f
                }
        } else {
            binding.root.visibility = View.GONE
        }
    }

    fun updateTitle() {
        header.updateTitle()
    }

    /**
     * Gives the list the same background as [AppListActivity] would have.
     */
    // obtainStyledAttributes with a plain attribute array, not a styleable
    @SuppressLint("ResourceType")
    private fun applyBackground() {
        val background = LauncherPreferences.theme().background()
        val colorTheme = LauncherPreferences.theme().colorTheme()
        if (background == Background.SOLID || colorTheme == ColorTheme.LIGHT) {
            // The home screen's theme has a transparent window background,
            // so build the theme [AppListActivity] would use to look up its background.
            val theme = activity.resources.newTheme().also {
                it.applyStyle(R.style.launcherBaseTheme, true)
                colorTheme.applyToTheme(it, LauncherPreferences.theme().textShadow())
                Background.SOLID.applyToTheme(it)
            }
            // must be sorted by attribute id
            val attributes = theme.obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.windowBackground,
                    android.R.attr.statusBarColor,
                    android.R.attr.navigationBarColor
                )
            )
            try {
                binding.root.background = attributes.getDrawable(0)
                setSystemBarColors(
                    attributes.getColor(1, Color.TRANSPARENT),
                    attributes.getColor(2, Color.TRANSPARENT)
                )
            } finally {
                attributes.recycle()
            }
        } else {
            // The list draws the dim itself instead of setting FLAG_DIM_BEHIND on the home
            // screen's window, so that it is guaranteed to go away together with the list.
            // Same strength as the dim behind the window of [AppListActivity].
            val dimColor = Color.argb((background.dimAmount() * 255).roundToInt(), 0, 0, 0)
            binding.root.setBackgroundColor(dimColor)
            setSystemBarColors(dimColor, dimColor)
            // The wallpaper is drawn behind the window, so only the window can blur it.
            if (background.blurs()) {
                Background.applyBlurToWindow(activity.window, true)
                windowBlurred = true
            }
        }
    }

    private fun resetBackground() {
        if (windowBlurred) {
            Background.applyBlurToWindow(activity.window, false)
            windowBlurred = false
        }
        // the home screen's theme uses transparent system bars
        setSystemBarColors(Color.TRANSPARENT, Color.TRANSPARENT)
    }

    @Suppress("deprecation") // no effect on Android 15+, where the list draws behind the bars
    private fun setSystemBarColors(statusBarColor: Int, navigationBarColor: Int) {
        activity.window.statusBarColor = statusBarColor
        activity.window.navigationBarColor = navigationBarColor
    }
}
