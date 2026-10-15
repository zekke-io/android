# `feature:vault` — secrets and their tabs

The Vault section: secrets as a name and a value, filed in tabs ([`feature:tabs`](../tabs/README.md)),
the value masked until revealed, copy through the sensitive clipboard, *Recently deleted*. The module
reaches the core only through `VaultServices`, which `app` implements (`CoreVaultServices`).

## What the screen does

| Action | Behaviour |
| --- | --- |
| The list | The open tab's secrets, newest first: the name and the value masked as ••••••••. One toggle for the whole section, *Show values* / *Hide values*, reveals them; it is cleared when the session locks. A secret that cannot be opened is *Unreadable item* and cannot be opened |
| Copy | The icon on a row copies the value through `SensitiveClipboard` and says so |
| Add | *Add a secret*: a name (trimmed) and a value (kept as typed); the keyboard's Next goes from the name to the value. While a tab other than the main one is open the new secret is **filed in it** |
| A row's sheet | *Copy value*, *Move to “…”* for each other tab (when there are two or more), and *Delete* on a full device. There is no editing of a value: the web app has none |
| Delete | Only on a full device, after the web app's question; the secret goes to Recently deleted |
| Recently deleted | Restore; *Delete permanently* and *Delete all permanently* only on a full device, each confirmed. A purge forgets the secrets' placements |
| Tabs | Create, rename, delete and the reset of invalid tabs, as `feature:tabs` describes; deleting a tab deletes its secrets through `VaultServices.delete` first |

A purged secret leaves a feed tombstone, so a normal pull removes it from the replica of every phone.

## What `VaultServices` promises

| Call | Contract |
| --- | --- |
| `load()` | The core's `vaultView` over the replica and whether the scope has finished its first pull |
| `create(payload)` | One `POST /secrets` with a fresh id, which it returns so the screen can file it |
| `delete(ids)`, `restore(id)`, `purge(ids)` | The signed batch delete, the restore, the signed purge; each runs to its end even when the app leaves the foreground |
| `canDelete` | Whether this device is a full device |
| `changes` | Emits when the feed changed the secrets scope, which includes the tabs' manifest |

Writes go straight to the server and say so when offline, as in Passwords.

## Tests

| Test | What |
| --- | --- |
| `VaultViewModelTest` | The first load, values hidden and the toggle; a load failure and a reload on a change; add needing a name and a value and trimming only the name; filing in the open tab and not in the main one; a failed add keeping the form and no double submit; opening a row, not an unreadable one; moving; delete needing a full device and a confirmation, and its failure; a tab's items deleted through the services; restore and purge, the placements forgotten, a failed purge |
| `VaultScreensTest` (Robolectric) | Names shown and values masked until the toggle; the empty vault; the main tab; the add button enabled only when complete and Next to the value; a row's sheet and the delete question; no delete on a device that is not full; the deleted page with restore and a confirmed purge |

## Sharing

The screen takes an optional `onShare` slot; `app` provides it and hosts the share sheet of [`feature:sharing`](../sharing/README.md). `VaultScreensTest` covers: the optional *share* slot: absent without it, and it hands the secret to the host and closes the detail.
