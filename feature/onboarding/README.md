# `feature:onboarding` — creating an account

The steps from a fresh install to an open account. The module draws them and keeps their state; it
reaches the core only through `OnboardingServices`, which `app` implements, so the screens and the
ViewModel are tested with a fake and no network.

## The steps

| Step | What the user does |
| --- | --- |
| `Start` | *Create an account* (the accent button, the only one in the app) |
| `Phrase` | Chooses 12 or 24 words. *Create my recovery phrase* generates it; it is not shown yet |
| `Mode` | Standard or Paranoid, each with its summary and its trade-off. Choosing Paranoid shows the one-way warning: no way back, no reset, a forgotten account PIN loses the account |
| `Pin` | The PIN twice. The core's `validatePin` refuses a weak one with its reason; a different confirmation is refused before anything is sent. On Paranoid the one-way warning stays on screen, and this PIN becomes the account PIN too |
| `Creating` | The spinner while the core signs up, registers the PIN and opens the session |
| `Recovery` | The permanent username, then *Show my phrase*: the words numbered, in mono, **never copyable**. *I have written it down* is enabled only after the phrase was shown |
| `Done` | The app routes to the session; the ViewModel holds nothing |

*Back* moves one step at a time and clears the PINs. Choosing the phrase length again generates a
new phrase and zeroes the previous one.

## What `OnboardingServices` promises

| Call | Contract |
| --- | --- |
| `newPhrase(wordCount)` | Fresh BIP39 words as `CharArray`s, and any pending draft discarded |
| `createAccount(words, pin, paranoid)` | Creates the account and opens the session, or throws `AccountCreationException` with `Offline`, `RateLimited(retryAfterSeconds)` or `Failed`. **A retry reuses the same draft**, so a sign-up that failed after reaching the server is completed, not duplicated. `paranoidFailed` says the account was made Standard because turning Paranoid on did not finish |
| `onLocked(listener)` | Called when the session locks; returns the way to stop listening |
| `forgetDraft()` | Zeroes a draft the user abandoned |

## Secrets

The phrase lives as `CharArray`s in the ViewModel from `Phrase` to `Done`; a copy goes to the core,
which zeroes it. The words are turned into `String`s only when *Show my phrase* is pressed, for the
screen. The PIN is a `String` in the state while typed (Compose text needs one) and a `CharArray`
zeroed after the attempt. *Back*, a new phrase, *I have written it down*, a lock and the ViewModel's
end all zero the words.

## A lock while onboarding

A lock before the account exists starts over. **A lock once the account exists** (while creating,
or on `Recovery`) zeroes the phrase, and the app shows *Your account was created*: the phrase cannot
be shown again, and this phone is the only way in. Task A1.2 decides how the phrase survives this.

## Tests

| Test | What |
| --- | --- |
| `OnboardingViewModelTest` | The steps and *Back*; the phrase length; a new phrase zeroing the old; a weak PIN and a mismatch refused before anything is sent; the account created with the generated phrase, the PIN and the mode; the phrase shown only on request and zeroed at the end; Paranoid that did not finish; a failure returning to the PIN with the same phrase; a lock before, while and after creating |
| `OnboardingScreensTest` (Robolectric) | The one-way warning only on Paranoid; a weak PIN named on screen; *I have written it down* disabled until the phrase is shown; a failure shown on the PIN step |
