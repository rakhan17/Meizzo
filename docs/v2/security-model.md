# MEIZZO V2 security model

## Trust boundaries

- A six-digit pairing code is a short-lived invitation, not a permanent
  credential.
- Explicit Client approval creates the initial Controller relationship.
- Every later request is authorized against the specific Client relationship.
- A WebSocket connection does not authorize screen, camera, files, location,
  or input by itself.
- A session approval is scoped to a Client, Controller, and session type.
- Android and iOS operating-system permission prompts remain authoritative.

## Controls implemented in the beta

- WSS for the published Android release configuration.
- Expiring, single-use pairing offers and attempt limits.
- Random role-specific bearer tokens.
- Per-device authorization and revocation.
- Android Keystore-backed local credential encryption.
- iOS Keychain integration in the development targets.
- Client approval for pairing and session transitions.
- Interaction commands gated by an approved interaction session.

## Current gaps

- Hosted Durable Object state contains token records; server-side token
  hashing and a complete rotation lifecycle remain future hardening.
- Formal audit-event storage and review workflows are incomplete.
- Retention, incident response, and operational key-management procedures
  need to be documented and tested.
- The local Node.js harness uses in-memory state and is not production
  persistent.
- No independent security audit or formal security certification is claimed.

## Sensitive capability requirements

Before adding screen capture, camera, location, file access, or general input:

1. Define capability-specific authorization scopes.
2. Require explicit, understandable Client consent.
3. Use platform-native permission flows.
4. Bind every operation to an active session and authorized identity.
5. Log security-relevant transitions without storing secrets or private content.
6. Ensure revocation and session end stop the capability promptly.
7. Add isolation tests for two Clients and multiple Controllers.

## Assurance roadmap

The next assurance work is independent security review, focused isolation and
abuse tests, token hashing/rotation, durable audit events, and operational
incident/retention procedures. Automated CI success must not be described as
certification.
