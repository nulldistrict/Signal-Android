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
