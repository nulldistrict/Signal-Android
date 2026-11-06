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
| Data encryption at rest (passphrase-derived DB secret) | NEEDS_PORT | High | High |
| Secure RAM wiper | NEEDS_PORT | Medium | High |
| Automatic application lock (passphrase/biometric extensions) | NEEDS_PORT | Medium | High |
| UnifiedPush notifications | NEEDS_PORT | High | Medium |
| Unknown-contact blocking | TESTED | Low | Medium |
| Custom backup scheduling and retention | NEEDS_PORT | Low–Medium | Medium |
| SOCKS5 / Tor / Orbot proxy | NEEDS_PORT | High | High |
| Optional debug logging | TESTED | Low | Low |
| Manual device linking without QR scan | TESTED | Low | Medium |
| Minor WebView / share / logging hardening | NEEDS_PORT | Low | Low–Medium |
| Disappearing call history | EXISTS_SIGNAL | — | — |
| Multi-device / tablet linking | EXISTS_SIGNAL | — | — |
| Alternative application icons | EXISTS_SIGNAL | — | — |
| FOSS / degoogled build variant | OWNER_DECISION | — | Medium |
| APK self-updater and reproducible builds | OWNER_DECISION | — | Medium |
| MobileCoin removal | OWNER_DECISION | — | Low |

### 2.2 Feature Detail

#### Data encryption at rest (passphrase-derived database secret)

- Status: `NEEDS_PORT`
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/crypto/MasterSecretUtil.java`, `PassphraseBasedKdf.java`, `Argon2Benchmark.java`, `SecureSecretKeySpec.java`, `PassphraseValidator.java`, `EncryptedPreferences.java`
- Molly dependencies: `im.molly:argon2:13.1-1`, `im.molly:native-utils`
- Current Signal: SQLCipher is already used for the primary database. The database secret is generated and sealed with `KeyStoreHelper` through `DatabaseSecretProvider`. Legacy passphrase code remains in `MasterSecretUtil` but is not the active database-key path.
- What Molly adds: derives the master/database secret from a user passphrase using Argon2id plus a keystore-held HMAC key, and encrypts derived material with AES-GCM. Adds passphrase change/validation, KDF calibration and encrypted preferences.
- Security implications: central to data-at-rest protection. Requires dedicated crypto review; must not weaken keystore sealing or introduce a KDF downgrade. Migration/rollback behavior (existing installs, forgotten passphrases) must be defined before implementation.
- Tests: Molly includes `EncryptedPreferencesTest`; no equivalent KDF migration tests observed.
- Notes: Do not describe the application lock as database encryption. This feature and the application lock are separate.

#### Secure RAM wiper

- Status: `NEEDS_PORT`
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/service/WipeMemoryService.java`, native `allocPages` / `wipePage`
- Current Signal: no equivalent found.
- Behavior: allocates memory pages and overwrites process memory on lock, then triggers a GC pass, guarded by a low-memory threshold adjustment and a foreground service notification.
- Security implications: process-memory hygiene. Must not cause ANRs, should be best-effort on modern Android, and must never log sensitive data.
- Complexity: Medium. Depends on lifecycle hooks from the application lock.

#### Automatic application lock (passphrase / biometric extensions)

- Status: `NEEDS_PORT` (base screen lock is `EXISTS_SIGNAL`)
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/ScreenLockController.kt`, `components/settings/app/privacy/PrivacySettingsFragment.kt`, `PrivacySettingsViewModel.kt`, `service/KeyCachingService.java`, `util/TextSecurePreferences.java`
- Current Signal: base screen lock exists (`ScreenLockSettingsRepository`, `KeyCachingService`, `components/settings/app/privacy/screenlock`).
- What Molly adds: passphrase lock as an alternative to device screen lock, biometric unlock option, configurable lock triggers (`PASSPHRASE_LOCK_TRIGGER`), timeout (`PASSPHRASE_LOCK_TIMEOUT`), notification behavior (`PASSPHRASE_LOCK_NOTIFICATIONS`), screenshot blocking (including recents on API 33+), and PIP handling.
- Security implications: High. Lock-state transitions, key caching, and process death must be handled carefully. Must not weaken Signal's key storage or unlock paths.
- Complexity: Medium. Reuses Signal's existing lock infrastructure.
- Note: `PASSPHRASE_LOCK` is a soft application lock; it is not the same as passphrase-based database encryption (see above).

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

- Status: `NEEDS_PORT`
- Molly sources: `preferences/BackupsPreferenceFragment.java`, `util/BackupUtil.java`, `util/TextSecurePreferences.java` (`BACKUP_INTERVAL` / `pref_backup_interval`, default 1 day; `BACKUP_MAX_FILES` / `pref_backup_max_files`, default 2), `res/values/arrays.xml` (`backup_intervals`)
- Current Signal: local backups support a daily scheduled time only (`scheduleTimeLabel`, `LocalBackupListener`). No weekly interval and no retention-count setting found.
- What Molly adds: selectable backup interval and a maximum number of retained backup files.
- Security implications: Medium. Retention deletion must never delete the only good backup; backup contents are already encrypted by Signal.
- Complexity: Low–Medium.

#### SOCKS5 / Tor / Orbot proxy

- Status: `NEEDS_PORT`
- Molly sources: `app/src/main/java/org/thoughtcrime/securesms/net/NetworkProxy.kt`, `ProxyProvider`, `ProxySocketFactory`, `SocksProxy`, `DohClient`, `res/xml/preferences_network.xml`, related network preferences
- Molly dependencies: `netcipher` (Tor/Orbot integration), SOCKS support
- Current Signal: has a built-in **Signal proxy** (host/port via `ProxyValues`, `core/network`, `EditProxyActivity`/`EditProxyFragment`). This is not general SOCKS5, Orbot or system-proxy support.
- Security implications: High. DNS-leak prevention is critical (Molly has dedicated commits: `Fix SOCKS proxy DNS leak`, `Fix proxy resolution when no valid SOCKS proxy is available`). Must not send traffic outside the proxy. DoH interactions must be reviewed.
- Complexity: High.
- Prerequisite: build a proxy abstraction over Signal's existing `core/network` configuration rather than replacing it.

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

- Status: `NEEDS_PORT` (individually optional)
- Items: disable Google Safe Browsing in WebViews (Molly `ddc05b14ae`), remove the share-selection recipient limit, remove the debuglog Tracer.
- Security implications: Low–Medium. Each item needs its own review; some change upstream behavior and should be opt-in.
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

#### APK self-updater and reproducible builds

- Status: `OWNER_DECISION`
- Evidence: Molly `apkupdate/*` (`MANAGE_MOLLY_UPDATES`, F-Droid update URL, `ApkUpdateJob`, installer/notifications) and reproducible-build infrastructure (`reprocheck.yml`, pinned build config).
- This is distribution/build infrastructure, not core runtime functionality. It depends on a release channel and signing strategy. Deferred until release strategy is decided.

#### MobileCoin removal

- Status: `OWNER_DECISION`
- Evidence: Molly commit `5a8a33070d Remove MobileCoin payments`.
- This is a divergence (removal of a Signal feature), not a port. It conflicts with the requirement to preserve applicable Signal functionality, so it requires explicit owner approval.

## 3. Signal-JW Feature Candidates

These features require individual investigation and project owner approval.

Verified against Signal-JW `v8.33.2.0-JW` (`da443926af`) and compared with Signal `515db93fb4`.

### 3.1 Summary Table

| Feature | Status | Complexity | Security impact |
|---|---|---|---|
| Encrypted backup export/restore at any time | AWAITING_APPROVAL | High | High |
| Plaintext backup export/import | AWAITING_APPROVAL | Medium–High | High |
| Backup location / removable storage selection | EXISTS_SIGNAL (largely) | Low | Medium |
| Encrypted ZIP backups (`zip4j`) | AWAITING_APPROVAL | Low–Medium | Medium |
| Application passphrase (soft) lock | AWAITING_APPROVAL (duplicates Molly) | Medium | High |
| Link preview image toggle | AWAITING_APPROVAL | Low | Low–Medium |
| Keep view-once media | AWAITING_APPROVAL | Low | High |
| Ignore remote delete requests | AWAITING_APPROVAL | Low–Medium | High |
| Ignore group-admin delete requests | AWAITING_APPROVAL | Low–Medium | High |
| Delete media only (keep message) | AWAITING_APPROVAL | Low | Medium |
| Map type selection | AWAITING_APPROVAL | Low | Low |
| Group invitation controls | AWAITING_APPROVAL | Medium–High | Medium |
| FCM / WebSocket notification preference | EXISTS_SIGNAL | — | — |
| Exit-app action | AWAITING_APPROVAL | Low | Low |
| APK expiry removal | NOT_RECOMMENDED | Low | High |

### 3.2 Candidate Detail

- **Encrypted backup export/restore at any time** — `EncryptedBackupExporter.java` (~529 lines), `XmlBackup`, `ImportExportFragment`, `ImportExportActivity`. Lets the user export/import a full encrypted backup outside onboarding. High security/complexity: overwrites the current database on restore; must interoperate with Signal's current backup format and key model. Duplicate of nothing in current Signal (Signal restricts full backup restore to setup).
- **Plaintext backup export/import** — `PlaintextBackupExporter.java`, `PlaintextBackupImporter.java`. Adds XML plaintext export and additive import. High security risk (plaintext of messages). Would require a dedicated warning flow and explicit confirmation. Not recommended without a security review.
- **Backup location / removable storage selection** — `BACKUP_LOCATION_REMOVABLE_PREF`, `BACKUP_LOCATION_CHANGED`, `MANAGE_EXTERNAL_STORAGE`. Current Signal already has a backup folder picker (SAF). Likely `EXISTS_SIGNAL` or a legacy duplicate; the broad `MANAGE_EXTERNAL_STORAGE` permission is undesirable.
- **Encrypted ZIP backups** — uses `net.lingala.zip4j:zip4j:2.11.6`. Low priority; evaluate only if backup work is approved.
- **Application passphrase (soft) lock** — `PassphrasePromptActivity`, `Passphrase`/`softlock` branches. Duplicates Molly's passphrase lock; do not implement twice. If approved, implement once via the Molly-aligned design.
- **Link preview image toggle** — `preferences_chats__send_link_preview_images`. Current Signal only has a single link-preview on/off setting, no image-specific toggle. Low complexity.
- **Keep view-once media** — `preferences_chats__chat_keep_view_once_messages`. Privacy-sensitive; breaks the view-once expectation. Requires explicit owner approval and clear UI disclosure.
- **Ignore remote delete requests** — `preferences_chats__chat_ignore_remote_delete`. Privacy-sensitive; affects synchronization expectations. Approve only with explicit disclosure.
- **Ignore group-admin delete requests** — `preferences_chats__chat_ignore_admin_delete`. Same concerns as remote delete; group-governance implications.
- **Delete media only** — `preferences_chats__delete_media_only`. Keeps message text while deleting media. Medium privacy impact.
- **Map type selection** — `SignalMapView.getGoogleMapType`, `preferences` map type (normal/hybrid/satellite/terrain/none). Current Signal hardcodes `GoogleMap.MAP_TYPE_NORMAL`. Low complexity; low risk.
- **Group invitation controls** — `whoCanAddYouToGroups` (anyone / nonblocked / onlycontacts / onlysystemcontacts / nobody), `GroupsV2StateProcessor`. Current Signal has group-level "who can add members" permissions, not a per-user policy for who can add you. Medium–high complexity (group state + sync semantics).
- **FCM / WebSocket notification preference** — Current Signal already exposes `forceWebsocketMode` in Data & Storage. `EXISTS_SIGNAL`; do not duplicate.
- **Exit-app action** — `ExitActivity.java`, `MainToolbar`. Minor convenience; low risk.
- **APK expiry removal** — `BuildConfig` expiry behavior. Not recommended: removing update enforcement weakens a security control.

### 3.3 Approval Rule

A candidate may already exist in Signal or Molly. Verify before porting.

No Signal-JW feature may be implemented without explicit project owner approval of that specific feature.

## 4. Explicitly Excluded Features

| Feature | Status |
|---|---|
| SMS database import | EXCLUDED |
| WhatsApp database import | EXCLUDED |
| SMS export/reset (JW, tied to removed SMS) | EXCLUDED |
| WhatsApp database handling (`WhatsappBackup*`, `WaDb*`) | EXCLUDED |

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
