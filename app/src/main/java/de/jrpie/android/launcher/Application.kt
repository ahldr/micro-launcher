package de.jrpie.android.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.UserHandle
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import de.jrpie.android.launcher.actions.Action
import de.jrpie.android.launcher.actions.Gesture
import de.jrpie.android.launcher.actions.TorchManager
import de.jrpie.android.launcher.apps.AbstractAppInfo
import de.jrpie.android.launcher.apps.AbstractDetailedAppInfo
import de.jrpie.android.launcher.apps.isPrivateSpaceLocked
import de.jrpie.android.launcher.preferences.LauncherPreferences
import de.jrpie.android.launcher.preferences.migratePreferencesToNewVersion
import de.jrpie.android.launcher.preferences.resetPreferences
import de.jrpie.android.launcher.widgets.WidgetCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.system.exitProcess


const val APP_WIDGET_HOST_ID = 42


class Application : android.app.Application() {
    val apps = MutableLiveData<List<AbstractDetailedAppInfo>>()
    val privateSpaceLocked = MutableLiveData<Boolean>()
    val onThemeChanged = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    lateinit var appWidgetHost: AppWidgetHost
    lateinit var appWidgetManager: AppWidgetManager

    private val profileAvailabilityBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // TODO: only update specific apps
            // use Intent.EXTRA_USER
            loadApps()
        }
    }

    // TODO: only update specific apps
    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p0: String?, p1: UserHandle?) {
            loadApps()
        }

        override fun onPackageAdded(p0: String?, p1: UserHandle?) {
            loadApps()
        }

        override fun onPackageChanged(p0: String?, p1: UserHandle?) {
            loadApps()
        }

        override fun onPackagesAvailable(p0: Array<out String>?, p1: UserHandle?, p2: Boolean) {
            // TODO
        }

        override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
            // TODO
        }

        override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) {
            // TODO
        }

        override fun onPackagesUnavailable(p0: Array<out String>?, p1: UserHandle?, p2: Boolean) {
            // TODO
        }

        override fun onPackageLoadingProgressChanged(
            packageName: String,
            user: UserHandle,
            progress: Float
        ) {
            // TODO
        }

        override fun onShortcutsChanged(
            packageName: String,
            shortcuts: MutableList<ShortcutInfo>,
            user: UserHandle
        ) {
            // TODO
        }
    }

    var torchManager: TorchManager? = null
    private var customAppNames: HashMap<AbstractAppInfo, String>? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, pref ->
        if (pref == getString(R.string.settings_apps_custom_names_key)) {
            customAppNames = LauncherPreferences.apps().customNames()
        } else if (pref == LauncherPreferences.apps().keys().pinnedShortcuts()) {
            loadApps()
        } else if (pref == LauncherPreferences.theme().keys().iconPack() ||
            pref == getString(R.string.settings_apps_custom_icons_key)) {
            de.jrpie.android.launcher.icons.IconPackManager.getInstance(this).clearCache()
            loadApps()
        }

        if (pref?.startsWith("theme.") == true || pref?.startsWith("display.") == true) {
            onThemeChanged.tryEmit(Unit)
        }
    }

    // Preferences hold their listeners weakly, so this must be kept in a field.
    private val widgetCacheListener = SharedPreferences.OnSharedPreferenceChangeListener { _, pref ->
        // pref is null if the preferences were cleared
        if (pref == null || pref == LauncherPreferences.widgets().keys().widgets()) {
            WidgetCache.invalidate()
        }
    }

    // Keeps the decoded gesture action cache in sync with changes made through
    // import, reset or any other path that bypasses Action.setActionForGesture.
    private val actionCacheListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) {
            Action.invalidateCache()
        } else if (Gesture.byId(key) != null) {
            Action.invalidateCache(key)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // TODO  Error: Invalid resource ID 0x00000000.
        // DynamicColors.applyToActivitiesIfAvailable(this)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            sendCrashNotification(this@Application, throwable)
            exitProcess(1)
        }


        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            torchManager = TorchManager(this)
        }

        appWidgetHost = AppWidgetHost(this.applicationContext, APP_WIDGET_HOST_ID)
        appWidgetManager = AppWidgetManager.getInstance(this.applicationContext)


        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        LauncherPreferences.init(preferences, this.resources)

        // Registered before the migrations below, which write widgets.
        preferences.registerOnSharedPreferenceChangeListener(widgetCacheListener)

        // Try to restore old preferences
        migratePreferencesToNewVersion(this)

        // First time opening the app: set defaults
        // The tutorial is started from HomeActivity#onStart, as starting it here is blocked by android
        if (!LauncherPreferences.internal().started()) {
            resetPreferences(this)
        }


        LauncherPreferences.getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(listener)
        LauncherPreferences.getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(actionCacheListener)


        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.registerCallback(launcherAppsCallback)

        if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
            val filter = IntentFilter().also {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.VANILLA_ICE_CREAM) {
                    it.addAction(Intent.ACTION_PROFILE_AVAILABLE)
                    it.addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                } else {
                    it.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                    it.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                }
            }
            ContextCompat.registerReceiver(
                this, profileAvailabilityBroadcastReceiver, filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        if (Build.VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
            // Only binder calls and preference reads, so keep it off the main thread.
            appScope.launch {
                removeUnusedShortcuts(this@Application)
            }
        }
        loadApps()

        createNotificationChannels(this)
    }

    fun getCustomAppNames(): HashMap<AbstractAppInfo, String> {
        return (customAppNames ?: LauncherPreferences.apps().customNames() ?: HashMap())
            .also { customAppNames = it }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null

    // May be called from binder threads (LauncherApps callbacks), hence synchronized.
    @Synchronized
    private fun loadApps() {
        privateSpaceLocked.postValue(isPrivateSpaceLocked(this))
        loadJob?.cancel()
        loadJob = appScope.launch {
            // debounce bursts of package callbacks
            delay(150)
            val result = getApps(packageManager, applicationContext)
            ensureActive()
            // show the labels right away, icons are resolved lazily
            apps.postValue(result)
            preloadAppIcons(result, applicationContext) {
                // post again so observers can pick up the icons of the first rows
                ensureActive()
                apps.postValue(result)
            }
        }
    }
}
