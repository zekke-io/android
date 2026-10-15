# `feature` — the screens, one module per area

Each module holds the screens of one area and their ViewModels. A screen calls one flow of the core
and draws what it returns; it never touches a key, a byte layout, an endpoint or a ciphertext, and a
ViewModel holds plaintext only for what is on screen. Every string is a resource.

| Module | Area | Task |
| --- | --- | --- |
| `onboarding` | The start screen, creating an account, adding the phone with the phrase, the recovery kit | A1.1–A1.3, A2.1, A2.2 |
| `unlock` | The PIN, its outcomes, removing this phone, the client policy | A1.4, A1.5 |
| `passwords` | The password list and everything done to a credential ([README](passwords/README.md)) | A4.1 |
| `tabs` | The tab row and its dialogs, shared by Vault and Notes ([README](tabs/README.md)) | A4.2, A4.3 |
| `vault` | Secrets and their tabs ([README](vault/README.md)) | A4.2 |
| `notes` | The note grid and the editor ([README](notes/README.md)) | A4.3 |
| `drive` | Folders, files, uploads, previews, the Trash | A5 |
| `sharing` | Connections, the Shared space, sending, linking the extension | A6 |
| `settings` | Devices, the account (turning Paranoid on, the account PIN: A2.3), the plan, notifications, security, about | A2.3, A3.4, A7 |

`onboarding` holds creating an account and adding this phone with its recovery phrase
([its README](onboarding/README.md)). `unlock` handles the PIN and account restoration outcomes,
removing this phone and the client policy ([its README](unlock/README.md)). `settings` holds, until
A7, the account's security: turning Paranoid mode on and changing the account PIN
([its README](settings/README.md)). `passwords`, `tabs`, `vault` and `notes` hold their areas ([passwords](passwords/README.md), [tabs](tabs/README.md), [vault](vault/README.md), [notes](notes/README.md)). The other modules are empty, wired into the app, waiting for
their tasks.
