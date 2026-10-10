# `feature:unlock` — opening this phone

The locked screen of an account already on this phone. The six-digit device PIN is submitted when
the sixth digit is typed, and every result of the core's `unlockWithPin` becomes one of the web
app's outcomes, worded as the web app words them with *this phone* for *this browser*. The module
reaches the core only through `UnlockServices`, which `app` implements (`CoreUnlockServices`), so
the screen and the ViewModel are tested with a fake and no network.

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
locked shows nothing.

## Forgetting this phone

*I forgot this phone's PIN* asks for confirmation, then deletes the device record. **A locked phone
cannot sign its own removal**: its signing key is sealed under the PIN it forgot. So the phone stays
in the account's list of devices until it is removed from another device or with the recovery
phrase, and the confirmation says so. This is where the copy differs from the web app's, whose
browser can sign its own removal. Afterwards the app shows the start, where the recovery phrase
adds this phone again.

## What `UnlockServices` promises

| Call | Contract |
| --- | --- |
| `unlock(pin)` | `unlockWithPin` with the process held (`holdProcess`), so leaving the app does not cut an attempt that has already spent a try. Returns an `UnlockAttempt`, never throws for an outcome. Zeroes the PIN; if the app is in the background when it ends, it locks at once; if a lock happened while it ran (`SessionGuard.lockVersion` moved), the session it opened is locked again and the result is `Interrupted` |
| `rewrapStaleKeys()` | The core's `rewrapStale`; `null` when it finished or when the session locked meanwhile, otherwise `Offline`, `RateLimited` or `Failed` |
| `forgetThisPhone()` | The core's `forgetThisDevice`: the record deleted locally, the session locked |

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
| `UnlockScreensTest` (Robolectric) | The sixth digit submits and the attempts left show; each failure's notice; a removed phone offered the phrase; forgetting needs confirmation and says the phone stays listed; the unlocked shell's chain and rewrap notices |
| `UnlockFlowDeviceTest` (instrumented) | The sixth digit submits to a fake service on a device, and the attempts left show |
| `SessionGuardTest` (in `app`) | Every kind of lock moves `lockVersion`; an unlock without a lock does not |
