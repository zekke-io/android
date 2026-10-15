# `app` — the activity, the session's services and the shell

The application module: `MainActivity`, the dependency injection (`di/CoreModule`), the services
that implement each feature's interface over the core, and the shell the unlocked account lives in.

## The shell

The unlocked account opens on **Home**: the sections this phone holds as `AppTile`s (a phone without
the `files` scope has no Drive tile), *Lock*, the notifications bell and Settings. The design system's
Home is a tile grid with a top bar on each section, not a tab bar, so the "tabs" of the plan are
tiles; a section is a page with a back button, its title and *Lock*. `BackHandler` goes back to Home
(from Notifications, to where it was opened).

| Page | Holds |
| --- | --- |
| Home | The marker *Your account is open on this phone.*, the version and session notices (an unverified chain, a re-wrap that failed), the tiles, *Lock*, the bell with the unread count as a badge (capped at 99+) |
| Passwords, Vault, Notes | How many items the replica holds, then the sync status. The lists arrive with A4 |
| Drive | The sync status; Drive has no count, because telling a thumbnail from a file needs the manifests decrypted |
| Settings | The notifications entry with the unread count, the outbox's refused changes with *Discard*, then the account-security section and *Remove this phone* |
| Notifications | `feature:settings`' list, opened from Home or Settings |

Counts and statuses are read from the replica without decrypting anything, and refresh when the feed
reports a change.

## Sync — `SyncService`

One service for the process, opened when the session unlocks and closed when it locks. It never calls
`session.touch()`: background work must not keep a lock from happening.

| When | What |
| --- | --- |
| Unlock | The outbox is flushed, then every held scope is pulled (the replica fills before any screen asks) |
| The app returns to the foreground | One pull, and polling starts |
| While the app is visible | A pull every 30 seconds, doubling to 120 when nothing changed, back to 30 when something did |
| The app goes to the background, or the session locks | Polling stops |
| After an own write | `syncNow()` (the producers arrive with A4) |

A pull is: flush the outbox, pull each held scope, read the unread count of notifications. Each
failure is kept for the screens: **offline**, **rate limited** (with the server's wait), **session
over**, or **failed**; none is hidden. No `holdProcess()` is needed: a pull only reads, and an outbox
entry replayed twice is the server's to refuse as it would any retry.

The outbox shows three numbers: entries waiting to be sent, entries waiting for this phone's keys to
catch up after a rotation (they are re-wrapped as the account's items are), and the **refused**
ones, listed in Settings with *Discard*. The item re-wrap pass stays the unlock ViewModel's job.

`SyncBackend` is what the service needs from the core (`CoreSyncBackend`: `Replica`, `Outbox`,
`Feed`); `SyncSession` is what it needs from the keystore. Both are small interfaces so
`SyncServiceTest` runs without the native library.

## Leaving the account

| Event | Replica | Outbox |
| --- | --- | --- |
| *Remove this phone*, *I forgot this phone's PIN* | Deleted | Deleted |
| Unlock finds the phone forgotten or removed from another device | Deleted | Kept: the unsent writes are the person's and the core discards them if another account signs in |

## Tests

| Test | What |
| --- | --- |
| `SyncServiceTest` | Opening and closing with the session; the first deep pull; polling's 30-second pace, its doubling and its stop in the background and on a lock; a pull on returning to the foreground; every failure kept; the unread count kept when it cannot be read; the outbox counted by state and a refusal discarded; a change relayed and counted; leaving the account wipes as above |
| `ShellTest` (Robolectric) | Home keeps its marker, the notices and *Lock*; only held sections have a tile; a section's count, its singular and empty wording, the sync failure and the outbox counts; *Back* and the page's *Lock*; the bell and Settings both reach Notifications and *Back* returns to where it was opened; the bell names the unread count and shows it as a badge, capped at 99+, and none when nothing is unread |
| Existing `AppRootTest`, `ClientGateTest`, `SessionGuardTest`, `ClientPolicyMonitorTest`, `CoreThisPhoneServicesTest`, … | The session, the update gate and the removal |
