# Security Policy

## 1. Security Objective

Preserve the security properties of Signal and Molly while integrating additional privacy and application features.

The project must not weaken end-to-end encryption, authentication, identity verification or key management for convenience.

## 2. Security Principles

- Preserve Signal's cryptographic protocols.
- Prefer established upstream implementations.
- Avoid custom cryptography.
- Minimize sensitive data exposure.
- Protect authentication and encryption secrets.
- Avoid unnecessary network communication.
- Keep security-sensitive changes small and reviewable.
- Document changes affecting user privacy.
- Preserve upstream security fixes.

## 3. Sensitive Data

Treat the following as sensitive:

- Encryption keys
- Authentication tokens
- Registration credentials
- Message contents
- Contact information
- Attachment contents
- Backup encryption material
- Private application databases
- User location information
- Private logs and diagnostic information

Never include sensitive data in:

- Git commits
- Public issue reports
- Build logs
- Debug output
- Screenshots
- Test fixtures
- Release artifacts

Use synthetic test data.

## 4. Data at Rest

Inspect and preserve Molly's existing data-at-rest protections.

Verify:

- Database encryption
- Backup encryption
- Key storage
- Passphrase handling
- Temporary file handling
- Application lock behavior
- Database migration behavior

Do not assume that application locking automatically encrypts the database.

Document the difference between:

- UI/application lock
- Access to encryption keys
- Database encryption
- Android device encryption

## 5. Backup Security

Encrypted backups should be the preferred option.

Plaintext backup functionality, if implemented, must:

- Be explicitly requested by the user.
- Clearly explain the security risk.
- Avoid accidental automatic export.
- Avoid uploading plaintext data.
- Avoid unnecessary temporary copies.
- Handle failed exports safely.

Backup and restore tests must use synthetic data.

## 6. Privacy-Sensitive Message Behavior

Features that alter message deletion or view-once behavior require explicit documentation.

Examples:

- Ignoring remote deletion requests
- Ignoring group administrator deletion requests
- Changing view-once media handling
- Preserving message text after media deletion

For each feature, document:

- What changes locally
- What changes remotely
- What other participants may observe
- What happens on linked devices
- Whether the behavior persists after an update

Do not silently enable these behaviors.

## 7. Network and Notifications

Review all network-related changes.

Verify:

- FCM behavior
- WebSocket behavior
- UnifiedPush behavior
- SOCKS proxy support
- Tor / Orbot integration
- Connection fallback behavior
- Duplicate notification behavior

Do not claim that a connection is anonymous or private without evidence.

Avoid exposing message contents in network diagnostics.

## 8. Permissions

Request only permissions required for a documented feature.

Review changes to:

- Contacts
- Camera
- Microphone
- Location
- Notifications
- Files and media
- Background execution

Explain why new permissions are required.

## 9. Dependencies

Before introducing a dependency, review:

- Project activity
- Security advisories
- License
- Transitive dependencies
- Data collection behavior
- Network behavior

Prefer existing maintained dependencies.

## 10. Release Security

- Keep release signing keys private.
- Never commit signing passwords or private keys.
- Do not use debug signing for public releases.
- Protect CI secrets.
- Publish source corresponding to distributed binaries.
- Record the exact source commit used for each release.
- Do not publish unverified artifacts.

## 11. Security Review Checklist

Before releasing a security-sensitive change:

- [ ] No cryptographic protocol changes without a dedicated review.
- [ ] No secrets in source code or logs.
- [ ] No unintended network communication.
- [ ] No accidental plaintext backup.
- [ ] No weakened authentication or key handling.
- [ ] Permissions are justified.
- [ ] Privacy-sensitive defaults are documented.
- [ ] Relevant tests have passed.
- [ ] Upstream security changes are preserved.

## 12. Vulnerability Handling

Do not publish exploitable vulnerability details in public issue trackers before responsible assessment.

Use an appropriate private reporting channel where available.

Document affected versions, impact, mitigation and fixed versions when disclosure is appropriate.
