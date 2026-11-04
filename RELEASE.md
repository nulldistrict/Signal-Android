 # Build and Release Guide

## 1. Objective

Define a repeatable process for building, signing, verifying and distributing this project.

The project is primarily intended for personal use, with optional public source-code and APK distribution.

## 2. Build Environment

Primary development machine: Dell G15 laptop.

Builds must use conservative resource settings.

Required Gradle configuration:

```properties
org.gradle.workers.max=2
org.gradle.parallel=false
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=true
```

Additional rules:

- Never run multiple Gradle builds concurrently.
- Prefer targeted test tasks.
- Avoid unrestricted native compilation.
- Inspect memory availability before large builds.
- Do not increase Gradle workers automatically.
- Do not assume Gradle settings impose a hard limit on every child process.

## 3. Build Configuration

Before building, inspect:

- Available Gradle tasks
- Existing build variants
- Application ID
- Version code and version name
- Signing configuration
- Required SDK versions
- Required environment variables
- Required local configuration files

Do not invent task names or build variants.

Use tasks discovered from the actual project.

## 4. Development Builds

Development builds should:

- Use the appropriate debug variant.
- Be distinguishable from release builds.
- Avoid release signing credentials.
- Support targeted testing.
- Avoid modifying user data unnecessarily.

## 5. Release Signing

Use a dedicated release signing key.

Requirements:

- Keep the keystore outside the repository.
- Keep passwords outside tracked files.
- Use environment variables or an appropriate local secrets mechanism.
- Back up the signing key securely.
- Never publish the private signing key.
- Never replace the signing identity casually.

Losing the signing key may prevent existing installations from accepting future updates.

Do not use the Android debug keystore for public releases.

## 6. Versioning

Maintain:

- Application version name
- Application version code
- Upstream Signal version
- Molly base commit
- Project release tag

Version codes must increase for updates installed over an existing release.

Do not assume upstream version codes can be copied without considering the project's own release history.

## 7. Release Verification

Before publishing:

- [ ] Correct application ID
- [ ] Correct version name and version code
- [ ] Correct release signing certificate
- [ ] Successful build
- [ ] Relevant tests passed
- [ ] Application launches
- [ ] Registration and messaging smoke tests passed
- [ ] Backup and restore checked where applicable
- [ ] Molly features verified
- [ ] Custom features verified
- [ ] No signing secrets included
- [ ] Source commit recorded
- [ ] License and attribution notices preserved
- [ ] Release notes prepared

Do not publish if a critical security or data migration issue remains unresolved.

## 8. Release Artifacts

A release may include:

- APK
- SHA-256 checksum
- Release notes
- Source code reference
- Git tag
- Build environment information
- Signing certificate fingerprint

Never claim reproducible builds unless they have actually been verified.

## 9. Public Distribution

Before public distribution:

- Verify AGPL-3.0 obligations.
- Preserve upstream license and copyright notices.
- Provide corresponding source code as required.
- Identify modifications to upstream projects.
- Avoid implying official Signal or Molly endorsement.
- Clearly identify the project as an independent fork.
- Explain supported Android versions and known limitations.

## 10. Release Notes Template

```text
Version:
Release date:
Source commit:
Molly base:
Signal version:

Added:
Changed:
Fixed:
Security:
Known issues:

Build variant:
Signing certificate fingerprint:
Artifact checksum:
```

## 11. Rollback

Do not assume Android permits downgrading an installed application without consequences.

Before a release:

- Preserve the previous source tag.
- Preserve the previous release artifact.
- Preserve release notes.
- Maintain a verified backup and recovery procedure.

Do not downgrade database schemas or application versions automatically.

