# `feature:drive` — browsing the Drive, folders and the Trash

The Drive section: the folders and files the phone holds, as a grid or a list, with the storage used
against the quota, and everything done to them short of transferring bytes: new folders, rename, move,
delete and the Trash. The module reaches the core only through `DriveServices`, which `app` implements
(`CoreDriveServices`), so the screens and the ViewModel are tested with a fake and no network.

## What the screen does

| Action | Behaviour |
| --- | --- |
| Browsing | Folders first, then files, by name, from the replica, eight levels deep (`MAX_TREE_DEPTH`); a path of buttons from *Drive* to the open folder and *Up one folder*; grid (three columns) or list |
| A file's caption | Its true size (from the sealed manifest, never the padded size), or why it cannot be opened: *This upload never finished, so the file is not in your vault.*, *This file is being repaired…*, *Saved. The second copy has not been made.* A file whose name will not open is *Unreadable file* |
| Previews | A file with a thumbnail draws it in its tile (320 px JPEG, a file of its own), fetched when the tile appears and kept for the screen; any failure leaves the glyph. Thumbnails never show as files |
| Storage | The meter draws what the store holds, with what unfinished uploads reserve as a quieter segment, and says so |
| New folder | A name checked as the web app checks a folder's (empty, 120 characters, the same as a sibling's); disabled in a folder already eight levels deep |
| Rename | A file's name must be 1 to 255 characters with no `/`, `\` or control character, and says when the extension changes (*The file stays a JPG file. Changing the name does not convert it.*); only the sealed manifest changes, the file's key stays |
| Move | A sheet of the folders as an indented list; a folder cannot go into itself or its own descendants, nothing offers the place the item already is |
| Delete | Only on a **full device**, after the web app's question: to the Trash when the account keeps deleted files (for its `retention_days`), permanent when it does not |
| Open | Preview, Save to device and Open with… on a finished file; an unfinished one can only be discarded, on any device |
| Trash | What is waiting, newest first, with the days left; *Restore* (one, or everything), *Delete for good* and *Empty the Trash* only on a full device, after the web app's warning. A restore puts things back where they were, or at the top if their folder is gone |

## Uploading

*Upload* in the corner opens a menu: *Photos and videos* (the system photo picker) and *Files* (the system document picker). Both count as the app staying, and no storage permission is asked. The picked files join a persistent queue, and each upload shows on a row of the folder it was picked in: *Waiting to upload*, *Uploading… 37%*, *Finishing the upload…*, or why it stopped:

| Status | What the row says and does |
| --- | --- |
| Offline | The upload tries again in 30 seconds |
| Rate limited | *Uploads are paused for a moment. Nothing failed.* for the server's wait, then it carries on |
| No room | *There is no room left in your storage…* with *Try again*; never retried by itself |
| The file is gone or is not the same | *Choose the same file again to finish the upload* (system picker; the core refuses a different file) |
| Failed | *This upload did not finish* with *Try again* |

A lock stops the transfer and the next unlock resumes it from the queue. Discarding a row asks first, gives the reservation back (`abandonUpload`) and forgets the file. Selecting several files at once is not built.

## Opening a file

A tap on a file opens its sheet. *Preview* opens an **image** (JPEG, PNG, GIF, WebP, AVIF, BMP, HEIC, up to 48 MiB) from memory, decoded at most 2048 px on the longest edge with the orientation applied, or a **PDF**, which is decrypted into a temporary file under the app's no-backup directory that `PdfRenderer` reads page by page. That file is **the one place the app writes plaintext on its own initiative**: it is deleted when the preview closes, when the ViewModel ends, before every lock and at every process start. Other kinds have no in-app preview.

*Save to device* streams the decrypted file into a document made with the system picker (the picker counts as the app staying), and deletes the document if the transfer fails. *Open with…* writes one copy under `noBackupFilesDir/shared` and hands the other app a `FileProvider` URI with a temporary read grant. **Both say the copy leaves Zekke's protection.** Because leaving the app locks it, the shared copy is deleted at the next process start, not at the lock: the other app may need it after the lock.

## What `DriveServices` promises

| Call | Contract |
| --- | --- |
| `load()` | The live files and folders from the replica (`driveView`, `treeFoldersFromReplica`), whether the scope has finished its first pull, and the sealed-object cache pruned against the listing |
| `usage()` | `GET /files/usage` and the account's `retention_days` |
| `preview(file)`, `saveTo(id, target)`, `shareFile(id)` | The image's bytes or the temporary PDF; the decrypted file streamed into an output stream, or into a shared copy whose URI is returned. Each runs to its end even when the app leaves the foreground |
| `thumbnail(id)` | The thumbnail's bytes through the core's `openPreview` and the on-disk **ciphertext** cache; `null` on any failure |
| Writes | Each runs to its end even when the app leaves the foreground, and says why it failed in `ItemFailure`'s words |

## Tests

| Test | What |
| --- | --- |
| `DriveRulesTest` | File kinds as the web app names them; extensions; the 80-character display name; every file-name problem; the extension note; the Trash's day count |
| `DriveViewModelTest` | The first load, a failure and a reload; folders, the path and the way up; a deleted folder sending you to the top; the view toggle; a thumbnail asked once; new folder names and the depth limit; renames of folders and files; moves refused into oneself; deletes needing a full device and a confirmation, and their failure; the Trash's restore and confirmed purge |
| `DriveScreensTest` (Robolectric) | The grid with captions and the storage; opening a folder and going up; the empty folder; the list view; a file's actions and the Trash question with its retention; no delete on a device that is not full; the Trash page |

## Sharing

The screen takes an optional `onShare` slot; `app` provides it and hosts the share sheet of [`feature:sharing`](../sharing/README.md). `DriveScreensTest` covers: the optional *share* slot: offered on a finished file, not on an unfinished upload, absent without it.
