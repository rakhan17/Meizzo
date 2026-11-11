# Android permission model

This document describes the current Android v3 development line. Capabilities
marked as prototypes are implemented in the app code but still require the
physical-device and cross-network acceptance work listed in the v3 roadmap.

| Capability | Android platform boundary | v3 implementation state |
|---|---|---|
| Network signaling | `INTERNET`, TLS/WSS | Implemented |
| Pairing and device presence | Authenticated WebSocket, six-digit pairing, heartbeat | Implemented |
| Battery/storage/device metadata | Public Android APIs | Implemented telemetry snapshot |
| Live screen viewing | MediaProjection consent + media-projection foreground service + WebRTC | Implemented prototype; Client must approve the session and Android must show its system capture prompt |
| Remote tap and swipe | User-enabled AccessibilityService with gesture capability | Implemented; requires a separately approved interaction session |
| Remote navigation | Accessibility global actions for Back, Home, and Recents | Implemented; requires a separately approved interaction session |
| Remote lock screen | Accessibility global action (Android 9+) | Implemented; requires a separately approved interaction session |
| Remote screenshot | PixelCopy of the Controller's currently rendered live-screen surface | Implemented in Controller; saved to Pictures/MEIZZO on Android 10+, or app-specific Pictures storage on older supported versions |
| Camera streaming | Runtime CAMERA permission + WebRTC | Implemented prototype; requires Client session approval and camera permission |
| Location | Runtime coarse/fine location permission and explicit Find My toggle | Opt-in last-known location snapshot only; not continuous tracking |
| Flashlight | CAMERA permission and torch hardware | Implemented command path |
| Alarm | Foreground service and audio policy | Implemented command path |
| File transfer | WebRTC DataChannel + Storage Access Framework | Implemented prototype; each receiving transfer requires a user-selected destination |
| Managed Mode | Legitimate Device Owner/Profile Owner enrollment | Not implemented |

## Consent and security boundaries

- Pairing does not grant screen, camera, file, location, or remote-input access.
- The Client must approve each sensitive session request.
- Screen capture additionally requires Android's system MediaProjection
  confirmation. MEIZZO cannot silently grant or reuse that consent.
- Remote input is unavailable until the device owner enables MEIZZO in Android
  Accessibility settings and approves an interaction session.
- Accessibility access is limited to the implemented gestures and global
  actions. MEIZZO does not expose arbitrary shell execution or protected-UI
  bypasses.
- Screenshots are captured from the Controller's live video surface. They do
  not bypass Android capture restrictions or create a hidden capture path.
- Location remains opt-in and uses cached last-known coordinates only.
- Device Owner status must never be treated as permission to bypass
  MediaProjection or runtime consent.

Unavailable or denied data must be represented as unknown/unavailable, never
as a fabricated zero. Physical-device, OEM, Android-version, and cross-network
validation remains pending until the final testing phase.
