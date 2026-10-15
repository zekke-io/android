# Zekke for Android — tasks

The tasks that build the Android app, in order. Each milestone (A0 to A8) waits for nothing in the
Kotlin core any more: `mobile-base` has every core milestone in code. A task is done when its
"done when" holds, its module's `README.md` says what it does, and nothing in it is commented out.

States: `[ ]` open, `[~]` under way, `[x]` done.

## Decisions the tasks assume

These are open questions with a default. A task that depends on one says so; none blocks starting.

| #  | Question | Default the tasks use | Must be decided before |
| -- | -------- | --------------------- | ---------------------- |
| R1 | The application id and the Kotlin package root, permanent after the first upload to Play | `zekke.android`, provisional | The first upload to Play (A8.6) |
| R2 | The app's languages | The web app's language only, every string in resources from the start | — |
| R3 | The PDF preview's temporary plaintext file | Accepted, deleted on close and on lock | A5.6 |
| O1 | Offline unlock | Online unlock only | The first version's release |
| O2 | Biometric unlock | PIN only | — |
| O3 | A grace period before locking in the background | Lock immediately | — |
| P1 | Selling plans on Android | No purchase and no link in the app | — |

**Outside the app, before production**: HTTPS and HSTS on the API host (the workspace's Task 132),
and an `android` row in the API's `client_releases` (A8.4).

---

## A0 — Foundation

- [x] **A0.1 The project.** A Gradle project at `android/` with an `app` module: one activity,
  Jetpack Compose, `mobile-base` through a composite build, every version pinned in
  `gradle/libs.versions.toml`, `minSdk` 28 and the current `targetSdk`. From the first build:
  `FLAG_SECURE` on the activity, no backup (`allowBackup="false"` and data-extraction rules that
  exclude everything), HTTPS only except the debug build's local API, R8 stripping `android.util.Log`
  in release, and the `Zekke-Client: android/<version>` identity from the version name.
  *Done when* the debug and release APKs build in the toolchain container, the release one minified,
  and the debug one starts on the emulator and draws its first screen.
  **Done 2026-10-09**: both variants build, the release one minified to 2.4 MB with the core's native
  library for the three ABIs; on an Android 14 emulator the debug app starts, its first screen's text
  is in the accessibility tree, and a screenshot comes back empty, which is `FLAG_SECURE` at work.
  The app is restricted to the ABIs the core builds, after the first build showed AndroidX bringing
  an `x86` directory the core has no library for.
- [x] **A0.2 Dependency verification and CI.** `gradle/verification-metadata.xml` with a SHA-256
  for every artifact; a CI workflow that builds both variants, runs the unit tests and the lint.
  *Done when* a changed artifact fails the build.
  **Done 2026-10-09**: 543 components verified, the core's and the plugins' included; with one
  checksum changed the build stops with `Dependency verification failed … activity-1.13.0.aar`.
  Lint treats warnings as errors, except the checks that only report newer versions. The workflow
  checks out `mobile-base` beside the app; it has not run yet, since neither is a repository.
- [x] **A0.3 The modules.** `ui`, `platform`, and `feature/…` modules, each with its README; Koin
  wiring the core's services (`ZekkeApi`, `SessionKeystore`, `AccountServices`, the replica, the
  outbox, the object store) once, in `app`.
  **Done 2026-10-09**: `ui` (the theme), `platform` (the device vault binding and the no-backup
  databases), eight `feature` modules (`onboarding` holds the start screen, the others wait for
  their tasks), and `coreModule` in `app`. `CoreModuleTest` passes on an Android 14 emulator: every
  binding resolves with the real native library, one API and one session are shared, and the
  replica and the outbox open in the no-backup directory. The device vault was the core's
  in-memory one until A0.5.
- [x] **A0.4 The design system.** `ui`: the web app's tokens (colours, type scale, shapes) as a
  Material 3 theme, light and dark; `PinField`, `SecretField` (masked until revealed),
  `CopyButton` (the clip marked sensitive, cleared after a timeout), and the private-text modifiers
  every secret-bearing field uses (no personalised learning, no suggestions, not important for
  autofill). *Done when* Compose UI tests pin the masking, the clip's sensitivity and the clearing.
  **Done 2026-10-09**: the light palette from the web app's `globals.css` (its `DESIGN.md` header
  is not what it uses), a dark palette derived from it, Inter and JetBrains Mono bundled, the
  components and the sensitive clipboard (cleared by an alarm, so it survives the lock ending the
  process). The whole window is excluded from autofill. `ComponentsTest`, `PaletteContrastTest`
  and the screenshots of both themes pass.
  - [x] **The dark palette ships** (2026-10-10, the owner's call: shipped as derived, to be refined
    later). The window theme follows the system setting too.
  - [x] **The rest of the kit.** The design system's remaining tokens (`brandMark`, `brandSoft`,
    the action gradient, `focusRing`, `scrim`, the folder's shine and edge), the outline icons and
    the feature marks as vector drawables, and the components the screens will need: icon buttons,
    the floating add button and menu, `Notice`, `EmptyState`, `LoadingIndicator`, `StorageMeter`,
    `Block`, `ItemRow`, `AppTile`, `ConfirmDeleteDialog`, `FormSheet`, the accent button and the
    neutral badge. **Done 2026-10-10**: `KitTest`, the 3:1 pairs in `PaletteContrastTest` and the
    `components-*.png` screenshots pass, the existing gallery screenshots are unchanged, and lint
    is clean.
  - [ ] **`verification-metadata.xml` is missing `groovy-bom-4.0.29.pom`**: `:ui:lintDebug` stops
    on it when the lint tool is resolved online (it passed with `--dependency-verification lenient`).
    Regenerate the file as the README says and review the diff.
- [x] **A0.5 The device vault.** `platform`: replacing the provisional `MemoryDeviceVault` binding,
  the core's `DeviceVault` backed by an AES-256-GCM key in
  the Android Keystore, StrongBox where present, `setUnlockedDeviceRequired(true)`, never
  exportable, the record in the app's no-backup storage. *Done when* instrumented tests store, read
  and forget a record, and a record copied to another install does not open.
  **Done 2026-10-09**: `KeystoreDeviceVault` is the binding. The emulator showed two things the
  plan did not say: the Keystore cannot create an unlocked-device-required key on a phone with no
  secure lock screen, so the requirement applies only when there is one; and a read while the phone
  is locked must not be taken for a foreign record, so it raises `DeviceVaultUnavailableException`
  and keeps the file. Both are tested, with a real lock and unlock on the emulator.
  - [x] **A1.4 maps `DeviceVaultUnavailableException`** to "unlock the phone first" on the PIN screen.
  - [ ] **A8.2 checks `keyLocation`** is the trusted environment or StrongBox on a real phone.

- [x] **A0.6 The session's lifecycle.** The app observes the core's session state; a lock clears
  every screen's state and navigates to the unlock screen. Locking on leaving the foreground
  (`ProcessLifecycleOwner`), on the screen turning off, after the idle timeout; the exception for a
  system picker the app launched, with a maximum; ending the process after a lock unless an upload
  runs. *Done when* Robolectric tests cover each trigger and the picker exception.
  **Done 2026-10-09**: `SessionGuard` and `AppRoot` in `app`; 9 Robolectric tests for the triggers
  (leaving the foreground, the screen-off broadcast, the idle lock in and out of the foreground,
  user activity), the picker exception and its maximum, and a transfer keeping the process; one
  for a lock clearing every ViewModel and remembered value. On the emulator, Home ends the process
  and the app reopens in a new one.
  - [ ] **A5.2 calls `pickerWillOpen()`** before every picker and `transferStarted()` /
    `transferFinished()` around every upload.

## A1 — Onboarding and unlock

- [x] **A1.1 Creating an account.** Start, the mode (Standard or Paranoid, the one-way sentence
  before the PIN), the PIN (twice for Paranoid, the account PIN becoming this phone's), creating,
  then the recovery kit. *Done when* an account created on the emulator against the local API opens
  in the web app with its phrase.
  **Done 2026-10-10**: `feature:onboarding` and `CoreOnboardingServices` in `app`; 11 ViewModel
  tests and 4 Robolectric screen tests. On the emulator against the local API, a Paranoid account
  with 24 words was created through the screens (a weak PIN refused first), its phrase read from the
  screen, *Lock* and a relaunch opened on the locked screen, and the web app's own `lib/account`
  entered the account with that phrase and PIN, found the same username and Paranoid mode, and
  deleted it. A1.2 turned the recovery step into the kit step.
- [x] **A1.2 The recovery kit.** The web app's page from the core's content, the phrase as vector
  outlines and the QR code holding the bare phrase, printed through the print framework or saved
  through the system picker; the vault stays shut until it was saved or printed once. The phrase can
  be revealed, never copied.
  **Done 2026-10-10**: the content is the core's new `recoverykit` package (ported from the web
  app, its glyph outlines checked against the web app's); the page is drawn with `PdfDocument`, the
  QR code by zxing's encoder; *Save as PDF* through the system picker and *Print* through the print
  framework. **What a lock does on the kit step was decided here**: the onboarding holds the
  process and the phrase from creating until the kit is saved, so leaving the app or the idle lock
  no longer loses the phrase; if the process ends anyway, the app says so once on its next start. On
  the emulator: the saved PDF's text layer holds no word of the phrase, its QR code decodes to the
  phrase, a cancelled print keeps the vault shut, and every account opened in the web app with its
  phrase.
- [x] **A1.3 Adding this phone with the phrase.** One field per word, an in-app BIP39 suggestion row,
  the checksum before the PIN step, the account PIN on a Paranoid account (one message for every
  refusal), `TooManyDevices` listing the devices to choose from.
  **Done 2026-10-10**: `EnrolmentScreens.kt` and the enrolment half of `OnboardingViewModel`, over the
  core's `enrolThisDevice` and its new `mnemonicWordSuggestions`; `ui` gained `CheckboxRow` and the
  keyboard options of `PrivateField`. The process is held while the phone is added; no account for
  the phrase has its own message; after two refused account PINs the screen suggests waiting. 13
  ViewModel and 4 Robolectric tests, the keyboard's *Next* among them. On the emulator against the
  local API, typing with the keyboard: a Standard account at its 20-device limit, the phone added by
  removing one device it chose (gone on the server afterwards), and a Paranoid account with 24 words
  refusing a wrong account PIN twice, then adding the phone with the right one.
- [x] **A1.4 Unlock.** Six boxes submitted on the sixth digit; every outcome worded as the web app
  words it (wrong PIN with attempts left, registration gone, offline, rate limited); the chain
  verified after unlocking and a problem shown, not hidden; `rewrapStale` after unlocking.
  **Done 2026-10-10**: Core outcomes map to the locked screen, stale device-keyring wraps are retried
  after unlock, and chain or rewrap problems remain visible in the unlocked shell. All 16 module
  unit/Robolectric tests and the unlock Compose test on the API 37 AVD pass. Core interop and the
  web app account-library E2E both unlocked a temporary account against the local API; the temporary
  account was deleted.
- [x] **A1.5 Removing this phone and the client policy.** Log out (no rotation); the
  `UPGRADE_REQUIRED` screen and the update notice from `GET /clients/android/policy`.
  **Done 2026-10-14**: *Remove this phone* signs a self removal with no rotation, holding the
  process and surviving the lock it causes; the client policy is read at start, after 15 minutes
  and after any `426`, failing open; the update screen replaces every other screen, and the
  notices for a deprecated or available version show on the unlock and home screens. Tests: app 34,
  unlock 43, onboarding 37, ui 15, mobile-base 303. On an API 34 emulator against the local API: no
  policy shows nothing, the removal is cancelled then done (3 devices, then 2, and the start screen
  after a relaunch), the deprecated and available notices show, a required version shows the update
  screen naming 0.2.0, a `426` in the middle of a removal refuses it (the phone stays a device), and
  the temporary account and the policy row were removed afterwards. The same run passed on an API 37
  emulator once the debug build's local-network permission was granted. Decisions taken:
  - the update screen says to update where the app was installed from, with no button, until the
    store's wording exists;
  - an update-available notice is shown and can be dismissed for the rest of the process;
  - a phone the server already removed finishes locally and shows the start screen;
  - the core gained `ZekkeApi.upgradeRequired`, raised by any `426`, so any call can reach the
    update screen;
  - a version that becomes unsupported locks the session at once, except while the onboarding
    creates the account or shows the recovery kit;
  - *Remove this phone* sits on the unlocked holding screen until Settings exists.
  - [ ] A7.1 moves *Remove this phone* into Settings → Devices.
  - [ ] A7.3 moves the version notices into About, with the version.
  - [ ] A8.6 words the update screen for the store.
  - [ ] The core's `ZekkeApi.upgradeRequired` must be committed and pushed in `mobile-base` before the app's commit (CI checks out `mobile-base` `main`).
  - [x] Debug builds could not reach the local API on an API 37 emulator: Android 17 blocks
    local-network addresses (`10.0.2.2`) for apps targeting SDK 37 that do not hold
    `android.permission.ACCESS_LOCAL_NETWORK`. The debug manifest declares it; it is a runtime
    permission, granted on test emulators with `adb shell pm grant`. The end-to-end run passed on
    API 34 and API 37. The release build does not declare it.

## A2 — The phrase's other ways in

- [~] **A2.1 Scanning the kit.** CameraX and zxing-cpp, frames decoded on the phone and kept
  nowhere; the bare phrase accepted with stray space or case.
  **Built 2026-10-14, not closed**: `ui` gained `PhraseScanSheet`, `QrScanner` and `QrDecoder` (CameraX
  1.6.2 and zxing's Java decoder, the library the kit's encoder already uses, instead of zxing-cpp:
  no native library to build and verify; the scanner lives in `ui` because onboarding and Settings
  both use it), the `CAMERA` permission and its optional features in the manifest, and the shared
  `PhraseEntry` the scan fills. *Proven*: the decoder against QR images made with zxing's encoder (a
  12 and a 24-word kit, a camera row with padding, a code turned on its side, a small code in a large
  frame, nothing from a blank frame or another kind of code); a scanned text accepted in any case
  and spacing and a URL refused; the permission path on an API 34 emulator against the local API
  (the camera explained first, the system dialog, the scanner opening); the manifest in both APKs
  (`aapt2`). *Not proven*: **reading a code from a live camera.** The emulator's virtual scene never
  produced a read in three poster setups, and it is not known whether the poster renders there.
  - [ ] **A2.1 live scan on a real phone** (with A8.2's arm64 phone): a 12 and a 24-word kit read by
    the camera, the fields filled, the checksum passing. Until then the camera path is untested.
- [x] **A2.2 "I lost my other devices".** The enrolment that removes them in the same batch, with
  the warning, then the re-wrap.
  **Done 2026-10-14**: *I lost my other devices: remove them all* on the PIN step (`DeviceRemoval.All`,
  the web app's warning, a danger button), replacing the choice at the device limit; the removal
  rotates what the other devices held, so `app` runs the re-wrap as soon as the session shows
  (`KeyRotationEvents`, the unlock's `rewrapNow()`), which also fixes the removal chosen at the limit
  (A1.3), which did not re-wrap. `ui` gained the shared `PhraseEntry` (the 12 or 24 words, the
  suggestions, the keyboard's Next, the scan) that onboarding now uses, and `rateLimitedMessage`.
  Tests: ui 50, onboarding 40, unlock 44, settings 25, app 54 (`DeviceEnrolmentTest` among them). On the
  emulator against the local API: an account with three browsers and a secret made before; the phone
  added with the box ticked left only itself on the server; the secret moved from key generation 1 to 2
  and still opened.
- [x] **A2.3 The Paranoid flows.** Turning Paranoid on with the phrase after the one-way warning;
  rotating the account PIN; both say the other full devices are signed out, and re-wrap.
  **Done 2026-10-14**: `feature/settings` (account security: *Turn on Paranoid mode…* and *Change the
  account PIN…* in a sheet, with the web app's wording, on the unlocked home screen until Settings
  exists: A7.2) over the core's `turnOnParanoid` and `changeAccountPin`, with the process held and the
  call run to its end. Typed outcomes for the wrong phrase, a refused PIN (one message, the web
  app's advice to wait after two tries, counted across reopenings), offline, rate limited, a locked
  phone, a chain that does not verify, and a Paranoid mode that was turned on but did not finish. On
  the emulator against the local API: Paranoid turned on (the account Paranoid on the server, one
  browser signed out and the notice saying so, the web entering only with the account PIN, the phone
  unlocking with it); two wrong current PINs then the right one (the wait advice only after the second,
  the new PIN accepted by the web and the phone, the old one refused, one browser signed out); a secret
  made before each rotation moved to the new key generation (1 to 3, 2 to 3, 3 to 4) and still opened.
  - [ ] **A2.3 a change that fails half-way**: turning on is followed by a read of the account's mode to
    tell whether the server applied it (`ParanoidOnUnfinished`); changing the PIN has no such read, so a
    failure after the PIN rotated leaves the person told only that it failed. Decide whether it needs
    one when Settings is built (A7.2).

**Decided by default during A2 and A3, awaiting the owner's confirmation**: the scanner reads with zxing's Java
decoder (plan: zxing-cpp); it lives in `ui` (plan: `platform`) and is offered wherever the phrase is
typed; the account's security section sits in Settings (it was on the home screen);
`feature/settings` keeps its own copy of the PIN-problem wording (onboarding's is not shareable between
feature modules) to be consolidated.

## A3 — The replica and the shell

- [~] **A3.1 The databases.** The replica and the outbox on the Android SQLite driver, in no-backup
  storage; the core's replica and outbox tests run on the emulator.
  **Built 2026-10-14; awaiting the owner on the wording**: `LocalDatabases` opens both on `AndroidSqliteDriver` in `noBackupFilesDir` and
  can delete the replica alone or both (`deleteReplica`, `deleteAll`). The core's own replica and
  outbox tests stay `jvmTest` (JDBC driver); on the emulator `LocalDatabasesTest` proves the same
  behaviours on the Android driver: the files in no-backup storage, a replica surviving a reopen, another
  account finding it empty, the outbox keeping its entries, and the two deletions. `CoreModuleTest`
  opens both through Koin. A phone that leaves the account on purpose (*Remove this phone*, *I forgot
  this phone's PIN*) loses both; one that loses its registration (forgotten, removed from another
  device) loses only the replica, so unsent writes survive a new enrolment of the same account. The
  task says the core's own tests run on the emulator: they still run on the JVM only, and the
  instrumented test above stands in for them.
- [~] **A3.2 The shell.** Passwords, Vault, Notes and Drive as tabs, Shared and Settings reached
  from them; screens draw from the replica, then catch up.
  **Built 2026-10-14**: Home with a tile for each scope the phone holds, the section pages (their
  title, back, *Lock*, the item count read from the replica without decrypting, the sync status) and
  Settings (notifications, the outbox's refused changes, the account-security section, *Remove this
  phone*). The design system's Home is a tile grid with a top bar, not tabs ("a TabView is not the
  pattern"), so the plan's tabs are tiles: **awaiting the owner**. Missing: *Shared* (no tile until the
  sharing screens exist), a Drive count (a thumbnail is only told from a file by decrypting the
  manifests), and the lists themselves (A4 and A5). The Home keeps *Your account is open on this
  phone.* and *Lock*; the account-security section and *Remove this phone* moved to Settings, where
  A7.2 will place them.
- [~] **A3.3 Sync.** At unlock, on returning to the foreground, after each own write, and polling
  while a screen is visible; the outbox flushed, re-wrapped at unlock, its refusals shown.
  **Built 2026-10-14**: `SyncService` in `app`: at unlock the outbox is flushed and every held scope
  pulled; on returning to the foreground one pull and the polling (30 seconds, doubling to 120 when
  nothing changed); polling stops in the background and on a lock; each failure (offline, rate limited,
  session over, failed) is kept and shown; refused outbox entries are listed in Settings with *Discard*;
  the replica is wiped on leaving the account as in A3.1. On the emulator against the local API: a
  secret made on the web appeared on the open Vault within one poll; one made while the phone was
  locked appeared after the unlock; a third appeared on the open Vault without any action. Missing:
  *after each own write* is the `syncNow()` hook, with no writer to call it until A4; the outbox's
  re-wrap at unlock needs an entry body re-wrapped, which no core helper does and no entry exists to
  need until A4. The stop after a lock is proven by `SyncServiceTest` and by the process ending when
  the app leaves the foreground; the local API logs no request paths, so it is not observed on the wire.
- [x] **A3.4 Notifications.** The in-app list, worded by kind; an unknown kind as a generic notice.
  **Done 2026-10-14**: `feature/settings` lists the notices in the web app's words (the plan, dates in
  the phone's locale and zone, sizes, counts, a relative age), marks the unread ones it shows as read
  and offers *Show older*; an unknown kind is *Account notice*; a bell on Home and an entry in Settings
  carry the unread count. The local API refuses a kind it does not know, so the generic notice is
  proven by `NotificationCopyTest`, not on the wire. On the emulator against the local API: two notices
  pushed through billing's route showed, worded and marked *Unread*, within one poll; reading them
  cleared the count, and it stayed cleared after a lock and an unlock (read on the server).
  A second pair of notices pushed later showed on a second visit, with the bell's badge and its label
  naming the count. Tests: app 81, settings 47 (`NotificationCopyTest`, `NotificationsViewModelTest`),
  ui 50, onboarding 40, unlock 44; instrumented `LocalDatabasesTest` 5 and `CoreModuleTest` 2; the end
  to end script 54 checks, the A2 script (Settings now) 53, the A1.5 script 63 of 65: its two
  failures are the old expectation that the removal call meets the 426 first, which now arrives
  earlier (the policy block replaces the app before the person taps), so that path is covered by
  `CoreThisPhoneServicesTest` only.

## A4 — Passwords, Vault and Notes

- [ ] **A4.1 Passwords.** Search, view, copy, add, edit (an append), delete, Recently deleted with
  restore and purge, Previous passwords.
- [ ] **A4.2 Vault.** The tabs, secrets as name and value, the value masked until revealed, copy with
  clearing, Recently deleted, restore, purge, tab edits.
- [ ] **A4.3 Notes.** The spaces, the grid of tiles, the WYSIWYG editor over the core's note format,
  autosave after two seconds, the character counter.
- [ ] **A4.4 Against the web app.** Every item written here reads in the web app and the reverse.

## A5 — Drive

- [ ] **A5.1 Browsing.** Folders to eight levels, grid and list, thumbnails, usage against the quota.
- [ ] **A5.2 Picking and uploading.** The photo picker and the Storage Access Framework, no storage
  permission; the upload as a user-initiated data transfer (a `dataSync` foreground service below
  Android 14) with a notification naming nothing; a `429` pauses, `507` is never retried.
- [ ] **A5.3 Resuming.** The URI's read permission persisted only while its upload is unfinished; the
  core's same-source check before a byte is sent; a lock pauses, the next unlock resumes.
- [ ] **A5.4 Thumbnails.** 320 px, JPEG 0.72, HEIF included, uploaded after the parent, never failing
  the upload.
- [ ] **A5.5 The sealed-object cache.** The core's `SealedObjectCache` on disk, ciphertext only,
  pruned against every listing.
- [ ] **A5.6 Preview and export.** Images from memory (Coil with no disk cache); PDFs through
  `PdfRenderer` with the temporary file deleted on close and on lock (R3); "Save to device" and
  "Open with…" saying the copy leaves Zekke's protection.
- [ ] **A5.7 Rename, move, delete and the Trash.**

## A6 — Sharing and the extension

- [ ] **A6.1 Connections.** Inviting, accepting with the fingerprint, nicknames, disconnecting, and
  the danger states saying *do not send*.
- [ ] **A6.2 The Shared space.** One folder per friendship, both directions, the friendship's
  folders, opening, *Copy to my account*, re-establishing in the background.
- [ ] **A6.3 Sending.** From each tab, trust checked first, filed into a folder in the same gesture.
- [ ] **A6.4 Linking the password extension.** The code, the compared fingerprint, the link, and a
  mismatch cancelling with no retry. Checked by hand with the extension.

## A7 — Settings

- [ ] **A7.1 Devices.** The list with labels from the address book, removing another device with the
  phrase and the re-wrap, renaming a device.
- [ ] **A7.2 Account.** The username, the mode, deleting the account with the phrase (and the account
  PIN on Paranoid) with `retention_days` stated.
- [ ] **A7.3 Plan, security and about.** The plan read-only, the idle timeout, changing the device
  PIN, the version and the client policy.

## A8 — The first release

- [ ] **A8.1 Low-end measurements.** The time of an unlock, a 1 GiB upload's memory, the first pull
  of a large account, on a real low-end phone and a recent one; recorded in the README.
- [ ] **A8.2 The core on an arm64 phone.** The core's tests on a physical device.
- [ ] **A8.3 The security checklist.** Every item of "what the phone must never leak", the manifest's
  permissions, the network config, the backup rules, the dependency list.
- [ ] **A8.4 The client policy row.** An `android` row in `client_releases` before the first release.
- [ ] **A8.5 Reproducible builds.** Our own upload and signing keys, each release's SHA-256
  published.
- [ ] **A8.6 The store listing.** Requires R1 decided.
