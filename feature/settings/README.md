# `feature:settings` — the account's security

What the account settings will hold (the username, the mode, the devices, the plan, notifications:
tasks A7), starting with the part that only the recovery phrase can do: **turning Paranoid mode on**
and **changing the account PIN**. Until the Settings screen exists (A7.2) the section lives on the
unlocked home screen under *Lock*, as *Remove this phone* does; it moves to Settings → Account without
changing module. The module reaches the core only through `AccountSecurityServices`, which `app`
implements (`CoreAccountSecurityServices`), so the screens and the ViewModel are tested with a fake and
no network.

## The section and the sheet

`AccountSecuritySection` is what the home screen draws: while the mode loads nothing; a *Try again*
when it could not be read; on a **Standard** account *Paranoid mode*, what it adds and *Turn on Paranoid
mode…*; on a **Paranoid** account *Paranoid mode is on*, what it needs, the one-way notice and *Change
the account PIN…*. A finished change leaves a success notice, with how many devices were signed out.

`AccountSecuritySheet` is the flow, a `FormSheet` over the home screen (*Cancel* closes it and forgets
the phrase; it cannot be closed while the change is running):

| Step | What the person does |
| --- | --- |
| Warning (turning on only) | Reads the web app's one-way sentence (no way back to Standard, no reset, a forgotten account PIN ends the account) and that their other full devices will be signed out |
| Phrase | Types or scans the phrase in `ui`'s `PhraseEntryFields`; the checksum is checked before the next step |
| Pins | Turning on: the account PIN twice. Changing: the current account PIN, then the new one twice. The new PIN is also this phone's PIN, and the copy says so |
| Working | The sheet holds and says so; the phrase and PINs are in the request |

## What `AccountSecurityServices` promises

| Call | Contract |
| --- | --- |
| `accountMode()` | `Standard`, `Paranoid`, or `null` when it could not be read |
| `turnOnParanoid(words, pin)` | The core's `turnOnParanoid`: the account PIN registered, this phone's PIN set to it, every other full device removed and the keys they held rotated |
| `changeAccountPin(words, current, new)` | The core's `changeAccountPin`: the same, with the PIN rotated instead |
| `phraseSuggestions`, `isValidPhrase` | The core's BIP-39 suggestions and checksum, for `PhraseEntry` |

Every call returns an `AccountSecurityOutcome`, never throws for an outcome:

| Outcome | Screen |
| --- | --- |
| `Done(signedOutDevices)` | The sheet closes; a success notice, and how many devices were signed out and that each comes back with the phrase and the account PIN |
| `PhraseMismatch` | Back to the phrase, kept, with the web app's *belongs to a different account* |
| `PinRefused` | (counted until the screen's session ends or a change succeeds, even if the sheet is closed and opened again, since the server rations the tries whatever the screen does) One message for every cause (a wrong current PIN and a bad proof cannot be told apart, by design); after two, the web app's advice to wait: an immediate retry cannot succeed and still counts as a try |
| `ParanoidOnUnfinished` | Turning on only: the answer was lost, or a later step failed, **but the account is Paranoid now**. The sheet closes and the home screen says so and what to do (*Change the account PIN*, with the PIN just chosen as the current one, finishes it) |
| `Offline`, `RateLimited`, `PhoneLocked`, `ChainProblem`, `Failed` | Back to the PINs with the phrase kept, each with its message |

**Turning on has one more rule than the web app's**: any failure of a request that reached the server is followed by a read of the account's mode, because the server may have applied the change and
lost the answer. Changing the PIN does not do that; if it fails half-way the account PIN may already be the new one, which the failure notices do not claim either way.

## After a change

Both flows remove the other full devices and rotate what they held, as the core says. `app` starts the
re-wrap that moves the account's items to the new keys as soon as the change is done (the same pass as
after unlocking, and idempotent, so a pass cut short is finished at the next unlock).

## Lifecycle and secrets

`CoreAccountSecurityServices` takes `holdProcess()` before the request and releases it on every
outcome, runs under `NonCancellable` (a lock clears the ViewModel while the call is in flight, and the
change must still end, with its devices removed and its keys rotated) and locks at once when the app is
in the background at the end. The phrase is `PhraseEntry`'s `CharArray`s; the PINs are `String`s while
typed (a field needs one) and `CharArray`s zeroed after the attempt. A late result from an attempt that
ended (the ViewModel cleared, the flow closed) changes nothing.

## Tests

| Test | What |
| --- | --- |
| `AccountSecurityViewModelTest` | The mode loaded once, unreadable and retried; the flows' first steps; the checksum before the PINs; a weak PIN, a mismatch and a missing current PIN refused before anything is sent; both calls with what they send and everything zeroed after; every outcome's next step with the phrase kept or forgotten; refusals counted; closing, not while working; a late result after the ViewModel ends |
| `AccountSecurityScreensTest` (Robolectric) | The section for each mode; the way through the warning, the phrase and the PINs with each button enabled only when it should be; the finished notice with the number of devices; the wait advice after two refusals; the mismatch on the phrase step; the unfinished notice staying on the home screen |
