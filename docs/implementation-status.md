# Implementation status

This page describes the MEIZZO v3 development branch. It separates implemented
code, automated build evidence, and physical-device acceptance. A successful CI
build does not prove runtime behavior on every Android vendor or network.

## Android v3 implementation

- Separate Android applications:
  - Client: `com.meizzo.client`
  - Controller: `com.meizzo.controller`
- Six-digit pairing offers, explicit pairing approval, role-specific tokens,
  multi-device authorization, presence, telemetry, and revocation.
- Session request, approval, rejection, Client-initiated termination, and
  Controller termination, with stale-session cleanup on reconnect.
- Live screen streaming prototype:
  - Android MediaProjection system consent.
  - Foreground media-projection service.
  - WebRTC video publishing from Client and video rendering in Controller.
  - ICE offer/answer/candidate exchange through authenticated WSS signaling.
  - Display-aspect-aware touch mapping on the Controller video surface.
- Remote camera streaming prototype:
  - Camera2 capture after Android runtime CAMERA permission.
  - WebRTC video publishing and Controller preview.
- Remote screen interaction:
  - Tap and swipe gestures from the live video surface.
  - A separate approved interaction session is required.
  - Android AccessibilityService must be enabled manually by the device owner.
- Remote lock screen:
  - Android AccessibilityService global lock action on Android 9+.
- File transfer prototype:
  - Bidirectional send/receive over an ordered WebRTC DataChannel.
  - Android Storage Access Framework for source and destination selection.
  - 12 KiB stop-and-wait chunks with per-chunk acknowledgements and SHA-256 verification.
  - 50 MiB maximum file size; transfer resume is not implemented.
- Android Keystore-backed AES-GCM credential storage, foreground connection
  services, reconnect backoff, and updater verification.

## Signaling and media architecture

The hosted signaling path uses Cloudflare Workers and a Durable Object.
The Worker persists signaling state through Durable Object storage. The local
Node.js service is a development/test harness with in-memory state only.

The protocol uses WSS for pairing, authentication, presence, commands, session
authorization, and WebRTC offer/answer/ICE exchange. Screen and camera media
are sent over WebRTC video tracks. Files are sent over a WebRTC data channel.
WSS is not the media transport.

The Worker and Node service support approved screen, camera, and files session
types, participant-bound signaling, and Client-initiated session termination.

## Automated verification

The v3 CI workflow builds Android Client and Controller independently and runs
signaling regression tests for pairing, multi-device isolation, revocation,
session lifecycle, reconnect cleanup, media signaling, and Client-initiated
media-session termination.

Latest workflow evidence is available in
[GitHub Actions](https://github.com/rakhan17/Meizzo/actions?query=branch%3Adevelop%2Fv3).
Automated success is build and protocol evidence only. It is not a substitute
for real Android device testing, network traversal testing, or a security audit.

## Current release tracks

- Android Stable: `v2.5.0`, published on `main`.
- Android v3 development: `3.0.0-beta.1`, branch `develop/v3`; no v3 APK
  has been published.
- iOS: development/Simulator track only; no physical-device distribution or
  remote-host feature parity is claimed.

## Not yet release-ready

- TURN relay deployment. Current WebRTC ICE configuration uses STUN only, so
  restrictive NATs, carrier-grade NAT, and firewalls may prevent connectivity.
- Adaptive bitrate, congestion control tuning, stream recovery, and network
  handover behavior.
- Resumable transfers, large-file optimization, and transfer integrity
  verification beyond ordered reliable DTLS-protected DataChannel delivery.
- Physical-device acceptance across Android OEMs, screen sizes, and Android
  versions, including backgrounding and battery-management behavior.
- iOS screen/camera hosting, remote input, and file transfer.
- Android Enterprise enrollment or Managed Mode.
- Independent security audit or formal security certification.

## Runtime and permission boundaries

- MediaProjection requires the system's explicit screen-capture consent.
- Camera streaming requires Android CAMERA permission.
- Remote tap, swipe, and lock actions require the user-enabled AccessibilityService.
- Each sensitive session requires explicit Client approval.
- File source and destination are selected through Android's document picker.
- Android foreground services cannot override force-stop, OEM battery
  management, disabled autostart, revoked permissions, or network loss.

## Acceptance checklist

Before calling a capability production-ready, record:

1. Exact app version and release asset.
2. Device model, Android version, and OEM settings.
3. Client and Controller installation result.
4. Pairing and explicit approval result.
5. Screen/camera stream start, quality, latency, and stop behavior.
6. Tap, swipe, lock, and Accessibility permission-denial behavior.
7. File transfer in both directions, including cancellation and interrupted
   transfers.
8. Direct and relayed network behavior, Wi-Fi/mobile handover, and reconnect.
9. Multi-device isolation, revocation, and session cleanup.
10. Sanitized logs without tokens, codes, passwords, or private data.

See [v3 implementation notes](v3/README.md),
[upstream/license notes](v3/UPSTREAM_AND_LICENSES.md), and
[testing](testing.md) for the detailed development and acceptance matrix.
