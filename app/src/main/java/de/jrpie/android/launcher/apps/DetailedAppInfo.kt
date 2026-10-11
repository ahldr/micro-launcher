package de.jrpie.android.launcher.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import de.jrpie.android.launcher.LOG_TAG
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.AppAction
import de.jrpie.android.launcher.icons.IconPackManager
import de.jrpie.android.launcher.getUserFromId

/**
 * Stores information used to create [de.jrpie.android.launcher.ui.list.apps.AppsRecyclerAdapter] rows.
 *
 * When created from a [LauncherActivityInfo], the icon is not resolved up front
 * (this decodes a bitmap or does an icon pack lookup), but on the first call to [getIcon],
 * which may happen on any thread. The result is cached, so every later call returns the
 * same [Drawable] instance.
 */
class DetailedAppInfo private constructor(
    private val app: AppInfo,
    private val label: CharSequence,
    preloadedIcon: Drawable?,
    /** Used to resolve the icon when [preloadedIcon] is `null`. */
    private val activityInfo: LauncherActivityInfo?,
    private val privateSpace: Boolean,
    private val removable: Boolean,
) : AbstractDetailedAppInfo {

    @Volatile
    private var icon: Drawable? = preloadedIcon

    constructor(
        app: AppInfo,
        label: CharSequence,
        icon: Drawable,
        privateSpace: Boolean,
        removable: Boolean = true,
    ) : this(app, label, icon, null, privateSpace, removable)

    // context is no longer needed here since the icon is resolved lazily;
    // the parameter is kept to leave the constructor signature unchanged.
    @Suppress("UNUSED_PARAMETER")
    constructor(activityInfo: LauncherActivityInfo, private: Boolean, context: Context) : this(
        AppInfo(
            activityInfo.applicationInfo.packageName,
            activityInfo.name,
            activityInfo.user.hashCode()
        ),
        activityInfo.label,
        null,
        activityInfo,
        private,
        // App can be uninstalled iff it is not a system app
        activityInfo.applicationInfo.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
    )


    override fun getLabel(): String {
        return label.toString()
    }

    /**
     * Returns the icon, resolving it first if this has not happened yet.
     * Resolving can be slow; [de.jrpie.android.launcher.preloadAppIcons] does it in the
     * background for the app list. Calling this on the main thread for an icon that was not
     * resolved yet is still correct, it just loads the icon synchronously.
     */
    override fun getIcon(context: Context): Drawable {
        icon?.let { return it }
        // Only one thread resolves a given icon, so all callers get the same instance.
        return synchronized(this) {
            icon ?: loadIcon(context)?.also { icon = it }
        } ?: context.packageManager.defaultActivityIcon
    }

    /**
     * Resolves the icon the same way the constructor used to do eagerly.
     * Returns `null` if this fails, so that it is tried again on the next call.
     */
    private fun loadIcon(context: Context): Drawable? {
        val info = activityInfo ?: return null
        return try {
            IconPackManager.getInstance(context).getIcon(app, info.componentName)
                ?: info.getBadgedIcon(0)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "exception thrown while loading icon of $app", e)
            null
        }
    }

    override fun getRawInfo(): AppInfo {
        return app
    }

    override fun getUser(context: Context): UserHandle {
        return getUserFromId(app.user, context)
    }

    override fun isPrivate(): Boolean {
        return privateSpace
    }

    override fun isRemovable(): Boolean {
        return removable
    }

    override fun getAction(): Action {
        return AppAction(app)
    }


    companion object {
        fun fromAppInfo(appInfo: AppInfo, context: Context): DetailedAppInfo? {
            return appInfo.getLauncherActivityInfo(context)?.let {
                DetailedAppInfo(it, it.user == getPrivateSpaceUser(context), context)
            }
        }
    }
}