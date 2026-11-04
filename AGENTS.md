# AGENTS.md — Project Instructions

## 1. Project Goal

Develop an independently maintained fork of Signal Android.

The project uses Signal Android as its primary codebase and integrates:

1. All applicable Molly features.
2. Selected Signal-JW features approved by the project owner.
3. Future project-specific improvements.

The goal is to preserve the existing Signal application identity and branding while extending its functionality.

Do not change the existing application ID, package identity, or branding unless explicitly authorized by the project owner.

## 2. Upstream Repositories

Primary upstream:

- Signal Android: https://github.com/signalapp/Signal-Android

Feature reference repositories:

- Molly Android: https://github.com/mollyim/mollyim-android
- Signal-JW: https://github.com/johanw666/Signal-Android

Signal Android is the primary source of truth.

Molly and Signal-JW are feature references, not alternative project bases.

Do not blindly merge either repository into this project.

## 3. Feature Scope

### Molly

All existing Molly features are required.

Investigate each feature and port missing functionality into the Signal-based project.

Do not duplicate features that already exist in Signal.

### Signal-JW

Investigate Signal-JW features individually.

Only integrate features explicitly approved by the project owner.

The following features are permanently excluded:

- SMS database import
- WhatsApp database import

Do not implement these features or their dependencies.

## 4. Mandatory Working Rules

Before changing code:

1. Inspect the Git working tree.
2. Inspect the active branch, remotes, tags and recent commits.
3. Read the relevant source code.
4. Identify affected modules and dependencies.
5. Explain the proposed implementation.
6. Identify security, privacy and compatibility risks.

After changing code:

1. Review the complete diff.
2. Run relevant tests.
3. Build the affected components when appropriate.
4. Report modified files.
5. Report actual test results.
6. Document remaining risks.

Never fabricate test results.

## 5. Git Safety

Preserve all existing user work and repository history.

Never execute the following without explicit approval:

- `git reset --hard`
- `git clean`
- Force pushes
- Destructive branch deletion
- Forced checkout
- Unrequested rebase
- Unrequested merge
- Unrequested cherry-pick
- Commands that discard uncommitted changes

Never automatically resolve unknown conflicts.

Never overwrite user modifications.

Prefer isolated feature commits and reviewable integration commits.

## 6. Architecture Rules

1. Signal Android remains the primary codebase.
2. Preserve the existing application ID.
3. Preserve the existing package identity.
4. Preserve the existing branding.
5. Preserve all applicable Signal functionality.
6. Preserve all Molly features.
7. Integrate only approved Signal-JW features.
8. Avoid unnecessary modifications to upstream code.
9. Document unavoidable upstream modifications.
10. Avoid duplicated implementations.
11. Keep feature-specific changes logically isolated.
12. Do not weaken cryptography, authentication or key management.

Do not assume a feature is missing. Inspect the current Signal source first.

## 7. Build Resource Constraints

The primary development machine is a Dell G15 laptop.

Prevent excessive resource consumption and system freezes.

Required Gradle settings:

```properties
org.gradle.workers.max=2
org.gradle.parallel=false
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=true
```

Mandatory rules:

- Never intentionally use all CPU cores for Gradle.
- Use a maximum of two Gradle workers.
- Keep Gradle parallel execution disabled.
- Never run multiple Gradle builds concurrently.
- Do not automatically increase worker count.
- Prefer incremental builds and targeted tests.
- Inspect memory before large builds.
- Stop and investigate excessive resource consumption.

These settings do not impose a hard resource limit on every compiler or child process.

Inspect the existing Gradle configuration before modifying it.

## 8. Security Requirements

- Preserve Signal's cryptographic protocols.
- Do not introduce custom cryptography without a dedicated security review.
- Never log message contents, keys or authentication tokens.
- Preserve Molly's data protection features.
- Do not introduce insecure secret storage.
- Keep release signing credentials outside the repository.
- Preserve upstream security fixes.
- Explicitly document privacy-sensitive behavior changes.
- Do not describe an application lock as database encryption unless it actually encrypts the database.

## 9. Application Identity

The existing Signal application identity must remain unchanged.

Do not modify:

- Application ID
- Package identity
- Branding
- Existing signing configuration

unless explicitly approved by the project owner.

Inspect the existing build configuration before making any changes.

Do not assume that a differently signed APK can update an existing Signal installation.

## 10. Upstream Maintenance

Signal Android is the primary upstream.

For each update:

1. Identify the new Signal release.
2. Inspect upstream changes.
3. Identify affected custom features.
4. Preserve Molly integrations.
5. Preserve approved JW integrations.
6. Resolve conflicts deliberately.
7. Run relevant tests.
8. Verify critical functionality.

A conflict-free merge does not prove functional compatibility.

Never discard custom changes to obtain a clean merge.

## 11. Documentation

Maintain:

- `AGENTS.md`
- `ARCHITECTURE.md`
- `FEATURE_MATRIX.md`
- `UPSTREAM.md`
- `PORTING_GUIDE.md`
- `SECURITY.md`
- `RELEASE.md`
- `CHANGELOG.md`

Update documentation whenever architecture, feature status, security behavior or release procedures change.

## 12. Licensing

Respect the licenses and copyright notices of all upstream projects.

Inspect license compatibility before porting code.

Do not remove required copyright notices or license headers.

Do not imply official endorsement by Signal or Molly.

## 13. Task Boundaries

Work in small, independently verifiable tasks.

Do not implement unrelated features in one task.

Do not perform broad refactoring during a feature port.

For destructive, security-sensitive or identity-changing operations, stop and request explicit approval.

When information is missing, inspect the repository instead of guessing.
