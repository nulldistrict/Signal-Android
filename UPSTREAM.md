# Upstream Maintenance

## 1. Objective

Maintain this project against future Signal Android releases while preserving Molly integrations and approved Signal-JW features.

Signal Android is the only primary upstream.

Molly and Signal-JW are feature reference repositories.

## 2. Repositories

| Name | Repository | Role |
|---|---|---|
| Signal Android | https://github.com/signalapp/Signal-Android | Primary upstream |
| Molly Android | https://github.com/mollyim/mollyim-android | Feature reference |
| Signal-JW | https://github.com/johanw666/Signal-Android | Feature reference |

## 3. Update Principles

1. Follow Signal Android releases directly.
2. Do not replace the Signal base with Molly.
3. Preserve all integrated Molly features.
4. Preserve all approved JW features.
5. Review upstream security changes carefully.
6. Keep custom changes identifiable.
7. Never assume a conflict-free merge guarantees compatibility.

## 4. Update Procedure

### Phase A — Discovery

Record:

- Current project commit
- Current Signal base commit
- New Signal release or commit
- Current Molly reference commit
- Relevant Molly changes
- Current Signal-JW reference commit
- Relevant approved JW changes

### Phase B — Change Analysis

Review changes affecting:

- Messaging
- Cryptography
- Key management
- Database schema
- Backup and restore
- Android storage
- Notifications
- Application lifecycle
- Dependencies
- Build configuration
- Custom feature integration points

Produce an update report before integration.

### Phase C — Integration

1. Preserve the current working state.
2. Create a dedicated integration branch.
3. Integrate the selected Signal update.
4. Review conflicts individually.
5. Preserve custom functionality.
6. Inspect affected database migrations.
7. Avoid unrelated refactoring.

Never discard custom code to obtain a clean merge.

### Phase D — Verification

Run appropriate:

- Gradle configuration checks
- Compilation
- Unit tests
- Instrumentation tests
- Database migration tests
- Backup and restore tests
- Notification tests
- Security-sensitive tests
- Manual smoke tests

Record tests that were not executed.

### Phase E — Release Decision

Verify:

- Application starts.
- Registration works.
- Messaging works.
- Existing data is preserved.
- Molly features remain functional.
- Approved JW features remain functional.
- Application identity remains unchanged.
- Release signing is correct.
- Source and release artifacts correspond to the same commit.

## 5. Feature Patch Maintenance

Where practical:

- Keep features in separate commits.
- Document modifications to upstream files.
- Maintain feature-specific tests.
- Record upstream dependencies.
- Keep custom code reviewable.

Do not force artificial modularization.

## 6. Update Report Template

```text
Update date:
Previous project commit:
New Signal commit:
Signal version:
Molly reference commit:
Signal-JW reference commit:

Upstream changes reviewed:
Molly features affected:
Approved JW features affected:
Conflicts:
Security changes:
Database migration concerns:
Build result:
Test results:
Manual verification:
Known limitations:
Release decision:
```

## 7. Automation Roadmap

Potential future scripts:

- scripts/upstream-check
- scripts/upstream-diff
- scripts/check-patches
- scripts/verify-features
- scripts/build-release
- scripts/release-notes

Implement automation only after understanding the repository and update workflow.

Automation must never silently discard changes or resolve unknown conflicts.

## 8. Recovery

Preserve recoverable Git state before integration.

Do not delete old branches or tags during routine updates.

If integration fails:

1. Stop.
2. Preserve the failed state.
3. Record the problem.
4. Identify affected changes.
5. Propose a recovery plan.
6. Obtain approval before destructive recovery operations.
