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
  - Stats-driven sender bitrate ceiling adapts to available outgoing bandwidth, remote packet loss, and RTT while retaining WebRTC's native congestion control.
  - Controller initiates bounded ICE-restart renegotiation after sustained disconnection or ICE failure.
  - ICE offer/answer/candidate exchange through authenticated WSS signaling.
  - Display-aspect-aware touch mapping on the Controller video surface.
- Remote camera streaming prototype:
  - Camera2 capture after Android runtime CAMERA permission.
  - WebRTC video publishing and Controller preview, with Controller-triggered front/rear camera switching.
- Remote screen interaction:
  - Tap, swipe, long-press, and two-finger trackpad scrolling from the live video surface.
  - Back, Home, Recents, notification shade, Quick Settings, power menu, and lock-screen actions through the user-enabled AccessibilityService.
  - Focused-field text insertion at the active selection, Backspace, Enter, Tab, Escape, arrow keys, and selected Ctrl shortcuts.
  - Controller can save a remote video-frame screenshot to its Pictures/MEIZZO folder.
  - A separate approved interaction session is required; AccessibilityService must be enabled manually by the device owner.
- Voice and media controls:
  - Optional two-way microphone audio tracks over WebRTC. Each side requires RECORD_AUDIO permission and an explicit in-session action; Controller microphone capture uses its own foreground service.
  - Controller can mute received Client audio; both sides can locally stop microphone transmission.
  - Remote Client media volume up/down/mute/unmute and Client alarm ON/OFF controls.
  - Internal app/system audio capture is not implemented; physical-device voice routing and echo-cancellation validation remain.
- Clipboard transfer:
  - Manual text clipboard read/write in both directions, with a separate Client approval dialog for every operation.
- Remote file management:
  - Client-granted Storage Access Framework tree browsing with folder navigation and metadata listing.
  - Individual file downloads through a separate approved WebRTC files session and per-file Client confirmation.
  - Folder creation, rename, and file deletion when the provider grants write access and advertises support; folder deletion is disabled.
- File transfer prototype:
  - Bidirectional single- and multi-file send/receive over an ordered WebRTC DataChannel.
  - 12 KiB stop-and-wait chunks, unique transfer IDs, bounded retries, duplicate completion handling, sequence validation, and SHA-256 verification.
  - Incremental progress and explicit cancellation; 50 MiB maximum per file.
  - Durable sender/receiver checkpoints with acknowledged-offset resume after a files session is re-established; source and destination SAF grants are persisted where Android permits.
  - Worker-provided STUN/TURN configuration and bounded ICE-restart recovery; parallel chunks and large-file optimization are not implemented.
- Android Keystore-backed AES-GCM credential storage, foreground connection
  service-owned WSS lifecycle, queued signaling delivery while the Activity is
  detached, network-aware reconnect backoff, and updater verification.
- RustDesk upstream source pinned as a Git submodule at
  `third_party/rustdesk` (`95cca3f661f635b3cc85c04935fe8d96d8f432ef`).
  The source is available for integration work, but its engine is not yet
  linked into the MEIZZO APKs.
- Remote alarm playback uses a looping AudioTrack in the Client foreground
  service with a local notification stop action. Controller command feedback
  now waits for the Client result instead of claiming success on send. Runtime
  audio, OEM background, and acknowledgement behavior still require device
  acceptance.

## Signaling and media architecture

The hosted signaling path uses Cloudflare Workers and a Durable Object.
The Worker persists signaling state through Durable Object storage. The local
Node.js service is a development/test harness with in-memory state only.

The protocol uses WSS for pairing, authentication, presence, commands, session
authorization, and WebRTC offer/answer/ICE exchange. Screen and camera media
are sent over WebRTC video tracks, optional voice uses WebRTC audio tracks, and
files are sent over a WebRTC data channel. WSS is not the media transport.

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

- Internal app/system audio capture, automatic clipboard synchronization, and
  full hardware-keyboard/modifier coverage. Current clipboard support is
  manual and explicitly approved; current voice support is microphone audio.
- RustDesk's upstream engine is not integrated into the APKs; current features
  are native MEIZZO prototypes and do not yet provide full RustDesk parity.
- Production TURN secret configuration and network traversal acceptance. The
  Worker can issue short-lived Cloudflare Realtime TURN credentials when
  configured; without them, restrictive NATs, carrier-grade NAT, and firewalls
  may prevent connectivity.
- Physical tuning of adaptive bitrate thresholds, congestion behavior, stream recovery, and Wi-Fi/mobile handover across real networks.
- Resumable transfers, large-file optimization, and signed transfer manifests.
- Physical-device acceptance across Android OEMs, screen sizes, and Android
  versions, including backgrounding and battery-management behavior.
- iOS screen/camera hosting, remote input, and file transfer.
- Android Enterprise enrollment or Managed Mode.
- Independent security audit or formal security certification.

## Runtime and permission boundaries

- MediaProjection requires the system's explicit screen-capture consent.
- Camera streaming requires Android CAMERA permission.
- Client and Controller microphone transmission each require RECORD_AUDIO permission and a user-initiated in-session action; Controller voice uses a microphone foreground service.
- Remote tap, swipe, keyboard, navigation, and lock actions require the user-enabled AccessibilityService.
- Clipboard reads/writes and remote file browse/mutations require additional Client confirmation.
- Remote file browsing requires a persisted Storage Access Framework tree grant; rename/delete additionally require write permission and provider support.
- File source and destination are selected through Android's document picker.
- Android foreground services cannot override force-stop, OEM battery
  management, disabled autostart, revoked permissions, or network loss.

## Acceptance checklist

Before calling a capability production-ready, record:

1. Exact app version and release asset.
2. Device model, Android version, and OEM settings.
3. Client and Controller installation result.
4. Pairing and explicit approval result.
5. Screen/camera stream start, quality, latency, camera switching, and stop behavior.
6. Two-way voice start/mute/stop, permission denial, audio routing, echo, and background behavior.
7. Remote alarm: verify continuous playback beyond 30 seconds, immediate remote
   OFF, local notification Stop alarm, background playback, and volume behavior.
8. Tap, swipe, keyboard shortcuts, system navigation, lock, and Accessibility permission-denial behavior.
9. Clipboard approval, remote folder navigation and creation, file download, rename/delete, and SAF permission revocation.
10. File transfer in both directions, including multi-file batches, progress, cancellation, interrupted
   transfers.
11. Direct and relayed network behavior, Wi-Fi/mobile handover, and reconnect.
12. Multi-device isolation, revocation, and session cleanup.
13. Sanitized logs without tokens, codes, passwords, or private data.

See [v3 implementation notes](v3/README.md),
[upstream/license notes](v3/UPSTREAM_AND_LICENSES.md), and
[testing](testing.md) for the detailed development and acceptance matrix.
