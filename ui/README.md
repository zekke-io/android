# `ui` — the design system

What every screen is drawn with: the web app's look, adapted to Material 3 and to touch. It never
depends on the core: a component draws what it is given and holds no key. Besides Compose and
Material 3 it uses what its components need to talk to the phone: CameraX and zxing's decoder for
the QR scanner, and the activity result API for the camera permission.

## Where the look comes from

**The web app as people see it**: the tokens of `web-app/src/app/globals.css`, which the prose of
`web-app/design/DESIGN.md` confirms. (The YAML header of `DESIGN.md` is a generated Material palette
that the web app does not use, and is not a source.)

| Token | Light (the web app's) | Dark (derived here) |
| --- | --- | --- |
| `brand` | `#4F46E5` (brand-600, its buttons) | `#818CF8` (brand-400) |
| `brandText` | `#4338CA` (brand-700) | `#A5B4FC` (brand-300) |
| `brandTint` / `brandTintLine` | `#EEF2FF` / `#E0E7FF` | `#1E1B4B` / `#312E81` |
| `accent` | `#8B5CF6` | `#A78BFA` |
| `ground` / `surface` / `raised` | `#F9FAFB` / `#FFFFFF` / `#F9FAFB` | `#111827` / `#1F2937` / `#273244` |
| `line` / `lineStrong` | `#E5E7EB` / `#D1D5DB` | `#374151` / `#4B5563` |
| `ink` / `inkSoft` / `inkMuted` / `inkFaint` | `#1F2937` / `#4B5563` / `#6B7280` / `#9CA3AF` | `#F9FAFB` / `#D1D5DB` / `#9CA3AF` / `#6B7280` |
| `success` text / background / line | `#047857` / `#ECFDF5` / `#A7F3D0` | `#34D399` / `#022C22` / `#065F46` |
| `warning` | `#B45309` / `#FFFBEB` / `#FDE68A` | `#FBBF24` / `#451A03` / `#92400E` |
| `danger` | `#B91C1C` / `#FEF2F2` / `#FECACA` | `#F87171` / `#450A0A` / `#991B1B` |
| folders, file kinds | the web app's | their lighter steps |
| `brandMark` | `#6366F1` (brand-500: the logo, the active icon, the spinner) | `#818CF8` |
| `brandSoft` | `#C7D2FE` (the storage bar's reservations) | `#312E81`, so the stored bytes on top keep 3:1 |
| `gradientStart` → `gradientEnd`, `onGradient` | `#6366F1` → `#8B5CF6`, white: the action gradient, `actionGradient` | the same |
| `focusRing` | `#6366F1` | `#A5B4FC` |
| `scrim` | `#1F2937` at 40%, behind sheets | black at 60% |
| `folderShine`, `folderEdge` | `#C7D2FE`, `#6366F1` | the same |

**The web app has no dark theme.** The dark palette is derived from the same families (Tailwind's
gray and indigo, one step lighter for anything drawn on a dark ground), and it ships: `ZekkeTheme`
follows the system setting. The window behind Compose follows it too (`app`'s `values-night`
theme draws `ground`), so a dark phone never flashes white at launch.

`ZekkePalette` holds every token, reached in a composable as `Zekke.palette`; `colorScheme()` maps
it onto Material 3's roles, so Material components take the right colours by themselves.

**Every text colour reads at WCAG AA** (4.5:1) on the grounds it is used on, in both themes, and
every mark that carries meaning (the brand mark on its tint, the focus ring, the stored bytes over
the reservations) at 3:1; `PaletteContrastTest` checks each pair. **The one exception is the action
gradient**: white on its `#6366F1` end is 4.47:1 and on its `#8B5CF6` end 4.23:1, exactly as the
web app draws it. It carries one short semibold label (*Create account*) or the `+` glyph, and is
not tested as body text.

These tokens are the Zekke design system's (`design-system/tokens.json` in the workspace); its
`platforms.md` maps each one to the field here, and each component here has its guidelines in its
`components/` folder.

## Type and shape

- **Inter** for everything, **JetBrains Mono** for codes, fingerprints and ids (`MonoStyle`), as in
  the web app. Both are bundled in `res/font` (Inter as its variable font, one file for every
  weight), so no font is fetched at run time; their licences (SIL OFL 1.1) are in `licenses/`.
  The files are Inter 4.1's `InterVariable.ttf` (SHA-256 `4989b125…d0ccaf`) and JetBrains Mono
  2.304's `JetBrainsMono-Regular.ttf` (SHA-256 `a0bf60ef…98d138f`), from their projects' release
  archives.
- **The scale** is the web app's: 28/34 bold, 22/28 and 18/24 semibold for headings, 15/20 semibold
  for card titles, 14/20 body, 13/18 compact, 11/14 semibold captions. Body text gains 15/22 as
  `bodyLarge` for reading on a phone.
- **Shapes**: 8 dp for buttons and fields, 16 dp for cards, 24 dp for sheets; badges are pills.
- **Touch**: every button is at least 48 dp tall.

## Components

| Component | What it guarantees |
| --- | --- |
| `PrivateTextInput { … }` | Every text field inside it asks the keyboard **not to learn** (`IME_FLAG_NO_PERSONALIZED_LEARNING`); `privateKeyboard()` turns off suggestions, autocorrect and capitalisation. Every field that can hold a secret, a phrase word, a password or a note is inside one |
| `SecretField` | Masked until *Show*, masked again on *Hide*; a password keyboard while masked. The field is rebuilt when the mode changes, because Compose kept the password semantics of the masked field otherwise. Takes the IME action and its handler, so a form moves focus to its own next field |
| `PrivateField` | A plain field with the same protections, for text that is private but not masked. Takes the keyboard type, the IME action and its handler, so a form moves focus to its own next field |
| `PhraseEntry`, `PhraseEntryFields` | The recovery phrase typed one word per field: the 12 or 24 choice, the BIP-39 suggestions under the focused field, the Next key moving to the next word's own field, the checksum asked for by the consumer, a scanned phrase accepted whatever its case and spacing. The words are `CharArray`s outside the screen's state, zeroed when forgotten |
| `PhraseScanSheet`, `QrScanner`, `QrScannerFrame`, `QrDecoder` | Scanning the kit: a full-screen dialog that explains the camera before asking, handles a refusal and a permanent refusal (*Open settings*), reads one QR code with CameraX and zxing's Java decoder, keeps no frame and stops at the first code. A scanned text goes to `PhraseEntry.acceptScanned`; what is not a 12 or 24-word phrase is refused there |
| `PinField` | Six boxes, digits only, masked; `onComplete` fires once, on the sixth digit |
| `ItemFailure`, `itemFailureText`, `itemFailureTone` | The outcomes of an item call and the web app's sentence for each (offline, rate limited, session ended or renewed, keys changed, too large, scope missing, gone, changed elsewhere, failed), shared by the passwords, vault and notes screens |
| `rateLimitedMessage`, `waitDescription` | The web app's sentence for a `429` and its wait (*in a moment*, *in 45 seconds*, *in about 2 minutes*, rounded up), so every screen says it the same way |
| `CopyButton` | Copies through `SensitiveClipboard` and says *Copied — clears in 30 s* |
| `ZekkeButton` | The web app's variants: primary, secondary, ghost, danger, and accent (the action gradient, onboarding only) |
| `StatusBadge` | Neutral, brand, success, warning and danger pills, always on one line |
| `CheckboxRow` | One option to tick as a whole 48 dp row, a `Role.Checkbox` target with a title and an optional detail (mono for a value) |
| `SegmentedControl` | Two or three exclusive options in a pill track, the chosen one in `brand`; each segment is a radio button for TalkBack |
| `ZekkeIconButton` | An icon in a 48 dp target, plain, framed or danger. Its short `hint` shows on long-press; its `label` (default the hint) is what TalkBack reads, so a row passes *Delete Bank PIN*, not *Delete* |
| `ZekkeIconToggleButton` | The same, with a checked state (`brandTint` fill, `brandText` glyph): *Show values* / *Hide values*. TalkBack hears the state from the toggle's own semantics |
| `FloatingAddButton`, `FloatingAddMenu` | The 56 dp gradient disc; the menu variant opens a list of `AddOption`s and turns its `+` 45° while open |
| `Notice` | Info, success, warning and danger. Given `onDismiss` it draws a close button (*a message*); without, it stays (*part of the page*), the web app's rule |
| `EmptyState`, `LoadingIndicator` | An icon chip and one sentence; the brand spinner, named *Loading* for TalkBack |
| `StorageMeter`, `StorageReading`, `formatBytes` | The web app's storage bar: stored bytes solid over reservations, *Free* counting unfinished uploads. `formatBytes` is the web app's (binary units, one decimal) |
| `Block` | A title, a subtitle, header actions and content, **with no panel**: whitespace separates blocks, as on the web |
| `ItemRow` | A 64 dp row: lead icon on `brandTint`, a one-line title, metadata (in `MonoStyle` for a masked value), trailing actions, a divider |
| `FileTile` | A file or folder as a square: its preview cropped to the centre, or its glyph; the name, a caption, and a *more* control in the corner |
| `PageTile` | A note as a 3:4 sheet in a grid: its first characters at 9 px, its title and its date beneath |
| `NoteSurface`, `SurfaceLine`, `SurfaceSpan` | The note's writing surface: one private text field for the whole note, bold, italic and sizes drawn on their ranges, a title as a heading, a bullet or a checkbox in a gutter beside each topic or task line (the box ticks through `onToggleTask`). Plain data in, so it knows nothing of the note format |
| `TabStrip`, `TabItem` | The section's own tabs: a scrolling row of pills with their counts, the open one in `brandTint`, a trailing *New tab*, and a long press (with its accessibility label) to rename or delete; the main tab is not manageable. Plain data only: it knows nothing of folders |
| `TopBar` | The section page's bar: the back arrow (none on Home), the title as a one-line heading, and the trailing actions (*Lock* always among them) |
| `AppTile` | A Home tile: a feature mark on a `surface` square with 22% corners, its label below, pressed to 95%; `description` is the click label TalkBack reads |
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
