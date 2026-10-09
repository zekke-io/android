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
  ui/                        the design system
  platform/                  what the core asks Android for: the device vault, the databases
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
| The client's identity | `Zekke-Client: android/<versionName>` on every request, from `ZekkeApplication` |

The only permission is `INTERNET`. AndroidX adds two entries of its own to the merged manifest: a
signature-level permission for its internal receivers, and the profile installer's receiver, guarded
by `android.permission.DUMP`.

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

The parent directory is mounted because the composite build reads `../mobile-base`. The release APK
comes out unsigned: signing is task A8.5.
