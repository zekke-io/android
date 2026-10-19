# Zekke for Android — tasks

The tasks that build the Android app, in order. Each milestone (A0 to A8) waits for nothing in the
Kotlin core any more: `mobile-base` has every core milestone in code. A task is done when its
"done when" holds, its module's `README.md` says what it does, and nothing in it is commented out.

States: `[ ]` open, `[~]` under way, `[x]` done.


## Awaiting the owner

Decisions taken by default and not yet confirmed; each is recorded where it was taken.

- **The new Zekke design (orange, Nunito, mascot) is applied to the theme, the shared components, the
  sign-up steps, the lock screen and the Home header.** Home is redrawn too (greeting header, *Your apps* grid, a bottom bar of Home, Notifications and Settings; no *What shall we do now?* row, no recent activity). The Passwords, Vault, Notes and Drive lists are redrawn too (card rows, letter avatars, ink tab pills, note cards, the storage meter in a card, pill add buttons; Drive keeps its round add menu, and the search field and *Recently deleted* button stay where they are). Not yet redrawn: the enrolment PIN step, the
  recovery step, the item sheets and the Shared, Settings and Notifications screens.
  Deviations from the design, taken by default: no *Paste* button on sign-in (the phrase never passes a
  clipboard; the word fields and the QR scan stay); no *Search*, *Activity* or *Soon* tiles (they do not
  exist in the app); *Continue* on the recovery step still waits for the kit to be saved, not for a
  checkbox; the dark focus ring is #A99BFF, not #6B4CF0, for contrast; the storage meter's stored fill is
  #C2410C, for 3:1 on its track; the feature marks are an orange gradient. Not looked at on a device.
- **A7.2's wrong-account-PIN refusal on a device.** Deleting the account with a wrong PIN left the account
  intact but showed no notice in five emulator runs (the instrumentation could see no text, the log no
  error); the unit tests show the notice. Worth one look by hand on a Paranoid throwaway account.
- **A8.1, A8.2, A8.4, A8.5, A8.6** (the first release): the measurements and the arm64 run need real phones,
  the `client_releases` row is production SQL, the keys are the owner's and the listing waits for R1; each is
  described under A8.
- **The idle-lock choices** (1, 5, 15, 30, 60 minutes; 15 by default) are this app's own: the web app has no
  such setting to copy (A7.3).
- **A6.4 by hand with the real extension.** The linking is verified end to end against a scripted extension
  (same API calls and fingerprint construction); linking the phone to the actual browser extension build,
  once, is left to the owner.
- **Home tiles instead of tabs** (the design system's Home is a tile grid, A3.2).
- **The A3.1 substitute**: the core's own replica and outbox tests still run on the JVM only; an
  instrumented test on the Android driver stands in for them.
- **The A2 defaults**: zxing's Java decoder instead of zxing-cpp; the scanner in `ui`; the account's
  security section in Settings; `feature/settings`' own PIN-problem wording.
- **Writes go straight to the server, not through the outbox** the plan describes (Passwords, Vault,
  Drive); Notes drafts are the one thing queued, at the moment of a lock.
- **New shared pieces**: `feature:tabs`, and in `ui` `TabStrip`, `TopBar`, `NoteSurface`, `PageTile`,
  `FileTile`, `ItemFailure`. `../design-system` is **not in any git repository**: `TopBar.md`,
  `Tabs.md`, `NoteSurface.md`, `PageTile.md` and `FileTile.md` exist only on disk.
- **The file-kind glyphs** are not in the design system's assets, so every file shows the Drive glyph.
- **Coil replaced by `ImageDecoder`** for image previews (no new dependency).
- **Open with…** leaves one plaintext copy under `noBackupFilesDir/shared` until the next process start,
  because the other app may read it after the lock (R3's tension).
- **The background-upload contradiction**: the plan says both that uploads run in the background and that
  a lock pauses them; leaving the app locks it, so no service can keep an upload going. No foreground
  service or job was built (A5.2 is `[~]`).
- **A note draft whose save failed offline is lost at the idle lock** (the 15-minute lock comes from the
  core's timer, which the pre-lock flush does not see).
- **Not built**: multi-select in the Drive and Notes grids; opening documents (the core does not read them).
- **Soft-keyboard typing in Notes** is unproven (the end-to-end scripts inject text); a real phone is
  needed (A8.2).

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
  - [x] **A5.2 calls `pickerWillOpen()`** before every picker and `transferStarted()` /
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
  pattern"), so the plan's tabs are tiles: **awaiting the owner**. Missing: a Drive count (a thumbnail is only told from a file by decrypting the
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
  *after each own write* is `syncNow()`, called by the passwords writes; the outbox is re-wrapped at
  unlock, though no screen queues into it yet. The stop after a lock is proven by `SyncServiceTest` and by the process ending when
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

- [x] **A4.1 Passwords.** Search, view, copy, add, edit (an append), delete, Recently deleted with
  restore and purge, Previous passwords.
  **Done 2026-10-15**: `feature:passwords` (the list from the replica, search, copy through the sensitive
  clipboard, one sheet to add and edit with the keyboard's Next through its five fields, delete behind
  the web app's question, Recently deleted with restore and permanent deletion on a full device,
  Previous passwords) over the core's credential calls, with `ItemFailure` words shared in `ui`, a
  `TopBar` moved into `ui`, and a session renewed after a `401`. Edits start from the credential's own
  payload, so the extension's fields survive. On the emulator against the local API (54 checks): a
  credential made on the web shows without its password; one added on the phone with the keyboard's
  Enter opens on the web; an edit is a second revision there and keeps the field the phone does not
  show; delete, restore and purge each read from the web. Writes go straight to the server, not
  through the outbox (so *offline* is said, never silently queued); the phone's own purge resets the
  passwords scope of its replica, because the server writes no feed tombstone for a prune, so **a purge
  made on another device still reaches the phone only when its replica is reset** (the core's known
  gap). Also finished here: the outbox is re-wrapped at the first pull after an unlock
  (`rewrapStaleEntries`), a cancelled pull is rethrown, and the deep pull no longer holds the unlock
  collector. Tests: passwords 25, app 83, ui 50, settings 47, onboarding 40, unlock 44.
- [x] **A4.2 Vault.** The tabs, secrets as name and value, the value masked until revealed, copy with
  clearing, Recently deleted, restore, purge, tab edits.
  **Done 2026-10-15**: `feature:vault` (secrets as a name and a masked value with one *Show values*
  toggle, copy through the sensitive clipboard, add with the keyboard's Next, a secret added while a
  tab is open filed in it, a sheet to move or delete, Recently deleted with restore and purge; delete
  and purge only on a full device) and the new `feature:tabs` shared with Notes (new, rename, delete
  a tab with its items deleted first, the main tab undeletable, a reset for invalid tabs, the web
  app's name checks and nine reasons), over the core's `FolderStore`, which `app` opens with the sync
  backend and closes at lock because it caches the tab names. `mobile-base` gained `folderTabs`,
  `activeTabOf` and `itemsInTab` (tests 307). `ui` gained `TabStrip` and `ItemFailure.Conflict`.
  On the emulator against the local API (78 checks): a tab and a secret made on the web show on the
  phone; a tab and a secret made on the phone open on the web with the secret filed in the tab;
  rename, move, delete, restore, purge and deleting a tab with its secrets each read from the web.
  Tests: vault 20, tabs 17, passwords 25, app 83, ui 50, settings 47, onboarding 40, unlock 44.
  **Awaiting the owner**: the new `feature:tabs` module and `ui` `TabStrip`; writes go straight to the
  server instead of through the outbox the plan describes (offline is said, never silently queued).
- [x] **A4.3 Notes.** The spaces, the grid of tiles, the WYSIWYG editor over the core's note format,
  autosave after two seconds, the character counter.
  **Done 2026-10-15**: `feature:notes` (the grid of `PageTile`s over the replica, the tab strip of
  `feature:tabs` for the spaces, the editor, delete on a full device), `ui` `NoteSurface` (one private
  text field for the whole note, so a selection can span lines and a split or a merge of lines is an
  ordinary text change; bullets and checkboxes drawn in a gutter) and `PageTile`, and in `mobile-base`
  the editing model `NoteDocument` (a type, a tick and a style per character; edits found by common
  prefix and suffix; bold, italic and size over a range; the line commands; Enter on an empty topic or
  task ends the list), 20 tests. Autosave two seconds after the last change, one save at a time, a
  counter, the web app's labels. **Leaving the app**: `SessionGuard.onBeforeLock` lets
  `CoreNotesServices` seal the unsaved text into the outbox and hold the process until it is sent, so
  what was typed in the last two seconds is not lost. On the emulator against the local API (51
  checks): a note made on the web (a title, a topic, a done task, bold, a size run) shows with no
  markers; bold, two size steps and a ticked checklist made on the phone read on the web as
  `- [x] {{18}}**hello world**{{/}}`; a note made while a tab is open is filed there; text typed
  one second before leaving the app is on the web after the next unlock; delete reads from the web.
  **Unverified**: typing on a real soft keyboard (the end-to-end scripts inject text and key events, which
  bypass the keyboard's composition); the note editor keeps the keyboard's composition range, but only
  a person typing on a phone can prove it (A8.2). Not built: the web app's multi-select delete from the grid; a formatting key with an empty
  selection (bold and italic do nothing, size applies to the line). A note whose first save is in
  flight at the moment of leaving can lose what was typed after it. Tests: notes 28, vault 20, tabs 17,
  passwords 25, app 87, ui 50, settings 47, onboarding 40, unlock 44; core 327.
- [x] **A4.4 Against the web app.** Every item written here reads in the web app and the reverse.
  **Done 2026-10-15**: each of the three end-to-end scripts reads what the phone wrote through the
  web app's own libraries and the reverse (a credential with the extension's extra field kept through a
  phone edit; secrets and notes filed in tabs; the note markers byte for byte), 54 + 78 + 51 checks.

## A5 — Drive

- [x] **A5.1 Browsing.** Folders to eight levels, grid and list, thumbnails, usage against the quota.
  **Done 2026-10-15**: `feature:drive` (folders and files from the replica, a path of buttons and *Up one
  folder*, grid or list, a file's true size or why it cannot be opened, the thumbnail drawn in its tile
  and never listed as a file, the storage meter with what unfinished uploads hold) and `ui` `FileTile`.
  The file-kind glyphs are not in the design system's assets, so every file shows the Drive glyph and a
  kind label is not drawn: **awaiting the owner**. On the emulator against the local API (68 checks):
  a folder, a file inside it and a photo with a thumbnail made on the web show on the phone, the
  thumbnail file is not listed, and the thumbnail was fetched and cached as ciphertext. The app blocks
  screenshots, so the thumbnail's picture is proven by the cache, not by pixels.
- [~] **A5.2 Picking and uploading.** The photo picker and the Storage Access Framework, no storage
  permission; the upload as a user-initiated data transfer (a `dataSync` foreground service below
  Android 14) with a notification naming nothing; a `429` pauses, `507` is never retried.
  **Done 2026-10-15, with a deviation awaiting the owner**: the photo picker and the system document
  picker (no storage permission), a queue that persists, uploads one at a time with progress on the
  row, a `429` pausing the queue for the server's wait, a `507` stopping that upload until the person
  asks again, offline retrying every 30 seconds. **There is no foreground service or user-initiated job,
  because the plan contradicts itself**: it says uploads run in the background (line 117) and also
  that they run only while unlocked and a lock pauses them (line 331), and leaving the app locks it at
  once; sealing needs the file's key, which a lock zeroes (the core proves it: a lock during an upload
  stops it with a typed error before another chunk is sealed). So an upload cannot outlive the app
  whatever service holds the process; it pauses at the lock and resumes at the next unlock. The
  transfer is still counted by `SessionGuard` (`transferStarted`/`transferFinished`) and the pickers
  call `pickerWillOpen()`. A phone that wants uploads to carry on in the background needs the lock
  rule changed first: **owner decision**.
- [x] **A5.3 Resuming.** The URI's read permission persisted only while its upload is unfinished; the
  core's same-source check before a byte is sent; a lock pauses, the next unlock resumes.
  **Done 2026-10-15**: each queued upload is saved in `uploads.json` under no-backup storage with its id,
  URI, name, size and folder, the document URI's read permission is persisted
  (`takePersistableUriPermission`) and released when the upload completes or is discarded; a lock stops
  the transfer, the next unlock reloads the queue and **replays the same file id, which is the core's resume
  call** (the same-source check runs before a byte is sent); a file that can no longer be opened, or that
  is not the same file, asks to *choose the file again*. Photo-picker URIs are not persistable, so after
  the app's process ends such an upload asks for the file again. The queue and the cache are wiped when
  the phone leaves the account. On the emulator against the local API: with the local object store
  paused the 150 MiB upload was held, the app left (locked), the store resumed, and at the next unlock the
  upload resumed and the web read the same bytes (sha256). Tests: `UploadServiceTest` (state machine,
  every failure class, a lock, a reload, a file chosen again, cancel) and `UploadStoreTest`.
- [x] **A5.4 Thumbnails.** 320 px, JPEG 0.72, HEIF included, uploaded after the parent, never failing
  the upload.
  **Done 2026-10-15**: images the core can thumbnail (JPEG, PNG, WebP, GIF, AVIF, BMP, HEIC, HEIF) are
  decoded with `ImageDecoder` (which applies the EXIF orientation and reads HEIF), scaled to at most 320 px
  and written as JPEG at 0.72; the core uploads it after the parent and a failure never fails the upload;
  a resumed upload makes no new thumbnail. On the emulator: a 300x200 JPEG with orientation 6 gave a
  thumbnail the web reads as 200x300, upright. HEIF is not tried on the emulator.
- [x] **A5.5 The sealed-object cache.** The core's `SealedObjectCache` on disk, ciphertext only,
  pruned against every listing.
  **Done 2026-10-15**: `FileSealedObjectCache` in `platform` (one file per canonical id in the app's
  cache directory, written atomically, an id that is not a UUID never touches the disk, a half-written
  file swept), pruned against the listing at every load, and emptied when the phone leaves the account.
  5 unit tests; the end-to-end run saw the thumbnail's ciphertext there.
- [x] **A5.6 Preview and export.** Images from memory (Coil with no disk cache); PDFs through
  `PdfRenderer` with the temporary file deleted on close and on lock (R3); "Save to device" and
  "Open with…" saying the copy leaves Zekke's protection.
  **Done 2026-10-15**: an image opens from memory (`ImageDecoder`, which reads HEIF and the EXIF
  orientation, at most 2048 px on the longest edge; no Coil, so no new dependency: **a deviation from the
  task's text, awaiting the owner**); a PDF is decrypted into `noBackupFilesDir/previews`, read by
  `PdfRenderer` page by page, and the file is deleted when the preview closes, **before** every lock
  (the process ends right after a lock, so a listener that ran after it would never run) and at every
  process start; *Save to device* streams into a document made with the system picker and deletes the
  half-written document if it fails; *Open with…* writes one copy under `noBackupFilesDir/shared`, shared
  through a `FileProvider` with a temporary grant, and says the copy leaves Zekke's protection. **Open
  with has a tension, awaiting the owner**: leaving the app locks it, and the other app may read the
  file after that, so the copy is swept at the **next start** rather than at the lock, and sits on the
  phone until then. Also fixed: while what the account keeps in the Trash is unknown, nothing is offered
  for deletion (it said *permanent* before), and an unfinished upload is discarded, not deleted. On the
  emulator against the local API (40 checks): a two-page PDF shows its two pages, exactly one temporary
  file exists while it is open and none after closing or after leaving the app; an image opens without
  error; the saved text file is byte for byte the uploaded one (sha256); the shared copy exists while the
  chooser is open and is gone at the next start. Screenshots are blocked by `FLAG_SECURE`, so the pages'
  pixels are not checked.
- [x] **A5.7 Rename, move, delete and the Trash.**
  **Done 2026-10-15**: new folders (depth-checked), rename of files (the web's name rules and the
  extension note) and folders, move of a file or a folder (never into itself), delete to the Trash or
  permanently as the account's retention says, and the Trash page (restore, restore everything, delete
  for good, empty the Trash; the last two on a full device only). On the emulator (the same 68 checks,
  a premium account for the Trash): each action read back from the web, including a file restored to its
  folder and a purge. Not built: selecting several files at once.

## A6 — Sharing and the extension

- [x] **A6.1 Connections.** Inviting, accepting with the fingerprint, nicknames, disconnecting, and
  the danger states saying *do not send*.
  **Done 2026-10-15**: the *Shared* tile (any of the vault, notes or files scopes) opens `feature:sharing`:
  the invite form with the keyboard's *Send* and the one re-sharing sentence, *Waiting for you* with the
  fingerprint review (the phone never marks it compared; accept is disabled while the fingerprint loads or
  any alarm shows), the connections with their nicknames, and a row's sheet with the nickname and, on a
  full device, *Disconnect* behind a confirmation (the web has none). Every accepted connection and sent
  invitation is checked on each load: *Do not send* and the web's sentence for the three danger states, a
  warning for an unresolvable one; outbound connections are re-established silently. Unit tests cover
  every trust state, every invite refusal and the review's gates. On the emulator against the local API (44
  checks, three accounts): an unknown username; an invitation sent with the keyboard's *Send* and accepted
  on the web; an invitation from the web reviewed on the phone with both fingerprints equal to the web's
  `ownRootFingerprint`, then accepted; a nickname read back from the address book on the web; a disconnect
  that removes the other side. Not exercised on a device: the danger states (covered by unit tests, since
  they need a root key that changes). The connections ViewModel lives with the activity until a lock, so the
  page reads the list again each time it opens.
- [x] **A6.2 The Shared space.** One folder per friendship, both directions, the friendship's
  folders, opening, *Copy to my account*, re-establishing in the background.
  **Done 2026-10-15**: *Shared* on the Shared page (with *Connections* beside it): a folder per friendship,
  what went both ways with *Sent by you*, the folders both people make (create, rename, delete as
  grouping only, move an item), text opened in a sheet with a secret masked until shown, *Copy to my own
  account* with its refusals typed, a file saved through the system picker, a flat space for a phone
  without `sharing`, the reset of an invalid manifest, and a silent catch-up of outbound connections
  whenever the page opens. On the emulator against the local API (61 checks, two accounts): the three
  kinds shared from the web appear; the secret is masked, then shown; each kind copied and read back on
  the web with the same name and value; the file saved through the picker with the same sha256; a folder
  made on the phone with a note filed in it, read back on the web with `folderOf`; an item sent from the
  web shows *Sent by you* without a copy. **Not built**: preview of a shared image or video, moving a
  folder, and copying a file over 64 MiB (the core copies in memory; the phone says to download it).
  Re-establishing after the other account rotated its sharing keys is exercised with A6.3.
- [x] **A6.3 Sending.** From each tab, trust checked first, filed into a folder in the same gesture.
  **Done 2026-10-15**: *Share* on a secret's detail, on a saved note and on a finished file opens the
  web's sheet (the connections not yet sent to, *Put it in* with that friendship's folders, *Send*, *Already
  sent to* with *Remove* on a full device). The core checks trust inside the send and the sheet words each
  refusal as the web does; the share and the filing are one write, and an item sent but not filed says so.
  Passwords cannot be shared. On the emulator against the local API (59 checks, two accounts): after the
  other account **rotated its sharing keys on the web, opening Shared re-established the connection onto
  generation 2** (read back on the web), and then a secret sent into a folder the other account had made, a
  note and a file sent to the top were read back by that account with the right names and places; the sheet
  listed the recipient under *Already sent to*, and *Remove* made the item disappear for them. Not
  exercised on a device: a refused send (covered by unit tests, which cover every outcome).
- [~] **A6.4 Linking the password extension.** The code, the compared fingerprint, the link, and a
  mismatch cancelling with no retry. Checked by hand with the extension.
  **Built 2026-10-15**: *Browser extension* in Settings (a full device holding `passwords`): the code in
  mono with its countdown and *Cancel*, the number to compare (computed from this account's address and
  root key and the claimed keys), a name for the extension on a phone with `sharing`, *The numbers match*
  (the link, with the process held) and *They don’t match* (cancels the code, says why, no retry on it). The
  code expiring, the server's own end of the pairing and every failure have their notice. On the emulator
  against the local API (31 checks) **with the extension's half played by a script that claims the code the
  way the extension's engine does** (fresh device keys, the same `claimPairing` and `pairingFingerprint`):
  the number the phone showed equalled the script's; matching linked the device (the claim's status was
  `linked`, the device is in the account's chain and its name is sealed in the address book); a second
  code refused with different numbers was cancelled and never linked. **Missing**: the check by hand
  with the real extension build in a browser, which this environment cannot run: **awaiting the owner**.

## A7 — Settings

- [x] **A7.1 Devices.** The list with labels from the address book, removing another device with the
  phrase and the re-wrap, renaming a device.
  **Done 2026-10-15**: Settings is now an index (Notifications, the refused changes, then *Devices* and
  *Account* rows that open their own pages, with the system back and the bar's arrow returning to
  Settings). *Devices*: every device with its name from the address book (only on a phone holding
  `sharing`; *Unnamed device* otherwise), *This phone*, full or limited, what it holds (*Everything* or the
  scopes) and when it was added in the phone's locale; the account's chain verified on every load with
  the web's two notices; renaming from a row's sheet; *Remove* only on a full device, for another device,
  and only while the chain verifies, asking for the recovery phrase (checksum checked first, zeroed after)
  with the process held, the keys re-wrapped afterwards. The *Browser extension* section and *Remove this
  phone* moved here, as on the web, and the list refreshes when an extension links. A chain that does not
  verify is now its own failure (*ChainUnverified*, the web's wording) instead of *Something went wrong*,
  for every call. On the emulator against the local API (account with four web devices, a secret and
  this phone): the list and the verified notice; four unnamed web devices; a rename read back from the
  address book on the web; removing a device with the phrase left one device fewer, moved the secret to
  a newer key generation and it still opened; a phrase that fails its checksum removed nothing.
- [~] **A7.2 Account.** The username, the mode, deleting the account with the phrase (and the account
  PIN on Paranoid) with `retention_days` stated.
  **Built 2026-10-15**: the *Account* page now holds, in order, *Your username* (the web's rules and words:
  a malformed name refused before anything is sent, a taken name said without guessing who holds it, the
  permanence and old-name notices, full device only), the existing security mode section, and *Delete this
  account*: the recovery phrase typed (checksum checked first, zeroed after), the account PIN on a Paranoid
  account, an explicit confirmation, the process held; what it means is stated from the server's own
  documents (irreversible, no grace, every username stays taken, the Trash's retention does not apply to a
  deleted account's files, a renewing card subscription is cancelled and refunded); a limited phone is told to
  use a full device. After a deletion the phone takes the path of *Remove this phone*: the start screen and the
  local replica, outbox, caches and upload queue wiped. The page reads the account's mode again when the
  deletion opens (an E2E found it stale after Paranoid was turned on in the same visit, so the PIN was not
  asked and the core refused). On the emulator against the local API (a Paranoid account with a second
  account): a short name refused; another account's name unavailable; a new name resolving to this account
  and the old one to nobody; Paranoid turned on; the deletion with the right PIN ending on the start screen
  with no replica, outbox or upload files left on the phone, and the web no longer finding an account for the
  phrase; the other account deleted. **Not settled**: the attempt with a **wrong account PIN** showed no
  refusal notice on the device in five runs, with no error in the log, although the account survived it (the
  right PIN then deleted it). The refusal's wording and its count are covered by unit tests of the
  ViewModel, the mapper and the sheet, and the stale-result path was fixed, but the on-device refusal is
  unexplained: **awaiting the owner**.
- [x] **A7.3 Plan, security and about.** The plan read-only, the idle timeout, changing the device
  PIN, the version and the client policy.
  **Done 2026-10-15**: three more Settings pages. *Plan*: the plan's name, what it is paid until (or the
  grace end with what happens after), the storage meter, how long deleted items are kept, and that plans are
  bought on the web; read from the account. *Security*: *Lock after* with 1, 5, 15, 30 or 60 minutes,
  applied to the running session at once (the core's new `changeIdleTimeout`) and kept in a file under
  `noBackupFilesDir` that is read before the first unlock; and *This phone’s PIN*, two PIN fields and the
  same refusals as the other PIN screens, the process held, no recovery phrase. *About*: the version and the
  client policy's notice, which moved there from Home (the chain and re-wrap notices stay on Home). On the
  emulator against the local API (37 checks): the free plan, then the paid plan after a web change, with the
  trash retention; the version; the PIN changed, the old one refused and the new one opening; the idle
  window set to one minute, the stored file read back, the phone locking itself after it and still
  locked, with the choice kept, after a restart. **The web has no idle-lock setting**: the five choices are
  this app's own decision, recorded under *Awaiting the owner*.

## A8 — The first release

- [ ] **A8.1 Low-end measurements.** The time of an unlock, a 1 GiB upload's memory, the first pull
  of a large account, on a real low-end phone and a recent one; recorded in the README.
  **Blocked on hardware (the owner)**: the numbers mean something only on physical phones; the x86_64
  emulator under KVM says nothing about a low-end arm64 CPU. The emulator runs of the 150 MiB upload and
  the end-to-end flows prove the paths work, not how fast or how lean they are.
- [ ] **A8.2 The core on an arm64 phone.** The core's tests on a physical device.
  **Blocked on hardware (the owner)**: the core's own tests still run on the JVM, and the instrumented
  test on the emulator stands in for them (see A3.1); the native library is built for arm64 but has never
  run on an arm64 device here.
- [x] **A8.3 The security checklist.** Every item of "what the phone must never leak", the manifest's
  permissions, the network config, the backup rules, the dependency list.
  **Done 2026-10-15** on the release APK (merged manifest, packaged XML and dex read with `aapt2` and
  `dexdump`); the table is in [README.md](README.md#the-security-checklist). Every item holds. Found and
  fixed: the R8 rule left nine `Log` references of libraries (a used `isLoggable` result is not removable
  without a value), now two `Log.d` in CameraX with no data of ours. Found and recorded, not changed:
  `uploads.json` keeps queued uploads' names and URIs in plaintext until they finish; the release's API
  address still defaults to the test host.
- [ ] **A8.4 The client policy row.** An `android` row in `client_releases` before the first release.
  **An operator's SQL on the production database (the owner)**, as the clients domain's README says:
  `INSERT INTO client_releases (platform, min_supported, latest) VALUES ('android', '0.1.0', '0.1.0') ON
  CONFLICT (platform) DO NOTHING;`. Until it exists nothing is refused and no update is announced. The app's
  side (the update screen, the notices in *About*, the `426` handling) is built and was exercised against a
  local policy.
- [ ] **A8.5 Reproducible builds.** Our own upload and signing keys, each release's SHA-256
  published.
  **The owner's**: the upload and signing keys must be made and kept by the owner; nothing here should hold
  them. The release build is unsigned and its dependencies are verified by hash; whether the build is
  byte-reproducible has not been tried.
- [ ] **A8.6 The store listing.** Requires R1 decided.
  **Blocked on the owner's decision R1** (the distribution and the listing's wording).
