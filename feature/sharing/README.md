# `feature:sharing` — connections, the Shared space and sending

Sharing between accounts, drawn the way the web app draws it and with its words (*this phone* for *this
browser*). The module reaches the core only through `SharingServices`, which `app` implements
(`CoreSharingServices`, over one `SharingService` that forgets everything at each lock), so the screens
and the ViewModels are tested with a fake and no network. **Passwords cannot be shared**: the core's
sharable types are secrets, notes and files, and a document is not opened by the core.

The **Shared** tile on Home opens this module. It needs at least one of the vault, notes or files
scopes. It holds *Connections* (the web's Settings → Sharing tab).

## Connections

The page reads the connections **when it enters composition** (`ConnectionsViewModel.opened()`), not when
the ViewModel is created: the ViewModel lives with the activity until a lock, so a page opened again
must not show the list it left. Opening also clears the last invitation notice and refusal.

| Part | Behaviour |
| --- | --- |
| *Invite someone* | A username, sent with the keyboard's *Send* or the button. The one sentence on re-sharing (*Anything you send can be copied by the person you send it to…*) sits here and nowhere else |
| *Waiting for you* | An invitation sent to this account, with *Review* |
| *Connected* | Accepted connections and the invitations this account sent (*Waiting for them*). A row with a nickname shows the nickname and, under it, the username |
| A row's sheet | The nickname (sealed in the address book, only this account sees it) and, on a full device, *Disconnect* behind a confirmation that says every share between the two stops |
| *Review* | The other person's fingerprint, in mono, and this account's own to read back, six groups of four. **The phone never marks them compared on its own**: *It matches — accept* is the person's statement. It stays disabled while the fingerprint loads, when it could not be read, and while any alarm shows. *Decline* disconnects |

### The danger states

Every accepted connection and every invitation this account sent is checked against its pin on each
load (`checkTrust`); an invitation waiting for this account is checked in its review. A lookup that
fails shows nothing on the row. The result is a `TrustAlarm`:

| Core trust | Alarm | On the row |
| --- | --- | --- |
| `Trusted`, `Unpinned` | none | nothing |
| `RootChanged` | danger, a new invitation is the way back | *Do not send* and the web's sentence |
| `ProofInvalid` | danger | *Do not send* and the web's sentence |
| `AccountChanged` | danger, a new invitation | *Do not send*, the sentence and why the connection is gone |
| `Unresolvable` | warning | the sentence only |

Sending is refused by the core whatever the screen says (`ConnectionNotTrustedException`), so no screen
can skip the check.

## Services

| Call | Contract |
| --- | --- |
| `loadConnections()` | The connections with their nickname and standing (`Accepted`, `WaitingForMe`, `WaitingForThem`) |
| `checkTrust(ids)` | Each connection's alarm; an id whose lookup failed is absent |
| `catchUp(ids)` | Re-establishes the outbound connections whose counterparty rotated its sharing keys, silently, one failure never stopping the next |
| `invite(username)` | `Sent`, `UnknownUsername` (also a username that cannot exist), `AlreadyConnected`, `NotTrusted(alarm)` or a typed failure |
| `review(id)` | Their fingerprint (`null` when the account no longer resolves), this account's, and the alarm |
| `accept(id)`, `disconnect(id)`, `rename(id, name)` | Writes, each with the process held until it ends |

## Tests

| Test | What |
| --- | --- |
| `SharingPolicyTest` | Every core trust state is an alarm or none; which alarms are danger; the standing of a record; the groups; the fingerprint of each trust |
| `ConnectionsViewModelTest` | The load, the trust check and the silent catch-up; a failed check shown as nothing; an older check never overwriting a newer load; one invitation at a time; every refusal; the review waiting for the fingerprint and blocked by every alarm; accepting and declining and their failures; the nickname; the disconnect confirmation; a limited device; an unexpected exception |
| `ConnectionsScreensTest` (Robolectric) | A page left and opened again shows what changed meanwhile and no old notice; the invite form and the keyboard's *Send*; each refusal's words; *Do not send* and each alarm's sentence; the review and its disabled accept; accepting and declining; the nickname; the disconnect confirmation; a limited device offered no disconnect |
