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

### 5.1 Local signing

Create `keystore.release.properties` in the repository root (it is git-ignored):

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is resolved relative to the repository root. When the file is absent, release builds are unsigned, which is the upstream Signal behaviour. The Gradle release build type picks up the signing config automatically when the file exists.

The signing certificate fingerprint of an existing installation cannot change without a data migration/export path. Verify it before publishing:

```bash
apksigner verify --print-certs app-fork-release.apk
```

### 5.2 GitHub Actions signing secrets

The reproducible release workflow expects these repository secrets:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore (`base64 -w0 release.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

The workflow never prints the keystore or its passwords. The keystore is staged in `$RUNNER_TEMP`, mounted read-only into the signing container, and never committed.

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

### 8.1 Reproducible GitHub release workflow

`.github/workflows/reproducible-release.yml` builds the `githubProdRelease` variant inside Signal's own `reproducible-builds/` Docker image. It is triggered manually or by pushing a `v*` tag and never runs on pull requests.

The workflow:

1. Builds the same unsigned APK three times on separate runners.
2. Compares the three SHA-256 hashes. A mismatch fails the workflow; nothing is signed or published.
3. Signs the verified unsigned APK with the release keystore.
4. Uploads the signed APK, its SHA-256 checksum and `BUILD_INFO.txt`.
5. Publishes a GitHub release when a tag was supplied.

Verify a locally built unsigned APK against a published release with `reproducible-builds/apkdiff/apkdiff.py`, which ignores signing metadata:

```bash
uv run reproducible-builds/apkdiff/apkdiff.py \
  path/to/local-fork-unsigned.apk \
  path/to/downloaded-fork-release.apk
```

The signed and unsigned APKs are not byte-identical because signing changes the archive; the reproducibility guarantee applies to the unsigned build inputs and code contents.

The Docker image is rebuilt from `reproducible-builds/Dockerfile` at the built commit. If upstream changes the image, the same change applies to the fork and the workflow remains aligned with Signal's verified build environment.

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

