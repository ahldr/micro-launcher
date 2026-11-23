package de.jrpie.android.launcher.ui.list

import android.content.Context
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.R
import de.jrpie.android.launcher.actions.LauncherAction
import de.jrpie.android.launcher.apps.AppFilter.Companion.AppSetVisibility
import de.jrpie.android.launcher.apps.hidePrivateSpaceWhenLocked
import de.jrpie.android.launcher.apps.isPrivateSpaceLocked
import de.jrpie.android.launcher.apps.isPrivateSpaceSetUp
import de.jrpie.android.launcher.apps.togglePrivateSpaceLock
import de.jrpie.android.launcher.databinding.ActivityListBinding
import de.jrpie.android.launcher.preferences.LauncherPreferences

/**
 * The header of the app list: title, settings button, private space lock and close button.
 * Shared by [AppListActivity] and the app list on the home screen ([AppListOverlay]).
 */
class AppListHeader(
    private val activity: AppCompatActivity,
    private val binding: ActivityListBinding,
    private val host: AppListHost,
    private val close: () -> Unit,
) {
    init {
        binding.listSettings.setOnClickListener {
            LauncherAction.SETTINGS.launch(activity)
        }
        binding.listClose.setOnClickListener { close() }
        binding.listLock.setOnClickListener {
            togglePrivateSpaceLock(activity)
            if (host.privateSpaceVisibility == AppSetVisibility.EXCLUSIVE) {
                close()
            }
        }

        updateLockIcon()
        (activity.applicationContext as Application).privateSpaceLocked.observe(activity) {
            updateLockIcon(it)
        }
    }

    fun updateTitle() {
        val titleResource =
            if (host.hiddenVisibility == AppSetVisibility.EXCLUSIVE) {
                R.string.list_title_hidden
            } else if (host.privateSpaceVisibility == AppSetVisibility.EXCLUSIVE) {
                R.string.list_title_private_space
            } else if (host.favoritesVisibility == AppSetVisibility.EXCLUSIVE) {
                R.string.list_title_favorite
            } else {
                R.string.list_title_view
            }
        binding.listHeading.text = activity.getString(titleResource)
    }

    fun updateLockIcon(locked: Boolean = isPrivateSpaceLocked(activity)) {
        if (
        // hide lock when private space does not exist
            !isPrivateSpaceSetUp(activity)
            // hide lock when private space apps are hidden from the main list and we are not in the private space list
            || (LauncherPreferences.apps().hidePrivateSpaceApps()
                    && host.privateSpaceVisibility != AppSetVisibility.EXCLUSIVE)
            // hide lock when private space is locked and the hidden when locked setting is set
            || (locked && hidePrivateSpaceWhenLocked(activity))
        ) {
            binding.listLock.visibility = View.GONE
            return
        }

        binding.listLock.visibility = View.VISIBLE

        binding.listLock.setImageDrawable(
            AppCompatResources.getDrawable(
                activity,
                if (locked) {
                    R.drawable.baseline_lock_24
                } else {
                    R.drawable.baseline_lock_open_24
                }
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.listLock.tooltipText = activity.getString(
                if (locked) {
                    R.string.tooltip_unlock_private_space
                } else {
                    R.string.tooltip_lock_private_space
                }
            )
        }
    }
}

/**
 * If [host] only shows the private space, makes sure it is set up and unlocked.
 */
fun unlockPrivateSpaceIfExclusive(context: Context, host: AppListHost) {
    if (host.privateSpaceVisibility == AppSetVisibility.EXCLUSIVE) {
        isPrivateSpaceSetUp(context, showToast = true, launchSettings = true)
        if (isPrivateSpaceLocked(context)) {
            togglePrivateSpaceLock(context)
        }
    }
}
