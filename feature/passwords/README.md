# `feature:passwords` — the password list and everything done to a credential

The Passwords section: the list a phone draws from its replica, *Add a password*, editing (an append
of a new revision), deleting, *Recently deleted* with restore and permanent deletion, and *Previous
passwords*. The module reaches the core only through `PasswordsServices`, which `app` implements
(`CorePasswordsServices`), so the screens and the ViewModel are tested with a fake and no network.

## What the screen does

| Action | Behaviour |
| --- | --- |
| The list | Each credential's newest revision, sorted by site then username, as the core's `passwordsView` builds it from the replica: the site as its host (`siteLabel`), the username under it, the password **never shown** in the list. A credential not written by this app shows as *Unreadable credential* and cannot be opened |
| Copy | The icon on a row copies the password through `SensitiveClipboard` (marked sensitive, cleared after 30 seconds) and says so |
| Search | Filters in the ViewModel by site, its host, username, note and the other addresses, ignoring case; its own message when nothing matches |
| Add / edit | One sheet: *Site*, *Username*, *Password* (masked until *Show*), *Note*, *Also used on* (comma separated) and *Offer it on* (any address of the same site, or only these exact addresses). The keyboard's *Next* walks the fields in that order through their own focus requesters. Site, username and note are trimmed; **the password never is**. Editing starts from the credential's own payload, so fields this app does not show (the extension's, kept in `extra`) survive the new revision |
| Previous passwords | *Show previous passwords* in the edit sheet lists the older revisions with a copy button, loaded on demand |
| Delete | A confirmation that says the password disappears from every device, can be restored from *Recently deleted*, and until it is deleted permanently **from a full device** when this phone is not one |
| Recently deleted | Restore (a new revision of the last live one); *Delete permanently* and *Delete all permanently* only on a **full device**, each behind the web app's confirmation |

Every write and read says why it failed in the web app's words (`ItemFailure`: offline, rate limited,
signed out, session renewed, keys changed while saving, too large, scope missing, gone, failed). A
session that ended is **renewed** by `app` and the person is told to try again, as the web app does.

## Where the password lives

The ViewModel's state holds each credential's password as a `String`, because the list draws from
the replica and a sheet must show it on demand; it is dropped when the session locks (the session's
ViewModels are cleared). The form's draft is the same. The clipboard is the only place the password
leaves the screen, and it clears itself.

## What `PasswordsServices` promises

| Call | Contract |
| --- | --- |
| `load()` | The core's `passwordsView` over the replica, and whether the scope has finished its first pull (until then an empty list is *Loading*, not *No passwords saved yet*) |
| `save(payload, credentialId)` | One `POST /credentials` with a fresh revision id; an edit passes the credential's id. The write runs to its end even when the app leaves the foreground |
| `delete(id)` | The device-signed delete of the credential, history kept |
| `restore(deleted)`, `purge(deleted)` | Restore appends the last live revision again; purge erases every revision, then `app` resets the passwords scope of the replica and pulls it again, because the server writes no feed tombstone for a prune. **A purge made on another device still reaches this phone only when its replica is reset** (a known gap, described in the core's credentials README) |
| `previous(id)` | The older, undeleted revisions, newest first, opened one by one; an unreadable one is left out |
| `changes` | Emits when the feed changed the passwords scope; the ViewModel reloads |

Writes go **straight to the server** and say so when offline, as the web app does: nothing is queued,
so nothing is saved that the person was not told about. The core can also queue a credential in the
outbox (`queueCredential`); it is not used until the lists can show a pending item.

## Tests

| Test | What |
| --- | --- |
| `PasswordsViewModelTest` | The first load and its synced flag; a failure and a throwing service; a reload on a feed change; search over every field; add: trimming, the password kept as typed, the addresses split and trimmed; edit keeping `extra` and `match` and writing to the same credential; an unreadable credential not opened; a failed save keeping the form; no double save and no closing while saving; delete with its question, its failure, and closing that password's sheet; restore; purge needing a full device and a confirmation; the previous passwords and their failure |
| `PasswordsScreensTest` (Robolectric) | The rows without their passwords; the empty list; search and its empty result; a failure's words; the add button enabled only when complete; the keyboard's Next through the five fields; editing and the delete confirmation with *Keep it*; the deleted page with restore and a confirmed purge; no permanent deletion on a device that is not full |
