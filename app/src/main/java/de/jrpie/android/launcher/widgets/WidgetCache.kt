package de.jrpie.android.launcher.widgets

import de.jrpie.android.launcher.preferences.LauncherPreferences

/**
 * In-memory cache of the widgets stored in the preferences.
 *
 * Reading [LauncherPreferences.widgets] deserialises every widget from JSON, which is too
 * expensive for hot paths such as touch handling. The cache is filled lazily and must be
 * invalidated with [invalidate] whenever the stored widgets change.
 * [de.jrpie.android.launcher.Application] does this from a preference change listener;
 * code which writes widgets and then immediately reads them from a preference listener should
 * call [invalidate] right before writing (see [updateWidget]), because the order in which
 * preference listeners are notified is unspecified.
 *
 * The cached instances are never handed out: [Widget] is mutable and callers modify widgets
 * in place before calling [updateWidget]. [get] and [getAll] return copies, so such edits
 * can't corrupt the cache. Read-only queries which are needed on hot paths get dedicated
 * methods which don't allocate, such as [isInteractionAllowed].
 */
object WidgetCache {
    private val lock = Any()

    /** Immutable snapshot of the widgets by id; `null` if it has to be (re)loaded. */
    @Volatile
    private var widgets: Map<Int, Widget>? = null

    /** Incremented on each invalidation, so that a load racing with one is discarded. */
    private var generation = 0

    fun invalidate() {
        synchronized(lock) {
            generation++
            widgets = null
        }
    }

    private fun load(): Map<Int, Widget> {
        widgets?.let { return it }
        val loadedGeneration = synchronized(lock) { generation }
        val loaded = (LauncherPreferences.widgets().widgets() ?: setOf()).associateBy { it.id }
        synchronized(lock) {
            if (loadedGeneration == generation) {
                widgets = loaded
            }
        }
        return loaded
    }

    /** @return a copy of the widget with the given id, which the caller may modify. */
    fun get(id: Int): Widget? = load()[id]?.copy()

    /** @return copies of all widgets, which the caller may modify. */
    fun getAll(): List<Widget> = load().values.map { it.copy() }

    /**
     * @return whether the user can interact with the widget [id],
     * or `null` if there is no such widget.
     */
    fun isInteractionAllowed(id: Int): Boolean? = load()[id]?.allowInteraction
}
