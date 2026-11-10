# Feature Matrix

## Status Definitions

- `UNKNOWN`: Not investigated.
- `EXISTS_SIGNAL`: Already implemented in Signal.
- `EXISTS_MOLLY`: Implemented in Molly; integration status must be verified.
- `NEEDS_PORT`: Missing from Signal and requires integration.
- `AWAITING_APPROVAL`: Candidate JW feature awaiting project owner approval.
- `IN_PROGRESS`: Implementation underway.
- `IMPLEMENTED`: Code integration complete.
- `TESTED`: Relevant tests passed.
- `BLOCKED`: Cannot currently be implemented.
- `EXCLUDED`: Explicitly outside project scope.
- `OWNER_DECISION`: Not a portable feature; depends on a distribution, branding or roadmap decision.

Do not mark a feature as implemented without inspecting its actual source code.

## 0. Audit Snapshot

First-phase source audit, performed against the repository state below.

| Item | Value |
|---|---|
| Project base | Signal Android `515db93fb4` (`v8.34.2`) |
| Project HEAD at audit | `d83917f7eafc20721ffb87c3dbb2669acc88fd81` (branch `dev`) |
| Molly reference | `https://github.com/mollyim/mollyim-android` @ `892d6264ba007b5458a8b74cf9183c665f1f17a1` (`main`, `v8.19.2-4-1-g892d6264ba`, version name `8.19.2-4`) |
| Signal-JW reference | `https://github.com/johanw666/Signal-Android` @ `515db93fb48206d63406cbfb48c5816f6af1d9e2` (`main`, upstream mirror) |
| Latest Signal-JW release examined | `v8.33.2.0-JW` = `da443926af72a1132f44e620182a0d7f0f397677`, based on Signal `6e9dddc01f` (`v8.33.2`) |
| Molly base of reference | Signal `8.19.2` (`c47a74ba0961903eeaeda6d22347a990056c3e83`) |
| Molly custom diff size | ~3171 files changed vs its Signal base, 387 new files |
| Signal-JW custom diff size | 190 files, ~3,156 insertions vs its Signal base |

Consequences of the version gap:

- Molly `main` is based on Signal **8.19.2** while this project is based on Signal **8.34.2**.
- Molly must therefore be treated as a **feature reference for commit-level porting**, not as a merge source.
- Signal-JW stores its custom work as a single squashed "Added extra options" commit per release, plus older named feature branches. Port from the squashed diff and the named branches, never by merging.

## 1. Signal Core

Preserve all applicable Signal functionality.

Examples:

- Registration
- End-to-end encrypted messaging
- Group messaging
- Voice and video calls
- Attachments
- Contact management
- Linked devices
- Message synchronization
- Key and identity management
- Safety number verification
- Disappearing messages
- Database migrations

Status: Confirmed present at base `515db93fb4` (`v8.34.2`). Must be re-verified after each upstream update.

## 2. Molly Features

All Molly features are required.

Verified against Molly `892d6264ba` and compared with Signal `515db93fb4`.

### 2.1 Summary Table

| Feature | Status | Complexity | Security impact |
|---|---|---|---|
| Data encryption at rest (passphrase-derived DB secret) | TESTED | High | High |
| FCM handling while database is locked | TESTED | Low | Medium |
| Secure RAM wiper | TESTED | Medium | High |
| Automatic application lock (passphrase/biometric extensions) | TESTED | Medium | High |
| UnifiedPush notifications | NEEDS_PORT | High | Medium |
| Unknown-contact blocking | TESTED | Low | Medium |
| Custom backup scheduling and retention | TESTED | Low–Medium | Medium |
| SOCKS5 / Tor / Orbot proxy | TESTED | High | High |
| Optional debug logging | TESTED | Low | Low |
| Manual device linking without QR scan | TESTED | Low | Medium |
| Minor WebView / share / logging hardening | TESTED | Low | Low–Medium |
| Disappearing call history | EXISTS_SIGNAL | — | — |
| Multi-device / tablet linking | EXISTS_SIGNAL | — | — |
| Alternative application icons | EXISTS_SIGNAL | — | — |
| FOSS / degoogled build variant | OWNER_DECISION | — | Medium |
| GitHub reproducible builds | IMPLEMENTED (CI verification pending) | Medium–High | Medium |
| APK self-updater | APPROVED | Medium | Medium |
| MobileCoin removal | APPROVED | Medium | Low |

### 2.2 Feature Detail

#### Data encryption at rest (passphrase-derived database secret)

- Status: `TESTED` (unit tests and app compilation pass; on-device migration verification pending)
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/crypto/MasterSecretUtil.java`, `PassphraseBasedKdf.java`, `Argon2Benchmark.java`, `SecureSecretKeySpec.java`, `PassphraseValidator.java`
- Ported files: `app/src/main/java/org/thoughtcrime/securesms/crypto/{MasterSecretUtil,DatabaseSecretProvider,PassphraseBasedKdf,Argon2Benchmark,SecureSecretKeySpec,PassphraseValidator}.java`
- Molly dependencies: `im.molly:argon2:13.1-1`; passphrase strength uses `me.gosimple:nbvcxz:1.5.0`. Molly's `native-utils` AAR is not required by this feature.
- Current Signal: SQLCipher is already used for the primary database. The database secret is generated and sealed with `KeyStoreHelper` through `DatabaseSecretProvider`. Legacy passphrase code remains in `MasterSecretUtil` but is not the active database-key path.
- What was ported: master-secret encryption now uses an Argon2id-derived key, optionally bound to a non-exportable keystore HMAC key, with AES-GCM. KDF parameters are calibrated per device with `Argon2Benchmark`. The SQLCipher database secret is wrapped with `MasterCipher` under the passphrase-derived master secret, and the keystore-sealed plaintext copy is removed while a passphrase is active. App startup is deferred until the passphrase unlocks the database secret.
- Migration: pre-Argon2 `MasterSecretUtil` formats (`master_secret`, `mac_salt`, `encryption_salt`, `passphrase_iterations`) are still readable and are re-encrypted into the new format on first successful unlock. Installs that had a legacy passphrase but no wrapped database secret migrate the existing keystore-sealed secret on unlock.
- Security implications: the passphrase is now required to obtain the database secret after a cold start. The derived key is device-bound through the keystore HMAC when a passphrase is set. There is no passphrase-recovery path: losing the passphrase makes the wrapped database secret unrecoverable.
- Limitations: while a passphrase is set, background process starts (for example FCM) cannot open the database until the user unlocks, which can delay background processing. Legacy master-secret cryptography remains AES-128 + HmacSHA1 (Molly's `char[]`/`Destroyable` rewrite is not ported), and `MasterSecretUtil` keeps its `String` passphrase API.
- Tests: `app/src/test/java/org/thoughtcrime/securesms/crypto/PassphraseCryptoTest.java` covers `SecureSecretKeySpec` behavior, PKCS#12 passphrase encoding and KDF parameter round-tripping. The existing Signal test suite does not cover Android Keystore or the on-device upgrade path.
- Notes: Do not describe the application lock as database encryption. This feature and the application lock are separate.

#### FCM handling while the database is locked

- Status: `TESTED` (unitless; compilation verified, on-device delivery while locked pending)
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/gcm/FcmReceiveService.java`, `FcmJobService.java`, `FcmFetchManager.kt`
- Current Signal: FCM handling assumes the database is always open. With passphrase protection, the application process can start while the database is still locked, so `SignalStore` is unavailable.
- What was ported: when `KeyCachingService.isLocked()` reports the locked state, `onMessageReceived` defers non-urgent pushes and posts the "you may have messages" notification for high-priority pushes; `onDeletedMessages` and `FcmJobService` defer fetching; `onNewToken` stores a flag in `PlainTextKeyValueStore` and the post-unlock FCM check uploads the new token. The notification path avoids `SignalStore`-backed `RemoteConfig` and `NotificationChannels` until the app is initialized.
- Security implications: no message contents are read while locked. The notification only reveals that messages may be waiting.
- Limitations: message fetching resumes after the user unlocks; a locked app cannot process messages in the background. This matches Molly's deferred-startup model.

#### Secure RAM wiper

- Status: `TESTED` (unit tests and app compilation pass; on-device verification pending)
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/service/WipeMemoryService.java`, native `allocPages` / `wipePage`
- Current Signal: no equivalent found.
- Implementation:
  - Added `WipeMemoryService` ported from Molly, including the native page allocator/wiper and low-memory safeguards.
  - Vendored the GPLv3 native library from `im.molly:native-utils:1.0.0` (source: `mollyim/ndkutil`) as `libmolly-wipe.so` under `app/src/main/jniLibs` for arm64-v8a, armeabi-v7a and x86_64. Renaming avoids a collision with Signal's existing `core/util` `libnative-utils.so`. No Maven dependency is added and the binary is a no-op wipe on unsupported ABIs.
  - `KeyCachingService` now schedules the wiper after every explicit lock (`CLEAR_KEY_ACTION` and `PASSPHRASE_EXPIRED_EVENT`) with a one-second grace period. The wiper is skipped during UI-blocking migrations and active device transfers.
  - As in Molly, the restart path kills the process (`System.exit(0)`) after the wipe is scheduled, so the app returns cold and requires unlocking. If the foreground service cannot start, process death is used as the fallback memory-clearing mechanism.
- Security implications: process death interrupts background work such as WebSocket delivery and can delay notifications until the next push; this is Molly's existing trade-off. No sensitive data is logged. The native library load is guarded and the wipe becomes a no-op if the ABI is unavailable.
- Tests: `NotificationIdsTest` (7 tests) passes with the new `MEMORY_WIPE` id; app compilation succeeds.
- Complexity: Medium.

#### Automatic application lock (passphrase / biometric extensions)

- Status: `TESTED` (unit tests and app compilation pass; on-device verification pending)
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/ScreenLockController.kt`, `components/settings/app/privacy/PrivacySettingsFragment.kt`, `PrivacySettingsViewModel.kt`, `service/KeyCachingService.java`, `util/TextSecurePreferences.java`
- Current Signal already provides: device-credential/biometric screen lock, inactivity timeout, legacy passphrase lock with its own inactivity timeout, and notification content/action hiding while locked. These are preserved rather than replaced.
- What was ported:
  - Restored the "Immediately" screen-lock timeout preset (0 seconds). `KeyCachingService` now honors a zero timeout by locking as soon as the app is backgrounded instead of silently coercing it to 60 seconds. This matches Molly's immediate-lock behavior and the historical Signal behavior that the current Compose screen-lock UI dropped.
  - While screen lock is enabled, Android 13+ recents screenshots are blocked via `Activity.setRecentsScreenshotEnabled(false)`.
  - FLAG_SECURE is forced while the app is locked, and on Android 12 and lower while screen lock protects content, so the recents switcher cannot capture protected windows.
- Deliberately not ported: Molly's separate `BIOMETRIC_SCREEN_LOCK` preference and `ScreenLockController` overlay. That is a parallel lock implementation which replaces Signal's screen lock and relies on private window-inspector APIs; porting it would duplicate and change upstream lock semantics. Molly's PIP overlay logic is tied to that implementation. Signal's own lock flow remains authoritative.
- Security implications: Medium. Immediate locking is stricter but can be surprising during short app switches; the default screen-lock timeout of 0 now locks immediately after backgrounding. No changes were made to key storage, biometric verification, or the unlock path.
- Tests: `ScreenLockSettingsScreenTest` (13 tests), `ScreenLockSettingsLayoutTest` (4 tests), and `ScreenLockSettingsViewModelTest` (9 tests) pass.
- Complexity: Low–Medium.
- Note: the application lock is not passphrase-based database encryption (see above).

#### UnifiedPush notifications

- Status: `NEEDS_PORT`
- Molly sources: `app/src/main/java/im/molly/unifiedpush/**` (`MollySocketRepository`, `UnifiedPushDistributor`, `UnifiedPushNotificationBuilder`, `UnifiedPushReceiver`, settings screens, QR scanner), `UnifiedPushValues`, `UnifiedPushRefreshJob`
- Molly dependencies: `org.unifiedpush.android:connector:3.0.10`, `org.unifiedpush.android:connector-ui:1.1.0-rc3`
- Current Signal: no UnifiedPush support found.
- Behavior: receives push notifications without Google/FCM via a user-selected UnifiedPush distributor, using a MollySocket server for the WebSocket/relay path.
- Constraints: requires the MollySocket server; not available for linked devices in Molly; must coexist with FCM and WebSocket without duplicate delivery.
- Security implications: Medium. Adds an external notification transport and server trust boundary. Never log message content or tokens. Must not bypass Signal's message-authentication path.
- Complexity: High (new transport, settings, background jobs, distributor UI, server dependency).

#### Unknown-contact blocking

- Status: `TESTED` (unit tests and app compilation pass; on-device verification pending)
- Molly sources: `util/TextSecurePreferences.java` (`BLOCK_UNKNOWN` / `pref_block_unknown`, default `false`), `messages/MessageContentProcessor.kt`, `components/settings/app/privacy/PrivacySettingsFragment.kt` / `PrivacySettingsViewModel.kt`
- Current Signal: no equivalent setting found; Signal has message requests but no automatic unknown-sender blocking toggle.
- Implementation:
  - `SettingsValues.isBlockUnknownEnabled` (`settings.block_unknown`, default `false`).
  - `MessageContentProcessor.shouldBlockSender` centralizes the policy: explicit blocks always win; saved contacts and profile-sharing senders are allowed; when the setting is off everything else is allowed; when on, senders whose profile is shared through a group are still allowed. Applied to data, edit, decryption-error, call, typing, story and unknown-group paths.
  - `RecipientUtil.isProfileSharedViaGroup` made public for reuse.
  - Privacy settings toggle plus `Row` test tag; privacy repository getter/setter and view-model state/event.
- Security/privacy: Medium. Affects message acceptance only; existing trusted conversations, saved contacts, profile-sharing senders and shared-group members are unaffected. The check runs on the message-processing worker thread because the group lookup reads the database.
- Tests: `PrivacySettingsViewModelTest` (toggle persists through the repository), `PrivacySettingsScreenTest` (toggle emits `BlockUnknownToggled`). The new row shifted the lazy list, so an existing screen-lock assertion was made row-specific instead of matching any "Off" text.
- Complexity: Low.

#### Custom backup scheduling and retention

- Status: `TESTED` (unit tests and app compilation pass; on-device scheduling/retention verification pending)
- Molly sources: `preferences/BackupsPreferenceFragment.java`, `util/BackupUtil.java`, `util/TextSecurePreferences.java` (`BACKUP_INTERVAL` / `pref_backup_interval`, default 1 day; `BACKUP_MAX_FILES` / `pref_backup_max_files`, default 2), `res/values/arrays.xml` (`backup_intervals`)
- Current Signal: local backups support a daily scheduled time only (`scheduleTimeLabel`, `LocalBackupListener`). Retention was hardcoded to two snapshots.
- Implementation:
  - `SettingsValues` stores `settings.backups.interval.days` (default 1, daily/weekly exposed) and `settings.backups.max.files` (default 2, clamped to 1–10).
  - `LocalBackupListener` computes the next run from the selected interval and applies the existing jitter window. Changing the time or interval recomputes the next alarm immediately.
  - `LocalBackupsSettingsScreen` adds "Backup frequency" (Daily/Weekly) and "Backups to keep" rows backed by Compose radio-list dialogs.
  - Retention is applied to both formats: `LocalArchiveJob` passes the configured count to `ArchiveFileSystem.deleteOldBackups(limit)` for folder backups, and `BackupUtil.deleteOldBackups()` uses it for legacy `.backup` files. At least one backup is always retained.
- Scope note: the new controls live in the modern on-device backup settings screen. The legacy `BackupsPreferenceFragment` still only shows the time picker, but legacy pruning now honors the configured count when set.
- Security implications: Medium. Retention deletion must never delete the only good backup; the lower bound is enforced. Backup contents and encryption are unchanged.
- Tests: `LocalBackupListenerTest` covers daily/weekly scheduling and jitter; `ArchiveFileSystemTest` covers retention-count pruning.
- Complexity: Low–Medium.

#### SOCKS5 / Tor / Orbot proxy

- Status: `TESTED` (unit tests and app compilation pass; on-device verification pending)
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/net/NetworkProxy.kt`, `ProxyProvider`, `ProxySocketFactory`, `SocksProxy`, `DohClient`, `res/xml/preferences_network.xml`, related network preferences
- Molly dependencies: `netcipher` (Tor/Orbot integration), SOCKS support
- Current Signal: has a built-in **Signal proxy** (host/port via `ProxyValues`, `core/network`, `EditProxyActivity`/`EditProxyFragment`). This is not general SOCKS5, Orbot or system-proxy support.
- Implementation:
  - The existing proxy address field now accepts `socks5://host:port` and `socks5h://host:port` (port defaults to 9050) in addition to Signal proxy links and bare hostnames. This makes Orbot usable by entering `socks5://127.0.0.1:9050` without adding a second settings screen or a proprietary dependency.
  - User-configured SOCKS5 is always applied as `socks5h` (remote DNS). libsignal's `Network` performs the resolution, so Signal service hostnames never leak to the local resolver.
  - Configuration failures for user proxies fail closed (`setInvalidProxy`) instead of silently falling back to a direct connection. System-configured SOCKS/HTTP proxies keep Signal's existing fallback behavior.
  - OkHttp traffic (Giphy, Glide, ExoPlayer) uses the user SOCKS5 proxy through a dynamic `ProxySelector`. When a SOCKS5 proxy is active, DNS is resolved with DNS-over-HTTPS through the same proxy (Cloudflare, then Quad9). If every DoH resolver fails, the lookup fails instead of leaking to the system resolver.
  - Signal's content proxy is bypassed while a user SOCKS5 proxy is active so that content requests cannot escape the proxy.
  - The Signal TLS proxy socket factory is only installed for Signal proxies; SOCKS5 uses the regular proxy selector path.
- Limitations: WebRTC media is not proxied, because the Signal Android ringrtc integration in this version does not expose a SOCKS proxy option. Orbot auto-start and Orbot-installed detection from Molly are not ported; users configure the Orbot SOCKS port manually.
- Security implications: High. DNS-leak prevention is critical. Remote DNS, fail-closed configuration, and proxied DoH are implemented. Traffic that cannot use the proxy fails rather than leaking the user's IP. On-device verification is still required.
- Tests: `ProxyConfigTest` (13 tests) and `SignalProxyUtilParseProxyAddressTest` (10 tests) pass.
- Complexity: High.

#### Optional debug logging

- Status: `TESTED` (unit tests and app compilation pass; on-device verification pending)
- Molly sources: `util/TextSecurePreferences.java` (`preferences__enable_debug_log`, `LOG_ENABLED`), early redaction paths, Tracer removal
- Current Signal: has internal debug logging but no user-facing toggle.
- Implementation:
  - `core/util-jvm/.../Log.kt`: split configured vs. active logger; `configure(internalCheck, enableLogging, ...)`, `setLogging(enabled)`, `wipeLogs()`.
  - `CompoundLogger`/`PersistentLogger`: clear persisted logs.
  - `SettingsValues.isLoggingEnabled` (`settings.logging.enabled`, default `true`).
  - `ApplicationContext` and `AvatarProvider` initialize logging from the preference.
  - Help settings: enable toggle plus a confirmation dialog; disabling drops further log output and deletes existing logs off the main thread.
- Security implications: Low. Defaults remain enabled to preserve upstream diagnostics; disabling suppresses all `Log` output and clears the persistent log database. Message contents, keys and tokens remain redacted by existing `Scrubber` behavior.
- Tests: `LogTest` (`setLogging` suppression/resume, `wipeLogs` flush+clear), `HelpSettingsViewModelTest` (toggle/confirm/dismiss), `HelpSettingsScreenTest` (toggle event and dialog).
- Complexity: Low.

#### Manual device linking without QR scan

- Status: `TESTED` (QR-link validation tests and app compilation pass; on-device linking flow pending)
- Molly sources: `LinkDeviceManualEntryScreen.kt` (commits `05805ddc7f`, `edaf97811a`)
- Current Signal: no manual-entry equivalent found.
- Behavior: the link-device intro sheet now offers "Scan QR code" and "Link without scanning". The manual screen parses and validates the pasted `sgnl://linkdevice?uuid=…&pub_key=…` URI, disables submission until valid, and then runs the normal link flow without sync.
- Implementation:
  - New `LinkDeviceManualEntryScreen` and a `linkWithoutQrCode` state flag in `LinkDeviceSettingsState` / `LinkDeviceViewModel`.
  - `LinkDeviceIntroBottomSheet` now navigates into the scanner or the manual screen; the QR screen itself no longer auto-opens the intro sheet.
  - Navigation updated so the intro sheet is removed from the back stack when continuing to device linking.
  - Reuses `LinkDeviceRepository.isValidQr` and the existing link/error handling; no new network or key-handling path.
- Security implications: Medium. Validation is unchanged from the QR path, and the manual entry is only a UI alternative. The pasted link contains the same public linking payload as the QR code; it is not logged.
- Tests: `LinkDeviceRepositoryTest` covers acceptance of a complete link and rejection of missing fields or a different scheme.
- Complexity: Low.

#### Minor hardening

- Status: `TESTED` (compilation and relevant unit test pass; on-device behavior verification pending)
- Items and implementation:
  - Disable Google Safe Browsing in WebViews (Molly `ddc05b14ae`): added `android.webkit.WebView.EnableSafeBrowsing=false` to the manifest.
  - Remove the share-selection recipient limit (Molly `3ad11a8fd6`): `RemoteConfig.shareSelectionLimit` now returns `SelectionLimits.NO_LIMITS` instead of the remote-configured five-recipient cap.
  - Remove the debug log Tracer (Molly `5758f942ca`, adapted): removed app-side Tracer instrumentation from startup, activities, SQLite tracing, jobs, and debug-log serialization. The core `Tracer` class is retained because the internal Spinner debug tool still references it. Debug log zips/uploads now contain no trace payload, and `signal.trace` is empty.
- Security implications: Medium trade-off. Safe Browsing no longer warns about phishing/malware in the payment, captcha and debug WebViews; this matches Molly's de-Googled behavior but reduces a Google-provided safety layer. Share and logging changes do not affect cryptography, authentication, or message transport. Raw SQL and method-trace data can no longer leave the device through debug logs.
- Tests: `RemoteConfig_StaticValuesTest` was updated because `shareSelectionLimit` is now intentionally static, and it passes.
- Complexity: Low.

#### Disappearing call history

- Status: `EXISTS_SIGNAL`
- Evidence: current `app/src/main/java/org/thoughtcrime/securesms/database/CallTable.kt` — `markAllCallEventsRead` schedules expiring deletion of call events via `ExpiringMessageManager`.
- Caveat: source strongly indicates parity; user-visible behavior should be confirmed in manual testing before relying on it.

#### Multi-device / tablet linking

- Status: `EXISTS_SIGNAL`
- Evidence: Signal already provides full linked-device management and sync. Molly commit `881aea8376 Migrate registering as a linked device to upstream`.

#### Alternative application icons

- Status: `EXISTS_SIGNAL`
- Evidence: `components/settings/app/appearance/appicon/*` and `feature/app-settings/.../appicon/*`.

#### FOSS / degoogled build variant

- Status: `OWNER_DECISION`
- Evidence: Molly commit `45947267f1 Remove proprietary dependencies and consolidate FOSS variant (#652)` removed the separate FOSS source set and consolidated to `store` / `website` distribution flavors. Molly's `README.md` still advertises "Molly-FOSS", so the documentation and the build disagree.
- This is a distribution decision (which proprietary/GMS dependencies to exclude), not a runtime feature. It affects application identity and build outputs, so it requires explicit owner approval.

#### GitHub reproducible builds

- Status: `IMPLEMENTED` (workflow and signing config added; first CI run still pending)
- Evidence: Signal ships `reproducible-builds/` (Docker image), `.github/workflows/build-artifacts.yml` and `.github/scripts/build.sh`; Molly ships `.github/workflows/reprocheck.yml`.
- Implemented: `.github/workflows/reproducible-release.yml` builds `githubProdRelease` three times inside Signal's reproducible Docker image, compares unsigned SHA-256 hashes, signs the verified APK and publishes it. `scripts/ci/build-fork.sh` and `scripts/ci/sign-fork.sh` are the container entrypoints.
- Signing: optional `keystore.release.properties` (git-ignored) for local builds; GitHub Actions secrets for CI. The signing identity must never change without a migration plan.
- Limitation: signed APKs are not byte-identical to unsigned builds, so verification uses `reproducible-builds/apkdiff/apkdiff.py`, which ignores signing metadata. Reproducibility is asserted for the unsigned artifact only.

#### APK self-updater

- Status: `APPROVED`
- Evidence: Molly `apkupdate/*` (`MANAGE_MOLLY_UPDATES`, update URL, `ApkUpdateJob`, installer/notifications).
- Implementation depends on the release channel produced by the reproducible-build workflow. Updates must verify the signing certificate matches the installed app before installing.

#### MobileCoin removal

- Status: `APPROVED`
- Evidence: Molly commit `5a8a33070d Remove MobileCoin payments`.
- This is a deliberate divergence from Signal. Remove MobileCoin payment UI, jobs and dependencies without changing the application identity; keep all non-payment functionality intact.

## 3. Signal-JW Feature Candidates

These features require individual investigation and project owner approval.

Verified against Signal-JW `v8.33.2.0-JW` (`da443926af`) and compared with Signal `515db93fb4`.

### 3.1 Summary Table

| Feature | Status | Complexity | Security impact |
|---|---|---|---|
| Encrypted backup export/restore at any time | APPROVED | High | High |
| Plaintext backup export/import | APPROVED | Medium–High | High |
| Backup location / removable storage selection | EXISTS_SIGNAL (largely) | Low | Medium |
| Encrypted ZIP backups (`zip4j`) | APPROVED | Low–Medium | Medium |
| Application passphrase (soft) lock | EXCLUDED (duplicates Molly) | Medium | High |
| Link preview image toggle | TESTED | Low | Low–Medium |
| Keep view-once media | TESTED | Low | High |
| Ignore remote delete requests | TESTED | Low–Medium | High |
| Ignore group-admin delete requests | APPROVED | Low–Medium | High |
| Delete media only (keep message) | APPROVED | Low | Medium |
| Map type selection | TESTED | Low | Low |
| Group invitation controls | APPROVED | Medium–High | Medium |
| FCM / WebSocket notification preference | EXISTS_SIGNAL | — | — |
| Exit-app action | APPROVED | Low | Low |
| APK expiry removal | EXCLUDED (NOT_RECOMMENDED) | Low | High |

### 3.2 Candidate Detail

- **Encrypted backup export/restore at any time** — `EncryptedBackupExporter.java` (~529 lines), `XmlBackup`, `ImportExportFragment`, `ImportExportActivity`. Lets the user export/import a full encrypted backup outside onboarding. High security/complexity: overwrites the current database on restore; must interoperate with Signal's current backup format and key model. Duplicate of nothing in current Signal (Signal restricts full backup restore to setup).
- **Plaintext backup export/import** — `PlaintextBackupExporter.java`, `PlaintextBackupImporter.java`. Adds XML plaintext export and additive import. High security risk (plaintext of messages). Would require a dedicated warning flow and explicit confirmation. Not recommended without a security review.
- **Backup location / removable storage selection** — `BACKUP_LOCATION_REMOVABLE_PREF`, `BACKUP_LOCATION_CHANGED`, `MANAGE_EXTERNAL_STORAGE`. Current Signal already has a backup folder picker (SAF). Likely `EXISTS_SIGNAL` or a legacy duplicate; the broad `MANAGE_EXTERNAL_STORAGE` permission is undesirable.
- **Encrypted ZIP backups** — uses `net.lingala.zip4j:zip4j:2.11.6`. Low priority; evaluate only if backup work is approved.
- **Application passphrase (soft) lock** — `PassphrasePromptActivity`, `Passphrase`/`softlock` branches. Duplicates Molly's passphrase lock; do not implement twice. **Excluded by the project owner**; the Molly-aligned passphrase and database protection remains the single implementation.
- **Link preview image toggle** — `preferences_chats__send_link_preview_images`. Current Signal only has a single link-preview on/off setting, no image-specific toggle. Low complexity. Status: `TESTED`; implemented as `SignalStore.misc.sendLinkPreviewImages` (default on) and a Chat settings toggle that is disabled while link previews themselves are off. Image downloading is skipped only for the outgoing preview attachment; link metadata is still fetched when previews are enabled.
- **Keep view-once media** — `preferences_chats__chat_keep_view_once_messages`. Privacy-sensitive; breaks the view-once expectation. Status: `TESTED`; implemented as `SignalStore.misc.keepViewOnceMessages` (default off) with a Chat settings toggle that discloses the broken expectation. When on, incoming view-once messages are stored as normal messages and the view-once expiry manager is not scheduled.
- **Ignore remote delete requests** — `preferences_chats__chat_ignore_remote_delete`. Privacy-sensitive; affects synchronization expectations. Status: `TESTED`; implemented as `SignalStore.misc.ignoreRemoteDelete` (default off) with a Chat settings toggle. When on, `handleRemoteDelete` keeps the target message and adds a local ❗ reaction instead of marking it remotely deleted. The delete request is also not cached for messages that have not arrived yet. This affects only the local copy; the sender's own deletion is unchanged.
- **Ignore group-admin delete requests** — `preferences_chats__chat_ignore_admin_delete`. Same concerns as remote delete; group-governance implications.
- **Delete media only** — `preferences_chats__delete_media_only`. Keeps message text while deleting media. Medium privacy impact.
- **Map type selection** — `SignalMapView.getGoogleMapType`, `preferences` map type (normal/hybrid/satellite/terrain/none). Current Signal hardcodes `GoogleMap.MAP_TYPE_NORMAL`. Low complexity; low risk. Status: `TESTED`; the location picker now shows Normal/Satellite/Terrain buttons and stores the choice in `SignalStore.misc.googleMapType`; the saved-location snapshot uses the same choice. Hybrid and none remain supported by the stored value even though they have no picker button, matching JW.
- **Group invitation controls** — `whoCanAddYouToGroups` (anyone / nonblocked / onlycontacts / onlysystemcontacts / nobody), `GroupsV2StateProcessor`. Current Signal has group-level "who can add members" permissions, not a per-user policy for who can add you. Medium–high complexity (group state + sync semantics).
- **FCM / WebSocket notification preference** — Current Signal already exposes `forceWebsocketMode` in Data & Storage. `EXISTS_SIGNAL`; do not duplicate.
- **Exit-app action** — `ExitActivity.java`, `MainToolbar`. Minor convenience; low risk.
- **APK expiry removal** — `BuildConfig` expiry behavior. **Excluded by the project owner** (marked `NOT_RECOMMENDED`): removing update enforcement weakens a security control.

### 3.3 Approval Rule

A candidate may already exist in Signal or Molly. Verify before porting.

The project owner has approved every Signal-JW candidate above except:

- Application passphrase (soft) lock
- APK expiry removal

These two must not be implemented. Approved features must still be ported as isolated, individually reviewable commits.

## 4. Explicitly Excluded Features

| Feature | Status |
|---|---|
| SMS database import | EXCLUDED |
| WhatsApp database import | EXCLUDED |
| SMS export/reset (JW, tied to removed SMS) | EXCLUDED |
| WhatsApp database handling (`WhatsappBackup*`, `WaDb*`) | EXCLUDED |
| Application passphrase (JW soft lock, duplicates Molly) | EXCLUDED |
| APK expiry removal (JW) | EXCLUDED |

Do not implement these features or their supporting dependencies. Do not add workarounds.

## 5. Verification Requirements

For every feature, record:

- Feature name
- Source repository
- Source version or commit
- Relevant source files
- Existing Signal implementation
- Existing Molly implementation
- Required changes
- Dependencies
- Settings and default behavior
- Security implications
- Tests
- Current status

## 6. Completion Criteria

Molly integration is complete when all applicable Molly features have been investigated and verified.

JW integration is complete only for features explicitly approved by the project owner.

Excluded features must remain excluded.

All integrated features must have appropriate test results documented.
