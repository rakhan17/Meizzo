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
| Remote tap, swipe, and long-press | User-enabled AccessibilityService with gesture capability | Implemented; requires a separately approved interaction session |
| Remote navigation | Accessibility global actions for Back, Home, Recents, notifications, Quick Settings, and the power menu | Implemented; requires a separately approved interaction session |
| Remote text input | Accessibility window access and an editable, focused, non-password field | Appends up to 2,000 characters; custom editors may reject ACTION_SET_TEXT |
| Remote keyboard actions | Accessibility window access and a focused editable non-password field | Backspace fallback on older versions; Enter, Tab, Escape, and arrow keys use Android 13+ Accessibility input connections; full keycode/shortcut coverage is not implemented |
| Remote media volume | Android AudioManager | Media stream volume raise/lower/mute/unmute, gated by an approved interaction session |
| Clipboard transfer | Android ClipboardManager | Text-only read/write up to 10,000 characters; every operation requires a separate Client approval dialog |
| Remote lock screen | Accessibility global action (Android 9+) | Implemented; requires a separately approved interaction session |
| Remote screenshot | PixelCopy of the Controller's currently rendered live-screen surface | Implemented in Controller; saved to Pictures/MEIZZO on Android 10+, or app-specific Pictures storage on older supported versions |
| Camera streaming | Runtime CAMERA permission + camera foreground service + WebRTC | Implemented prototype; requires Client session approval and camera permission |
| Microphone audio streaming | Runtime RECORD_AUDIO + foreground microphone service + WebRTC | Optional one-way Client microphone audio; separately confirmed for each media session |
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
  Accessibility settings and approves an interaction session. Text insertion is
  limited to the currently focused editable non-password field.
- Clipboard reads and writes require a separate per-operation approval on the
  Client. Only plain text is transferred; Android may restrict clipboard reads
  while MEIZZO is not foregrounded.
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
