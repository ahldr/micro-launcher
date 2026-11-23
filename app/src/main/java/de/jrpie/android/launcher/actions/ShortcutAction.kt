package de.jrpie.android.launcher.actions

import android.app.Service
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.apps.DetailedPinnedShortcutInfo
import de.jrpie.android.launcher.apps.PinnedShortcutInfo
import de.jrpie.android.launcher.pinShortcut
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("action:shortcut")
class ShortcutAction(val shortcut: PinnedShortcutInfo) : Action {

    override fun invoke(context: Context, rect: Rect?): Boolean {
        val launcherApps = context.getSystemService(Service.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            // TODO
            return false
        }
        // The shortcut might have been removed by the app or the app might have been uninstalled.
        val shortcutInfo = shortcut.getShortcutInfo(context) ?: return false
        try {
            launcherApps.startShortcut(shortcutInfo, rect, null)
        } catch (e: Exception) {
            Log.w("Launcher", "Couldn't launch shortcut: $this", e)
            Toast.makeText(
                context,
                context.getString(R.string.toast_cant_launch_app),
                Toast.LENGTH_LONG
            ).show()
        }

        return true
    }

    override fun label(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return "?"
        }

        // longLabel is optional, e.g. Chrome's "New tab" shortcut only has a shortLabel
        return shortcut.getShortcutInfo(context)?.let { it.longLabel ?: it.shortLabel }?.toString()
            ?: context.getString(R.string.invalid_shortcut)
    }

    override fun getIcon(context: Context): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return null
        }
        return DetailedPinnedShortcutInfo.fromPinnedShortcutInfo(shortcut, context)
            ?.getIcon(context)
    }

    override fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return false
        }
        return shortcut.getShortcutInfo(context) != null
    }

    override fun canReachSettings(): Boolean {
        return false
    }

    companion object {
        /**
         * Binds an app shortcut (e.g. "New tab" of a browser) to [gesture].
         *
         * The shortcut is pinned, so the gesture keeps working
         * even if the app later removes the shortcut from its dynamic shortcuts.
         */
        @RequiresApi(Build.VERSION_CODES.N_MR1)
        fun bindToGesture(context: Context, shortcut: ShortcutInfo, gesture: Gesture) {
            pinShortcut(context, shortcut)
            Action.setActionForGesture(gesture, ShortcutAction(PinnedShortcutInfo(shortcut)))
        }
    }
}