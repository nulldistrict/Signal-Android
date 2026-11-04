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
