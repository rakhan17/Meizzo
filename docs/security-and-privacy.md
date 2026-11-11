# Security and privacy

MEIZZO is a beta project. This page describes implemented controls and known
security gaps; it is not a certification statement or an independent audit.

## Implemented controls

- Six-digit pairing offers with a maximum ten-minute lifetime.
- Single-use offer consumption after successful pairing.
- Explicit Client approval before pairing is completed.
- Rate limits for pairing attempts in the hosted Worker.
- Cryptographically random role-specific authentication tokens.
- Per-Client authorization checks for device lists, commands, and sessions.
- WSS transport for the published Android release configuration.
- Android app credentials encrypted with app-specific Android Keystore
  AES-GCM keys.
- iOS credentials stored using Keychain APIs in the development targets.
- Session approval before supported interaction commands are routed.
- Release APK checksum, package identity, and signing-certificate validation
  in the Android updater/release pipeline.

## Data and storage boundaries

### Android devices

Client and Controller credentials are encrypted locally using Android
Keystore-backed keys. Telemetry includes device status fields exposed by the
platform and implemented by the app. Unsupported fields should remain
unavailable rather than be represented as zero.

### Hosted signaling

The Cloudflare Worker routes WSS connections to a Durable Object. The
Durable Object persists pairing relationships, token records, presence,
telemetry metadata, pending requests, and session state in its storage.
The current beta does not provide a full user-account system or a documented
user-facing data-retention dashboard.

Server-side authentication tokens are represented as token records in the
Durable Object state. Hash-at-rest storage, a complete token rotation
workflow, formal audit-event retention, and an operational key-management
runbook remain hardening tasks.

### Local Node.js harness

The Node.js service stores state in process memory. Restarting it loses that
state. It is for local development and automated tests, not a production
deployment model.

## Consent and capability boundaries

- Pairing approval authorizes a relationship, not every future capability.
- Each supported session request requires a Client decision.
- An approved session does not itself enable screen capture, camera, files,
  location, or unrestricted input.
- Android permissions remain controlled by Android and the device owner.
- iOS public API and privacy restrictions remain in force.
- Foreground services cannot bypass force-stop or OEM background policies.

## Security assurance and certifications

No independent third-party security audit has been completed. MEIZZO does not
claim ISO/IEC 27001, SOC 2, GDPR certification, or any other security
certification. CI, automated tests, WSS, and Android signing are engineering
controls, not certifications.

The recommended next steps are an independent security review, dedicated
security and isolation tests, server-side token hashing/rotation, audit events,
and documented retention/incident-response procedures.

## Reporting a vulnerability

Please do not publish exploitable details in a public issue. Follow the
private reporting instructions in the repository's
[SECURITY.md](https://github.com/rakhan17/Meizzo/blob/main/SECURITY.md).
