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
- WSS for signaling, authentication, presence, and commands.
- Participant-bound WebRTC offer/answer/ICE relay for approved media sessions.
- WebRTC DTLS-SRTP media transport and DTLS-protected DataChannels.
- Android MediaProjection system consent before screen capture.
- Android runtime CAMERA permission before camera capture.
- Android AccessibilityService must be manually enabled by the device owner.
- File source and destination are selected through Android Storage Access
  Framework pickers; transfer size is capped at 50 MiB.
- Android app credentials encrypted with app-specific Android Keystore
  AES-GCM keys.
- iOS credentials stored using Keychain APIs in development targets.
- Session approval before supported interaction and media capabilities run.
- Release APK checksum, package identity, and signing-certificate validation
  in the Android updater/release pipeline.

## Data and storage boundaries

### Android devices

Client and Controller credentials are encrypted locally using Android
Keystore-backed keys. Screen and camera capture are performed on the Client
device after the operating system grants the required permission. File
content is transferred directly over the WebRTC DataChannel; the signaling
Worker routes only session negotiation messages.

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
state. It is for local development and automated tests, not production.

## Consent and capability boundaries

- Pairing approval authorizes a relationship, not every future capability.
- Each screen, camera, file, or interaction session requires a separate
  Client decision.
- Android MediaProjection consent is mandatory for screen capture.
- Android CAMERA permission is mandatory for camera capture.
- Remote tap, swipe, and lock actions require an approved interaction session
  and the user-enabled AccessibilityService.
- The receiver chooses a destination before a file is written.
- iOS public API and privacy restrictions remain in force.
- Foreground services cannot bypass force-stop or OEM background policies.

## Known network and transfer limitations

- The current WebRTC configuration uses STUN only. TURN relay is not deployed,
  so direct connectivity can fail behind restrictive NATs, carrier-grade NAT,
  or firewalls.
- File transfer is a stop-and-wait prototype with a 50 MiB per-file limit.
  It verifies a final SHA-256 hash but does not yet support resume or signed manifests.
- Physical-device and cross-network acceptance testing remains outstanding.

## Security assurance and certifications

No independent third-party security audit has been completed. MEIZZO does not
claim ISO/IEC 27001, SOC 2, GDPR certification, or any other security
certification. CI, automated tests, WSS, WebRTC encryption, and Android signing
are engineering controls, not certifications.

The recommended next steps are an independent security review, dedicated
security and isolation tests, server-side token hashing/rotation, audit events,
TURN deployment, and documented retention/incident-response procedures.

## Reporting a vulnerability

Please do not publish exploitable details in a public issue. Follow the
private reporting instructions in the repository's
[SECURITY.md](https://github.com/rakhan17/Meizzo/blob/main/SECURITY.md).
