# Improvement backlog: fluidity, UX and widgets

Each item below is meant to be handed to a coding agent as a self-contained task.
Items are independent unless a "Depends on" line says otherwise. Paths are relative
to `app/src/main/java/de/jrpie/android/launcher/`.

**Suggested model / effort** names the cheapest setup that should get the item right.
Use the stronger option if the first attempt fails review.

**General rules for every item**
- Keep the change scoped to the item. Don't refactor neighbouring code.
- The project has no unit tests. Verify with `./gradlew assembleDefaultDebug lintDefaultDebug`
  (or `make debug` inside `nix develop`), then do the manual check listed for the item.
- Keep behaviour identical for users who don't hit the problem described.
- One item per PR.

| # | Area | Title | Model / effort |
|---|------|-------|----------------|
| G1 | App list | Stop rebuilding the app list adapter on every `onStart` | Sonnet 5.5 / medium |
| G2 | App list | Only refresh the app list for preferences that affect it | Haiku 5.5 / low |
| G3 | App list | Use `ListAdapter` + `DiffUtil` instead of `notifyDataSetChanged` | Sonnet 5.5 / medium |
| G4 | App list | Make per-keystroke search cheap | Sonnet 5.5 / medium |
| G5 | App list | Remove per-bind allocations in `AppsRecyclerAdapter` | Haiku 5.5 / medium |
| G6 | Search | Fix keyboard not hiding after search auto-launch | Haiku 5.5 / low |
| G7 | App loading | Coalesce concurrent `loadApps()` calls | Sonnet 5.5 / medium |
| G8 | Icons | Make `IconPackManager` thread-safe | Sonnet 5.5 / high |
| G9 | Icons | Load the custom icon picker off the main thread | Sonnet 5.5 / medium |
| G10 | App loading | Show the app list before every icon is resolved | Opus 5.5 / high |
| W1 | Widgets | Update widget views incrementally instead of tearing them down | Opus 5.5 / high |
| W2 | Widgets | Cache widgets in memory; no JSON parsing on touch | Sonnet 5.5 / medium |
| W3 | Widgets | Keep the hosted widget's reported size in sync | Sonnet 5.5 / high |
| W4 | Widgets | Edit-mode ergonomics: dp handles, cancel handling, small widgets | Sonnet 5.5 / medium |
| W5 | Widgets | Placement and lifecycle robustness | Sonnet 5.5 / medium |
| P1 | Performance | Read icon preferences once per app load, not once per app | Haiku 5.5 / medium |
| P2 | Performance | Cache decoded gesture actions | Sonnet 5.5 / medium |
| P3 | Performance | Load widget picker icons and previews lazily | Sonnet 5.5 / medium |
| P4 | Performance | Drop unused preview decoding in widget edit mode | Haiku 5.5 / low |
| P5 | Performance | Move shortcut cleanup off the startup path | Haiku 5.5 / medium |
| P6 | Performance | Release build: Baseline Profile and resource shrinking | Sonnet 5.5 / high |

---

## General

### G1. Stop rebuilding the app list adapter on every `onStart`

**Problem.** `UIObject.onStart()` calls `adjustLayout()`. `ListFragmentApps.adjustLayout()`
(`ui/list/apps/ListFragmentApps.kt`) creates a **new** `AppsRecyclerAdapter`, a new layout
manager and new listeners every time the fragment starts, for example after returning from
an app's info screen or from the icon picker. Each new adapter calls
`apps.observe(activity)` in its `init` block (`ui/list/apps/AppsRecyclerAdapter.kt`). The
old observers are never removed, so every start adds one more observer that re-filters the
full list whenever apps change.

User-visible effect: scroll position and search state reset when coming back to the
drawer. Each extra observer also adds work and a full re-filter on later app changes.

**Fix.**
1. Move adapter, layout manager and listener setup from `adjustLayout()` into
   `onViewCreated()` of `ListFragmentApps`, so it runs once per view.
2. Make `adjustLayout()` a no-op (or only update things that can actually change between
   starts, such as the favourites checkbox state, which `onStart` already handles).
3. In `AppsRecyclerAdapter`, observe with the fragment's `viewLifecycleOwner` instead of
   `activity as AppCompatActivity`. Pass the `LifecycleOwner` in as a constructor parameter.
   This also removes the unchecked cast.

**Acceptance.** Open the drawer, scroll halfway, long-press an app, open "App info", press
back. The scroll position is preserved. Add a log line in the observer: it fires once per
app change, not once per previous `onStart`.

### G2. Only refresh the app list for preferences that affect it

**Problem.** `ListFragmentApps.sharedPreferencesListener` calls
`appsRecyclerAdapter.updateAppsList()` for **every** preference change. That includes
widget moves, clock settings and anything written while the drawer is open. Each call
re-sorts and re-filters all apps and calls `notifyDataSetChanged()`.

**Fix.** Only call `updateAppsList()` when the changed key is one of these:
- `apps.*`: hidden, favourites, custom names, custom icons, hide bound apps, hide paused apps
- `action.*`: affects "hide bound apps"
- `list.*`

Use the key accessors in `LauncherPreferences` (for example
`LauncherPreferences.apps().keys()...`) where they exist, and fall back to prefix checks
(`key.startsWith("apps.")`). Keep the existing layout-manager update for `gridColumns` and
`layout`.

**Acceptance.** Changing a clock setting while the drawer exists does not call
`updateAppsList()` (verify with a log line). Hiding or favouriting an app still updates the
list immediately.

### G3. Use `ListAdapter` + `DiffUtil` instead of `notifyDataSetChanged`

**Depends on:** G1 (otherwise you diff on an adapter that is thrown away on the next start).

**Problem.** `AppsRecyclerAdapter.updateAppsList()` clears its list and calls
`notifyDataSetChanged()`. That happens on every keystroke in search and on every app
change. RecyclerView then rebinds every visible row and cannot animate, so search feels
jumpy.

**Fix.**
1. Convert `AppsRecyclerAdapter` to extend
   `androidx.recyclerview.widget.ListAdapter<AbstractDetailedAppInfo, ViewHolder>`.
2. `DiffUtil.ItemCallback`:
   - `areItemsTheSame`: `a.getRawInfo() == b.getRawInfo()`
   - `areContentsTheSame`: same custom label and the same icon instance. Icons are
     recreated when apps reload, so a changed icon must count as different.
3. Replace `appsListDisplayed` with `currentList`. In `updateAppsList()`, call
   `submitList(newList)` and run the auto-launch check in the commit callback
   (`submitList(list) { ... }`), so it sees the new list.
4. `selectItem(pos)` must read `currentList.getOrNull(pos)`.
5. Keep `setHasFixedSize(true)`.

**Acceptance.** Typing in search animates rows in and out instead of flashing. Auto-launch
with a single match still works. Long-press menus still act on the right app after
filtering.

### G4. Make per-keystroke search cheap

**Problem.** `AppFilter.invoke()` (`apps/AppFilter.kt`) runs on the main thread on every
keystroke and does all of the following each time:
- sorts the full app list by lowercased custom label
- deserialises the `hidden` and `favorites` preference sets
- walks every enabled gesture, deserialising each action, for "hide bound apps"
- then filters by query

Only the last step depends on the query.

**Fix.**
1. Split `AppFilter` into a *base list* step and a *query* step. The base list covers
   sort, visibility and bound apps.
2. Cache the base list in `AppFilter`. Invalidate it when the input `apps` list reference
   changes or when a relevant preference changes. The simplest approach is an
   `invalidate()` method the fragment calls from the narrowed listener in G2.
3. `setSearchString()` should only rerun the query step on the cached base list.
4. Pre-compute the lowercase label once per app in the base step, not per comparison.

**Acceptance.** Same results as before for every visibility combination (favourites,
hidden, private space, hide bound apps). With 200+ apps installed, typing produces no
dropped frames in "Profile GPU rendering".

### G5. Remove per-bind allocations in `AppsRecyclerAdapter`

**Problem.** `onBindViewHolder` does all of the following on every bind:
- `appIcon.constantState?.newDrawable()`, a new drawable per bind, even when the row is
  rebound to the same app
- `packageManager.getUserBadgedLabel(...)`, which is a binder call when
  `layout.useBadgedText`
- creates four new click and long-click lambdas

**Fix.**
1. Set the click and long-click listeners once in `ViewHolder.init`. Look up the item via
   `bindingAdapterPosition`, and guard against `NO_POSITION`.
2. Leave `constantState?.newDrawable()` in place. One `Drawable` instance must not be
   attached to two views at once (bounds and callback are per-instance), and
   `ImageView.transformMonochrome` (`ui/Helper.kt`) sets the filter on the view, not the
   drawable. Only skip the call when `holder.img.drawable` already came from the same
   source drawable: track the source in a tag on the view.
3. Cache the badged label on the `AbstractDetailedAppInfo` or in a small map keyed by
   `getRawInfo()`. Rebuild the cache when the app list changes.

**Acceptance.** Behaviour unchanged, including the monochrome icons setting and the work
profile badge in the TEXT layout. Fast scrolling of the grid layout shows no GC churn in
the Android Studio memory profiler.

### G6. Fix keyboard not hiding after search auto-launch

**Problem.** In `AppsRecyclerAdapter.updateAppsList()`, after auto-launching the only match,
the code calls:

```kotlin
inputMethodManager.hideSoftInputFromWindow(View(activity).windowToken, 0)
```

A freshly constructed `View` is not attached to a window, so `windowToken` is `null` and the
call does nothing. When the user returns to the launcher, the keyboard can still be up.

**Fix.** Use a token from an attached view, either `root.windowToken` (the adapter already
receives `root`) or `activity.window.decorView.windowToken`. Clear the search query as
well, so returning to the drawer doesn't immediately re-trigger auto-launch with the same
text.

**Acceptance.** Enable "auto-launch on single match", type until one app matches, then
return to the launcher. The keyboard is hidden and the query is empty.

### G7. Coalesce concurrent `loadApps()` calls

**Problem.** `Application.loadApps()` launches a fresh, unscoped
`CoroutineScope(Dispatchers.Default).launch { ... }` on every `LauncherApps` callback.
Installing or updating an app often fires `onPackageRemoved`, `onPackageAdded` and
`onPackageChanged` in quick succession, so three full reloads run in parallel.
`getApps` resolves every icon. Results are posted with `postValue` in completion order, so
an older snapshot can overwrite a newer one.

**Fix.**
1. Add a single application-level scope:
   `private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
2. Keep a `private var loadJob: Job?`. In `loadApps()`, cancel the previous job, then
   start a new one that first calls `delay(150)` (debounce) and then loads.
3. Check `isActive` / `ensureActive()` before `postValue`, so a cancelled job never posts.
4. Leave the existing call sites unchanged.

**Acceptance.** Updating an app from a store results in exactly one `getApps` run (log it).
Uninstalling an app removes it from the drawer, and installing one adds it, both within
about a second.

### G8. Make `IconPackManager` thread-safe

**Problem.** `icons/IconPackManager.kt` keeps mutable state (`currentPack`, `packResources`,
`drawablesByComponent`) in a singleton that is used from two threads:
- `Dispatchers.Default`: `getApps` → `DetailedAppInfo` constructor → `getIcon`
- the main thread: `clearCache()` from the `Application` preference listener,
  `getAvailableIcons()` and `getDrawableByName()` from the icon picker

`setIconPack` clears and repopulates the map while another thread may be iterating it.
That can throw `ConcurrentModificationException` (which the global handler turns into a
crash notification and `exitProcess(1)`) or produce a half-loaded pack.

**Fix.**
1. Put the loaded state in an immutable holder:
   `data class LoadedPack(val pkg: String, val res: Resources, val map: Map<ComponentName, String>)`.
2. Store it in a `@Volatile var loaded: LoadedPack?`. Build a new `LoadedPack` completely,
   then assign it in a single step.
3. `clearCache()` just sets `loaded = null`.
4. Readers take a local `val pack = loaded` once and use only that.
5. Guard `setIconPack` with `synchronized(this)` so two threads don't parse the same pack
   at the same time.
6. Also cache `getIdentifier` results (`drawableName` → `resId`) inside `LoadedPack`, in a
   `ConcurrentHashMap`. `getIdentifier` is slow and is currently called on every lookup.

**Acceptance.** Switch the icon pack back and forth quickly while the drawer is open. There
is no crash, and the icons end in a state matching the selected pack.

### G9. Load the custom icon picker off the main thread

**Depends on:** G8 (thread safety).

**Problem.** `showCustomIconPickerDialog` (`ui/list/apps/ContextMenuActions.kt`) calls
`iconManager.getAvailableIcons(context)` synchronously before showing the dialog. That
method calls `pm.getActivityInfo()` for **every** entry in the pack's appfilter, which is
often 2,000 to 10,000 entries, so the UI freezes or hits an ANR. The adapter then calls
`getDrawableByName` (→ `getIdentifier` + decode) on the main thread for each bound item, and
filtering lowercases every label on each keystroke.

**Fix.**
1. Show the dialog immediately with a progress indicator in place of the recycler.
2. Load `getAvailableIcons` in `(context as? LifecycleOwner)?.lifecycleScope` on
   `Dispatchers.IO`. If the context isn't a `LifecycleOwner`, fall back to a plain scope that
   is cancelled in `dialog.setOnDismissListener`.
3. In `getAvailableIcons`, stop calling `getActivityInfo` per entry. Use the label from the
   already-loaded app list where available
   (`(context.applicationContext as Application).apps.value`), otherwise use the drawable
   name.
4. Pre-compute lowercase search keys once. Filter on a background dispatcher (or at least
   avoid reallocating per keystroke).
5. Load item drawables asynchronously, or at minimum rely on the `resId` cache from G8.

**Acceptance.** With a large icon pack installed (for example Arcticons), the picker opens
instantly, shows a spinner, then the grid. No "app not responding" dialog appears.

### G10. Show the app list before every icon is resolved

**Problem.** `getApps()` (`Functions.kt`) builds a `DetailedAppInfo` for every launcher
activity, and the constructor resolves the icon eagerly: either the icon pack lookup or
`activityInfo.getBadgedIcon(0)`, which decodes a bitmap. Nothing is posted until all icons
are loaded. On a cold start with many apps, the drawer is empty or stale for a noticeable
time.

**Fix (needs design care; keep the public API of `AbstractDetailedAppInfo` stable).**
1. Make the icon in `DetailedAppInfo` lazily resolved and cached. Store the
   `LauncherActivityInfo` (or enough to reload it) and resolve on first `getIcon()`. Use a
   `@Volatile` cached field.
2. In `getApps()`, post the list with labels first. Then, on the same background job,
   pre-warm icons for the first ~50 apps in sort order and post again (or notify through a
   lightweight `MutableLiveData<Unit>` "icons updated" signal).
3. Make sure the adapter (G3) treats an icon change as a content change.
4. Ensure `getIcon()` called on the main thread for a not-yet-loaded icon is still correct.
   It can load synchronously as a fallback; that is no worse than today.

**Acceptance.** On a cold start (`adb shell am force-stop` and then open the drawer), labels
appear immediately and icons fill in without layout jumps. Steady-state behaviour is
unchanged.

---

## Widgets

### W1. Update widget views incrementally instead of tearing them down

**Problem.** `WidgetContainerView.updateWidgets()` (`ui/widgets/WidgetContainerView.kt`)
removes **every** widget view and calls `createView()` for every widget again. It is called:
- on every `HomeActivity.onResume()`, which is every return to the home screen
- on every change to the `widgets` preference, including changes to widgets on *other*
  panels
- in `ManageWidgetsActivity` on every resume and every preference change, which in edit
  mode happens on every finished drag

Re-creating an `AppWidgetHostView` makes the widget flash its loading layout, loses
internal state (scroll position in list widgets, for example), and costs a binder
round-trip per widget. This is the biggest source of jank on the home screen.

**Fix.**
1. In `updateWidgets`, compute the target set for this panel
   (`widgets.filter { it.panelId == widgetPanelId }`).
2. Remove the views whose widget id is no longer in the target set.
3. For widgets that already have a view, update `(view.layoutParams as LayoutParams).position`
   if it changed. Fix z-order by re-ordering children only if the `zIndex` order changed
   (`bringChildToFront` in sorted order, or remove and re-add just that view).
4. Call `createView` only for new ids.
5. Call `requestLayout()` once at the end.
6. In `HomeActivity.onResume()`, keep calling `updateWidgets`; it's cheap now.
7. `WidgetManagerView.updateWidgets` must do the same for its overlay views. Keep the
   overlay of each widget above its widget view.
8. Keep `synchronized(widgetViewById)`. Note that this only runs on the main thread anyway.

**Acceptance.**
- Add a list widget (for example a calendar agenda), scroll it, go to another app and
  back: the scroll position is kept and there is no loading flash.
- Moving a widget in edit mode does not re-create the other widgets (log `createView`
  calls).
- Adding, removing, raising and lowering widgets still work.

### W2. Cache widgets in memory; no JSON parsing on touch

**Problem.** `LauncherPreferences.widgets().widgets()` deserialises every widget from JSON
on **every** call (`SetWidgetSerializer` in `preferences/serialization/PreferenceSerializers.kt`).
It is called on hot paths:
- `WidgetContainerView.onInterceptTouchEvent` → `Widget.byId(...)` for each widget under
  the finger, on **every touch event** on the home screen, including swipe gestures
- `WidgetOverlayView.widgetId` setter, `showPopupMenu`, `WidgetPanel.getWidgets()`,
  `generateInternalId()`

`Widget.byId` even has `// TODO: do some caching`.

**Fix.**
1. Add a small in-memory cache, for example `object WidgetCache` in `widgets/`, holding
   `Map<Int, Widget>`. Populate it lazily from `LauncherPreferences.widgets().widgets()`.
2. Invalidate it in a `SharedPreferences` listener registered once from `Application`
   (`onCreate`) when the key equals `LauncherPreferences.widgets().keys().widgets()`.
3. Implement `Widget.byId` and `WidgetPanel.getWidgets()` on top of the cache.
4. In `WidgetContainerView.onInterceptTouchEvent`, only do work on `ACTION_DOWN`. Remember
   whether the down event hit a non-interactive widget and return that for the rest of the
   gesture. Look up `allowInteraction` from the cache, not from preferences.
5. Do **not** hand out mutable cached instances that callers then modify in place. Today
   callers mutate `widget.position` and then call `updateWidget(widget)`. Either return
   copies, or make sure every mutation is followed by `updateWidget()`, which invalidates
   the cache.

**Acceptance.** A swipe on the home screen over a widget triggers no
`Json.decodeFromString` calls (check with a breakpoint or log). The interaction on/off
toggle still takes effect immediately.

### W3. Keep the hosted widget's reported size in sync

**Problem.** `AppWidget.createView()` (`widgets/AppWidget.kt`) calls `updateAppWidgetSize`
once, using `resources.displayMetrics` width and height, and only on API 31+. As a result:
- After a resize in edit mode, the provider is never told the new size. Responsive widgets
  keep rendering the old layout until the view is re-created (which W1 will make rarer).
- Sizes are based on the full display, but the container may be a different size
  (system bars, rotation, split screen).
- On API < 31, no size options are sent at all, so many widgets fall back to their
  minimum layout.

**Fix.**
1. Add `fun AppWidget.updateSize(view: AppWidgetHostView, containerWidth: Int, containerHeight: Int)`.
   - Compute the absolute rect via `position.getAbsoluteRect(containerWidth, containerHeight)`.
   - Convert to dp.
   - API 31+: call `view.updateAppWidgetSize(Bundle.EMPTY, listOf(SizeF(w, h)))`.
   - Older API levels: call the deprecated
     `updateAppWidgetSize(null, minW, minH, maxW, maxH)` with `w, h, w, h`, or build the
     options `Bundle` with `OPTION_APPWIDGET_MIN_WIDTH` and the related keys, and call
     `appWidgetManager.updateAppWidgetOptions(id, bundle)`.
2. Call it from `WidgetContainerView.onLayout` for each `AppWidgetHostView` child whose
   rect changed since the last call. Track the last size per id to avoid spamming the
   provider.
3. Remove the size call from `createView()`, since `onLayout` now covers it.

**Acceptance.** Add a responsive widget (for example the Google search bar or a weather
widget). Resizing it in edit mode switches its layout immediately on release. Rotating the
device updates the layout.

### W4. Edit-mode ergonomics: dp handles, cancel handling, small widgets

**Problem.** In `ui/widgets/manage/WidgetOverlayView.kt` and `WidgetManagerView.kt`:
- `HANDLE_SIZE = 100` is in **pixels**. Handles are huge on low-density screens and tiny on
  high-density ones. On a small widget (under ~240 px), the edge handles have negative
  length and you cannot grab them, so the widget becomes move-only.
- `WidgetManagerView.onTouchEvent` handles `DOWN`, `MOVE` and `UP`, but not
  `ACTION_CANCEL`. If the system cancels the gesture (for example a notification-shade
  pull or the back gesture), the long-press runnable may still fire, the selected handle
  stays highlighted, and the position is never saved or reverted.
- The interaction-disabled label uses a fixed `textSize = 60f` px and is not clipped, so on
  small widgets it overflows.
- The minimum size during resize is hard-coded as `2 * screen / GRID_SIZE + 5`, while
  `WidgetPosition.fromAbsoluteRect` clamps width and height to at least 2 cells. These two
  should come from one constant.

**Fix.**
1. Convert `HANDLE_SIZE`, `HANDLE_EDGE_SIZE` and the settings icon sizes to dp, multiplying
   by `resources.displayMetrics.density` at runtime (for example 32 dp handles, 40 dp
   corner exclusion, 28 dp icon).
2. In `getHandles()`, shrink the handles proportionally when
   `width < 3 * HANDLE_EDGE_SIZE` or `height < 3 * HANDLE_EDGE_SIZE`, so each edge always
   keeps a grabbable area.
3. Handle `ACTION_CANCEL` in `WidgetManagerView.onTouchEvent`: restore the layout params of
   the selected overlay and widget view to `startWidgetPosition` (converted back to a
   `WidgetPosition`), call `requestLayout()`, then `endInteraction()`.
4. Set the label text size in sp. If the text is wider than the widget, scale it down to
   fit or hide it.
5. Introduce `const val MIN_WIDGET_CELLS: Short = 2` in `widgets/WidgetPosition.kt` and use
   it in both places.

**Acceptance.** On a 2×2-cell widget, all four edge handles can be dragged. Starting a drag
and then pulling down the notification shade leaves the widget in its original place. The
handles look the same physical size on a phone and a tablet.

### W5. Placement and lifecycle robustness

**Problem.** Several small bugs make adding and removing widgets unreliable:
1. `WidgetPosition.findFreeSpace` (`widgets/WidgetPosition.kt`) has three problems:
   - It loops `x in 0..<GRID_SIZE - minWidth`, which skips the last valid column and row.
     Use `0..GRID_SIZE - minWidth`.
   - It scans column-major (x outer, y inner), so new widgets fill top-to-bottom in the
     left column first. Users expect row-major.
   - When nothing fits, it returns `(0,0)`, overlapping the existing widgets with no
     feedback.
2. The clock widget is always added at a fixed `WidgetPosition(0, 4, 12, 3)` in
   `SelectWidgetActivity.tryBindWidget`, ignoring `findFreeSpace`.
3. `ManageWidgetsActivity.configureWidget` does `data.extras!!`, which crashes if a
   provider returns an empty result intent.
4. If a widget's provider app is uninstalled, `AppWidget.createView()` returns `null` (see
   the TODO there). The widget stays in preferences forever: it is invisible on the home
   screen, still occupies space for `findFreeSpace`, and still has an overlay in edit mode
   with no content.
5. `WidgetOverlayView.showPopupMenu` reconfigure uses request code `100` (`// TODO: use real
   request code`), which `ManageWidgetsActivity.onActivityResult` does not handle.
   `REQUEST_RECONFIGURE_APPWIDGET` (3) already exists.

**Fix.**
1. Fix the loop bounds, scan row-major (y outer), and when nothing fits, return a position
   offset from the last widget or show a toast ("No free space on this panel; placed on
   top").
2. Use `findFreeSpace(WidgetPanel.byId(widgetPanelId), 12, 3)` for the clock widget.
3. Replace `extras!!` with a null-safe early return.
4. Orphaned widgets:
   - In `WidgetManagerView`, give overlays of widgets whose `createView` returned `null` a
     visible placeholder. Draw the text "Widget unavailable" with the stored
     `packageName`; the string must go in `res/values/strings.xml`.
   - The overlay's popup menu already offers remove.
   - Do **not** auto-delete on the home screen: the app might be temporarily unavailable
     (SD card, work profile paused).
5. Use `REQUEST_RECONFIGURE_APPWIDGET` for reconfigure. No result handling is needed beyond
   ignoring it, but it must not collide with other codes.

**Acceptance.**
- Fill a panel with widgets and add another: no silent overlap.
- On an empty panel, the first widget goes top-left and the second goes to its right.
- Uninstall an app whose widget is placed: edit mode shows a labelled placeholder that can
  be removed.
- Adding a clock widget to a panel that already has one doesn't stack them.

---

## Performance

These are lower-level optimisations. Most of them don't change behaviour; they reduce work
on hot paths.

### P1. Read icon preferences once per app load, not once per app

**Problem.** `getApps()` (`Functions.kt`) constructs a `DetailedAppInfo` per launcher
activity. Each one calls `IconPackManager.getIcon(appInfo, componentName)`
(`icons/IconPackManager.kt`), which on **every call** reads:
- `LauncherPreferences.theme().iconPack()`
- `LauncherPreferences.apps().customIcons()`, which deserialises the whole custom-icon map
  from JSON (`MapAbstractAppInfoStringPreferenceSerializer`)

With N apps and M custom icons, one app load costs N × M JSON decodes. The fallback path
in `getApps()` calls `IconPackManager.getInstance(context)` inside the loop as well.

**Fix.**
1. Add an overload
   `getIcon(appInfo, componentName, customIcons: Map<AbstractAppInfo, String>?)` that does
   not read preferences.
2. In `getApps()`, read `iconPack()` once (calling `setIconPack` if needed) and read
   `customIcons()` once. Pass the snapshot through the `DetailedAppInfo` constructor to the
   new overload. Hoist `IconPackManager.getInstance` out of the fallback loop.
3. Keep the old `getIcon` signature for other callers (`DetailedAppInfo.fromAppInfo`); it
   can delegate to the new one.

**Acceptance.** Set 5+ custom icons, then compare the `"apps loaded (Xms)"` log line before
and after. The new time should be lower and the icons identical.

### P2. Cache decoded gesture actions

**Problem.** `Action.forGesture(gesture)` (`actions/Action.kt`) reads the preference string
and runs `Json.decodeFromString` on every call. It is called from:
- `HomeActivity.updateSettingsFallbackButtonVisibility()`, on every `onResume`, for all
  ~40 gestures
- `AppFilter` with "hide bound apps", on every search keystroke (see also G4)
- every gesture invocation
- `removeUnusedShortcuts()` at startup

**Fix.**
1. Add a `ConcurrentHashMap<String, Action?>` cache in `Action.Companion`.
   `forGesture` returns the cached value when present, otherwise decodes and stores it. Use
   a sentinel or `containsKey` so that cached `null` values also count as hits.
2. Invalidate entries in `setActionForGesture`, `clearActionForGesture` and
   `resetToDefaultActions`.
3. Because preferences can also change through import or reset, register a
   `SharedPreferences` listener once in `Application.onCreate` that removes `key` from the
   cache when `Gesture.byId(key) != null`, and clears everything when `key == null`
   (preferences cleared).

**Acceptance.** Binding, unbinding and resetting gestures take effect immediately.
Returning to the home screen performs no `Json.decodeFromString` calls for actions (check
with a breakpoint).

### P3. Load widget picker icons and previews lazily

**Problem.** `SelectWidgetActivity.SelectWidgetRecyclerAdapter` calls
`getAppWidgetProviders()` in a property initialiser on the main thread. For **every**
installed widget provider, the `LauncherAppWidgetProvider` constructor
(`widgets/LauncherWidgetProvider.kt`) eagerly calls `loadIcon`, `getUserBadgedIcon` and
`loadPreviewImage`, which decodes a bitmap, and `LauncherApps.getActivityList`, which is a
binder call. With 50 to 100 providers, opening "Add widget" stalls visibly.

**Fix.**
1. Make `icon` and `previewImage` lazily loaded: store the `AppWidgetProviderInfo` and a
   `Context`, and turn the properties into `by lazy`. The clock provider can keep its eager
   values.
2. Load the provider list in `lifecycleScope.launch(Dispatchers.IO)` and show an empty
   list or progress state until it arrives.
3. In `onBindViewHolder`, if the preview isn't loaded yet, set a placeholder and load it on
   `Dispatchers.IO`. Post the result back only if the holder still shows the same provider:
   compare `bindingAdapterPosition`, or keep a tag on the view.
4. Search and sort must keep working on labels only, without needing icons.

**Acceptance.** "Add widget" opens instantly. Previews fill in while scrolling. Search and
sort still work.

### P4. Drop unused preview decoding in widget edit mode

**Problem.** In `ui/widgets/manage/WidgetOverlayView.kt`, the `widgetId` setter calls
`it.getPreview(context)`. That decodes the provider's preview image at high density
(`AppWidget.getPreview`). It runs for every overlay, every time
`WidgetManagerView.updateWidgets` runs, which is on every resume and every preference
change. The preview is never drawn: the drawing code in `onDraw` is commented out.

**Fix.** Remove the `preview` field and the `getPreview` call from `WidgetOverlayView`.
Keep `Widget.getPreview` itself; `SelectWidgetActivity` and others may use it. If
`isConfigurable` / `isReconfigurable` / `getIcon` are called together in hot paths, fetch
`getAppWidgetInfo(id)` once and reuse it.

**Acceptance.** Edit mode looks and behaves the same. No preview bitmaps are decoded when
entering it.

### P5. Move shortcut cleanup off the startup path

**Problem.** `Application.onCreate()` calls `removeUnusedShortcuts(this)` (`Functions.kt`) on
the main thread. It queries pinned shortcuts for every user profile, which is binder work,
decodes every gesture action, and calls `pinShortcuts`. That runs on every process start,
including after the system kills the launcher in the background, and it delays the first
frame of the home screen.

**Fix.** Run `removeUnusedShortcuts` on a background dispatcher, for example the
application scope from G7, or a one-off `CoroutineScope(Dispatchers.IO).launch` if G7 has
not landed. The function only reads preferences and talks to `LauncherApps`, so it doesn't
need the main thread. Leave `migratePreferencesToNewVersion` and `resetPreferences` on
the main thread: later code depends on them having finished.

**Acceptance.** Shortcuts not bound to any gesture are still unpinned. Cold start (for
example `adb shell am start -W` of the home activity after `am force-stop`) reports a
shorter `TotalTime`.

### P6. Release build: Baseline Profile and resource shrinking

**Problem.** `app/build.gradle.kts` enables R8 (`isMinifyEnabled = true`) but:
- does not enable `isShrinkResources`
- ships no Baseline Profile

Without a profile, the home screen, gesture detector and app drawer run interpreted or
JIT-compiled for the first several launches after every install or update. That is
exactly when the launcher feels sluggish.

**Fix.**
1. Add `isShrinkResources = true` to the `release` build type. Build the release APK and
   check it still starts. Verify that resources referenced only by name aren't stripped.
   For example, `R.string` keys used through the preferences library might be. Add a
   `res/raw/keep.xml` with `tools:keep` if needed.
2. Add `androidx.profileinstaller:profileinstaller` as a dependency. It is small and needed
   for sideloaded and F-Droid installs to apply profiles.
3. Add a `:baselineprofile` module using the `androidx.baselineprofile` Gradle plugin, with
   one generator test that:
   - starts the launcher as home
   - opens the app drawer with a swipe-up gesture
   - types in search
   - scrolls the list
   - opens settings
   Commit the generated `baseline-prof.txt` to `app/src/main/`.
4. Check the F-Droid / reproducible build constraints in `docs/build.md` before adding
   plugins. If a new plugin is a problem there, do step 1 and step 2 with a hand-written
   `baseline-prof.txt` covering `HomeActivity`, `TouchGestureDetector`, `AppListActivity`,
   `AppsRecyclerAdapter` and `WidgetContainerView`.

**Acceptance.** `./gradlew assembleDefaultRelease` succeeds. The APK size doesn't grow.
Compare cold start and drawer-open frame times with a macrobenchmark, or with
`adb shell am start -W` plus `dumpsys gfxinfo`, before and after.
