package de.jrpie.android.launcher.widgets

import android.app.Service
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import androidx.appcompat.content.res.AppCompatResources
import de.jrpie.android.launcher.R

sealed class LauncherWidgetProvider(
    val label: CharSequence?,
    val description: CharSequence?,
    val appName: CharSequence?
) {
    /** The icon of the widget. May be expensive to load for [LauncherAppWidgetProvider]. */
    abstract val icon: Drawable?

    /** The preview of the widget. May be expensive to load for [LauncherAppWidgetProvider]. */
    abstract val previewImage: Drawable?

    /** Whether [icon] and [previewImage] can be read without doing any expensive work. */
    abstract val imagesLoaded: Boolean
}

/**
 * [icon] and [previewImage] are loaded lazily, since decoding them is expensive.
 * [label], [description] and [appName] (which are used for sorting and searching)
 * are loaded eagerly. Consider constructing this off the main thread.
 */
class LauncherAppWidgetProvider(
    val info: AppWidgetProviderInfo,
    context: Context,
) : LauncherWidgetProvider(
    info.loadLabel(context.packageManager),
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        info.loadDescription(context)
    } else {
        null
    },
    // use LauncherApps to resolve labels of apps from other user profiles
    (context.getSystemService(Service.LAUNCHER_APPS_SERVICE) as LauncherApps)
        .getActivityList(info.provider.packageName, info.profile)
        .firstOrNull()?.label ?: info.provider.packageName
) {
    private val appContext: Context = context.applicationContext

    private val lazyIcon = lazy {
        info.loadIcon(appContext, DisplayMetrics.DENSITY_DEFAULT)?.let {
            // badge the icon so widgets from other profiles can be distinguished
            appContext.packageManager.getUserBadgedIcon(it, info.profile)
        }
    }
    private val lazyPreviewImage = lazy {
        info.loadPreviewImage(appContext, DisplayMetrics.DENSITY_DEFAULT)
    }

    override val icon: Drawable? by lazyIcon
    override val previewImage: Drawable? by lazyPreviewImage
    override val imagesLoaded: Boolean
        get() = lazyIcon.isInitialized() && lazyPreviewImage.isInitialized()
}

class LauncherClockWidgetProvider(context: Context) : LauncherWidgetProvider(
    context.getString(R.string.widget_clock_label),
    context.getString(R.string.widget_clock_description),
    context.getString(R.string.app_name)
) {
    override val icon: Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.baseline_clock_24)
    override val previewImage: Drawable? = null
    override val imagesLoaded: Boolean = true
}