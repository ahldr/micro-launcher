package de.jrpie.android.launcher.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.AppAction
import de.jrpie.android.launcher.icons.IconPackManager
import de.jrpie.android.launcher.getUserFromId
import de.jrpie.android.launcher.preferences.LauncherPreferences

/**
 * Stores information used to create [de.jrpie.android.launcher.ui.list.apps.AppsRecyclerAdapter] rows.
 */
class DetailedAppInfo(
    private val app: AppInfo,
    private val label: CharSequence,
    private val icon: Drawable,
    private val privateSpace: Boolean,
    private val removable: Boolean = true,
) : AbstractDetailedAppInfo {

    /**
     * Reads the icon preferences for this single app. When creating many instances, pass a
     * snapshot of the preferences with the constructor below instead.
     */
    constructor(activityInfo: LauncherActivityInfo, private: Boolean, context: Context) : this(
        activityInfo,
        private,
        context,
        readIconPreferences(context)
    )

    /**
     * [customIcons] is a snapshot of the custom icon preference. The icon pack must already be
     * set on [IconPackManager] (see [getApps]).
     */
    constructor(
        activityInfo: LauncherActivityInfo,
        private: Boolean,
        context: Context,
        customIcons: Map<AbstractAppInfo, String>?,
    ) : this(
        AppInfo(
            activityInfo.applicationInfo.packageName,
            activityInfo.name,
            activityInfo.user.hashCode()
        ),
        activityInfo.label,
        IconPackManager.getInstance(context).getIcon(
            AppInfo(
                activityInfo.applicationInfo.packageName,
                activityInfo.name,
                activityInfo.user.hashCode()
            ),
            activityInfo.componentName,
            customIcons
        ) ?: activityInfo.getBadgedIcon(0),
        private,
        // App can be uninstalled iff it is not a system app
        activityInfo.applicationInfo.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
    )


    override fun getLabel(): String {
        return label.toString()
    }

    override fun getIcon(context: Context): Drawable {
        return icon
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
        private fun readIconPreferences(context: Context): Map<AbstractAppInfo, String>? {
            IconPackManager.getInstance(context).setIconPack(LauncherPreferences.theme().iconPack())
            return LauncherPreferences.apps().customIcons()
        }

        fun fromAppInfo(appInfo: AppInfo, context: Context): DetailedAppInfo? {
            return appInfo.getLauncherActivityInfo(context)?.let {
                DetailedAppInfo(it, it.user == getPrivateSpaceUser(context), context)
            }
        }
    }
}