# `feature:unlock` — opening and removing this phone, and the client policy

The locked screen of an account already on this phone. The six-digit device PIN is submitted when
the sixth digit is typed, and every result of the core's `unlockWithPin` becomes one of the web
app's outcomes, worded as the web app words them with *this phone* for *this browser*. The module
reaches the core only through `UnlockServices`, which `app` implements (`CoreUnlockServices`), so
the screen and the ViewModel are tested with a fake and no network. The same module holds *Remove this
phone* and the screens of the client policy, reached through `ThisPhoneServices`.

## Outcomes

| Core result | Screen |
| --- | --- |
| `Unlocked(chainProblem)` | The vault opens. A chain that does not verify stays visible as a danger notice in the unlocked shell, never hidden |
| `WrongPin(attemptsRemaining)` | The PIN is cleared and the notice says how many attempts are left, with its own wording for the last one |
| `Forgotten`, `NoDevice` | The registration is gone: too many wrong PINs, or the phone was removed from another device. The vault is untouched; *I have a recovery phrase* opens the onboarding's phrase entry |
| `Removed` | This phone is no longer a device of the account; the same way back |
| `Offline` | Not a PIN error, and no attempt was used |
| `RateLimited(retryAfterSeconds)` | The server's wait, worded; the PIN and the account are not the problem |
| `DeviceVaultUnavailableException` | *Unlock the phone first*: the Keystore refuses the record while the phone is locked. Kept apart from a wrong PIN |
| A lock while unlocking | *The phone locked before unlocking finished*: the session opened by that attempt is locked again |

## After the vault opens

`rewrapStaleKeys()` runs **once the vault is open**, not while the PIN screen waits: the pass walks
the listings of every scope, which grows with the vault. It re-wraps what an earlier rotation left
under an old generation (a rotation made by another device is caught only this way). It is
idempotent, so a pass cut short by a lock or the network is finished by the next unlock. A failure
leaves the vault open and shows why in the unlocked shell; a pass that ends because the session
locked shows nothing. The ViewModel's `rewrapNow()` runs the same pass on demand: `app` calls it when
devices were removed and their keys rotated (adding this phone with *I lost my other devices*,
turning Paranoid on, changing the account PIN), so those flows end in the same notices.

The wording of a rate limit (*Too many requests have come from this network… Try again in about two
minutes*) is `ui`'s `rateLimitedMessage`, shared with the other modules.

## Forgetting this phone

*I forgot this phone's PIN* asks for confirmation, then deletes the device record. **A locked phone
cannot sign its own removal**: its signing key is sealed under the PIN it forgot. So the phone stays
in the account's list of devices until it is removed from another device or with the recovery
phrase, and the confirmation says so. This is where the copy differs from the web app's, whose
browser can sign its own removal. Afterwards the app shows the start, where the recovery phrase
adds this phone again.

## Removing this phone

*Remove this phone* (the web's *Remove this browser*) is a log-out: the open session signs a self
`device-remove`, with **no rotation**; the record and the session go and the app shows the start
screen. It lives in the shell's Settings page, with the
web's inline confirmation (*Remove this phone*, then *Stay signed in*).

| Core result | `RemovalOutcome` | Screen |
| --- | --- | --- |
| returns | `Removed` | Session locked and record gone: the start screen, no message |
| `DeviceRemovedException` (the server already removed the phone) | `AlreadyRemoved` | The phone forgets itself (`forgetThisDevice`) and shows the start screen, as `Removed` does: its goal is reached either way |
| `NetworkError` | `Offline` | Warning: removing needs the server; this phone is still one of the devices |
| `429` | `RateLimited` | Warning with the server's wait |
| `400 INVALID_BATCH` | `Refused` | Danger: the server refused, nothing changed |
| `ChainNotVerifiedException` | `ChainProblem` | Danger: the history does not verify, so the phone was not removed |
| `DeviceVaultUnavailableException` | `PhoneLocked` | Warning: unlock the phone first |
| `426`, `NoDeviceRecordException`, any other error | `Failed` | *Something went wrong.* A `426` is invisible by design: it raised `ZekkeApi.upgradeRequired` and the update screen replaces the page |

`CoreThisPhoneServices` holds the process for the whole call and releases it on every outcome, runs
under `NonCancellable` (the removal's own lock clears the ViewModel mid-call, and the core must
finish), and locks at once when it ends in the background. The ViewModel's attempt counter makes a
late result after `onCleared` change nothing. `app`'s `ThisPhoneEvents` counts removals because the
core locks the session *before* it deletes the record: `MainActivity` looks for the record again on
the count, not only on the lock.

## The client policy

`ClientPolicyMonitor` (in `app`) reads `GET /clients/android/policy` through the core's
`getClientPolicy`, which **fails open** (any failure is no policy). It reads at the process start,
again when the app returns to the foreground after 15 minutes, and after any `426`. Any `426` from
any call raises the core's `ZekkeApi.upgradeRequired`; the version is then blocked whatever the
policy says.

| Source | Status | Screen |
| --- | --- | --- |
| No policy, or the version is current | `Current` | Nothing |
| `Available(latest)` | `UpdateAvailable` | Info notice, dismissible until a newer version exists: *Version X of Zekke is available* |
| `Deprecated` with a date | `Deprecated` | Warning notice with the end date in the phone's locale and zone |
| `Deprecated` with an unreadable date | `UpdateAvailable` | As available; a raw date string is never shown |
| `Required`, or any `426` | `UpgradeRequired` | `UpgradeRequiredScreen`: *Update Zekke*, a danger notice, the current version when known, and where to update. No button |

The notices show on the unlock screen and on the unlocked Home page (A7.3 moves them into About).
The blocking screen replaces every screen (`ClientGate` in `app`), except the onboarding's
creating and recovery-kit steps. When the version becomes unsupported while unlocked, the session is locked at
once. The update screen names a version only when it is newer than this one, and no store while the app is in none (A8.6 words it for the store).

## What `UnlockServices` promises

| Call | Contract |
| --- | --- |
| `unlock(pin)` | `unlockWithPin` with the process held (`holdProcess`), so leaving the app does not cut an attempt that has already spent a try. Returns an `UnlockAttempt`, never throws for an outcome. Zeroes the PIN; if the app is in the background when it ends, it locks at once; if a lock happened while it ran (`SessionGuard.lockVersion` moved), the session it opened is locked again and the result is `Interrupted` |
| `rewrapStaleKeys()` | The core's `rewrapStale`; `null` when it finished or when the session locked meanwhile, otherwise `Offline`, `RateLimited` or `Failed` |
| `forgetThisPhone()` | The core's `forgetThisDevice`: the record deleted locally, the session locked |

`ThisPhoneServices.removeThisPhone()` returns a `RemovalOutcome` and never throws for an outcome; it holds the process for the whole call, releases on every outcome, is not cancelled by its caller, and locks at once if it ends in the background.

## Lifecycle and secrets

The typed PIN is a `CharArray` in the ViewModel, outside Compose state, read by the field through
`pinValue()`; the state's `pinRevision` makes the screen recompose. It is cleared as soon as a
submission starts, and the copy sent is zeroed in a `finally`. An attempt counter, moved by leaving
for the phrase and by the ViewModel's end, makes a late result from an older attempt change
nothing. The ViewModel lives with the activity, not with the session's ViewModels, because it
outlives every lock.

## Tests

| Test | What |
| --- | --- |
| `UnlockViewModelTest` | Submission only at six digits and the PIN cleared; every outcome; the server's attempt count; registration loss and the way to the phrase; the field frozen while unlocking and the PIN copy zeroed after; a late result after the ViewModel ends; the rewrap pass only after opening, its issues, and none after a failed unlock; forgetting with confirmation, its failure, and the start shown after; every core outcome mapped |
| `UnlockScreensTest` (Robolectric) | The sixth digit submits and the attempts left show; each failure's notice; a removed phone offered the phrase; forgetting needs confirmation and says the phone stays listed; the unlocked shell's chain and rewrap notices; the version notice on the unlock screen |
| `RemoveThisPhoneViewModelTest` | The confirmation and *Stay*; one call while busy; a removal leaves no failure; every failure shown and the confirmation closed; an unexpected exception is `Failed`; a late result after the ViewModel ends changes nothing |
| `RemoveThisPhoneSectionTest` (Robolectric) | The button opens the web's confirmation; *Stay signed in* closes it; both buttons disabled while removing; each failure's exact notice, dismissible |
| `ClientPolicyScreensTest` (Robolectric) | The update screen with and without the latest version; the deprecation date in the phone's locale; an available update dismissed; a current version shows nothing |
| `UnlockFlowDeviceTest` (instrumented) | The sixth digit submits to a fake service on a device, and the attempts left show |
| `SessionGuardTest` (in `app`) | Every kind of lock moves `lockVersion`; an unlock without a lock does not |
| `CoreThisPhoneServicesTest` (in `app`) | The hold until the end; a removal cut by leaving the app; a cancelled caller; a lock at once in the background; every failure typed with the hold released; a phone already removed |
| `ClientPolicyMonitorTest` (in `app`) | Fail open; each notice's status; a `426` from any request blocks and reads the policy again; the 15-minute refresh; a dismissed update; one fetch at a time |
| `ClientGateTest` (in `app`) | The update screen replaces the app, except the onboarding; a `426` reaches the gate through the monitor |
