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
| Live screen streaming | Not implemented yet. Approved-session WebRTC signaling relay is in place; Android MediaProjection capture, peer connection, and Controller video rendering remain. |
| Remote camera streaming | Not implemented yet. Uses the same planned WebRTC media plane; camera capture, permission flow, and rendering remain. |
| Remote screen interaction | Android tap/swipe gesture injection is implemented through the user-enabled AccessibilityService. Controller currently sends normalized coordinates; tying gestures to a live video surface remains. |
| Remote lock screen | Android lock action is implemented through AccessibilityService on Android 9+. The user must explicitly enable MEIZZO in Android Accessibility settings. |
| File transfer | Not implemented yet. Requires an approved file session, Android Storage Access Framework, encrypted WebRTC data channel, chunking, and integrity checks. |

## Session and signaling foundation

- Client pairing remains a persistent app-managed connection; there is no Client-side Stop pairing control.
- Session requests require explicit Client approval.
- Controller session termination and reconnect cleanup remove stale authorization state so a fresh request can be shown.
- Cloudflare Durable Object and local Node signaling implementations support approved-session WebRTC offer/answer/ICE signaling for screen, camera, and file sessions.
- Regression tests cover repeated session requests, termination, Controller disconnect, Client reconnect, and authorized media-signal relay.

## Security boundaries

- WSS authenticates and authorizes signaling. It is not itself a media transport.
- Media signaling is accepted only for an approved screen, camera, or file session and only from a session participant.
- WebRTC media transport, TURN relay deployment, and end-to-end media encryption validation must be completed before screen/camera streaming is called production-ready.
- Android permissions and Accessibility access remain controlled by the device owner and operating system.
- A Device Owner grant must never be treated as silent consent or a bypass for MediaProjection.
