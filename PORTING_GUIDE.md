# Feature Porting Guide

## 1. Objective

Integrate all applicable Molly features and individually approved Signal-JW features into the Signal Android codebase.

Signal remains the primary application implementation.

SMS database import and WhatsApp database import are excluded.

## 2. Source Investigation

Before porting a feature:

1. Locate its implementation in the reference repository.
2. Record its source commit or version.
3. Inspect related commits.
4. Locate corresponding functionality in Signal.
5. Determine whether Signal already implements it.
6. Identify dependencies.
7. Identify preferences and UI components.
8. Identify database and storage changes.
9. Review security and privacy implications.

Never copy files without understanding their dependencies.

## 3. Porting Priority

For Molly features:

1. Inspect Signal's implementation.
2. Inspect Molly's implementation.
3. Reuse existing Signal functionality where possible.
4. Port missing Molly functionality.
5. Preserve Molly's original behavior.

For Signal-JW features:

1. Inspect Signal's implementation.
2. Inspect Molly's implementation.
3. Inspect Signal-JW's implementation.
4. Present the feature and its implications to the project owner.
5. Port only after approval.

## 4. Feature Integration Workflow

### Step 1 — Specification

Document:

- User-visible behavior
- Default behavior
- Settings
- Reference implementation
- Dependencies
- Security implications
- Compatibility requirements

### Step 2 — Source Analysis

Identify:

- Classes
- Methods
- Resources
- Preference keys
- Database changes
- Gradle dependencies
- Android permissions
- Tests

### Step 3 — Integration Plan

Explain:

- Files to modify
- Files to add
- Upstream files affected
- Interactions with Signal functionality
- Interactions with Molly features
- Required tests

### Step 4 — Implementation

Implement one logical feature at a time.

Avoid unrelated refactoring.

Do not silently change existing behavior.

### Step 5 — Verification

Run relevant:

- Unit tests
- Instrumentation tests
- Compilation tasks
- Migration tests
- Manual tests where necessary

Review the complete Git diff.

### Step 6 — Documentation

Update:

- FEATURE_MATRIX.md
- SECURITY.md when security behavior changes
- CHANGELOG.md when a change becomes part of a release

## 5. Security-Sensitive Areas

### Backup and Restore

- Prefer encrypted backups.
- Require explicit action for plaintext export.
- Clearly explain plaintext backup risks.
- Avoid unnecessary temporary plaintext files.
- Verify backup integrity and restore behavior.
- Never log backup contents or secrets.

### Application Lock

- Distinguish application locking from encryption at rest.
- Protect sensitive key material.
- Handle process termination and lock-state transitions.
- Test authentication failures and recovery.

### View-Once Media

- Document the exact behavior.
- Preserve default behavior unless explicitly approved.
- Review privacy implications.
- Test linked-device and media-storage interactions.

### Remote and Administrator Deletion

- Document which deletion requests are affected.
- Do not modify cryptographic protocol behavior.
- Require explicit user configuration for privacy-sensitive behavior.
- Test synchronization and local/remote state consistency.

### Group Invitation Controls

- Document who can invite the user.
- Test all supported policy combinations.
- Verify preference migrations.

### Notifications

- Review FCM, WebSocket and UnifiedPush interactions.
- Avoid duplicate notification delivery.
- Avoid logging message content.
- Test offline and reconnect behavior.

### Maps

- Preserve location permission requirements.
- Document map provider dependencies.
- Avoid unrelated location data transmission.
- Test unavailable map services.

## 6. Commit Strategy

Prefer one logical feature per commit.

Examples:

```text
feat(molly): integrate secure RAM wiping
feat(molly): add automatic application locking
feat(molly): integrate UnifiedPush support
feat(jw): add approved group invitation controls
feat(jw): add approved map type preferences
```

Do not combine unrelated dependency upgrades, formatting and feature changes.

## 7. Completion Requirements

A feature is complete only when:

- Source implementation is understood.
- Integration is documented.
- Defaults are intentional.
- Security implications are reviewed.
- Relevant tests have been run.
- Existing Signal functionality remains intact.
- Existing Molly functionality remains intact.
- FEATURE_MATRIX.md is updated.

## 8. Implementation Roadmap

Derived from the first-phase audit (`FEATURE_MATRIX.md`). Phases are ordered by risk and dependency, not by feature count.

### Phase 0 — Foundation (no user-visible feature)

- Confirm the build works at the current base.
- Confirm conservative Gradle limits (per `AGENTS.md`): 2 workers, parallel off, `-Xmx2g`, in-process Kotlin compilation.
- Establish a feature-branch naming convention and a per-feature commit template.
- Do not change application identity.

### Phase 1 — Low-risk, low-coupling ports

Start with features that reuse existing Signal infrastructure and have contained blast radius.

1. Optional debug logging (#1 candidate).
2. Unknown-contact blocking.
3. Manual device linking without QR scan.
4. Map type selection (only if approved — see JW section).
5. Custom backup scheduling and retention.

Rationale: small diffs, low security surface, useful early validation of the upstream-update workflow.

### Phase 2 — Privacy and security hardening

1. Application lock extensions (passphrase/biometric/triggers/screenshot/PIP). Reuse Signal's existing `KeyCachingService` and screen-lock settings.
2. Secure RAM wiper. Depends on Phase 2 application-lock lifecycle hooks.
3. Minor hardening items (Safe Browsing, share limit, Tracer), each reviewed separately.

### Phase 3 — Data-at-rest security

1. Passphrase-derived database secret (Argon2id + keystore HMAC + AES-GCM).
2. Migration/rollback strategy and forgotten-passphrase recovery.
3. Dedicated crypto review before merge.
4. Encrypted preferences if required by the above.

This phase must not start until Phase 2 lock/lifecycle behavior is stable. It must not weaken `KeyStoreHelper` sealing. It is distinct from the application lock.

### Phase 4 — Network transport and dependencies

1. SOCKS5/Tor/Orbot proxy abstraction over Signal's existing `core/network` proxy configuration.
2. DNS-leak prevention and proxy-resolution fallbacks (mirror Molly's fixes conceptually, not blindly).
3. UnifiedPush transport, settings, distributor UI, refresh jobs, and coexistence with FCM/WebSocket.

Highest dependency and testing burden. UnifiedPush additionally requires an external server decision.

### Phase 5 — Owner decisions

- FOSS/degoogled packaging (distribution flavors).
- APK self-updater and reproducible builds.
- MobileCoin removal (if ever approved).
- Signal-JW candidates only after per-feature approval.

### Dependencies Between Features

- Secure RAM wiper → application lock lifecycle.
- Passphrase database secret → application lock/lifecycle stability and crypto review.
- SOCKS/Tor → network configuration layer; must precede any network-dependent hardening.
- UnifiedPush → notification transport abstraction; must not duplicate FCM/WebSocket delivery.
- Custom backup scheduling → any approved backup-format work.
- All Signal-JW candidates → explicit owner approval before any code.

### Recommended First Feature

Optional debug logging, followed by unknown-contact blocking. Both are low-complexity, reuse existing Signal patterns, and provide an early end-to-end test of the audit → port → test → documentation → commit workflow without touching cryptography, identity or key management.
