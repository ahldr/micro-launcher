package de.jrpie.android.launcher.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import de.jrpie.android.launcher.Application
import de.jrpie.android.launcher.apps.AbstractAppInfo
import de.jrpie.android.launcher.apps.AppInfo
import de.jrpie.android.launcher.preferences.LauncherPreferences
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class IconEntry(
    val label: String,
    val componentNameStr: String,
    val drawableName: String
) {
    // Lowercase search keys, computed once instead of on every keystroke.
    val labelKey: String = label.lowercase()
    val drawableNameKey: String = drawableName.lowercase()
}

class IconPackManager private constructor(private val context: Context) {

    val availablePacks = mutableMapOf<String, String>()

    /** Immutable snapshot of a fully loaded icon pack. */
    private data class LoadedPack(
        val pkg: String,
        val res: Resources,
        val map: Map<ComponentName, String>,
        /** drawable name -> resource id (0 if not found) */
        val resIds: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
    )

    @Volatile
    private var loaded: LoadedPack? = null

    init {
        loadAvailablePacks()
    }

    fun clearCache() {
        loaded = null
    }

    fun loadAvailablePacks() {
        availablePacks.clear()
        val pm = context.packageManager
        val intents = listOf(
            Intent("fr.neamar.kiss.THEMES"),
            Intent("org.adw.launcher.THEMES"),
            Intent("com.dlto.atom.launcher.THEME"),
            Intent("com.gau.go.launcherex.theme")
        )
        for (intent in intents) {
            val themes = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (resolveInfo in themes) {
                val pkg = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                availablePacks[pkg] = label
            }
        }
    }

    fun setIconPack(packageName: String?) {
        loadPack(packageName)
    }

    private fun loadPack(packageName: String?): LoadedPack? {
        if (packageName.isNullOrEmpty() || packageName == "null") {
            loaded = null
            return null
        }
        synchronized(this) {
            loaded?.let { if (it.pkg == packageName) return it }
            loaded = null
            return try {
                val res = context.packageManager.getResourcesForApplication(packageName)
                val pack = LoadedPack(packageName, res, parseAppFilterXml(res, packageName))
                loaded = pack
                pack
            } catch (e: Exception) {
                Log.e("IconPackManager", "Failed to load icon pack $packageName", e)
                null
            }
        }
    }

    /** Returns the pack selected in the preferences, loading it if necessary. */
    private fun currentPack(): LoadedPack? {
        val prefPack = LauncherPreferences.theme().iconPack()
        val pack = loaded
        if (pack != null && pack.pkg == prefPack) return pack
        return loadPack(prefPack)
    }

    private fun parseAppFilterXml(
        resources: Resources,
        packageName: String
    ): Map<ComponentName, String> {
        val result = HashMap<ComponentName, String>()
        var parser: XmlPullParser? = null

        // Try to get from xml folder
        val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
        if (xmlId != 0) {
            parser = resources.getXml(xmlId)
        } else {
            // Try to get from raw folder
            val rawId = resources.getIdentifier("appfilter", "raw", packageName)
            if (rawId != 0) {
                val input: InputStream = resources.openRawResource(rawId)
                val factory = XmlPullParserFactory.newInstance()
                parser = factory.newPullParser()
                parser.setInput(input, "UTF-8")
            }
        }

        if (parser == null) return result

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    var componentStr: String? = null
                    var drawableStr: String? = null
                    for (i in 0 until parser.attributeCount) {
                        when (parser.getAttributeName(i)) {
                            "component" -> componentStr = parser.getAttributeValue(i)
                            "drawable" -> drawableStr = parser.getAttributeValue(i)
                        }
                    }
                    if (componentStr != null && drawableStr != null) {
                        val component = parseComponent(componentStr)
                        if (component != null) {
                            result[component] = drawableStr
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("IconPackManager", "Error parsing appfilter", e)
        }
        return result
    }

    private fun parseComponent(str: String): ComponentName? {
        if (str.startsWith("ComponentInfo{") && str.endsWith("}")) {
            return ComponentName.unflattenFromString(str.substring(14, str.length - 1))
        }
        return null
    }

    fun getIcon(appInfo: AbstractAppInfo, defaultComponentName: ComponentName): Drawable? {
        val pack = currentPack()

        // Check for custom per-app icon override
        val customMap = LauncherPreferences.apps().customIcons()
        val customTarget = customMap?.get(appInfo)
        if (!customTarget.isNullOrEmpty()) {
            val customComponent = ComponentName.unflattenFromString(customTarget)
            if (customComponent != null) {
                val icon = getIconForComponent(pack, customComponent)
                if (icon != null) return icon
            }
            val icon = getDrawableByName(pack, customTarget)
            if (icon != null) return icon
        }

        return getIconForComponent(pack, defaultComponentName)
    }

    fun getIcon(componentName: ComponentName): Drawable? {
        return getIconForComponent(currentPack(), componentName)
    }

    private fun getIconForComponent(pack: LoadedPack?, componentName: ComponentName): Drawable? {
        if (pack == null) return null
        val drawableName = pack.map[componentName] ?: return null
        return getDrawableByName(pack, drawableName)
    }

    fun getDrawableByName(drawableName: String): Drawable? =
        getDrawableByName(loaded, drawableName)

    private fun getDrawableByName(pack: LoadedPack?, drawableName: String): Drawable? {
        if (pack == null) return null
        val resId = pack.resIds.getOrPut(drawableName) {
            pack.res.getIdentifier(drawableName, "drawable", pack.pkg)
        }
        if (resId != 0) {
            return try {
                ResourcesCompat.getDrawable(pack.res, resId, null)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Lists all icons of the current pack. Does not touch the main thread's state,
     * so it is safe (and intended) to call this from a background thread.
     */
    fun getAvailableIcons(context: Context): List<IconEntry> {
        val pack = currentPack() ?: return emptyList()

        // Prefer labels from the already loaded app list over a PackageManager query per entry.
        val labels = HashMap<ComponentName, String>()
        (context.applicationContext as? Application)?.apps?.value?.forEach { app ->
            val info = app.getRawInfo() as? AppInfo ?: return@forEach
            val activity = info.activityName ?: return@forEach
            labels.putIfAbsent(ComponentName(info.packageName, activity), app.getLabel())
        }

        val list = ArrayList<IconEntry>(pack.map.size)
        for ((comp, drawableName) in pack.map) {
            list.add(IconEntry(labels[comp] ?: drawableName, comp.flattenToString(), drawableName))
        }

        list.sortBy { it.labelKey }
        return list
    }

    companion object {
        @Volatile
        private var INSTANCE: IconPackManager? = null

        fun getInstance(context: Context): IconPackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IconPackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
