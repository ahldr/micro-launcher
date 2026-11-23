package de.jrpie.android.launcher.preferences.theme

import android.content.res.Resources
import android.os.Build
import android.view.Window
import android.view.WindowManager
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.preferences.LauncherPreferences

@Suppress("unused")
enum class Background(val id: Int, val dim: Boolean = false, val blur: Boolean = false) {
    TRANSPARENT(R.style.backgroundWallpaper),
    DIM(R.style.backgroundWallpaper, dim = true),
    BLUR(R.style.backgroundWallpaper, dim = true, blur = true),
    SOLID(R.style.backgroundSolid),
    ;

    fun applyToTheme(theme: Resources.Theme) {
        theme.applyStyle(id, true)
    }

    /**
     * Whether the content behind is blurred. Blur needs Android 12,
     * older devices get a stronger dim instead (see [dimAmount]).
     */
    fun blurs(): Boolean {
        return blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * How strongly the content behind is dimmed (0 to 1).
     */
    fun dimAmount(): Float {
        if (!dim || LauncherPreferences.theme().colorTheme() == ColorTheme.LIGHT) {
            return 0f
        }
        // TODO: add a setting to change this?
        // replace blur by more intense dim on old devices
        return if (blur && !blurs()) 0.8f else 0.7f
    }

    fun applyToWindow(window: Window) {
        val layoutParams: WindowManager.LayoutParams = window.attributes

        if (dim) {
            layoutParams.dimAmount = dimAmount()
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        setBlurBehind(window, layoutParams, blurs())

        window.attributes = layoutParams
    }

    companion object {
        /**
         * Blurs (or stops blurring) what is behind [window], without changing its dim.
         */
        fun applyBlurToWindow(window: Window, blur: Boolean) {
            val layoutParams = window.attributes
            setBlurBehind(window, layoutParams, blur)
            window.attributes = layoutParams
        }

        private fun setBlurBehind(
            window: Window,
            layoutParams: WindowManager.LayoutParams,
            blur: Boolean
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return
            }
            if (blur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                layoutParams.blurBehindRadius = 10
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                layoutParams.blurBehindRadius = 0
            }
        }
    }
}
