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

Each module has its own README: [ui](ui/README.md), [platform](platform/README.md),
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
| `ObjectStore` | One `HttpObjectStore`, for the drive's presigned URLs |
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
until A3, the unlocked branch is a holding screen with *Lock* and the unlock's notices (a chain that
does not verify, a re-wrap that did not finish).

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

Versions are semantic (`major.minor.patch`) because the API's client policy compares them.

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
