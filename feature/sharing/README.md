# `feature:sharing` — connections, the Shared space and sending

Sharing between accounts, drawn the way the web app draws it and with its words (*this phone* for *this
browser*). The module reaches the core only through `SharingServices`, which `app` implements
(`CoreSharingServices`, over one `SharingService` that forgets everything at each lock), so the screens
and the ViewModels are tested with a fake and no network. **Passwords cannot be shared**: the core's
sharable types are secrets, notes and files, and a document is not opened by the core.

The **Shared** tile on Home opens this module. It needs at least one of the vault, notes or files
scopes. A segmented control switches between *Shared* (the web's Shared section) and *Connections*
(the web's Settings → Sharing tab); a phone without the `sharing` scope has no *Connections*, as the web
hides that tab, and sees only the flat *Shared* space.

## Connections

The page reads the connections **when it enters composition** (`ConnectionsViewModel.opened()`), not when
the ViewModel is created: the ViewModel lives with the activity until a lock, so a page opened again
must not show the list it left. Opening also clears the last invitation notice and refusal.

| Part | Behaviour |
| --- | --- |
| *Invite someone* | A username, sent with the keyboard's *Send* or the button. The one sentence on re-sharing (*Anything you send can be copied by the person you send it to…*) sits here and nowhere else |
| *Waiting for you* | An invitation sent to this account, with *Review* |
| *Connected* | Accepted connections and the invitations this account sent (*Waiting for them*). A row with a nickname shows the nickname and, under it, the username |
| A row's sheet | The nickname (sealed in the address book, only this account sees it) and, on a full device, *Disconnect* behind a confirmation that says every share between the two stops |
| *Review* | The other person's fingerprint, in mono, and this account's own to read back, six groups of four. **The phone never marks them compared on its own**: *It matches — accept* is the person's statement. It stays disabled while the fingerprint loads, when it could not be read, and while any alarm shows. *Decline* disconnects |

### The danger states

Every accepted connection and every invitation this account sent is checked against its pin on each
load (`checkTrust`); an invitation waiting for this account is checked in its review. A lookup that
fails shows nothing on the row. The result is a `TrustAlarm`:

| Core trust | Alarm | On the row |
| --- | --- | --- |
| `Trusted`, `Unpinned` | none | nothing |
| `RootChanged` | danger, a new invitation is the way back | *Do not send* and the web's sentence |
| `ProofInvalid` | danger | *Do not send* and the web's sentence |
| `AccountChanged` | danger, a new invitation | *Do not send*, the sentence and why the connection is gone |
| `Unresolvable` | warning | the sentence only |

Sending is refused by the core whatever the screen says (`ConnectionNotTrustedException`), so no screen
can skip the check.

## The Shared space

One folder per accepted connection, named after the other person (their nickname if there is one), opened
from *Shared*. Inside, **both directions together**: what they sent and what this account sent (*Sent by
you*), in the folders **either** person made. The page reads when it enters composition, re-establishes
the outbound connections silently, and a slow read of another friendship never lands in the one open now.

| Part | Behaviour |
| --- | --- |
| The list | Folders first, then items as rows: the name (a secret's, a note's first line, a file's), its kind and who it came from. The device's held scopes decide the kinds shown, documents included: a document is listed and says it cannot be opened here yet |
| Navigating | A breadcrumb (*Shared › friend › folders*) and the system back, which steps item → folder → parent → friendships |
| Folders | *New folder* in the open folder, rename and delete from a folder's menu, *Move to a folder* on an item. **Deleting a folder removes the grouping only**: what was filed in it, and in its subfolders, falls to the top and stays shared, so it needs no full device. A folder move is not built |
| An item | Text opens in a sheet. A secret's value is masked until *Show value*. *Copy to my own account* is offered for what arrived and whose scope this phone holds, never for a document or for what this account sent; the copy is re-encrypted under a fresh key. A file is **saved through the system picker** (the only way a file leaves, with the warning that the saved copy is not encrypted), its own *Sent by you* files included |
| Copy refusals | A file over 64 MiB (the core copies in memory) says to download it instead; a full storage says there is no room; both are typed, as is every other failure |
| A phone without `sharing` | Lists only what arrived (`listInbox`), with no folders, and says why |
| A manifest that fails validation | Everything is listed at the top, with *Reset the folders*, which empties them for both people and unshares nothing |

Image and video preview, and moving a folder, are not built.

## Services

| Call | Contract |
| --- | --- |
| `loadConnections()` | The connections with their nickname and standing (`Accepted`, `WaitingForMe`, `WaitingForThem`) |
| `checkTrust(ids)` | Each connection's alarm; an id whose lookup failed is absent |
| `catchUp(ids)` | Re-establishes the outbound connections whose counterparty rotated its sharing keys, silently, one failure never stopping the next |
| `invite(username)` | `Sent`, `UnknownUsername` (also a username that cannot exist), `AlreadyConnected`, `NotTrusted(alarm)` or a typed failure |
| `review(id)` | Their fingerprint (`null` when the account no longer resolves), this account's, and the alarm |
| `accept(id)`, `disconnect(id)`, `rename(id, name)` | Writes, each with the process held until it ends |
| `loadSpace(id)` | The items of one friendship with their folders: both directions with `sharing`, only what arrived without it, the invalid manifest reported rather than thrown |
| `editFolders(id, edit)`, `resetFolders(id)` | `Done` with the new manifest, `Refused(problem)` for a refused edit (worded as the web words it), or a typed failure; a kept-changing conflict is one `Conflict` |
| `copyToMyAccount(id, item)` | `Copied` (and the sections sync), `TooLarge`, `NoRoom` or a typed failure |
| `saveTo(id, item, target)` | Streams the file into the target and discards the target on failure; the picker is announced to the session guard |

## Tests

| Test | What |
| --- | --- |
| `SharingPolicyTest` | Every core trust state is an alarm or none; which alarms are danger; the standing of a record; the groups; the fingerprint of each trust |
| `ConnectionsViewModelTest` | The load, the trust check and the silent catch-up; a failed check shown as nothing; an older check never overwriting a newer load; one invitation at a time; every refusal; the review waiting for the fingerprint and blocked by every alarm; accepting and declining and their failures; the nickname; the disconnect confirmation; a limited device; an unexpected exception |
| `SharedPolicyTest` | What can be copied and downloaded; which folder an item is in, including after its folder is deleted; the folder choices with their depth; the friendships by name |
| `SharedViewModelTest` | The friendships and the silent catch-up; a slow read of another friendship ignored; back stepping item, folder, parent, friendships; creating, renaming and deleting folders and every refusal; one edit at a time; moving an item; the flat and the invalid space; every copy outcome; saving and discarding the target; the picker announced |
| `SharedScreensTest` (Robolectric) | The friendships and their empty state; both directions with *Sent by you*; a secret masked then shown and copied; a note in full; a document that cannot be opened; a file's download and its warning; an unreadable item; a folder created from the keyboard, opened and left; the delete confirmation's words; moving; the flat space; the reset; the host's two sections and the missing one |
| `ConnectionsScreensTest` (Robolectric) | A page left and opened again shows what changed meanwhile and no old notice; the invite form and the keyboard's *Send*; each refusal's words; *Do not send* and each alarm's sentence; the review and its disabled accept; accepting and declining; the nickname; the disconnect confirmation; a limited device offered no disconnect |
