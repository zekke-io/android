# `feature:tabs` — the tabs of Vault and the spaces of Notes

The folder manifest of a scope, shown as a row of tabs: the main tab, the ones the person made, how
many items each holds, and the open one. Vault (`ManifestScope.SECRETS`) and Notes
(`ManifestScope.NOTES`) share it, so it is one module. The module reaches the core only through
`FolderTabsServices`, which `app` implements (`CoreFolderTabsServices`), so the controller and its
dialogs are tested with a fake and no network.

## What the person can do

| Action | Behaviour |
| --- | --- |
| Open a tab | The section's items are filtered to the ones filed in it; an item never filed, or filed in a deleted tab, counts in the main tab (`itemsInTab`) |
| New tab | A sheet with the name; refused before it is sent when empty, over 120 characters, or the same as another tab's (ignoring case): *There is already a tab called “Work”.* The new tab opens |
| Rename | A long press on a tab, then *Rename*; the tab's own name is allowed, another's is not. The main tab can be renamed |
| Delete | A long press, then *Delete tab*. The main tab cannot be deleted. A tab with items deletes **the items first**, then the tab and where it filed them, in one edit; only a full device may delete items, otherwise the dialog says to move them out first. An empty tab is deleted alone |
| Move an item | `move(ids, tab)` writes the placement; the section offers it from an item's sheet |
| File | `file(id, tab)` places a new item in the open tab (nothing is written for the main tab) |
| Forget | `forget(ids)` drops the placement of items that were purged |
| Reset | When the stored tabs do not validate (nine reasons, worded as the web app words them) everything is listed in one place, and *Reset the tabs* keeps every item and forgets only the tabs |

A failure is an `ItemFailure` shown above the tabs. A tab edit that lost a race with another device
is retried by the core's `FolderStore` and, if it keeps losing, says *That changed on another device
while it was being saved. Nothing was saved. Try again.*

## Where the names live

`FolderStore` caches the decrypted manifests, **tab names included**, so `app` opens it with the
sync backend at unlock and closes it at lock (`SyncService.folderStore()`); it is never a Koin single.
The controller holds only the manifest it was given, and is owned by the section's ViewModel, so a
lock clears it with the rest.

## Tests

| Test | What |
| --- | --- |
| `FolderTabsControllerTest` | Tabs with home first and their counts; an invalid manifest and its reset; a load failure; a reload on a feed change; the open tab and its fallbacks; the filter; every name problem; renaming; creating opening the tab; a refused name never sent; a failed edit keeping the dialog; the refusals to delete (main tab, not a full device); deleting a tab's items first, then the tab; the items' failure keeping the tab; an empty tab; move, file and forget; the management choice |
