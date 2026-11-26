@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package de.jrpie.android.launcher.preferences.serialization

import de.jrpie.android.launcher.apps.AbstractAppInfo
import de.jrpie.android.launcher.apps.FavoriteCategories
import de.jrpie.android.launcher.apps.PinnedShortcutInfo
import de.jrpie.android.launcher.icons.IconPackHandle
import de.jrpie.android.launcher.preferences.list.GridSpec
import de.jrpie.android.launcher.widgets.Widget
import de.jrpie.android.launcher.widgets.WidgetPanel
import eu.jonahbauer.android.preference.annotations.serializer.PreferenceSerializationException
import eu.jonahbauer.android.preference.annotations.serializer.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import de.jrpie.android.launcher.icons.DefaultIconPackHandle


const val LOG_TAG = "PreferenceSerializer"
@Suppress("UNCHECKED_CAST")
class SetAbstractAppInfoPreferenceSerializer :
    PreferenceSerializer<java.util.Set<AbstractAppInfo>?, java.util.Set<java.lang.String>?> {
    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: java.util.Set<AbstractAppInfo>?): java.util.Set<java.lang.String> {
        return value?.map(AbstractAppInfo::serialize)
            ?.toHashSet() as java.util.Set<java.lang.String>
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.util.Set<java.lang.String>?): java.util.Set<AbstractAppInfo>? {
        return value?.map(java.lang.String::toString)?.map(AbstractAppInfo::deserialize)
            ?.toHashSet() as? java.util.Set<AbstractAppInfo>
    }
}


@Suppress("UNCHECKED_CAST")
class SetWidgetSerializer :
    PreferenceSerializer<java.util.Set<Widget>?, java.util.Set<java.lang.String>?> {
    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: java.util.Set<Widget>?): java.util.Set<java.lang.String>? {
        return value?.map(Widget::serialize)
            ?.toHashSet() as? java.util.Set<java.lang.String>
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.util.Set<java.lang.String>?): java.util.Set<Widget>? {
        return value?.map(java.lang.String::toString)?.map(Widget::deserialize)
            ?.toHashSet() as? java.util.Set<Widget>
    }
}

@Suppress("UNCHECKED_CAST")
class SetWidgetPanelSerializer :
    PreferenceSerializer<java.util.Set<WidgetPanel>?, java.util.Set<java.lang.String>?> {
    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: java.util.Set<WidgetPanel>?): java.util.Set<java.lang.String>? {
        return value?.map(WidgetPanel::serialize)
            ?.toHashSet() as? java.util.Set<java.lang.String>
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.util.Set<java.lang.String>?): java.util.Set<WidgetPanel>? {
        return value?.map(java.lang.String::toString)?.map(WidgetPanel::deserialize)
            ?.toHashSet() as? java.util.Set<WidgetPanel>
    }
}


@Suppress("UNCHECKED_CAST")
class SetPinnedShortcutInfoPreferenceSerializer :
    PreferenceSerializer<java.util.Set<PinnedShortcutInfo>?, java.util.Set<java.lang.String>?> {
    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: java.util.Set<PinnedShortcutInfo>?): java.util.Set<java.lang.String> {
        return value?.map { Json.encodeToString<PinnedShortcutInfo>(it) }
            ?.toHashSet() as java.util.Set<java.lang.String>
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.util.Set<java.lang.String>?): java.util.Set<PinnedShortcutInfo>? {
        return value?.map(java.lang.String::toString)
            ?.map { Json.decodeFromString<PinnedShortcutInfo>(it) }
            ?.toHashSet() as? java.util.Set<PinnedShortcutInfo>
    }
}


@Suppress("UNCHECKED_CAST")
class MapAbstractAppInfoStringPreferenceSerializer :
    PreferenceSerializer<java.util.HashMap<AbstractAppInfo, String>?, java.util.Set<java.lang.String>?> {

    @Serializable
    private class MapEntry(val key: AbstractAppInfo, val value: String)

    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: java.util.HashMap<AbstractAppInfo, String>?): java.util.Set<java.lang.String>? {
        return value?.map { (key, value) ->
            Json.encodeToString(MapEntry(key, value))
        }?.toHashSet() as? java.util.Set<java.lang.String>
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.util.Set<java.lang.String>?): java.util.HashMap<AbstractAppInfo, String>? {
        return value?.associateTo(HashMap()) {
            val entry = Json.decodeFromString<MapEntry>(it.toString())
            Pair(entry.key, entry.value)
        }
    }
}

private val lenientJson = Json {
    ignoreUnknownKeys = true
    // keep stored values when the defaults change
    encodeDefaults = true
}

class GridSpecSerializer :
    PreferenceSerializer<GridSpec, java.lang.String?> {
    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: GridSpec): java.lang.String {
        return lenientJson.encodeToString(value) as java.lang.String
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.lang.String?): GridSpec {
        value ?: return GridSpec()
        return try {
            lenientJson.decodeFromString<GridSpec>(value.toString()).coerced()
        } catch (e: IllegalArgumentException) {
            // includes SerializationException
            Log.w(LOG_TAG, "Failed to deserialize GridSpec", e)
            GridSpec()
        }
    }
}

class FavoriteCategoriesSerializer :
    PreferenceSerializer<FavoriteCategories, java.lang.String?> {
    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: FavoriteCategories): java.lang.String {
        return lenientJson.encodeToString(value) as java.lang.String
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.lang.String?): FavoriteCategories {
        value ?: return FavoriteCategories()
        return try {
            lenientJson.decodeFromString<FavoriteCategories>(value.toString())
        } catch (e: IllegalArgumentException) {
            // includes SerializationException
            Log.w(LOG_TAG, "Failed to deserialize FavoriteCategories", e)
            FavoriteCategories()
        }
    }
}

class IconPackHandleSerializer:
    PreferenceSerializer<IconPackHandle, java.lang.String?> {
    @Throws(PreferenceSerializationException::class)
    override fun serialize(value: IconPackHandle): java.lang.String {
        return Json.encodeToString(value) as java.lang.String
    }

    @Throws(PreferenceSerializationException::class)
    override fun deserialize(value: java.lang.String?): IconPackHandle {
        value?.let {
            try {
                return Json.decodeFromString<IconPackHandle>(it.toString())
            } catch (e: SerializationException) {
                Log.w(LOG_TAG, "Failed to deserialize IconPackHandle", e)
            } catch (e: IllegalArgumentException) {
                Log.w(LOG_TAG, "Failed to deserialize IconPackHandle", e)
            }
        }
        return DefaultIconPackHandle()
    }
}

