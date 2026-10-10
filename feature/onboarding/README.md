# `feature:onboarding` — creating an account and adding this phone

The steps from a fresh install to an open account, by creating one or by adding this phone to an
existing one with its recovery phrase. The module draws them and keeps their state; it
reaches the core only through `OnboardingServices`, which `app` implements, so the screens and the
ViewModel are tested with a fake and no network.

## The steps

| Step | What the user does |
| --- | --- |
| `Start` | *Create an account* (the accent button, the only one in the app) or *I have a recovery phrase* |
| `Phrase` | Chooses 12 or 24 words. *Create my recovery phrase* generates it; it is not shown yet |
| `Mode` | Standard or Paranoid, each with its summary and its trade-off. Choosing Paranoid shows the one-way warning: no way back, no reset, a forgotten account PIN loses the account |
| `Pin` | The PIN twice. The core's `validatePin` refuses a weak one with its reason; a different confirmation is refused before anything is sent. On Paranoid the one-way warning stays on screen, and this PIN becomes the account PIN too |
| `Creating` | The spinner while the core signs up, registers the PIN and opens the session |
| `Recovery` | The recovery kit: the permanent username, *Save as PDF* and *Print*, and *Show my phrase* (the words numbered, in mono, **never copyable**). **The vault stays shut until the kit was saved or printed once**: *Continue to my vault* is enabled only then |
| `EnterRecoveryPhrase` | *Add this phone*: 12 or 24 words, one private field each, with the core's BIP39 suggestions under the focused field. The keyboard's *Next* moves to the next word's field; the checksum is checked before the PIN step |
| `EnrolPin` | This phone's PIN, twice; on a Paranoid account it is the account PIN, which is checked and becomes this phone's PIN. At the device limit, the account's devices as rows to tick before *Remove selected devices and add this phone* |
| `Enrolling` | The spinner while the core reads the chain, enrols this phone, registers the PIN and opens the session |
| `Done` | The app routes to the session; the ViewModel holds nothing |

*Back* moves one step at a time and clears the PINs. Choosing the phrase length again generates a
new phrase and zeroes the previous one.

## What `OnboardingServices` promises

| Call | Contract |
| --- | --- |
| `newPhrase(wordCount)` | Fresh BIP39 words as `CharArray`s, and any pending draft discarded |
| `phraseSuggestions(prefix)`, `isValidPhrase(words)` | The core's `mnemonicWordSuggestions` and `isValidMnemonic`; the module carries no wordlist of its own |
| `enrolDevice(words, pin, removeDeviceIds)` | The core's `enrolThisDevice`, removing the listed devices in the same batch; it opens the session, and zeroes the phrase and PIN it was given on every outcome. Failures are `DeviceEnrolmentException` with `NoAccount`, `Refused` (the account PIN, one message for every refusal), `Offline`, `RateLimited`, `TooManyDevices(devices)` or `Failed`. If the app is in the background when it ends, it locks at once |
| `createAccount(words, pin, paranoid)` | Creates the account and opens the session, or throws `AccountCreationException` with `Offline`, `RateLimited(retryAfterSeconds)` or `Failed`. **A retry reuses the same draft**, so a sign-up that failed after reaching the server is completed, not duplicated. `paranoidFailed` says the account was made Standard because turning Paranoid on did not finish. Before signing up it records that a kit is pending |
| `username()` | The account's username, asked of the server up to three times; `null` when it could not be had. The kit needs it, so the step offers *Try again* |
| `kitContent(username, words, createdAt)` | The core's `recoveryKitContent` |
| `onLocked(listener)` | Called when the session locks; returns the way to stop listening |
| `holdProcess()` | Keeps the process alive in the background until the returned function is called |
| `systemScreenWillOpen()`, `systemScreenReturned()` | Around the file picker and the print dialog, so leaving for them does not lock (the session guard's picker exception) |
| `kitWasLost()`, `kitSettled()` | Whether a pending kit was never saved and the phone holds the account; clearing it once the kit is saved or the loss acknowledged |
| `forgetDraft()` | Zeroes a draft the user abandoned |

## Adding this phone

- **The phrase** is one `CharArray` per word in the ViewModel, not in the state; the fields read it
  through `phraseWord(index)`, as Compose text needs a `String` while a word is typed. Each field is
  `ui`'s `PrivateField`, so the keyboard does not learn, correct or suggest; a word keeps only its
  lowercase letters, eight at most (the longest BIP-39 word). *Back* to the start, a lock, another
  phrase length, success and the ViewModel's end zero the words; *Back* from the PIN keeps them.
- **Focus**: the keyboard's *Next* asks the next word's field for focus by its `FocusRequester`,
  never with `moveFocus(Down)`, which would land on the suggestion row drawn under the field.
  Choosing a suggestion fills the word and moves to the next field the same way.
- **Refusals**: no account for the phrase has its own message (the web app's); a refused account
  PIN has one message for every cause, and after two the screen suggests waiting, since the server
  rations the account PIN's tries.
- **The device limit**: the core lists the account's devices; each is a `CheckboxRow` with the day
  it was added (`deviceAddedDate`, the phone's locale and zone) and the end of its id. Nothing is
  removed until at least one is ticked and the danger button pressed; the PIN is typed again.
- **The process is held** (`holdProcess`) from the request until it ends, so leaving the app while
  the phone is being added does not end the process half-way, which could leave a device enrolled
  on the server whose keys this phone lost. A failure or success releases it.

## Secrets

The phrase lives as `CharArray`s in the ViewModel from `Phrase` to `Done`; a copy goes to the core,
which zeroes it. The words are turned into `String`s only when *Show my phrase* is pressed, for the
screen. The PIN is a `String` in the state while typed (Compose text needs one) and a `CharArray`
zeroed after the attempt. *Back*, a new phrase, *I have written it down*, a lock and the ViewModel's
end all zero the words.

## The recovery kit

The kit is the web app's page, drawn here (`kit/RecoveryKitDocument.kt`) from the core's
`recoverykit` content with `android.graphics.pdf.PdfDocument`: A4, the same layout, sizes and
colours (the light palette's `brand`, `ink`, `inkMuted`, `line` and `warning`, since paper is
light). The headings and labels are ordinary text in the platform's sans-serif; **the phrase is
drawn as the core's glyph outlines**, never as text, so the saved PDF has no word of the phrase in
its text layer. The QR code is encoded by **zxing's** encoder at level `M` (`com.google.zxing:core`,
the pure-Java encoder, no Play services), its quiet zone included in the matrix, and filled as one
path. `RecoveryKitOverflowException` refuses a page whose content would run past the bottom margin.
`PdfDocument` cannot set a document title, so the file's metadata has none.

- **Save as PDF** opens the system's file picker (`ACTION_CREATE_DOCUMENT`) with
  `zekke-recovery-kit-<username>.pdf`, and the PDF is written straight into the stream the picker
  returns: the app never writes it anywhere it chose itself, and the bytes are never held whole in
  memory.
- **Print** goes through the print framework (`PrintManager`, A4 asked for); the page is written
  into the descriptor the print system hands over. It counts as delivered when the job ended up
  queued, started, blocked or completed (*Save as PDF* among the printers included); a print
  dialog closed without printing does not count.

The words exist as `String`s while the page is drawn, as text fields and the page itself need them.

## A lock while onboarding

A lock before the account exists starts over. **Once the account is being created, a lock keeps
the onboarding**: the session locks (its keys are zeroed), but the phrase stays in the ViewModel
and the process is held (`holdProcess`) from the moment creating starts until the kit is saved and
*Continue to my vault* pressed, so leaving the app on the kit step, or the idle timeout, does not
lose the phrase. *Continue* then lands on the locked screen. **The phrase is in memory, unlocked by
nothing, for as long as the kit step is open**, in the foreground or the background; that is the
price of not losing it, and it is the same phrase the step shows on screen.

What a hold cannot survive is the process ending anyway: the user swiping the app away, or Android
reclaiming memory. A flag in the app's preferences (`kit_pending`, not a secret) is set before the
sign-up and cleared when the kit is saved. When the app starts with the flag set and a device
record present, it shows *Your account was created*: Zekke closed before the kit was saved, the
phrase cannot be shown again, and this phone is the only way into the account. That is said once.

## Tests

| Test | What |
| --- | --- |
| `OnboardingViewModelTest` | The steps and *Back*; the phrase length; a new phrase zeroing the old; a weak PIN and a mismatch refused before anything is sent; the account created with the generated phrase, the PIN and the mode; the phrase shown only on request; the vault shut until the kit is delivered, and the phrase zeroed after; the process held from creating to the end, and released by a failure; a missing username looked up again; a lost kit said once; a lock before, while and after creating |
| `EnrolmentViewModelTest` | An empty form of either length; a word cut to its lowercase letters; suggestions and choosing one; a failed checksum refused before the PIN; a weak PIN and a mismatch refused before anything is sent; the phone added with the process held, then released and the phrase zeroed; a failure releasing the process and keeping the phrase; no account; refusals counted; a full account's devices chosen and sent on the retry; *Back* zeroing the phrase; a lock while adding; the day a device was added |
| `EnrolmentScreensTest` (Robolectric) | The keyboard's *Next* reaching the next word with suggestions shown (it fails with `moveFocus(Down)`); a suggestion filling the word and moving on; a full account's devices as rows to tick; the no-account message, and the hint to wait after two refusals |
| `OnboardingScreensTest` (Robolectric) | The one-way warning only on Paranoid; a weak PIN named on screen; *Continue to my vault* disabled until the kit is saved; a failure shown on the PIN step |
| `RecoveryKitDocumentTest` (instrumented) | The page rendered back with `PdfRenderer` is one page, and zxing's reader decodes its QR code to the bare phrase, for 12 and 24 words; 24 words with a 64-character username stay on one page; the matrix carries its quiet zone |

On the emulator against the local API: the kit saved through the file picker into Downloads, its
text layer (`pdftotext`) holding the headings and no word of the phrase; a cancelled print leaving
*Continue* disabled; Home on the kit step keeping the same process and the step; a force-stop on the
kit step showing *Your account was created* once; and every account entered by the web app's own
`lib/account` with the phrase and deleted.
