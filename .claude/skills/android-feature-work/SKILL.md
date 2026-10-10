---
name: android-feature-work
description: How to build, review and verify a screen or flow in the Zekke Android app (android/), so it keeps the design system, the security model, the web app's behaviour and the test bar. Use before implementing or reviewing any task of android/tasks.md, any change under android/feature, android/app or android/ui, and before calling such a task done.
---

# Building and verifying an Android feature

Each rule below exists because breaking it shipped a real defect once. Work through the sections in
order: **before**, **while**, **tests**, **verification**, **closing**.

## Before writing code

1. Read `android/README.md`, the task in `android/tasks.md`, its section of `../mobile/android.md`,
   and the module's `README.md`.
2. **Find the web app's version of the flow** in `../web-app/src/lib/app/*.ts` (the copy constants:
   `ENROL_STEP_COPY`, `RECOVERY_KIT_STEP_COPY`, `MODE_COPY`, …) and `../web-app/src/components`. The
   app has **the same outcomes and the same words**, with *this phone* for *this browser*. List
   every outcome the web app distinguishes before designing the screen.
3. List what the core flow can throw (`../mobile-base/src/commonMain/kotlin/zekke/core/<module>`).
   Every exception becomes a typed outcome in the feature's services interface; none falls into a
   generic message by accident.
4. Read the design-system file of every component the screen needs
   (`../design-system/components/<Name>.md`) and `android/ui/README.md`.

## While writing

### Components (android/CLAUDE.md, enforced)

- A feature module uses **only `ui`'s components and tokens**. No `OutlinedTextField`,
  `TextField`, `Checkbox`, `Switch`, `RadioButton`, `Card` or other Material control directly in a
  feature, no `Color(0x…)`, font size or corner radius.
- If `ui` lacks something (a parameter included), **extend `ui`**: the composable, its
  `../design-system/components/<Name>.md` (create it or update its **Android** line), its row in
  `android/ui/README.md`, an entry in `ComponentGallery`, then re-record the screenshots and look at
  both themes. Check the gallery still fits its capture height in `GalleryScreenshotTest`.
- Text input is `PrivateField`, `SecretField` or `PinField`, never a bare field: the keyboard must
  not learn what is typed.
- Values from the server are formatted for people: a date with the phone's locale and zone, never a
  raw ISO string; an id shortened to its end in `MonoStyle`.

### Focus and forms

- In a form of several fields, the keyboard's *Next* calls the **next field's `FocusRequester`**.
  Never `focusManager.moveFocus(FocusDirection.Down)`: anything drawn between two fields
  (suggestions, notices, buttons) takes the focus, the keyboard closes and the typed text is lost.
- A field's value that is a secret lives in the ViewModel as a `CharArray`, outside the state, and
  is read through a function; the state carries a revision counter so the screen recomposes.

### Copy that cannot lie

The copy is checked against these facts of the model, which no screen may contradict:

- **The phone never keeps the recovery phrase.** It is typed to add a device, to bring this phone
  back, to remove a device; nothing says it "stays on this phone".
- The PIN never leaves the phone; the server rations tries without seeing it; there is no reset.
- A wrong account PIN is **one message for every cause** (bad proof and bad signature are
  indistinguishable by design); after two, suggest waiting.
- A phrase no account uses has **its own message** (the web app's `noAccount`).
- Sentence case, verbs on buttons, *this phone*, no emoji; one spelling: *enrol*, *enrolment*.

### Lifecycle (the session guard)

- Leaving the app locks the session and **ends the process** (`SessionGuard`). Any flow that writes
  to the server and must not be cut half-way (sign-up, enrolment, a removal, a rotation) takes
  `holdProcess()` through the services interface **before** the request and releases it on
  **every** outcome: success, each failure, a lock, the ViewModel's end.
- If the flow ends while the app is in the background, the services implementation locks at once
  (`if (!guard.isInForeground) guard.lockNow()`).
- Opening a system screen (file picker, print dialog, share sheet, camera) goes between
  `pickerWillOpen()` and `pickerReturned()`.

### Secrets

- Phrase words and PINs are `CharArray`s. Copies go to the core; every array is zeroed with
  `'\u0000'` in a `finally`, by whoever made it. Words become `String`s only where Compose or a page
  needs them, and that is stated in the README.
- A late coroutine result checks it still belongs to the current attempt (an attempt counter
  bumped by a lock, *Back* and `onCleared`).

## Tests (required before a task is done)

- **ViewModel**: every public method, and **every outcome of every service call**, failures
  included. The fake services must be able to fail each call (`CompletableDeferred` completed
  exceptionally), count holds and their releases, and record what was sent.
- **Screens (Robolectric)**: the interactions, not only the texts: IME actions
  (`performImeAction` then `assertIsFocused` on the next field), enabled and disabled buttons,
  toggles, the notice of each failure. Call `performScrollTo()` before clicking anything that may
  be below the fold, or the click misses.
- **A fake never calls a core function that needs the native library** (anything reaching
  `platformPrimitives()`: `isValidMnemonic`, key derivation, sealing). Unit tests run on the JVM
  without `libzekke_native`; use known values instead. Pure core functions (the wordlist search, the
  recovery-kit layout) may be called.
- **A core function added for the app gets its tests in `mobile-base`'s `commonTest`.**
- **A bug fix proves its test**: run the test against the old code once and see it fail.

## Verification (run it, do not assume it)

The toolchain image and the Gradle home live in the session scratchpad; the commands are
`android/README.md` § Building. Run, in this order:

1. `mobile-base`: `./gradlew jvmTest` when the core changed.
2. `android`: `assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest lintDebug
   :ui:verifyRoborazziDebug` (the CI set). A lint warning is an error.
3. A new dependency: regenerate `gradle/verification-metadata.xml` and check each new hash against
   the repository's own checksum before keeping it.
4. **End to end on the emulator against the local API** for any flow that talks to the server:
   - Prepare accounts with the web app's own `lib/account` in a scratch vitest script. **Write
     every phrase to disk before the first request**, and wait out `429` with `Retry-After`: the
     public routes allow 30 requests a minute.
   - Drive the app with `uiautomator dump` and `input`. Swipe above the keyboard, not on it; a
     Compose checkbox is a node with `checkable="true"`, not `android.widget.CheckBox`.
   - Type with the keyboard's *Enter* at least once, as a person would.
   - Check the result from the web app's side, then **delete every account created**.
5. If Gradle reports a file "located outside the root directory", the host's Android Studio and
   the container built into the same `build/`: clean the module and build again.

## Closing

- Update the module's `README.md` (steps, the services contract table, the tests table), any `ui`
  and `design-system` file touched, and the task in `android/tasks.md`: `[x]` and
  **Done YYYY-MM-DD** only when the *Done when* holds, end to end included. Otherwise say what is
  missing.
- **Core changes the app depends on are committed in `mobile-base`** before the app's commit:
  CI checks out `mobile-base`'s `main`, so an uncommitted core file breaks the app's build there.
- Git: read diffs freely; commit messages are a short description of what changed, with no
  co-author line and no mention of internal docs or tasks; never push without asking.
- Review the diff once more against this file before reporting.
