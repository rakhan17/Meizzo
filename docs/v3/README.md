# MEIZZO v3 development

This branch is the isolated development line for MEIZZO 3.x. The v2.5.0 stable line remains preserved on `stable/v2.5.0`.

## Versioning

- Stable releases: `vX.Y.Z`
- Beta releases: `vX.Y.Z-beta.N`
- Initial v3 preview: `v3.0.0-beta.1`

## Development rules

- Keep v2.5.0 stable behavior intact unless a deliberate migration is documented.
- Implement features end-to-end across Client, Controller, signaling protocol, tests, and documentation.
- Do not present mock controls or placeholder screens as working features.
- Require explicit Client consent for every sensitive session capability.
- Preserve Android signing identity and validate package/version metadata in release builds.
- Keep iOS capability claims separate from Android until physical-device validation is complete.

## v3 priority status

| Priority | Current implementation state |
|---|---|
| Live screen streaming | Android prototype implemented: MediaProjection consent, WebRTC video publishing, Controller video rendering, ICE signaling, and aspect-ratio-aware touch mapping. Requires physical-device/network testing and TURN before broad release. |
| Remote camera streaming | Android prototype implemented: Camera2 capture after runtime CAMERA permission, WebRTC video publishing, and Controller camera viewer. Rear/front camera selection and device compatibility need physical testing. |
| Remote screen interaction | Android tap/swipe is wired to touch gestures on the live video surface. A separate approved interaction session is required; AccessibilityService must be enabled by the device owner. |
| Remote lock screen | Android lock action is implemented through AccessibilityService on Android 9+. The user must explicitly enable MEIZZO in Android Accessibility settings and approve an interaction session. |
| File transfer | Android bidirectional prototype implemented over an ordered WebRTC DataChannel with per-chunk acknowledgements, 12 KiB chunks, a 50 MiB limit, and Storage Access Framework destination consent. Needs interrupted-transfer, large-file, and cross-network testing. |

## Session and signaling foundation

- Client pairing remains a persistent app-managed connection; there is no Client-side Stop pairing control.
- Session requests require explicit Client approval.
- Controller session termination and reconnect cleanup remove stale authorization state so a fresh request can be shown.
- Cloudflare Durable Object and local Node signaling implementations support approved-session WebRTC offer/answer/ICE signaling for screen, camera, and file sessions.
- Regression tests cover repeated session requests, termination, Controller disconnect, Client reconnect, and authorized media-signal relay.

## Security boundaries

- WSS authenticates and authorizes signaling. It is not itself a media transport.
- Media signaling is accepted only for an approved screen, camera, or file session and only from a session participant.
- WebRTC media transport currently uses STUN only. TURN relay deployment, reconnection/recovery, bitrate adaptation, and end-to-end media security review remain before screen/camera streaming is production-ready.
- File transfer currently uses stop-and-wait Base64 chunks over an ordered DTLS-protected WebRTC DataChannel; it is a functional prototype, not yet optimized for large files or resumable transfer.
- No iOS hosting capability is claimed. iOS capture and control require a separate platform implementation and Apple permission constraints.
- Android permissions and Accessibility access remain controlled by the device owner and operating system.
- A Device Owner grant must never be treated as silent consent or a bypass for MediaProjection.
