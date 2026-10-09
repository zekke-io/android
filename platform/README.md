# `platform` — what the core asks Android for

The Android side of what `mobile-base` leaves to each app, bound in Koin by `platformModule`.

| Binding | What | Until |
| --- | --- | --- |
| `DeviceVault` | Where the sealed device record lives | **Provisional: the core's `MemoryDeviceVault`**, which keeps the record in memory and loses it with the process. Task A0.5 replaces it with the Android Keystore vault; nothing that stores an account runs before then |
| `LocalDatabases` | The SQLite drivers of the replica and the outbox, as `replica.db` and `outbox.db` in the app's **no-backup** directory (`Context.noBackupFilesDir`), which Android never backs up or transfers | — |

Later tasks add the QR scanner (A2.1), file access and the upload service (A5), and the clipboard's
clearing (A0.4).
