# MEIZZO Updates & Release Log

> This is the detailed project update ledger. It records published releases separately from development candidates so that a CI build is never mistaken for a downloadable, signed release.
>
> **Stable channel:** `v2.5.0` (published, unchanged).  
> **Current beta line:** `v3.0.0-beta.1` (beta prerelease; signed APK publication is handled by the dedicated release workflow).

- [Latest beta candidate](#v300-beta1--liquid-glass-ui-candidate)
- [Stable release](#v250--stable)
- [Historical beta releases](#historical-beta-releases)
- [Architecture and release policy](#architecture-and-release-policy)
- [Known limitations and test status](#known-limitations-and-test-status)

---

## v3.0.0-beta.1 · Liquid Glass UI beta

<sub>12 November 2026 · Beta prerelease · Published after release validation</sub>

This is the current v3 beta candidate on the `develop/v3` development line, including the Android UI work merged through [PR #10](https://github.com/rakhan17/Meizzo/pull/10). It is based on the v3 Android remote-assistance foundation and adds a shared Liquid Glass presentation system.

### Android Controller

- Replaces the single-page entry layout with exactly three bottom tabs:
  - **Link:** dedicated pairing-code entry surface.
  - **Devices:** paired-device management and remote-operation entry points.
  - **Settings:** appearance and Auto-Alert configuration.
- On successful pairing, navigation switches automatically from Link to Devices.
- Device cards show device name, online/offline state, platform, and available management actions.
- Device actions and management surfaces use the shared Compose Liquid Glass components.
- Settings supports light and dark appearance, with system status/navigation bar icon contrast updated to match.
- Auto-Alert controls use the upstream Liquid Glass toggle and slider component examples.

### Android Client

- Focuses the app on one page containing the temporary six-digit pairing code and connection state.
- Keeps pairing approval and every sensitive capability approval on the Client.
- App-owned warnings, confirmations, and input prompts use the shared Liquid Glass dialog surface.

### Shared Liquid Glass system

- Integrates **AndroidLiquidGlass 2.0.1**, the latest upstream release verified for this implementation.
- Uses the upstream Backdrop effect primitives and official sample component implementations for LiquidBottomTabs, LiquidButton, LiquidToggle, and LiquidSlider. The shared integration is in `apps/glass-ui`.
- Uses upstream backdrop surfaces for scrollable content rather than drawing a home-made glass imitation.
- Provides shared app-owned glass dialogs, including text-entry prompts and keyboard-aware dialog layout.
- Keeps the upstream Apache-2.0 license and third-party notices with the copied/adapted sample source.
- The design is iOS-inspired, but remains a native Android Compose application. It does not bundle Apple's proprietary SF Pro font or replace Android-owned system permission/document pickers.

### Existing v3 capabilities carried into this candidate

- Six-digit expiring pairing offers, explicit Client pairing approval, role-specific credentials, multi-device relationships, presence, telemetry, and revocation.
- Session request/approval/rejection and participant-bound signaling for screen, camera, interaction, and files sessions.
- Android MediaProjection screen-sharing prototype and Camera2 camera-streaming prototype over WebRTC.
- Optional two-way microphone audio tracks, subject to RECORD_AUDIO permission and user action on each side.
- Approved remote tap, swipe, long-press, trackpad scrolling, text insertion, selected keyboard commands, navigation, notification shade, Quick Settings, power menu, and lock actions through AccessibilityService.
- Remote media volume and alarm commands.
- Manually approved text clipboard read/write.
- Client-granted Storage Access Framework folder browsing and supported folder/file management actions.
- Bidirectional single/multi-file WebRTC DataChannel transfer with acknowledged chunks, bounded retries, SHA-256 verification, progress, cancellation, a 50 MiB per-file ceiling, and checkpoint/resume support where SAF providers preserve required access and append behavior.
- Android foreground connection services, reconnect handling, and Android Keystore-backed local credential encryption.

### Build and verification evidence

The Liquid Glass implementation was first validated at candidate head `2fefef82b2b7c4b9d70823a86a5330c416402da9`. The beta release pipeline then rebuilds both apps as signed release APKs and validates package IDs, version metadata, signing certificates, SHA-256 hashes, and the updater manifest.

- [MEIZZO CI](https://github.com/rakhan17/Meizzo/actions/runs/43144166641): Android Client debug APK, Android Controller debug APK, signaling tests/contracts, and pinned RustDesk source/license verification.
- [MEIZZO iOS](https://github.com/rakhan17/Meizzo/actions/runs/43144166627): iOS Simulator workflow passed. This is not evidence of iOS Liquid Glass parity or physical-device distribution.
- [Documentation](https://github.com/rakhan17/Meizzo/actions/runs/43144166568): Markdown link validation and strict MkDocs build passed.

Debug APK artifacts are attached to the [MEIZZO CI run](https://github.com/rakhan17/Meizzo/actions/runs/43144166641):

- `meizzo-client-debug`
- `meizzo-controller-debug`

The debug artifacts above are build evidence only. Public beta downloads are the separately signed APK assets attached to the [v3.0.0-beta.1 prerelease](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1), together with the update manifest and SHA-256 checksums.

### Not included / not claimed

- The RustDesk engine, Flutter bridge, or hbbs/hbbr server is not integrated into the MEIZZO runtime. The upstream source is pinned and license-checked only.
- No TURN deployment or universal NAT traversal guarantee is claimed. The current Android builds use the existing MEIZZO signaling endpoint and STUN; TURN relay depends on the existing Worker secrets being configured.
- Android-owned permission pickers, system document pickers, and OS installation confirmation remain native Android UI.
- No iOS screen hosting, camera hosting, file transfer, or remote input parity is claimed.
- No independent security audit or formal security certification is claimed.

---

## v2.5.0 · Stable

<sub>Published 11 November 2026 · Stable channel · Preserved</sub>

- Promoted the v2.5 foundation to the stable Android channel.
- Improved Client presence handling so a WebSocket close does not immediately mark a device offline; heartbeat timeout remains authoritative.
- Added a Client foreground connection service to own the WebSocket lifecycle beyond Activity recreation.
- Improved restart/reconnect state handling and aligned Cloudflare presence with heartbeat expiry.
- Added configurable Controller telemetry Auto-Alerts, permission handling, and offline-alert deduplication.
- Added a consented emergency-message command.
- Added opt-in last-known location for Find My Phone. This is not continuous background location tracking.
- Expanded signaling regression tests for heartbeat-driven offline transitions.
- Updated security, architecture, telemetry, user, and deployment documentation.

**Stable assets (unchanged):**

- [MEIZZO Client v2.5.0 APK](https://github.com/rakhan17/Meizzo/releases/download/v2.5.0/MEIZZO-Client-v2.5.0.apk)
- [MEIZZO Controller v2.5.0 APK](https://github.com/rakhan17/Meizzo/releases/download/v2.5.0/MEIZZO-Controller-v2.5.0.apk)
- [v2.5.0 release page and checksums](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0)

The stable tag, APKs, signing identity, and download links are not changed by the v3 beta work.

---

## Historical beta releases

### v0.1.0-beta.5

<sub>Published 11 November 2026</sub>

- Refreshed the release/download presentation and restored repository status badges.
- Organized stable and beta download sections.
- This tag's source delta is documentation/release-presentation focused; it does not represent a new remote-control feature set.
- Preserved separate Client and Controller APK assets, update manifest, and SHA-256 checksums.

[Release assets](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.5) · [Compare with beta.4](https://github.com/rakhan17/Meizzo/compare/v0.1.0-beta.4...v0.1.0-beta.5)

### v0.1.0-beta.4

<sub>Published 10 November 2026</sub>

- Reworked Android app experience and corrected Client/Controller presence synchronization.
- Added heartbeat-driven online/offline semantics and telemetry documentation.
- Established the v2 architecture and authorized session lifecycle.
- Added secure in-app update flow with release metadata, package identity checks, checksum verification, signing-certificate verification, and Android Package Installer handoff.
- Improved BlueStacks debug networking and custom debug endpoint artifacts.
- Added hosted Cloudflare Workers + Durable Objects signaling deployment and per-device Controller token authorization.
- Added iOS device-signing workflow groundwork and expanded repository security/community documentation.
- Corrected multiple Android compilation, SDK, and updater integration issues.

[Release assets](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.4) · [Compare with beta.3](https://github.com/rakhan17/Meizzo/compare/v0.1.0-beta.3...v0.1.0-beta.4)

### v0.1.0-beta.3

<sub>Published 9 November 2026</sub>

- Added signed Android release packaging and fixed package signing/icon spacing.
- Added iOS Client and Controller Simulator release artifacts.
- Continued Android app metadata and release pipeline hardening.
- Updated documentation for platform-specific distribution.

[Release assets](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.3) · [Compare with beta.2](https://github.com/rakhan17/Meizzo/compare/v0.1.0-beta.2...v0.1.0-beta.3)

### v0.1.0-beta.2

<sub>Published 9 November 2026</sub>

- Added the first iOS SwiftUI Client and Controller beta applications and Simulator build workflow.
- Added iOS bundle metadata, app icon catalogs, and logo assets.
- Integrated MEIZZO branding into Android and iOS app surfaces.
- Added iOS Simulator artifact links and refreshed the project README.

[Release assets](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.2) · [Compare with beta.1](https://github.com/rakhan17/Meizzo/compare/v0.1.0-beta.1...v0.1.0-beta.2)

### v0.1.0-beta.1

<sub>Published 9 November 2026</sub>

- Established the Phase-1 MEIZZO foundation.
- Added the initial secure six-digit pairing/signaling scaffold.
- Published separate Android Client and Controller APKs and the initial release pipeline.

[Release assets](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.1)

---

## Architecture and release policy

### Current architecture

- **Client:** native Kotlin Android app on the assisted device. Owns pairing, consent, capture permissions, file selection, and device-side operations.
- **Controller:** native Kotlin Android app on the operator device. Manages paired Clients and requests approved sessions.
- **Shared Android UI:** Jetpack Compose module `apps/glass-ui`, integrating AndroidLiquidGlass / Backdrop 2.0.1.
- **Control plane:** existing Cloudflare Worker and Durable Object over WSS for pairing, authentication, presence, telemetry, commands, session authorization, and WebRTC signaling.
- **Media plane:** peer-to-peer WebRTC for approved video/audio tracks and file DataChannels. WSS does not carry media/file payloads.
- **Local harness:** Node.js signaling implementation for development and automated tests only.
- **iOS:** separate SwiftUI Client/Controller Simulator development targets.

### Version policy

| Channel | Version | Meaning |
|---|---|---|
| Android Stable | `v2.5.0` | Published stable baseline. Keep this line unchanged during v3 beta work. |
| Android Beta | `v3.0.0-beta.N` | Prerelease v3 line. Beta numbers increment for each published candidate. |
| iOS | Development | Simulator builds only until signing, provisioning, and physical-device testing are complete. |

Beta builds can contain incomplete or device-dependent functionality. A passing CI build is not a certification of runtime quality. Release APKs must be built and signed using the established long-lived Android release keystore; never bypass signature validation in the in-app updater.

---

## Known limitations and test status

- Screen/camera/voice/file-transfer implementations are Android prototypes and still need real-device and multi-network acceptance.
- STUN-only paths can fail behind restrictive NAT, CGNAT, or firewalls. TURN depends on secrets configured on the existing Worker; do not create a second Worker just for beta.
- Android OEM background restrictions, AccessibilityService behavior, camera/audio routing, MediaProjection lifecycle, and Storage Access Framework provider differences require device testing.
- File transfer currently has a 50 MiB per-file ceiling; checkpoint resume depends on document provider behavior.
- Internal app/system audio capture, full hardware-keyboard coverage, automatic clipboard sync, large-file optimization, and RustDesk runtime integration are not implemented.
- iOS physical-device distribution and Android/iOS feature parity are not claimed.
- MEIZZO has not undergone an independent security audit and does not claim formal security certification.

See [implementation status](docs/implementation-status.md), [architecture](docs/architecture.md), [testing](docs/testing.md), and [security and privacy](docs/security-and-privacy.md) for the detailed acceptance criteria.
