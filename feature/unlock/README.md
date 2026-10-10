# feature:unlock — opening this phone

The locked screen for an account already registered on this phone. It submits a six-digit device PIN
when the last digit is entered and maps the shared account core's typed results to the same outcomes
as the web app.

## Outcomes

| Core result | Screen |
| --- | --- |
| WrongPin | Clears the PIN and says how many attempts remain, including the final-attempt warning |
| Forgotten, NoDevice | Says the registration is gone, the vault is untouched and the recovery phrase is needed to add this phone again |
| Removed | Says this phone is no longer a device and offers the recovery phrase path |
| Offline | Says the server is needed, this is not a PIN error and no attempt was used |
| RateLimited | Uses the server's retry delay and says the PIN and account are not the problem |
| DeviceVaultUnavailableException | Says to unlock the phone before entering the Zekke PIN |
| Unlocked(chainProblem) | Opens the app; an unverified device history stays visible as a danger notice |
| stale-wrap rekey failure | Leaves the app open and shows why the pass could not finish |

The *I forgot this phone's PIN* action asks for confirmation before it deletes the local device
record. The recovery phrase can then add this phone with a new PIN.

## Lifecycle and secrets

CoreUnlockServices takes a process hold across unlock and the rewrapStale pass. A lock that
arrives while the work is running changes the guard's lock version, stops the pass from being treated
as a successful unlock, and locks the session. If the app backgrounds, the session is locked before
the hold is released.

The ViewModel keeps the typed PIN in a CharArray, outside Compose state. It clears the local buffer
as soon as a submission starts, clears its copy in a finally, and invalidates results from an
older attempt. The DeviceVaultUnavailableException is kept distinct from a wrong PIN.

## Tests

| Test | What |
| --- | --- |
| UnlockViewModelTest | Six-digit submission, clearing, every core outcome, attempt counts, registration loss, forget confirmation, and session warnings |
| UnlockScreensTest (Robolectric) | Typing the sixth digit submits; the attempt count is shown; forgetting requires confirmation; lost registration offers the phrase; chain and rewrap warnings are visible |
| UnlockFlowDeviceTest (API 37 AVD) | The last digit submits to a fake service and the screen shows the remaining attempts |
