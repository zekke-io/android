# android

The Zekke app for Android: every screen, around the shared Kotlin core `mobile-base`, which holds
the protocol, the keys, the API client and the replica. The app never touches a key, a byte layout,
an endpoint or a ciphertext: it calls the core's flows and draws what they return.

The work is listed in [tasks.md](tasks.md).

## Layout

```
android/
  settings.gradle.kts        the app's build, with ../mobile-base included as a composite build
  gradle/libs.versions.toml  every dependency pinned to an exact version
  gradle/verification-metadata.xml  the SHA-256 of every artifact the build may use
  .github/workflows/ci.yml   both variants, the unit tests and the lint, on every push
  app/                       the application: the activity, the dependency injection, resources
  ui/                        the design system: tokens, fonts, components, the sensitive clipboard
  platform/                  what the core asks Android for: the Keystore device vault, the databases
  feature/…                  the screens, one module per area
```

Each module has its own README: [app](app/README.md), [ui](ui/README.md), [platform](platform/README.md),
[feature](feature/README.md).

## Dependency injection

Koin, started once in `ZekkeApplication` with `platformModule` and `coreModule`
(`app/src/main/kotlin/zekke/android/di`). Nothing else constructs a core service.

| Binding | Lifetime |
| --- | --- |
| `ClientIdentity`, `ZekkeApi` | One for the process; `Zekke-Client: android/<version>` on every request |
| `SessionKeystore` | One for the process, its idle timer on the app's coroutine scope |
| `AccountServices` | One, over the API, the session and the platform's `DeviceVault` |
| `OnboardingServices` | A new `CoreOnboardingServices` per onboarding ViewModel: the core's sign-up, the session guard's lock |
| `ThisPhoneServices` | A new `CoreThisPhoneServices` per use: the core's `removeThisDevice`, with the process held |
| `AccountSecurityServices` | A new `CoreAccountSecurityServices` per use: the core's `turnOnParanoid` and `changeAccountPin`, with the process held and the call run to its end |
| `KeyRotationEvents` | One for the process: says that devices were removed and their keys rotated, so `MainActivity` runs the re-wrap once the session shows |
| `ThisPhoneEvents` | One for the process: counts the phone's removals, so `MainActivity` looks for the device record again |
| `ClientPolicyMonitor` | One for the process, over `ZekkeApi.upgradeRequired` and `getClientPolicy`: read at the first `ON_START` of the process, again when the app returns to the foreground after 15 minutes, and after any `426` |
| `ObjectStore` | One `HttpObjectStore`, for the drive's presigned URLs |
| `SharingService` | One for the process, built on the first use after an unlock: it registers its own lock listener, which forgets the published keys, the connections, the address book and the friendships' folders at every lock, so no copy of them outlives a session |
| `ItemContext` | A new one per use, over the same API and session |
| `Replica`, `Outbox` | Built with the account's `user_address` as a parameter, on the platform's no-backup databases; their lifetime per session is task A3.1's |

`CoreModuleTest` (instrumented) resolves every binding on a device with the real native library,
checks they share one API and one session, and opens both databases in the no-backup directory.

## The core

`mobile-base` is consumed as the module `zekke:mobile-base`. During development
`settings.gradle.kts` includes `../mobile-base` as a composite build and substitutes it, so a change
to the core is picked up on the next build without publishing anything. Its native library
(`libzekke_native.so`) is packaged for the three ABIs the core builds (`arm64-v8a`, `armeabi-v7a`,
`x86_64`); the app is restricted to those, so it never installs on a phone the core cannot run on.

## What the first build already guarantees

| Guarantee | Where |
| --- | --- |
| No screenshots and no recents thumbnail | `FLAG_SECURE`, set in `MainActivity` before anything is drawn |
| No cloud backup and no device transfer | `allowBackup="false"`, `fullBackupContent="false"`, `data_extraction_rules.xml` excluding every domain |
| HTTPS only | `network_security_config.xml`; the debug build alone allows cleartext to `10.0.2.2` and `localhost`, the emulator's way to a local API |
| No logs in release | `proguard-rules.pro` strips every `android.util.Log` call; release is minified and shrunk |
| Not offered to autofill | `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` on the window, in `MainActivity` |
| The client's identity | `Zekke-Client: android/<versionName>` on every request, from `coreModule` |

The only permission is `INTERNET`. AndroidX adds two entries of its own to the merged manifest: a
signature-level permission for its internal receivers, and the profile installer's receiver, guarded
by `android.permission.DUMP`.

## The session's lifecycle

The core's `SessionKeystore` holds every key while the app is unlocked, and its `lock()` zeroes
them. `SessionGuard` (`app/src/main/kotlin/zekke/android/session`) decides when that happens, and
what follows.

| Trigger | What happens |
| --- | --- |
| The app leaves the foreground (Home, another app, Recents) | `ProcessLifecycleOwner`'s `ON_STOP`, about 700 ms after the last activity stops, so a rotation does not count: **lock** |
| The screen turns off | `ACTION_SCREEN_OFF`: **lock**, even with a picker open |
| No interaction for the idle timeout (15 minutes) | The core's own timer, renewed by every touch (`MainActivity.onUserInteraction` → `userActivity`) |
| A system picker the app launched (files, photos, camera, share sheet) | `pickerWillOpen()` before launching it: leaving the foreground does **not** lock until it returns or **3 minutes** pass, whichever is first. Returning to the app ends the exception |
| `lockNow()` | The *Lock* action, when there is one |

**After a lock in the background, the process ends** (`Process.killProcess`), which reclaims what
zeroing cannot reach: copies inside JCA, buffers the garbage collector moved, the `String`s the
keyboard produced. A lock while the app is in the foreground (the idle timeout) keeps the process
and shows the locked screens instead. **A running transfer keeps the process alive** without keys:
`transferStarted()` and `transferFinished()` count them, and the process ends when the last one
finishes, if the session is locked and the app is in the background by then.

**A lock clears every screen.** The unlocked part of the UI lives under `AppRoot`, which draws it
only while the session is `UNLOCKED`, inside a `key` that changes with every lock, and gives it
`SessionViewModels` as its `ViewModelStoreOwner`. A lock clears that store (every ViewModel's
`onCleared` runs) and moves the key, so nothing a screen remembered, with `remember` or
`rememberSaveable`, survives into the next unlock. The locked branch is what the user sees
instead.

**What `MainActivity` draws**: the onboarding while the phone holds no device record (unless the
unlock screen is saying why the record went), while an onboarding is under way (the account exists
from `Creating` on, and the phrase is still to be shown) and after a lock lost the phrase;
otherwise `AppRoot`. Its locked branch is the unlock screen ([feature/unlock](feature/unlock/README.md));
until A3, the unlocked branch is a holding screen with *Lock*, the account's security section
(Paranoid mode, the account PIN), *Remove this phone* and the unlock's notices (a chain that does
not verify, a re-wrap that did not finish) and the version notices.

**A removal of devices is followed by the re-wrap.** Adding this phone with *I lost my other devices*
and turning Paranoid on or changing the account PIN remove the account's other full devices and
rotate what they held. The services say so through `KeyRotationEvents`, and `MainActivity`, in the
unlocked branch, runs the unlock's `rewrapNow()`: the same pass that follows a PIN unlock, with the
same notice when it cannot finish, and idempotent, so a pass cut short is finished at the next unlock.

**The update screen comes first.** Everything above is drawn inside `ClientGate`: when the client
policy says the version is `Required`, or any request of the process was answered `426`
(`ZekkeApi.upgradeRequired`), the update screen replaces every other screen, the session is locked
at once, and the screen stays until the app is updated and opened again. The one exception is the
onboarding's creating and recovery-kit steps: the phrase must not be lost, so the screen follows
when the kit is saved. Once raised, the block stays for the process even if a later policy read
fails, and the update screen names a current version only when it is newer than this one. The policy fails open: when it cannot be read, nothing is shown. A `426` met by any call
reaches the gate through the core's flag, so no feature handles it; each feature maps it to its
generic failure, which the gate hides.

**Removing this phone holds the process.** `CoreThisPhoneServices` takes `holdProcess()` before the
request and releases it on every outcome, after `lockNow()` when the app is in the background. The
call runs under `NonCancellable`: the removal locks the session before it deletes the record, and
that lock clears the ViewModel that asked for it. Between that lock and the record's deletion the
unlock screen can show for a moment; nothing can be typed on it. Leaving the app during a removal locks the
session; a removal not yet signed then fails and the phone stays one of the account's devices.

**Creating an account holds the process.** `SessionGuard.holdProcess()` returns a release; while
any hold is taken, a lock in the background does not end the process, and the last release ends it
if the session is locked and the app is in the background by then. The onboarding holds it from the
moment it starts creating the account until the recovery kit is saved, so neither the sign-up nor
the phrase is lost to leaving the app ([feature/onboarding](feature/onboarding/README.md#a-lock-while-onboarding)).
If the app is in the background when the sign-up finishes, it locks at once.

## Variants

| Variant | Application id | API |
| --- | --- | --- |
| debug | `zekke.android.debug` | `http://10.0.2.2:8080`, the local API seen from the emulator |
| release | `zekke.android` | the Gradle property `zekke.api.release`, `https://test-api.cryple.io` until production |

**The application id is provisional** (the plan's open question R1): it becomes permanent with the
first upload to Google Play, and is decided before then.

Versions are semantic (`major.minor.patch`) because the API's client policy compares them. The local
API has no `android` policy row, so the policy is absent and nothing is refused (it fails open).

## Dependencies are verified

Every artifact the build resolves, the app's, the plugins' and `mobile-base`'s alike, has its SHA-256
in `gradle/verification-metadata.xml`; in a composite build the root build's file covers the
included one. An artifact whose bytes differ fails the build with its name, and the Gradle
distribution itself is checked by the wrapper's `distributionSha256Sum`. A new version arrives only
by changing its pin in `libs.versions.toml` and regenerating the file:

```sh
./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease assembleDebugAndroidTest lintDebug testDebugUnitTest
```

and the diff of that file is reviewed like code: every new entry is an artifact the app now trusts.

**Android Studio's sync resolves files the command-line build never does.** Two kinds:

- **Source code and documentation** (`-sources.jar`, `-javadoc.jar`, and Gradle's own
  `gradle-<version>-src.zip`), which the Kotlin importer fetches for every library so the editor can
  show and navigate it. They are trusted by rule in the file's `<trusted-artifacts>`, as Gradle
  recommends for IDE use: they never reach a build or runtime classpath, so nothing in them runs or
  ships.
- **A few metadata files** (the `.pom` and `.module` of some BOMs). When a sync reports one as
  failing verification, it is fetched from Maven Central, checked against the SHA-1 Maven Central
  publishes, and added with the origin *Checked against Maven Central's SHA-1, added for Android
  Studio's sync*. Regenerating from the command line keeps those entries.

When a sync fails on verification, the list of artifacts is in the newest
`~/.gradle/daemon/<version>/daemon-*.out.log`.

## The security checklist

"What the phone must never leak" ([plan](../mobile/android.md#what-the-phone-must-never-leak)), checked on the
**release** APK on 2026-10-15 (merged manifest and packaged XML read with `aapt2`, the dex with `dexdump`).

| Item | Result |
| --- | --- |
| Screenshots and the recents thumbnail | `FLAG_SECURE` is set in `MainActivity.onCreate` before `super.onCreate`, for every screen; a `screencap` of the running app returns nothing |
| Backups | `allowBackup="false"`, `fullBackupContent="false"`, and `dataExtractionRules` excluding root, file, database, sharedpref and external from both cloud backup and device transfer. The record, the replica, the outbox, the caches and the upload queue all live under `noBackupFilesDir` |
| Keyboard learning | Text is entered only through `ui`'s `PrivateField`, `SecretField` and `PinField` (no feature or `app` source uses a Material text field), which go through `PrivateTextInput`: `IME_FLAG_NO_PERSONALIZED_LEARNING`, autocorrect off |
| Clipboard | `SensitiveClipboard` marks the clip `IS_SENSITIVE` and clears it after a timeout through `ClipboardClearReceiver` (not exported); the recovery phrase has no copy action and the screen says why |
| Autofill | The whole window is `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` |
| Plaintext on disk | Decrypted content is held in memory. The exceptions are named on screen: a file the person saves or opens with another app, and the recovery kit PDF they save. Previews and *Open with* copies live under `noBackupFilesDir` in folders swept at start, on lock and when closed. **One thing is plaintext metadata:** `uploads.json` keeps the source URI, name and size of a queued upload until it finishes or is discarded (it is cleared when the phone leaves the account); no file content |
| Logs | No source file calls `android.util.Log` or prints. R8 removes the calls (`-assumenosideeffects`, with `isLoggable` false); **two `Log.d` calls remain in the release dex, both inside CameraX's `PipeCameraPresenceSource`** ("monitoring is stopped"), neither with data of ours |
| Cleartext | The release network security config has `cleartextTrafficPermitted="false"` for the system trust anchors and no domain exception; `usesCleartextTraffic` is not set. The debug variant alone allows `10.0.2.2` and `localhost`, and `10.0.2.2` does not occur in the release dex. No certificate pinning, by the plan |
| Notifications and shortcuts | The app posts no notification and no shortcut |
| Manifest | Permissions: `INTERNET`, `CAMERA` (asked only when the QR scanner opens), `ACCESS_NETWORK_STATE` and a signature-level receiver permission, both added by libraries. Not `debuggable`. The only exported component of ours is the launcher activity; the file provider, the clipboard receiver, the startup provider and CameraX's service are not exported. `androidx.profileinstaller`'s receiver is exported, guarded by its own system permission, as the library ships it |
| Dependencies | 219 runtime artifacts in `releaseRuntimeClasspath`; the build runs with `gradle/verification-metadata.xml` (hashes, not signatures) and every one verifies. A new artifact needs the file regenerated and each hash checked, as [above](#dependencies-are-verified) |
| Secrets in the repository | No keystore, `.jks`, `.p12`, `google-services` or environment file is tracked here; the only env-style files elsewhere hold build pins or placeholders |

**Not covered here:** the release's API address defaults to `https://test-api.cryple.io` (the `zekke.api.release`
Gradle property sets the real one) and **the API host's HTTPS and HSTS (Task 132) must be in place first**;
the upload and signing keys are A8.5.

## Lint

Warnings are errors. The only checks turned off are the ones that report a newer version of Gradle,
the Android Gradle plugin or a dependency: versions move by changing a pin on purpose, never because
a newer one exists.

## CI

`.github/workflows/ci.yml` checks out this repository and `mobile-base` side by side (the composite
build reads `../mobile-base`; the core's ref is the repository variable `MOBILE_BASE_REF`, `main` by
default), installs the NDK the core pins, and runs `assembleDebug assembleRelease
assembleDebugAndroidTest testDebugUnitTest lintDebug`. The instrumented tests run on an emulator
locally for now; running them in CI comes with the first screen that needs them.

Android 17 blocks local-network addresses such as `10.0.2.2` for apps that do not hold
`android.permission.ACCESS_LOCAL_NETWORK`, so the debug build declares it (`app/src/debug`). It is a
runtime permission, not granted on install: on an API 37 emulator it must be granted once per fresh
install with `adb shell pm grant zekke.android.debug android.permission.ACCESS_LOCAL_NETWORK`, or by
installing with `-g` (Android Studio's run configuration has an install flag for it), which grants
runtime permissions on install. An update keeps the grant. The release build does not declare it, as
its API is not on a local network.

## Building

With a JDK 21 and the Android SDK (API 37, the NDK the core pins), or from `mobile-base`'s toolchain
image:

```sh
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp \
  -e GRADLE_USER_HOME=/gradle-home -v "$HOME/.gradle-zekke-mobile:/gradle-home" \
  -v "$PWD/..:/workspace" -w /workspace/android zekke-mobile-toolchain \
  ./gradlew assembleDebug assembleRelease
```

The parent directory is mounted because the composite build reads `../mobile-base`.

**In Android Studio**, open `android/`. What the IDE does not do for the included core:

- **`mobile-base/local.properties`** needs its own `sdk.dir`: Studio writes the SDK's location only
  into the opened project's `local.properties`, and without it the core's Android target has no
  boot classpath (the sync fails in `KotlinModelBuildingConfigurator`). Copy the line from
  `android/local.properties`; the file is ignored by git, as it is per machine.
- **The NDK the core pins** (`android-ndk` in `mobile-base/gradle/libs.versions.toml`, today
  28.2.13676358) must be installed in the SDK Manager (*SDK Tools*, *Show Package Details*). The
  core's native build reads the SDK's location from the Android plugin, so `ANDROID_HOME` need not
  be set.
- **A JDK 21** must be installed (`sudo apt install openjdk-21-jdk`): the core compiles with a
  Java 21 toolchain, and Studio's own runtime is newer. The release APK
comes out unsigned: signing is task A8.5.
