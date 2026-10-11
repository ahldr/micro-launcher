package de.jrpie.android.launcher.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import de.jrpie.android.launcher.apps.AbstractAppInfo
import de.jrpie.android.launcher.preferences.LauncherPreferences
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class IconEntry(
    val label: String,
    val componentNameStr: String,
    val drawableName: String
)

class IconPackManager private constructor(private val context: Context) {

    val availablePacks = mutableMapOf<String, String>()
    private var currentPack: String? = null
    private var packResources: Resources? = null
    private val drawablesByComponent = mutableMapOf<ComponentName, String>()

    init {
        loadAvailablePacks()
    }

    fun clearCache() {
        currentPack = null
        packResources = null
        drawablesByComponent.clear()
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
        if (packageName == currentPack && packResources != null) return
        drawablesByComponent.clear()
        currentPack = packageName
        packResources = null
        if (packageName.isNullOrEmpty() || packageName == "null") {
            return
        }

        try {
            val pm = context.packageManager
            packResources = pm.getResourcesForApplication(packageName)
            parseAppFilterXml(packageName)
        } catch (e: Exception) {
            Log.e("IconPackManager", "Failed to load icon pack $packageName", e)
        }
    }

    private fun parseAppFilterXml(packageName: String) {
        val resources = packResources ?: return
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

        if (parser == null) return

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
                            drawablesByComponent[component] = drawableStr
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("IconPackManager", "Error parsing appfilter", e)
        }
    }

    private fun parseComponent(str: String): ComponentName? {
        if (str.startsWith("ComponentInfo{") && str.endsWith("}")) {
            return ComponentName.unflattenFromString(str.substring(14, str.length - 1))
        }
        return null
    }

    fun getIcon(appInfo: AbstractAppInfo, defaultComponentName: ComponentName): Drawable? {
        val prefPack = LauncherPreferences.theme().iconPack()
        if (prefPack != currentPack) {
            setIconPack(prefPack)
        }
        return getIcon(appInfo, defaultComponentName, LauncherPreferences.apps().customIcons())
    }

    /**
     * Like [getIcon], but takes the custom icon map as a parameter and does not read preferences.
     * The caller must have set the icon pack already (see [setIconPack]).
     */
    fun getIcon(
        appInfo: AbstractAppInfo,
        defaultComponentName: ComponentName,
        customMap: Map<AbstractAppInfo, String>?
    ): Drawable? {
        // Check for custom per-app icon override
        val customTarget = customMap?.get(appInfo)
        if (!customTarget.isNullOrEmpty()) {
            val customComponent = ComponentName.unflattenFromString(customTarget)
            if (customComponent != null) {
                val icon = getIconForComponent(customComponent)
                if (icon != null) return icon
            }
            val icon = getDrawableByName(customTarget)
            if (icon != null) return icon
        }

        return getIconForComponent(defaultComponentName)
    }

    fun getIcon(componentName: ComponentName): Drawable? {
        val prefPack = LauncherPreferences.theme().iconPack()
        if (prefPack != currentPack) {
            setIconPack(prefPack)
        }
        return getIconForComponent(componentName)
    }

    private fun getIconForComponent(componentName: ComponentName): Drawable? {
        if (packResources == null || currentPack == null) return null
        val drawableName = drawablesByComponent[componentName] ?: return null
        return getDrawableByName(drawableName)
    }

    fun getDrawableByName(drawableName: String): Drawable? {
        if (packResources == null || currentPack == null) return null
        val resId = packResources!!.getIdentifier(drawableName, "drawable", currentPack)
        if (resId != 0) {
            return try {
                ResourcesCompat.getDrawable(packResources!!, resId, null)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun getAvailableIcons(context: Context): List<IconEntry> {
        val prefPack = LauncherPreferences.theme().iconPack()
        if (prefPack != currentPack) {
            setIconPack(prefPack)
        }

        val list = mutableListOf<IconEntry>()
        val pm = context.packageManager

        for ((comp, drawableName) in drawablesByComponent) {
            val label = try {
                val activityInfo = pm.getActivityInfo(comp, 0)
                activityInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                drawableName
            }
            list.add(IconEntry(label, comp.flattenToString(), drawableName))
        }

        list.sortBy { it.label.lowercase() }
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
