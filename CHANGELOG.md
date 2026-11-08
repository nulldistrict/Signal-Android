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
