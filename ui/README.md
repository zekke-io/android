# `ui` — the design system

What every screen is drawn with: the web app's look, adapted to Material 3 and to touch. It never
depends on the core: a component draws what it is given and holds no key. Besides Compose and
Material 3 it uses what its components need to talk to the phone: CameraX and zxing's decoder for
the QR scanner, and the activity result API for the camera permission.

## Where the look comes from

The Zekke design: a warm orange on cream with a violet zero-knowledge accent, Nunito type and the
armadillo mascot, from the Zekke App design and written down in `design-system/README.md`.

| Token | Light | Dark |
| --- | --- | --- |
| `brand` | `#FF7A1A` (buttons; text on it is `onBrand`, ink) | `#FF7A1A` |
| `brandStrong` | `#E8590C` (pressed) | `#FF9A4A` |
| `brandText` | `#9A3412` | `#FFB066` |
| `brandTint` / `brandSoft` | `#FFE3C7` | `#3A2E54` / `#4A3C66` |
| `brandMark` | `#C2410C` (marks that need 3:1 on `brandSoft`: the stored bytes) | `#FF7A1A` |
| `accent` | `#6B4CF0` (violet: selected borders on dark, details) | `#8C73FF` |
| `focusRing` | `#6B4CF0` | `#A99BFF` |
| `ground` / `surface` / `raised` | `#FFF4E8` / `#FFFFFF` / `#FFE3C7` | `#1C1428` / `#2B2140` / `#3A2E54` |
| `line` / `lineStrong` | `#F2DCC6` / `#F2D2AE` | `#4A3C66` / `#5B4B7E` |
| `ink` / `inkSoft` / `inkMuted` / `inkFaint` | `#2A1608` / `#4A2E1A` / `#6B4A32` / `#8A6A52` | `#FFF4E8` / `#DCCFE6` / `#B9AACB` / `#8F7FA6` |
| `warning` | the inverted block: `#FFF4E8` on `#2A1608` | `#2A1608` on `#FFB066` |
| `success`, `danger` | accessible trios | their lighter steps |
| folders, file kinds | orange folder; red, blue, green, teal … file hues | their lighter steps |
| `strongBorder` | `ink`: the 3 dp border of secondary buttons and the chosen option card | `accent` |

`ZekkePalette` holds every token, reached in a composable as `Zekke.palette`; `colorScheme()` maps
it onto Material 3's roles, so Material components take the right colours by themselves. The window
behind Compose follows the theme (`app`'s `values-night` draws `ground`), so a dark phone never
flashes light at launch.

**Every text colour reads at WCAG AA** (4.5:1) on the grounds it is used on, in both themes, and
every mark that carries meaning (the stored bytes over the reservations, the focus ring) at 3:1;
`PaletteContrastTest` checks each pair. White is never text on `brand` (2.6:1): text on orange is ink.

These tokens are the Zekke design system's (`design-system/tokens.json` in the workspace); its
`platforms.md` maps each one to the field here, and each component here has its guidelines in its
`components/` folder.

## Type and shape

- **Nunito** for everything (variable font, Black for headings and buttons, ExtraBold for titles,
  SemiBold for body), **JetBrains Mono** for codes, fingerprints and ids (`MonoStyle`). Both are
  bundled in `res/font`, so no font is fetched at run time; their licences (SIL OFL 1.1) are in
  `licenses/`. The files are Nunito's variable font from the Google Fonts repository (SHA-256
  `bb55a5ca…1e85207`) and JetBrains Mono 2.304's `JetBrainsMono-Regular.ttf` (SHA-256
  `a0bf60ef…98d138f`).
- **The scale**: 34/37 black hero, 28 and 22 black for headings, 17/24 semibold reading text, 15/21
  body, 13/18 compact, 16 black on buttons, 11 captions.
- **Shapes**: buttons are pills; 16 dp for PIN boxes and tags, 20 dp for inputs and keypad keys,
  24 dp for cards and option cards, 28 dp for sheets.
- **Touch**: every button is at least 48 dp tall; the main call to action is 58 dp.

## Components

| Component | What it guarantees |
| --- | --- |
| `PrivateTextInput { … }` | Every text field inside it asks the keyboard **not to learn** (`IME_FLAG_NO_PERSONALIZED_LEARNING`); `privateKeyboard()` turns off suggestions, autocorrect and capitalisation. Every field that can hold a secret, a phrase word, a password or a note is inside one |
| `SecretField` | Masked until *Show*, masked again on *Hide*; a password keyboard while masked. The field is rebuilt when the mode changes, because Compose kept the password semantics of the masked field otherwise. Takes the IME action and its handler, so a form moves focus to its own next field |
| `PrivateField` | A plain field with the same protections, for text that is private but not masked. Takes the keyboard type, the IME action and its handler, so a form moves focus to its own next field |
| `PhraseEntry`, `PhraseEntryFields` | The recovery phrase typed one word per field: the 12 or 24 choice, the BIP-39 suggestions under the focused field, the Next key moving to the next word's own field, the checksum asked for by the consumer, a scanned phrase accepted whatever its case and spacing. The words are `CharArray`s outside the screen's state, zeroed when forgotten |
| `PhraseScanSheet`, `QrScanner`, `QrScannerFrame`, `QrDecoder` | Scanning the kit: a full-screen dialog that explains the camera before asking, handles a refusal and a permanent refusal (*Open settings*), reads one QR code with CameraX and zxing's Java decoder, keeps no frame and stops at the first code. A scanned text goes to `PhraseEntry.acceptScanned`; what is not a 12 or 24-word phrase is refused there |
| `PinField` | Six boxes, digits only, masked; `onComplete` fires once, on the sixth digit |
| `PinPad`, `padPressed` | The same boxes over the app's own 3 × 4 numeric keypad, so the system keyboard never sees a PIN; `onComplete` fires on the sixth digit |
| `Mascot`, `MascotPose` | The armadillo as a vector drawable: closed (cream on orange), open, open as an app icon |
| `StepBar` | Four segments showing the step of a flow, announced as *Step N of M* |
| `OptionCard` | A choice among two or three, a 3 dp bordered card with a radio ring, a title, a description and an optional tag; the whole card is the target |
| `ItemFailure`, `itemFailureText`, `itemFailureTone` | The outcomes of an item call and the web app's sentence for each (offline, rate limited, session ended or renewed, keys changed, too large, scope missing, gone, changed elsewhere, failed), shared by the passwords, vault and notes screens |
| `rateLimitedMessage`, `waitDescription` | The web app's sentence for a `429` and its wait (*in a moment*, *in 45 seconds*, *in about 2 minutes*, rounded up), so every screen says it the same way |
| `CopyButton` | Copies through `SensitiveClipboard` and says *Copied — clears in 30 s* |
| `ZekkeButton` | Pill buttons: primary, secondary (3 dp strong outline), ghost, danger (outlined), and accent (onboarding); `ButtonSize.Large` is the 58 dp call to action |
| `StatusBadge` | Neutral, brand, success, warning and danger pills, always on one line |
| `CheckboxRow` | One option to tick as a whole 48 dp row, a `Role.Checkbox` target with a title and an optional detail (mono for a value) |
| `SegmentedControl` | Two or three exclusive options in a pill track, the chosen one in `brand`; each segment is a radio button for TalkBack |
| `ZekkeIconButton` | An icon in a 48 dp target, plain, framed or danger. Its short `hint` shows on long-press; its `label` (default the hint) is what TalkBack reads, so a row passes *Delete Bank PIN*, not *Delete* |
| `ZekkeIconToggleButton` | The same, with a checked state (`brandTint` fill, `brandText` glyph): *Show values* / *Hide values*. TalkBack hears the state from the toggle's own semantics |
| `ExtendedAddButton` | The pill form of the add button: the `brand` fill, a `+` and the action's name, for a list's *Add a password* / *Add a note* / *Add a secret* |
| `FloatingAddButton`, `FloatingAddMenu` | The 56 dp gradient disc; the menu variant opens a list of `AddOption`s and turns its `+` 45° while open |
| `Notice` | Info, success, warning and danger. Given `onDismiss` it draws a close button (*a message*); without, it stays (*part of the page*), the web app's rule |
| `EmptyState`, `LoadingIndicator` | An icon chip and one sentence; the brand spinner, named *Loading* for TalkBack |
| `SectionLabel`, `SettingsGroup`, `SettingsRow` | The Settings page's section titles and grouped rows: a 40 dp icon square, the title, an optional value, a chevron; a row can be off (greyed, no chevron) or dangerous |
| `SettingsProfileCard`, `PlanStorageCard` | The two cards that open Settings: the account (letter, `@username`, plan and mode tags) and the storage (used, plan tag, bar, free, link) |
| `StorageWarning` | The card Home and the Drive show only from 90% of the quota in use (`StorageReading.isAlmostFull`): *used of quota* over a `danger` bar |
| `ProvideTopBarActions`, `TopBarActionsSlot` | A page adds actions to the shell's top bar, before *Lock* |
| `StorageMeter`, `StorageReading`, `formatBytes` | The web app's storage bar: stored bytes solid over reservations, *Free* counting unfinished uploads. `formatBytes` is the web app's (binary units, one decimal) |
| `Block` | A title, a subtitle, header actions and content, **with no panel**: whitespace separates blocks, as on the web |
| `ItemRow` | A 64 dp row: lead icon on `brandTint`, a one-line title, metadata (in `MonoStyle` for a masked value), trailing actions, a divider. `card`: a 72 dp `surface` row with 20 dp corners and no divider; `avatar`: the first letter in a `brandTint` disc; `inkIcon`: the glyph on an `ink` rounded square, for secrets |
| `FileTile` | A file or folder as an OS-style miniature with no background: its preview cropped to the centre, or its `FileKindIcon`; the name and caption centred, and a *more* control in the corner |
| `FileKindIcon`, `FileKind`, `fileKindOf` | The folder and file miniatures of the design (`file_<kind>` vectors, label drawn over the badge), chosen from the file name's extension |
| `PageTile` | A note as a 22 dp card in a two-column grid: the title, up to four lines of its text, and the date at the foot |
| `NoteSurface`, `SurfaceLine`, `SurfaceSpan` | The note's writing surface: one private text field for the whole note, bold, italic and sizes drawn on their ranges, a title as a heading, a bullet or a checkbox in a gutter beside each topic or task line (the box ticks through `onToggleTask`). Plain data in, so it knows nothing of the note format |
| `TabStrip`, `TabItem` | The section's own tabs: a scrolling row of pills with their counts, the open one filled `ink` (`accent` in dark), a trailing *New tab*, and a long press (with its accessibility label) to rename or delete; the main tab is not manageable. Plain data only: it knows nothing of folders |
| `TopBar` | The section page's bar: the round soft back chevron (none on Home), the 24 sp black title as a one-line heading, and the trailing actions (*Lock* always among them) |
| `BottomBar` | Home's bottom navigation: icon-and-label items on a `surface` strip with a 2 dp `line` top rule, the selected one in `brand-mark`, an optional count badge; each item reads its `description` and its selected state |
| `AppTile` | A Home tile: an outline icon (`ic_app_<name>`) on a solid 72 dp square with 22 dp corners (`AppTileStyle`: Peach, Brand, Amber, Ink), its label below, pressed to 95%; `description` is the click label TalkBack reads |
| `ConfirmDeleteDialog` | *Keep it* and a danger button; with `onConfirm = null` it is a refusal with *Keep it* only. Not dismissable while `busy` |
| `FormSheet` | A full-height bottom sheet: title, scrolling body, a fixed *Cancel* / submit footer above the keyboard. It refuses to close while `busy` |

## Icons

`res/drawable` holds the web app's outline icons as vector drawables, `ic_<name>` (37 of them,
24 dp, drawn in black so a `tint` colours them), and the six feature marks of Home, `mark_<name>`
(64 dp, their own gradient: draw them with `Image`, never tinted). They were converted from
`web-app/src/components/ui/icons.tsx` and `landing-page/public/icons/`, circles and rounded
rectangles turned into paths, every coordinate kept; a changed icon there is converted again, not
redrawn here. The file-kind and folder glyphs are not here yet.

**The clipboard** (`SensitiveClipboard`): a copied value is marked sensitive
(`android.content.extra.IS_SENSITIVE`, so Android 13+ hides it from the copy preview), and an alarm
clears the clipboard after 30 seconds through `ClipboardClearReceiver`. An alarm, not a coroutine,
because the app locks and ends its process when it leaves the foreground, which is exactly when a
copied password is pasted elsewhere. Android may delay an inexact alarm while the phone is idle. Like
the web app, it clears whatever is on the clipboard by then: a background app cannot read it to
check.

**The camera** (`PhraseScanSheet`, `QrScanner`): used only to read the QR code of a recovery kit.
The permission is asked for in the app's own words first (*Zekke uses the camera only to read the QR
code of your recovery kit*), refused politely (*type the words instead*) and, when the system will
not ask again, pointed at the settings. A frame is analysed in memory by zxing's Java decoder
(`ImageAnalysis`, only the latest frame, every frame closed, the luminance copy zeroed) and dropped:
nothing is recorded, saved or sent, and the first code read ends the session. The scanned text is a
`String` on its way into `PhraseEntry`, which cannot be zeroed; the form keeps `CharArray`s from
there. The permission dialog and the settings count as system screens (`PhraseScan` tells the
caller), so the app does not lock while the person is in them. The manifest declares `CAMERA` and
marks the `camera` and `camera.any` features not required, so the app still installs on a phone
without a camera, where typing remains.

**Autofill**: the app excludes its whole window from autofill services
(`IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`, set in `MainActivity`), so no password manager on
the phone offers to save the phrase, a PIN or a secret.

## Tests

| Test | What |
| --- | --- |
| `PaletteContrastTest` | Every text colour on its ground, both themes, at 4.5:1 or more |
| `PaletteContrastTest` | Every mark that carries meaning at 3:1, both themes |
| `ComponentsTest` (Robolectric) | The secret's masking and unmasking; the PIN's filtering and its single completion; a copy marked sensitive, its alarm at 30 s, and the receiver clearing it |
| `KitTest` (Robolectric) | `formatBytes` against the web app's output; the storage reading's shares, free bytes and an over-quota account; a message notice closing and a page notice having no close; an icon button found by its full label; the add menu opening and running one option; a refusal showing only *Keep it* |
| `PhraseEntryFieldsTest` (Robolectric) | The keyboard's Next key reaching the next word with suggestions shown (it fails with `moveFocus(Down)`); a suggestion filling the word and moving on; the 24-word choice; the scan button only when offered; the checksum and refused-scan notices coming and going |
| `QrDecoderTest` | A 12 and a 24-word kit decoded as the bare phrase; a camera row with padding; a code turned on its side; a small code in a large frame; nothing from a blank frame or another kind of code; the text filling the form whatever its case, and a URL filling nothing |
| `RateLimitTest` (Robolectric) | The wait in seconds then in minutes rounded up, and the whole sentence |
| `PhraseEntryTest` | Normalising a word; the suggestions and the prefix copy zeroed; choosing one; the checksum with copies zeroed after; a scan in any case and spacing, 24 words, and everything that is not a phrase refused; forgetting |
| `GalleryScreenshotTest` (Roborazzi) | `DesignGallery` and `ComponentGallery` in both themes, recorded to `src/test/screenshots/gallery-*.png`, `components-*.png`, `phrase-entry-*.png` and `scanner-*.png` |

`./gradlew :ui:recordRoborazziDebug` records the screenshots again after a deliberate change;
`./gradlew :ui:verifyRoborazziDebug` fails when a rendering differs from them. The screenshots are
drawn on the JVM, so they exist even though `FLAG_SECURE` forbids capturing the app on a phone.
