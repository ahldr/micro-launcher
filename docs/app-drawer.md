+++
  weight = 10
+++

# App Drawer

Apps that are not needed all the time are shown in the app drawer.
There are several such drawers, but the basic concept is the same.
Besides regular apps, app drawers also show [pinned shortcuts](https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts)[^1].
&mu;Launcher treats apps and shortcuts in the same way.

The idea of the app drawer is to search for apps using the keyboard.
By default[^2], an app is launched automatically once it is the only app matching the query.
This can be prevented by typing a space.
Usually, only two or three characters are needed, which is much faster than scrolling to find an app.

[^1]: A pinned shortcut is created, for example, when pinning a website to the home screen.
[^2]: There are [several settings](/docs/settings/#functionality) available to modify the behavior.

When long-pressing an app, additional options are shown:
* Rename the app
* Add to / remove from Favorites: Adds the app to the [Favorite Apps](#favorite-apps) list or removes it from there.
* Category (only for favorites): Chooses the [category](#categories) of the app.
* Hide / Show: This hides the app from all drawers (except from [Hidden Apps](#hidden-apps)) or makes it visible again if it was hidden.
* App Info: Opens the system settings page for the app.
* Uninstall: Tries to uninstall the app or remove the shortcut.

Below these options, the app's shortcuts (e.g. *New tab* for a browser) are listed.
Tapping a shortcut launches it, long pressing it allows to [bind it to a gesture](/docs/actions-and-gestures/#available-actions).

## All Apps

This lists all apps except hidden apps.
By default, it is bound to `Swipe up`.

## Favorite Apps

Only shows favorite apps.
Pressing the star icon on the bottom right of any app drawer toggles whether only favorite apps should be shown.
Additionally, the `Favorite Apps` action can be used to launch this drawer directly.
By default, it is bound to `Swipe up (left edge)`.

The favorites can have [a layout of their own](/docs/settings/#layout-of-favorites),
e.g. only icons, and [their own grid](/docs/settings/#customize-grid-1).

### Categories

Favorite apps can be grouped into categories, e.g. Messaging or Media,
which are shown under headings in the order chosen in [the settings](/docs/settings/#categories).
Favorites that are in no category are shown last.

To put an app into a category, long-press it and choose the category icon.
Long-pressing a heading allows to choose the apps of the category, rename it or delete it.
Deleting a category doesn't remove its apps from the favorites.

## Private Space

When [private space](/docs/profiles/#private-space) is available, this drawer
shows only apps from the private space.
It can be opened using the `Private Space` action.
If the private space is locked, instead of showing the list, the unlock dialog is shown.

By default, apps from the private space are shown in All Apps as well; however, this is [configurable](/docs/settings/#hide-private-space-from-app-list).

## Hidden Apps

This list shows hidden apps.
It is only accessible through the settings.
The feature is intended to be used only for apps that are not needed at all but [can not be uninstalled](https://en.wikipedia.org/wiki/Software_bloat#Bloatware).
