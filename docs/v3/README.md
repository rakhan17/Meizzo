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
| Live screen streaming | Android prototype implemented: MediaProjection consent, WebRTC video publishing, Controller video rendering, ICE signaling, aspect-ratio-aware touch mapping, stats-driven sender bitrate ceiling, and bounded ICE-restart recovery. Requires physical-device/network testing and TURN before broad release. |
| Remote camera streaming | Android prototype implemented: Camera2 capture after runtime CAMERA permission, WebRTC video publishing, and Controller camera viewer. Controller can request a front/rear camera switch during an approved camera session; device compatibility needs physical testing. |
| Voice audio | Optional Client microphone audio and Controller-to-Client microphone audio are carried as WebRTC audio tracks. Each side requires runtime RECORD_AUDIO permission and an explicit in-session action; Client microphone capture uses its media foreground service, while Controller voice uses a microphone foreground service. Controller can mute Client playback and mute its own microphone. Internal app/system audio capture is not implemented; voice routing, echo cancellation, and OEM compatibility still require physical-device validation. |
| Remote screen interaction | Android tap, swipe, long-press, two-finger trackpad scrolling, Back, Home, Recents, notification shade, Quick Settings, power menu, and lock-screen actions are wired through an approved interaction session. AccessibilityService must be enabled by the device owner. |
| Remote lock screen and navigation | Android Back, Home, Recents, and lock actions are implemented through AccessibilityService. The user must explicitly enable MEIZZO in Android Accessibility settings and approve an interaction session. |
| Remote keyboard text input | Controller can insert up to 2,000 characters at the current selection in the Client's focused editable field. Password fields are explicitly rejected. This uses Accessibility window access; custom editors may reject insertion. Selected key events are handled separately through Android 13+ Accessibility input connections. |
| Remote keyboard controls | Controller can send Backspace, Enter, Tab, Escape, arrow keys, and Ctrl+A/C/V/X/Z/Y shortcuts through Android 13+ Accessibility input connections; Backspace and text insertion have a fallback on older versions. Full hardware-keyboard coverage and arbitrary modifier combinations are not implemented. |
| Remote media volume | Controller can raise, lower, mute, and unmute the Client's media stream volume through Android AudioManager during an approved interaction session. |
| Clipboard transfer | Controller can request Client text clipboard or send text to it. Every read and overwrite requires a separate approval dialog on the Client; only text up to 10,000 characters is handled. Android clipboard background-access restrictions may apply. |
| Remote screenshots | Controller can save a frame from its active live-screen viewer using PixelCopy. Android 10+ saves to Pictures/MEIZZO; older supported versions use app-specific picture storage. |
| Remote alarm | Continuous looped AudioTrack playback is hosted by the Client foreground service, with remote ON/OFF and a local notification stop action. Physical-device and OEM background acceptance is still required. |
| Remote file browser | Android directory browsing through a Client-granted Storage Access Framework tree URI. Controller can navigate folders and inspect names, MIME types, sizes, and modification dates. Individual files can be sent to the Controller through a separate approved file-transfer session after an additional Client confirmation. Folder creation, rename, and file deletion are available only when Android grants write access, the storage provider advertises support, and the Client approves. |
| File transfer | Android bidirectional single- and multi-file transfer over an ordered WebRTC DataChannel, with 12 KiB acknowledged chunks, bounded retries, duplicate handling, strict sequence and size validation, final SHA-256 verification, a 50 MiB per-file limit, SAF source/destination grants, progress, and cancellation. Sender and receiver persist checkpoints synchronously before acknowledging each received chunk and resume from the last acknowledged byte offset after a new approved files session. Resume depends on document providers preserving persistable read/write access and supporting append mode; physical-device and cross-network acceptance remain outstanding. |
| RustDesk engine | Upstream source is pinned at `third_party/rustdesk` (`95cca3f661f635b3cc85c04935fe8d96d8f432ef`) and verified in CI. Native Rust engine, Flutter bridge, and RustDesk server are not yet integrated into MEIZZO runtime. |

## Android Liquid Glass interface (current beta candidate)

The current UI candidate integrates AndroidLiquidGlass / Backdrop 2.0.1 in the shared `apps/glass-ui` Compose module. Controller has Link, Devices, and Settings tabs; successful pairing switches to Devices. Client uses a single pairing-code screen. Shared Liquid Glass surfaces, buttons, toggles, sliders, scroll effects, and app-owned dialogs are used where the upstream library provides implementations. Android system-owned permission/document/install surfaces remain native. The UI candidate's Android Client and Controller debug builds passed CI; signed public release is still a separate gate.

## Cloudflare signaling deployment

MEIZZO v3 uses the existing shared Worker endpoint:

`wss://meizzo-signaling.rakhanatanox.workers.dev`

Do not create a second Worker or a separate Workers Builds project. The existing Cloudflare Workers Builds project remains connected to `main` and deploys the root `wrangler.toml`. The v3 branch uses this endpoint for debug and release builds. Worker-side v3 changes deploy through the existing main-branch deployment after merging them into `main`.

## Cloudflare TURN setup

Use the existing Worker and follow [the TURN setup guide](../cloudflare-turn-setup.md). No VPS or second signaling service is required. The Worker falls back to STUN when TURN secrets are absent or the credential API is unavailable; cross-network reliability is not guaranteed until TURN is configured and physically tested.

## Upstream engine integration

RustDesk is now tracked as a pinned source submodule. This is source acquisition and provenance, not feature integration. RustDesk's Android app uses Flutter, a Rust `cdylib` built for Android ABIs, generated Flutter-Rust bridge code, Android native services, and its own rendezvous/relay protocol. MEIZZO currently uses native Kotlin apps and a Cloudflare Worker/Durable Object for control signaling. These paths must be deliberately integrated rather than assumed to be drop-in replacements.

Cloudflare Workers continue to host MEIZZO pairing and control signaling. They cannot host RustDesk's `hbbs`/`hbbr` server processes; adopting RustDesk's native network protocol requires a separately hosted rendezvous/relay service. The migration and deployment plan is documented in [upstream and license notes](UPSTREAM_AND_LICENSES.md).

## Session and signaling foundation

- Client pairing remains persistent and its WebSocket, heartbeat, network monitoring, and reconnect policy are owned by the foreground service rather than the Activity. Remote alarm commands are handled by the service while detached; other signaling messages are held in a bounded in-memory queue and replayed when the Activity reattaches. There is no Client-side Stop pairing control.
- Session requests require explicit Client approval.
- Controller session termination and reconnect cleanup remove stale authorization state so a fresh request can be shown.
- Cloudflare Durable Object and local Node signaling implementations support approved-session WebRTC offer/answer/ICE signaling for screen, camera, and file sessions.
- Regression tests cover repeated session requests, termination, Controller disconnect, Client reconnect, and authorized media-signal relay.

## Security boundaries

- WSS authenticates and authorizes signaling. It is not itself a media transport.
- Media signaling is accepted only for an approved screen, camera, or file session and only from a session participant.
- WebRTC media and file-transfer sessions use Cloudflare STUN by default and short-lived Cloudflare Realtime TURN credentials when Worker secrets are configured. TURN uses the existing `meizzo-signaling` Worker, not a VPS or a second server. Configure `REALTIME_TURN_TOKEN_ID` and `REALTIME_TURN_TOKEN_SECRET` as Worker secrets in the existing Cloudflare deployment; the `/healthz` response reports only whether both are present, never their values. Without these secrets, sessions use STUN-only connectivity and may fail behind restrictive NATs or firewalls. Stats-driven bitrate adaptation and bounded ICE-restart recovery are implemented; TURN secret setup, physical-network tuning, handover acceptance, and end-to-end media security review remain before production.
- File transfer uses stop-and-wait Base64 chunks over an ordered DTLS-protected WebRTC DataChannel. Acknowledged-offset resume is implemented with persisted SAF grants and SHA-256 prefix verification; large-file optimization and physical-device acceptance remain outstanding.
- No iOS hosting capability is claimed. iOS capture and control require a separate platform implementation and Apple permission constraints.
- Android permissions and Accessibility access remain controlled by the device owner and operating system.
- A Device Owner grant must never be treated as silent consent or a bypass for MediaProjection.
