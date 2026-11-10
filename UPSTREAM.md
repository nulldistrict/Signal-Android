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

### 2.1 Verified Reference Snapshot

Recorded during the first-phase source audit.

| Item | Value |
|---|---|
| Project base | Signal Android `515db93fb4` (`v8.34.2`) |
| Project HEAD at audit | `d83917f7eafc20721ffb87c3dbb2669acc88fd81` (`dev`) |
| Molly `main` | `892d6264ba007b5458a8b74cf9183c665f1f17a1`, version `8.19.2-4`, tag `v8.19.2-4` |
| Molly upstream base | Signal `8.19.2` (`c47a74ba0961903eeaeda6d22347a990056c3e83`) |
| Signal-JW `main` | `515db93fb48206d63406cbfb48c5816f6af1d9e2` (upstream mirror) |
| Latest Signal-JW release | `v8.33.2.0-JW` = `da443926af72a1132f44e620182a0d7f0f397677`, based on Signal `6e9dddc01f` (`v8.33.2`) |

Reference clones used for the audit (outside this repository, read-only):

- `~/Projects/molly-reference`
- `~/Projects/signal-jw-reference`

### 2.2 Reference Repository Characteristics

**Molly**

- Molly `main` is based on Signal **8.19.2**, while this project is based on Signal **8.34.2**.
- This version gap means Molly cannot be merged or used as an intermediate base. Port features commit-by-commit, adapted to current Signal architecture.
- Molly also maintains `origin/upstream-main` and `origin/upstream-stable` tracking branches; use them only to understand which Signal version a Molly feature was written against.
- Molly's build has `store` and `website` distribution flavors plus `prod`/`staging` environments. The former separate FOSS variant was consolidated in commit `45947267f1`; `README.md` still advertises "Molly-FOSS", so treat that as a documentation/build discrepancy.

**Signal-JW**

- Signal-JW `main` mirrors upstream Signal.
- Custom work is published as one squashed "Added extra options" commit per release (e.g. `da443926af` on `v8.33.2.0-JW`) plus older named feature branches (e.g. `Control_who_can_add_you_to_groups`, `Delete_media_only`, `Set_Google_map_type`).
- Port from the squashed diff and the named branches, never by merging or cherry-picking the release branch wholesale.

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

### 5.1 Low-conflict update rule

Every custom feature must remain a self-contained commit on top of the upstream base. Do not squash features together, and do not rewrite unrelated upstream code in the same commit.

After each Signal update:

1. Rebase the feature commits onto the new upstream tag one at a time, in the order recorded in `FEATURE_MATRIX.md`.
2. Resolve conflicts inside the feature that owns the changed upstream code.
3. Keep the feature's tests and documentation changes in the same commit as the feature.
4. Run the reproducible release workflow on the rebased branch.

A feature that cannot be rebased cleanly is stopped and reviewed instead of being dropped or auto-resolved. This keeps conflicts local to one feature and makes the next update cheaper.

Prefer extending existing Signal extension points (repositories, settings stores, build config fields) over editing large upstream files. When an upstream file must change, keep the change as small as possible and document it in the feature's `FEATURE_MATRIX.md` entry.

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

Current automation:

- `.github/workflows/reproducible-release.yml` builds the unsigned release APK three times, verifies identical hashes, signs the verified APK and publishes GitHub release artifacts.
- `scripts/ci/build-fork.sh` and `scripts/ci/sign-fork.sh` are the container entrypoints used by that workflow.

Recommended next automation, in order:

1. A non-destructive upstream check that reports the new Signal tag, the current base, and the custom commits that need rebasing.
2. A CI job that compiles and runs targeted tests on each rebased feature commit.
3. A release-notes generator that lists the feature commits included in a tag.

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

## 9. Reference Refresh Checklist

At the start of each upstream cycle:

1. Fetch the current Signal release and record its tag and commit.
2. Fetch Molly `origin/main` and record its commit, version name and the Signal version it is based on.
3. Fetch Signal-JW and identify the latest `vX.Y.Z.0-JW` tag and its base Signal commit.
4. Update the snapshot table in section 2.1.
5. Diff each reference against its own base, not against this project.
6. Map affected custom features to `FEATURE_MATRIX.md`.
7. Review security-relevant upstream changes before porting anything.
8. Do not modify the reference repositories.

A large reference-to-project version gap is expected and normal; it is handled by commit-level porting, not by merging reference branches.
