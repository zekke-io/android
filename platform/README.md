# `platform` — what the core asks Android for

The Android side of what `mobile-base` leaves to each app, bound in Koin by `platformModule`.

| Binding | What |
| --- | --- |
| `DeviceVault` → `KeystoreDeviceVault` | Where the sealed device record lives |
| `LocalDatabases` | The SQLite drivers of the replica and the outbox, as `replica.db` and `outbox.db` in the app's no-backup directory |

## The local databases — `LocalDatabases`

`replicaDriver()` and `outboxDriver()` open the core's SQLDelight schemas on `AndroidSqliteDriver`,
as `replica.db` and `outbox.db` in `noBackupFilesDir`: neither is copied by a backup or a device
transfer. The core's `Replica` and `Outbox` wipe themselves when they open for another account.

| Call | What |
| --- | --- |
| `deleteReplica()` | Removes only the replica: what a phone that left the account must not keep |
| `deleteAll()` | Removes the replica and the outbox: the phone was removed or forgotten on purpose |

The outbox outlives an involuntary loss of the registration (a phone removed from another device, too
many wrong PINs): its entries are still the person's, and the core's own account check discards them if
another account signs in. `LocalDatabasesTest` (instrumented) proves the files live in no-backup
storage, survive a reopen, belong to one account at a time, and are deleted as above.

Later tasks add the QR scanner (A2.1), file access and the upload service (A5).

## The device vault — `KeystoreDeviceVault`

The core seals the device record under the PIN's device-wrap key; this vault encrypts that sealed
record once more, under a key that never leaves the phone's secure hardware (ADR 00024's second
layer). A copy of the app's storage is useless off the phone.

- **The key**: AES-256-GCM in the Android Keystore under the alias `zekke.device-record`, created
  on the first `store`. **StrongBox** when the phone has one (`FEATURE_STRONGBOX_KEYSTORE`), the
  trusted environment otherwise. Never exportable (`key.encoded` is `null`), random IVs only.
  `keyLocation` says where it ended up: `STRONGBOX`, `TRUSTED_ENVIRONMENT`, `SECURE_HARDWARE`
  (below Android 12, which does not say which), or `SOFTWARE` (an emulator).
- **Usable only while the phone is unlocked** (`setUnlockedDeviceRequired`), **when the phone has a
  secure lock screen**. Without one the Keystore cannot create such a key at all (the emulator fails
  with an internal error), and there is no unlock to wait for, so the key is created without the
  requirement. A lock screen added later applies to the next key, which `delete` and a new device
  create.
- **The file**: `device-record.bin` in the no-backup directory, written atomically
  (`AtomicFile`): `0x01 ‖ iv(12) ‖ AES-GCM(record) ‖ tag(16)`. `hasRecord()` says whether the file
  exists, without touching the key: the app uses it to choose between the onboarding and the
  session. The vault is bound both as the core's `DeviceVault` and as itself.

**When the record does not open**, the vault tells apart what is final from what is temporary:

| Situation | Outcome |
| --- | --- |
| No file | `load()` is `null`: no device here |
| The key is gone, the file is malformed, the tag does not match (another install's record, a tampered file), or the key was permanently invalidated | The file is deleted and `load()` is `null`: the record can never open again, and the app asks for the phrase |
| The Keystore refuses for now (the phone is locked) | `DeviceVaultUnavailableException`, and **the record stays**. Deleting it here would sign the phone out every time it was read while locked |

`delete()` removes both the file and the key: forgetting this phone leaves nothing to reopen.

## Tests

`KeystoreDeviceVaultTest` (instrumented): a record stored, read and forgotten (the file and the
key both gone), never stored in the clear; a rewrite under a fresh IV; a record from another install
(another key) not opening and being forgotten; a tampered record not opening; the key not
exportable, 256 bits, encrypt and decrypt only. Three more steps run only when the
`lockStep` instrumentation argument names them, around a real lock: a record stored, the phone
locked (with a PIN set), the read refused with `DeviceVaultUnavailableException` and the file still
there, the phone unlocked with the PIN, the record read back. All pass on an Android 14 emulator,
with and without a lock screen.

```sh
adb shell am instrument -w zekke.android.platform.test/androidx.test.runner.AndroidJUnitRunner
adb shell locksettings set-pin 1234
adb shell am instrument -w -e lockStep store -e class zekke.android.platform.KeystoreDeviceVaultTest#lockStepStore zekke.android.platform.test/androidx.test.runner.AndroidJUnitRunner
adb shell input keyevent KEYCODE_SLEEP
adb shell am instrument -w -e lockStep locked -e class zekke.android.platform.KeystoreDeviceVaultTest#lockStepWhileLocked zekke.android.platform.test/androidx.test.runner.AndroidJUnitRunner
# unlock with the PIN, then
adb shell am instrument -w -e lockStep unlocked -e class zekke.android.platform.KeystoreDeviceVaultTest#lockStepAfterUnlock zekke.android.platform.test/androidx.test.runner.AndroidJUnitRunner
adb shell locksettings clear --old 1234
```

On an emulator the key's location is `SOFTWARE`; that it is the trusted environment or StrongBox
on a real phone is checked with the core on an arm64 phone (task A8.2).
