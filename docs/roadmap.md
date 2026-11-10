# MEIZZO Roadmap

This roadmap records intended work, not a promise that every item is scheduled. Status changes only when implementation and evidence are available.

## Current beta focus

- [x] Separate Android Client and Controller packages.
- [x] Separate iOS Client and Controller Simulator targets.
- [x] Six-digit pairing with explicit Client approval.
- [x] Authenticated pairing tokens and per-device session boundaries.
- [x] Backend heartbeat, presence, and telemetry foundations.
- [x] Android debug-only endpoint configuration.
- [ ] Verify BlueStacks Air networking and pairing on the maintainer's Mac.
- [ ] Publish a new Android beta only after production endpoint and signing compatibility are verified.
- [ ] Complete end-to-end UI validation on physical Android devices.

## Near-term engineering priorities

### Reliability

- Reconnect and stale-session regression coverage.
- More robust backend deployment and persistent authorization strategy.
- Observable diagnostics that never expose credentials.
- Explicit error states for endpoint, TLS, and authentication failures.

### Product experience

- Refine Client and Controller visual hierarchy and accessibility.
- Device detail screens with freshness indicators for telemetry.
- Clear separation between Controller-to-service status and each Client's presence.
- Setup guidance for permissions and unsupported hardware.

### Distribution

- Stable Android signing identity and update compatibility.
- Release checks for APK package identity, signatures, checksums, and changelog assets.
- Signed iOS device distribution when Apple signing and provisioning are configured.
- Clear distinction between Simulator ZIP artifacts and device-installable IPA packages.

## Longer-term exploration

- WebRTC media sessions with managed STUN/TURN infrastructure.
- Carefully scoped screen-sharing and remote assistance where platform APIs permit.
- Operational monitoring, rate limits, and recovery procedures for hosted signaling.
- Expanded automated UI and physical-device test coverage.

## How to interpret status

A checked item means the repository contains the corresponding implementation foundation. It does not automatically mean all supported platforms or physical environments have passed end-to-end runtime testing. Consult [Implementation status](implementation-status.md) and [Testing](testing.md) for evidence and limitations.
