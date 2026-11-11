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
| Remote camera streaming | Android prototype implemented: Camera2 capture after runtime CAMERA permission, WebRTC video publishing, and Controller camera viewer. Rear/front camera selection and device compatibility need physical testing. |
| Remote screen interaction | Android tap/swipe is wired to touch gestures on the live video surface. A separate approved interaction session is required; AccessibilityService must be enabled by the device owner. |
| Remote lock screen | Android lock action is implemented through AccessibilityService on Android 9+. The user must explicitly enable MEIZZO in Android Accessibility settings and approve an interaction session. |
| Remote alarm | Continuous looped AudioTrack playback is hosted by the Client foreground service, with remote ON/OFF and a local notification stop action. Physical-device and OEM background acceptance is still required. |
| File transfer | Android bidirectional prototype implemented over an ordered WebRTC DataChannel with per-chunk acknowledgements, 12 KiB chunks, final SHA-256 verification, a 50 MiB limit, and Storage Access Framework destination consent. Needs interrupted-transfer, large-file, and cross-network testing. |
| RustDesk engine | Upstream source is pinned at `third_party/rustdesk` (`95cca3f661f635b3cc85c04935fe8d96d8f432ef`) and verified in CI. Native Rust engine, Flutter bridge, and RustDesk server are not yet integrated into MEIZZO runtime. |

## Cloudflare v3 deployment

The stable Cloudflare Workers Builds project remains connected to `main` and deploys `wrangler.toml` as `meizzo-signaling`.

The v3 branch uses an isolated Worker configuration, `wrangler.v3.toml`, with its own Durable Object namespace. Create a **second Cloudflare Workers Builds project** for the same GitHub repository, set its production branch to `develop/v3`, leave the build command blank, and use this deploy command:

```bash
npx wrangler deploy --config wrangler.v3.toml
```

Use Cloudflare Workers Builds' automatically generated API token. No Cloudflare API token or account ID GitHub Actions secrets are required. After the one-time setup, pushes to `develop/v3` deploy the isolated Worker automatically. Follow the full [Cloudflare deployment instructions](../backend-deployment.md#isolated-meizzo-v3-worker).

The expected hostname is `meizzo-signaling-v3.rakhanatanox.workers.dev`, but Cloudflare's dashboard is authoritative. Confirm the actual deployment URL and health endpoint before treating the Worker as live. Configure Cloudflare Realtime TURN secrets on the v3 Worker separately if relay is enabled.

## Upstream engine integration

RustDesk is now tracked as a pinned source submodule. This is source acquisition and provenance, not feature integration. RustDesk's Android app uses Flutter, a Rust `cdylib` built for Android ABIs, generated Flutter-Rust bridge code, Android native services, and its own rendezvous/relay protocol. MEIZZO currently uses native Kotlin apps and a Cloudflare Worker/Durable Object for control signaling. These paths must be deliberately integrated rather than assumed to be drop-in replacements.

Cloudflare Workers continue to host MEIZZO pairing and control signaling. They cannot host RustDesk's `hbbs`/`hbbr` server processes; adopting RustDesk's native network protocol requires a separately hosted rendezvous/relay service. The migration and deployment plan is documented in [upstream and license notes](UPSTREAM_AND_LICENSES.md).

## Session and signaling foundation

- Client pairing remains a persistent app-managed connection; there is no Client-side Stop pairing control.
- Session requests require explicit Client approval.
- Controller session termination and reconnect cleanup remove stale authorization state so a fresh request can be shown.
- Cloudflare Durable Object and local Node signaling implementations support approved-session WebRTC offer/answer/ICE signaling for screen, camera, and file sessions.
- Regression tests cover repeated session requests, termination, Controller disconnect, Client reconnect, and authorized media-signal relay.

## Security boundaries

- WSS authenticates and authorizes signaling. It is not itself a media transport.
- Media signaling is accepted only for an approved screen, camera, or file session and only from a session participant.
- WebRTC uses Cloudflare STUN by default and supports short-lived Cloudflare Realtime TURN credentials when Worker secrets are configured. An initial stats-driven bitrate ceiling and bounded ICE-restart recovery are implemented; physical-network tuning, TURN setup, handover acceptance, and end-to-end media security review remain before screen/camera streaming is production-ready.
- File transfer currently uses stop-and-wait Base64 chunks over an ordered DTLS-protected WebRTC DataChannel; it is a functional prototype with SHA-256 completion checks, not yet optimized for large files or resumable transfer.
- No iOS hosting capability is claimed. iOS capture and control require a separate platform implementation and Apple permission constraints.
- Android permissions and Accessibility access remain controlled by the device owner and operating system.
- A Device Owner grant must never be treated as silent consent or a bypass for MediaProjection.
