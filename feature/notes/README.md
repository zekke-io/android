# `feature:notes` — the note grid and the editor

The Notes section: every note as a page in a grid, filed in tabs ([`feature:tabs`](../tabs/README.md)),
and one editor for the note that is open. The module reaches the core only through `NotesServices`,
which `app` implements (`CoreNotesServices`).

## The grid

Two columns of `PageTile`s, newest first: the note's first characters, its title (the first non-empty
line, or *Untitled note*), and its date in the phone's locale; a note that cannot be opened is
*Unreadable note* and does not open. The tab strip sits above, with the count of the open tab, and
*Add a note* is the floating button.

## The editor

One writing surface for the whole note (`ui`'s `NoteSurface`): the text is **one field**, so a
selection can span lines and a line break or its removal is an ordinary change of the text. The
model is the core's `NoteDocument` (`zekke.core.notes.NoteEditing`): a type, a tick and a style for
each character of each line, which the screen turns into drawn lines and styled ranges
(`surfaceModelOf`). The person never sees `#`, `-`, `**` or `{{18}}`; they are only how the note is
stored, byte for byte as the web app writes it.

| Action | Behaviour |
| --- | --- |
| Typing, pasting, deleting | The change of the text is found by common prefix and suffix; new characters take the style of the one before them. Enter on a topic or a task starts a new topic or task (never ticked); Enter on an **empty** topic or task turns it into plain text instead; deleting a line break merges the lines |
| Title, Topic, Checklist | Cycle the type of the lines the selection touches, as the web app does: title and topic toggle each other, a task goes open, done, off |
| A task's box | Ticks it |
| Bold, Italic | Over the selection: added, or removed when every character already has it. Nothing happens with no selection |
| Smaller / Larger text | One step along 12 to 24 over the selection, or over the whole line when nothing is selected |
| The counter | *4,998 characters left*, or *… characters over the limit*, counting what is seen (code points of the plain text) against 5000 |

### Saving

There is no Save button. **Two seconds after the last change** the note is saved (a create, then edits of
the same note: its id is minted once, when the editor opens), and the label says *Unsaved changes*,
*Saving…* or *Saved*. Saves never overlap: what is typed during one is saved after it. A note that is
empty, unchanged or over the limit is not saved and says why (*Nothing to save — use Delete to remove
this note*, *Too long to save*); a failed save keeps the text, says why, and tries again at the next
change. A feed change never replaces the draft. *Back* saves at once.

**Leaving the app.** The app locks and ends its process when it leaves the foreground, and sealing needs
the session's keys, so a draft typed in the last two seconds would be lost. Before any lock,
`CoreNotesServices` seals the unsaved text **into the outbox** and keeps the process alive until the
outbox has been sent; if the network or the token fails, the entry stays queued and goes at the next
unlock. (A note whose first save is still in flight at that very moment can lose what was typed after
it: its replayed create is answered `200`, which keeps the first text.)

A note that cannot be decrypted opens read-only with the web app's notice, and nothing is ever saved over it.

### Deleting

*Delete* is offered on a note that has been saved, on a full device only, behind *Deleting this note is
permanent, and it also removes it from anyone who was set to inherit it.* The web app's multi-select
delete from the grid is not built here.

## Tests

| Test | What |
| --- | --- |
| `NotesViewModelTest` | The load and its failure; the id minted once; a save two seconds after the last key, the timer restarting; an empty note and an over-limit one never saved; formatting being what is saved; line commands, ticking and Enter on an empty topic; opening a note; an unreadable note untouched; one save at a time and what was typed meanwhile; a failed save and no loop; a feed change keeping the draft; *Back* saving at once; what the lock is offered; a note filed in the open tab; deleting with its conditions; a space's items deleted |
| `NotesScreensTest` (Robolectric) | The grid with titles, *Untitled note*, *Unreadable note* and the count; the empty grid; the editor opening blank with its counter; opening a note and going back; no delete on a device that is not full; the surface model without markers |
