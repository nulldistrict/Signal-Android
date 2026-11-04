# Architecture

## 1. Overview

This project is an independently maintained fork of Signal Android.

Signal Android is the primary application codebase.

The project extends Signal with:

- All applicable Molly features.
- Selected Signal-JW features.
- Future project-specific functionality.

The existing Signal application identity and branding must remain unchanged.

## 2. Source Hierarchy

| Source | Responsibility |
|---|---|
| Signal Android | Primary application, messaging infrastructure and cryptographic implementation |
| Molly Android | Reference implementation for required features |
| Signal-JW | Reference implementation for individually approved features |
| This project | Integration, maintenance and project-specific improvements |

Signal is the primary source of truth.

Molly and Signal-JW must not be blindly merged into the project.

## 3. Architectural Principles

### 3.1 Preserve Signal

Preserve:

- Messaging functionality
- End-to-end encryption
- Identity and key management
- Registration
- Group functionality
- Linked devices
- Database behavior
- Android application lifecycle

Do not unnecessarily modify security-critical components.

### 3.2 Preserve Molly Features

All Molly features are required.

Before porting a feature:

1. Inspect the current Signal implementation.
2. Inspect the corresponding Molly implementation.
3. Determine whether the feature already exists.
4. Identify required changes.
5. Port only missing functionality.

Do not replace existing Signal implementations without a clear technical reason.

### 3.3 Selective Signal-JW Integration

Signal-JW is a feature reference.

Each candidate feature requires:

- Source inspection
- Compatibility analysis
- Dependency analysis
- Security review
- Project owner approval

SMS database import and WhatsApp database import are excluded.

### 3.4 Feature Isolation

Organize custom functionality into logical areas:

- Backup and restore
- Application locking
- Privacy controls
- Group controls
- Message behavior
- Media behavior
- Location and maps
- Notification transport
- Application preferences

These are logical boundaries, not a requirement to create a separate Gradle module for every feature.

Use the existing application architecture whenever possible.

## 4. Application Identity

Preserve the existing Signal application identity.

Do not change:

- Application ID
- Package identity
- Branding
- Existing signing configuration

Any proposed identity change requires explicit project owner approval.

A different signing certificate may prevent installation as an update over an existing Signal installation.

## 5. Upstream Update Model

Signal Android is the direct upstream.

Expected workflow:

Signal release
    ↓
Upstream change analysis
    ↓
Integration into project
    ↓
Molly feature compatibility checks
    ↓
Approved JW feature compatibility checks
    ↓
Testing
    ↓
Release

Do not use Molly as an intermediate upstream base.

## 6. Git Organization

Recommended remotes:

| Remote | Purpose |
|---|---|
| `origin` | Project repository |
| `signal` | Official Signal Android |
| `molly` | Molly feature reference |
| `signal-jw` | Signal-JW feature reference |

Recommended branch concepts:

- `main`: stable project history
- `integration/*`: upstream integration
- `feature/molly-*`: Molly feature ports
- `feature/jw-*`: approved JW feature ports

Adapt these names to the existing repository history.

Do not rewrite existing branches just to match this recommendation.

## 7. Dependency Policy

Before adding a dependency:

1. Check existing dependencies.
2. Check compatibility.
3. Review its license.
4. Review maintenance activity.
5. Review security history.
6. Consider APK size and build impact.

Avoid unnecessary dependencies.

## 8. Testing

Test:

- Core messaging
- Registration
- Database migrations
- Backup and restore
- Application locking
- Notifications
- Linked devices
- Privacy-sensitive features
- Upstream integration regressions

Use synthetic test data.

## 9. Architecture Decisions

Document significant decisions with:

- Context
- Options considered
- Chosen approach
- Consequences
- Migration implications

Avoid unnecessary architectural complexity.

## 10. Reference Version Gap

The first-phase audit established that:

- This project is based on Signal **8.34.2**.
- Molly `main` (`892d6264ba`) is based on Signal **8.19.2**.
- Signal-JW's latest release (`v8.33.2.0-JW`) is based on Signal **8.33.2**.

Implications:

- Molly is a **feature reference only**. Its branches must not be merged into this project.
- Molly features must be ported commit-by-commit and adapted to current Signal architecture.
- Signal-JW custom work is squashed per release and must be ported from diffs and named feature branches, never merged.
- Every port must record the reference commit it was derived from.
- Upstream Signal remains the only merge source.

This gap is expected to widen as upstream advances; the porting model must tolerate it.

## 11. Audit Findings Affecting Architecture

- Several features previously attributed to Molly/JW already exist in current Signal and must not be re-implemented: linked devices/tablet linking, alternative app icons, disappearing call history (scheduled call-event expiration), and the FCM/WebSocket preference.
- Signal already has a built-in Signal proxy. General SOCKS5/Tor support must extend that network layer rather than replace it.
- The passphrase-derived database secret and the application lock are separate concerns with separate security models and separate review paths.
- Molly's former FOSS build variant was consolidated into `store`/`website` distribution flavors. FOSS packaging is a distribution decision, not a runtime feature.
- See `FEATURE_MATRIX.md` sections 0, 2 and 3 for the verified inventory.
