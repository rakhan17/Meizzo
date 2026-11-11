# Implementation status

This page describes behavior present in the beta.5 release source and
automated checks. It separates code/build evidence from runtime acceptance.
A successful CI build does not prove that every feature works on every device.

## Verified in the current Android beta source

- Separate Android applications:
  - Client: `com.meizzo.client`
  - Controller: `com.meizzo.controller`
- Six-digit pairing offers with expiry, one-time consumption, and rate limits.
- Explicit Client approval before a Controller relationship is created.
- Role-specific authentication tokens and per-Client authorization.
- Multiple paired Clients per Controller.
- Heartbeat presence, online/offline transitions, and telemetry delivery.
- Per-device command routing, rename, revoke, and snapshot request paths.
- Authorization-only session lifecycle: request, pending, approve/reject,
  restore approved state after reconnect, and end.
- Interaction commands are gated by an approved interaction session.
- Android Keystore-backed AES-GCM credential storage in both apps.
- Foreground connection services, reconnect backoff, and boot receivers.
- Android updater validation for release metadata, checksum, package identity,
  signing-certificate identity, and Package Installer handoff.

## Signaling deployment

The hosted signaling path uses Cloudflare Workers and a Durable Object.
The Worker persists the signaling state through Durable Object storage.
The local Node.js service remains a development/test harness with in-memory
state only.

The hosted Worker currently advertises protocol version 2 and capabilities
for pairing, token authentication, presence, telemetry, device commands, and
multi-device routing.

## Verified by automated tests and CI

- Signaling tests cover pairing, one-time code use, authentication,
  multi-device isolation, revocation, presence, and session lifecycle paths.
- Android CI run [42946246894](https://github.com/rakhan17/Meizzo/actions/runs/42946246894)
  completed successfully for Client and Controller debug builds.
- iOS CI run [42946246991](https://github.com/rakhan17/Meizzo/actions/runs/42946246991)
  completed successfully for Simulator build/smoke jobs.
- Android release run
  [42948592178](https://github.com/rakhan17/Meizzo/actions/runs/42948592178)
  completed successfully and published beta.5 signed APKs, updater metadata,
  and SHA-256 checksums.

## Current release

- Tag: `v0.1.0-beta.5`
- Channel: Android beta prerelease
- Assets: Client APK, Controller APK, `MEIZZO-UPDATE.json`,
  `SHA256SUMS.txt`
- Release source workflow commit: `8ddb147c4af628a5166f455c6b58b4d4fbe3dc66`

The release is a beta build. Physical-device and multi-instance runtime
acceptance must still be recorded separately.

## Not implemented

- WebRTC screen/camera media transport, STUN/TURN relay, and adaptive quality.
- MediaProjection screen capture and camera streaming.
- General Accessibility-based remote input.
- Arbitrary file browsing or transfer.
- Location sharing.
- Android Enterprise enrollment or Managed Mode.
- Signed iOS distribution for physical iPhones.
- Independent security audit or formal security certification.

Session type labels such as `screen`, `camera`, and `files` currently
represent authorization/session metadata only. They do not implement those
capabilities.

## Runtime and platform limitations

- Android foreground services improve connection continuity but cannot
  override force-stop, OEM battery management, disabled autostart, revoked
  permissions, or loss of network.
- Flashlight requires supported hardware and camera permission.
- Alarm and other supported commands depend on OS/device behavior.
- Telemetry fields depend on platform API availability and permission.
- iOS Simulator success is not evidence of physical iOS installation,
  background behavior, or feature parity.

## Security hardening still required

- Server-side token hashing and rotation lifecycle.
- Durable audit events for security-sensitive transitions.
- Explicit data-retention and operational monitoring policies.
- Independent security review and isolation-focused tests.
- Production incident response and deployment runbooks.

## Acceptance checklist

Before calling a capability production-ready, record:

1. Exact app version and release asset.
2. Device model, OS version, and relevant OEM settings.
3. Client and Controller installation result.
4. Pairing and explicit approval result.
5. Background/foreground, reconnect, reboot, and network interruption result.
6. Multi-device isolation and revocation result.
7. Permission-denial and unsupported-hardware behavior.
8. Sanitized logs without tokens, codes, passwords, or private data.

See [testing](testing.md) for the repeatable manual matrix.
