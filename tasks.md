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
  - [ ] **The owner reviews the dark palette** (`ui/src/test/screenshots/gallery-dark.png`).
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
  - [ ] **A1.4 maps `DeviceVaultUnavailableException`** to "unlock the phone first".
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

- [ ] **A1.1 Creating an account.** Start, the mode (Standard or Paranoid, the one-way sentence
  before the PIN), the PIN (twice for Paranoid, the account PIN becoming this phone's), creating,
  then the recovery kit. *Done when* an account created on the emulator against the local API opens
  in the web app with its phrase.
- [ ] **A1.2 The recovery kit.** The web app's page from the core's content, the phrase as vector
  outlines and the QR code holding the bare phrase, printed through the print framework or saved
  through the system picker; the vault stays shut until it was saved or printed once. The phrase can
  be revealed, never copied.
- [ ] **A1.3 Adding this phone with the phrase.** One field per word, an in-app BIP39 suggestion row,
  the checksum before the PIN step, the account PIN on a Paranoid account (one message for every
  refusal), `TooManyDevices` listing the devices to choose from.
- [ ] **A1.4 Unlock.** Six boxes submitted on the sixth digit; every outcome worded as the web app
  words it (wrong PIN with attempts left, registration gone, offline, rate limited); the chain
  verified after unlocking and a problem shown, not hidden; `rewrapStale` after unlocking.
- [ ] **A1.5 Removing this phone and the client policy.** Log out (no rotation); the
  `UPGRADE_REQUIRED` screen and the update notice from `GET /clients/android/policy`.

## A2 — The phrase's other ways in

- [ ] **A2.1 Scanning the kit.** CameraX and zxing-cpp, frames decoded on the phone and kept
  nowhere; the bare phrase accepted with stray space or case.
- [ ] **A2.2 "I lost my other devices".** The enrolment that removes them in the same batch, with
  the warning, then the re-wrap.
- [ ] **A2.3 The Paranoid flows.** Turning Paranoid on with the phrase after the one-way warning;
  rotating the account PIN; both say the other full devices are signed out, and re-wrap.

## A3 — The replica and the shell

- [ ] **A3.1 The databases.** The replica and the outbox on the Android SQLite driver, in no-backup
  storage; the core's replica and outbox tests run on the emulator.
- [ ] **A3.2 The shell.** Passwords, Vault, Notes and Drive as tabs, Shared and Settings reached
  from them; screens draw from the replica, then catch up.
- [ ] **A3.3 Sync.** At unlock, on returning to the foreground, after each own write, and polling
  while a screen is visible; the outbox flushed, re-wrapped at unlock, its refusals shown.
- [ ] **A3.4 Notifications.** The in-app list, worded by kind; an unknown kind as a generic notice.

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
