# Feature Matrix

## Status Definitions

- `UNKNOWN`: Not investigated.
- `EXISTS_SIGNAL`: Already implemented in Signal.
- `EXISTS_MOLLY`: Implemented in Molly; integration status must be verified.
- `NEEDS_PORT`: Missing from Signal and requires integration.
- `AWAITING_APPROVAL`: Candidate JW feature awaiting project owner approval.
- `IN_PROGRESS`: Implementation underway.
- `IMPLEMENTED`: Code integration complete.
- `TESTED`: Relevant tests passed.
- `BLOCKED`: Cannot currently be implemented.
- `EXCLUDED`: Explicitly outside project scope.

Do not mark a feature as implemented without inspecting its actual source code.

## 1. Signal Core

Preserve all applicable Signal functionality.

Examples:

- Registration
- End-to-end encrypted messaging
- Group messaging
- Voice and video calls
- Attachments
- Contact management
- Linked devices
- Message synchronization
- Key and identity management
- Safety number verification
- Disappearing messages
- Database migrations

Status: Must be verified against the selected Signal base commit.

## 2. Molly Features

All Molly features are required.

| Feature | Initial status |
|---|---|
| Data encryption at rest | UNKNOWN |
| Passphrase protection | UNKNOWN |
| Secure RAM wiper | UNKNOWN |
| Automatic application lock | UNKNOWN |
| Multi-device linking | UNKNOWN |
| Android tablet linking | UNKNOWN |
| UnifiedPush | UNKNOWN |
| Unknown contact blocking | UNKNOWN |
| Disappearing call history | UNKNOWN |
| Custom backup scheduling | UNKNOWN |
| SOCKS proxy support | UNKNOWN |
| Tor / Orbot integration | UNKNOWN |
| Optional or disabled debug logs | UNKNOWN |
| Signal backup compatibility | UNKNOWN |
| Reproducible builds | UNKNOWN |
| FOSS build variant | UNKNOWN |
| Google-services build variant | UNKNOWN |

For every feature, compare the actual Molly implementation with the selected Signal base.

Do not assume historical features or implementation details remain unchanged.

## 3. Signal-JW Feature Candidates

These features require individual investigation and project owner approval.

| Feature | Initial status |
|---|---|
| Encrypted backup options | AWAITING_APPROVAL |
| Plaintext backup options | AWAITING_APPROVAL |
| Backup location selection | AWAITING_APPROVAL |
| Application passphrase lock | AWAITING_APPROVAL |
| Link preview image options | AWAITING_APPROVAL |
| View-once media behavior | AWAITING_APPROVAL |
| Ignore remote delete requests | AWAITING_APPROVAL |
| Ignore group administrator delete requests | AWAITING_APPROVAL |
| Preserve text after media deletion | AWAITING_APPROVAL |
| Map type selection | AWAITING_APPROVAL |
| Group invitation controls | AWAITING_APPROVAL |
| FCM notification preferences | AWAITING_APPROVAL |
| WebSocket notification preferences | AWAITING_APPROVAL |
| Other discovered features | UNKNOWN |

A candidate may already exist in Signal or Molly. Verify before porting.

## 4. Explicitly Excluded Features

| Feature | Status |
|---|---|
| SMS database import | EXCLUDED |
| WhatsApp database import | EXCLUDED |

Do not implement these features or their supporting dependencies.

## 5. Verification Requirements

For every feature, record:

- Feature name
- Source repository
- Source version or commit
- Relevant source files
- Existing Signal implementation
- Existing Molly implementation
- Required changes
- Dependencies
- Settings and default behavior
- Security implications
- Tests
- Current status

## 6. Completion Criteria

Molly integration is complete when all applicable Molly features have been investigated and verified.

JW integration is complete only for features explicitly approved by the project owner.

Excluded features must remain excluded.

All integrated features must have appropriate test results documented.
