@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package de.jrpie.android.launcher.apps

import de.jrpie.android.launcher.preferences.serialization.FavoriteCategoriesSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCategoriesTest {
    private val signal = AppInfo("org.thoughtcrime.securesms", "Main", 0)
    private val element = AppInfo("im.vector.app", "Main", 0)
    private val mpv = AppInfo("is.xyz.mpv", "Main", 0)
    private val terminal = AppInfo("com.termux", "Main", 0)
    private val work = AppInfo("com.termux", "Main", 10)

    private val messaging = FavoriteCategory(1, "messaging", setOf(signal, element))
    private val media = FavoriteCategory(2, "media", setOf(mpv))
    private val empty = FavoriteCategory(3, "empty")
    private val categories = FavoriteCategories(listOf(messaging, media, empty))

    // sorted by name, as in the app list
    private val apps = listOf(element, mpv, signal, terminal, work)

    @Test
    fun withoutCategoriesEverythingIsOneSection() {
        val sections = FavoriteCategories().group(apps) { it }
        assertEquals(listOf(FavoriteCategories.Section(null, apps)), sections)
    }

    @Test
    fun groupsInOrderOfCategoriesAndKeepsOrderOfApps() {
        val sections = categories.group(apps) { it }
        assertEquals(
            listOf(
                FavoriteCategories.Section(messaging, listOf(element, signal)),
                FavoriteCategories.Section(media, listOf(mpv)),
                FavoriteCategories.Section(null, listOf(terminal, work)),
            ),
            sections
        )
    }

    @Test
    fun noSectionForAppsInNoCategoryIfThereAreNone() {
        val sections = categories.group(listOf(signal, mpv)) { it }
        assertEquals(
            listOf(
                FavoriteCategories.Section(messaging, listOf(signal)),
                FavoriteCategories.Section(media, listOf(mpv)),
            ),
            sections
        )
    }

    @Test
    fun noHeadingsIfNoAppIsInACategory() {
        // e.g. while searching
        val sections = categories.group(listOf(terminal)) { it }
        assertEquals(listOf(FavoriteCategories.Section(null, listOf(terminal))), sections)
    }

    @Test
    fun noSectionsForNoApps() {
        val sections = categories.group(emptyList<AppInfo>()) { it }
        assertEquals(listOf(FavoriteCategories.Section<AppInfo>(null, emptyList())), sections)
    }

    @Test
    fun appsOfDifferentUsersAreDifferent() {
        val updated = categories.assign(terminal, media.id)
        assertEquals(media.id, updated.categoryOf(terminal)?.id)
        assertNull(updated.categoryOf(work))
    }

    @Test
    fun assignMovesAppToCategory() {
        val updated = categories.assign(signal, media.id)
        assertEquals(setOf(element), updated.byId(messaging.id)?.apps)
        assertEquals(setOf(mpv, signal), updated.byId(media.id)?.apps)
    }

    @Test
    fun assignNullRemovesAppFromCategories() {
        val updated = categories.assign(signal, null)
        assertNull(updated.categoryOf(signal))
        assertEquals(setOf(element), updated.byId(messaging.id)?.apps)
    }

    @Test
    fun setAppsOnlyChangesCandidates() {
        // terminal is not among the candidates (e.g. it is hidden) and stays in the category
        val withTerminal = categories.assign(terminal, empty.id)
        val updated = withTerminal.setApps(
            empty.id,
            selected = setOf(signal, mpv),
            candidates = setOf(signal, element, mpv)
        )
        assertEquals(setOf(terminal, signal, mpv), updated.byId(empty.id)?.apps)
        // chosen apps are moved out of their previous category
        assertEquals(setOf(element), updated.byId(messaging.id)?.apps)
        assertEquals(emptySet<AbstractAppInfo>(), updated.byId(media.id)?.apps)
    }

    @Test
    fun setAppsRemovesUnselectedCandidates() {
        val updated = categories.setApps(
            messaging.id,
            selected = setOf(element),
            candidates = setOf(signal, element)
        )
        assertEquals(setOf(element), updated.byId(messaging.id)?.apps)
        assertNull(updated.categoryOf(signal))
    }

    @Test
    fun addAllocatesNewId() {
        val (updated, added) = categories.remove(media.id).add("games")
        assertEquals(empty.id + 1, added.id)
        assertEquals("games", added.name)
        assertEquals(listOf(messaging, empty, added), updated.categories)
        assertEquals(1, FavoriteCategories().add("first").second.id)
    }

    @Test
    fun renameKeepsApps() {
        val updated = categories.rename(messaging.id, "chat")
        assertEquals(messaging.copy(name = "chat"), updated.byId(messaging.id))
    }

    @Test
    fun move() {
        assertEquals(
            listOf(empty, messaging, media),
            categories.move(2, 0).categories
        )
        assertEquals(
            listOf(media, messaging, empty),
            categories.move(0, 1).categories
        )
        assertEquals(categories, categories.move(0, 3))
        assertEquals(categories, categories.move(-1, 0))
    }

    @Test
    fun restoreUndoesRemove() {
        val removed = categories.remove(messaging.id)
        assertEquals(categories, removed.restore(0, messaging))
    }

    @Test
    fun restoreTakesAppsBack() {
        // signal was moved to another category after messaging was removed
        val changed = categories.remove(messaging.id).assign(signal, media.id)
        val restored = changed.restore(5, messaging)
        assertEquals(listOf(media.id, empty.id, messaging.id), restored.categories.map { it.id })
        assertEquals(messaging.id, restored.categoryOf(signal)?.id)
        assertEquals(setOf(mpv), restored.byId(media.id)?.apps)
    }

    @Test
    fun serialization() {
        val serializer = FavoriteCategoriesSerializer()
        val withShortcut = categories.assign(
            PinnedShortcutInfo("id", "org.example", "Main", 0), media.id
        )
        assertEquals(withShortcut, serializer.deserialize(serializer.serialize(withShortcut)))
    }

    @Test
    fun deserializationIsLenient() {
        val serializer = FavoriteCategoriesSerializer()
        assertTrue(serializer.deserialize(null).isEmpty())
        assertTrue(serializer.deserialize("not json" as java.lang.String).isEmpty())
        assertEquals(
            FavoriteCategories(listOf(FavoriteCategory(4, "tools"))),
            serializer.deserialize(
                """{"categories":[{"id":4,"name":"tools","color":"green"}],"version":2}"""
                        as java.lang.String
            )
        )
    }
}
