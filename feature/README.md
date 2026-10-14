# `feature` — the screens, one module per area

Each module holds the screens of one area and their ViewModels. A screen calls one flow of the core
and draws what it returns; it never touches a key, a byte layout, an endpoint or a ciphertext, and a
ViewModel holds plaintext only for what is on screen. Every string is a resource.

| Module | Area | Task |
| --- | --- | --- |
| `onboarding` | The start screen, creating an account, adding the phone with the phrase, the recovery kit | A1.1–A1.3, A2 |
| `unlock` | The PIN, its outcomes, removing this phone, the client policy | A1.4, A1.5 |
| `passwords` | The password list and everything done to a credential | A4.1 |
| `vault` | Secrets and their tabs | A4.2 |
| `notes` | The note grid and the editor | A4.3 |
| `drive` | Folders, files, uploads, previews, the Trash | A5 |
| `sharing` | Connections, the Shared space, sending, linking the extension | A6 |
| `settings` | Devices, the account, the plan, notifications, security, about | A3.4, A7 |

`onboarding` holds creating an account and adding this phone with its recovery phrase
([its README](onboarding/README.md)). `unlock` handles the PIN and account restoration outcomes,
removing this phone and the client policy ([its README](unlock/README.md)). The other modules are
empty, wired into the app, waiting for their tasks.
