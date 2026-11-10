# Changelog

All notable changes to this project will be documented here.

This project follows a practical changelog format inspired by Keep a Changelog.

Version numbers and release dates must reflect actual releases.

## [Unreleased]

### Added

- Project documentation foundation.
- First-phase source audit of Signal, Molly and Signal-JW, recorded in `FEATURE_MATRIX.md` and `UPSTREAM.md`.
- Verified Molly feature inventory with per-feature status, source files, dependencies and security impact.
- Verified Signal-JW candidate inventory, all marked `AWAITING_APPROVAL`.
- Established reference snapshot: Molly `892d6264ba` (Signal 8.19.2 base), Signal-JW `v8.33.2.0-JW` (`da443926af`, Signal 8.33.2 base).
- Molly feature: optional debug logging, toggled from Help settings. Disabling drops new log output and deletes existing logs.
- Molly feature: unknown-contact blocking, toggled from Privacy settings. Explicitly blocked senders are always blocked; saved contacts, profile-sharing senders and shared-group members remain allowed.
- Molly feature: link a device without scanning a QR code. The intro sheet offers manual entry of the linking URI and keeps the existing validation and link flow.
- Molly feature: configurable local backup frequency (daily/weekly) and retention count (1–10), with pruning applied to both folder and legacy backup formats.
- Molly hardening: Safe Browsing disabled for in-app WebViews, the remote share-recipient limit removed, and app-side debug-log Tracer instrumentation removed so trace data is not included in debug logs.
- Molly application-lock extensions: restored the "Immediately" screen-lock timeout, made a zero timeout actually lock on backgrounding instead of being coerced to 60 seconds, and block screen-lock-protected content from Android 13+ recents screenshots and from FLAG_SECURE windows while locked.
- Molly secure RAM wiper: wipe free process memory on lock and restart the process, matching Molly, using the GPLv3 `im.molly:native-utils` native code, vendored as `libmolly-wipe.so`. The wipe is skipped during migrations and device transfers; process death is the fallback when the foreground service cannot start.
- Molly SOCKS5/Orbot proxy: the proxy address field accepts `socks5://host:port` and `socks5h://host:port` (default port 9050). User SOCKS5 proxies always use remote DNS, and OkHttp traffic (including content and media) is routed through the same proxy with DNS-over-HTTPS resolved through it. WebRTC media is not proxied.
- Molly data encryption at rest: the local master secret is now protected by an Argon2id-derived key bound to a non-exportable keystore HMAC key, and the SQLCipher database secret is wrapped under the passphrase-derived master secret instead of remaining keystore-sealed. App startup waits for the passphrase to unlock the database. Existing legacy passphrase formats are migrated on first successful unlock.
- GitHub reproducible release infrastructure: a workflow builds the `githubProdRelease` APK three times inside Signal's `reproducible-builds/` Docker image, fails if the unsigned hashes differ, then signs the verified APK with the release key and publishes it. Release signing is configured through git-ignored `keystore.release.properties` or Actions secrets.
- Molly feature: FCM messages arriving while the passphrase-locked database is unavailable no longer crash or attempt to open the database. High-priority pushes post the "you may have messages" notification, other pushes are deferred, FCM job fetching is skipped, and a token rotated while locked is uploaded after the next unlock.
- Signal-JW feature: a "Send link preview images" toggle in Chat settings, enabled by default and disabled while link previews themselves are off. When off, outgoing link previews keep their title/description but omit the downloaded image.
- Signal-JW feature: map type selection in the location picker. Normal, Satellite and Terrain buttons change the live map and are remembered for future picks and saved-location snapshots.
- Signal-JW feature: optional "Keep view-once media" in Chat settings, off by default. When enabled, incoming view-once messages are stored like normal messages so their media remains available; the setting clearly discloses that this breaks the sender's view-once expectation.
- Signal-JW feature: optional "Ignore delete requests" in Chat settings, off by default. When enabled, "delete for everyone" requests no longer remove the local copy; the attempted delete is marked with a ❗ reaction. The setting discloses that it only affects this device.
- Signal-JW feature: optional "Ignore group admin deletes" in Chat settings, off by default. When enabled, valid group-admin deletes keep the local copy and mark the attempt with a ‼ reaction; the administrator and target thread are still validated.

### Approved but not yet implemented

The project owner approved the remaining Signal-JW candidates and the following project changes. They are not released functionality until each is implemented and tested.

- Signal-JW: encrypted backup export/restore at any time.
- Signal-JW: plaintext backup export/import (requires explicit warning flow).
- Signal-JW: encrypted ZIP backups.
- Signal-JW: delete media only.
- Signal-JW: group invitation controls.
- Signal-JW: exit-app action.
- APK self-updater.
- MobileCoin payment removal.

Explicitly not approved: the Signal-JW application passphrase (soft) lock and APK expiry removal.

### Changed

- Initial project architecture and maintenance strategy documented.
- Documented that Molly's Signal 8.19.2 base creates a version gap versus this project's Signal 8.34.2 base, requiring commit-level feature porting rather than merging.
- Documented reference refresh checklist in `UPSTREAM.md`.

### Fixed

- None.

### Security

- Initial security requirements documented.
- Audit identified the security-sensitive porting areas: passphrase-derived database secret, secure RAM wiping, application lock, proxy/Tor DNS-leak handling, plaintext backup, and deletion/view-once behavior.
- SMS and WhatsApp database import confirmed permanently excluded.
- Debug logging can now be fully disabled by the user, and disabling it clears persisted logs.
- Unknown-contact blocking is local-only (`settings.block_unknown`) and does not alter existing contacts, trusted conversations or group membership. Group-profile sharing remains an allow-list exception.
- Debug logs no longer include app instrumentation traces; the in-app WebViews no longer use Google Safe Browsing, which is a privacy/security trade-off documented in `FEATURE_MATRIX.md`.
- User SOCKS5 proxy configuration fails closed: an invalid or unreachable proxy prevents connections instead of falling back to a direct connection. DNS for proxied OkHttp traffic is resolved via DNS-over-HTTPS through the proxy, and the content proxy is bypassed while SOCKS5 is active.
- A passphrase-protected database now requires the passphrase to unwrap the SQLCipher database secret; the keystore-sealed copy is removed while the passphrase is active. The derived key is bound to the device keystore, and there is no passphrase-recovery path. While locked, background process starts cannot open the database until the user unlocks.
- Release signing material is never committed. GitHub Actions stages the keystore only in `$RUNNER_TEMP` and mounts it read-only; the workflow signs only an unsigned APK whose three independent builds produced identical SHA-256 hashes.

## Release History

No public releases yet.

---

## Changelog Guidelines

For each release, record changes under the following categories:

### Added

New features and functionality.

### Changed

Changes to existing behavior or configuration.

### Fixed

Bug fixes and regressions.

### Security

Security fixes and security-relevant changes.

### Removed

Removed features or functionality.

### Upstream

Important Signal or Molly changes inherited by this project.

Do not list unfinished work as released functionality.

Every release entry should correspond to an identifiable Git tag and source commit.
