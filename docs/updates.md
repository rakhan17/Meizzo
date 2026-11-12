# MEIZZO Updates & Release Log

> This is the detailed project update ledger. It records published releases separately from development candidates so that a CI build is never mistaken for a downloadable, signed release.
>
> **Stable channel:** `v2.5.0` (published, unchanged).  
> **Published beta candidate:** `v3.0.0-beta.1` (superseded; built from `main`, not `develop/v3`).  
> **Corrected beta:** in development on `develop/v3`; no corrected download is published yet.

- [Latest beta release](#v300-beta1--liquid-glass-ui-beta)
- [Stable release](#v250--stable)
- [Historical beta releases](#historical-beta-releases)
- [Architecture and release policy](#architecture-and-release-policy)
- [Known limitations and test status](#known-limitations-and-test-status)

---

## v3.0.0-beta.1 · Superseded candidate

<sub>12 November 2026 · Published prerelease · Superseded due to source mismatch</sub>

The tag resolves to commit `d8dc247` on the legacy `main` history. It does **not** contain the current v3 Android application source from `develop/v3`. The signed APKs, update manifest, and checksums remain available on the [historical release page](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1), but these packages are not the corrected v3 test build.

### Correction and current development state

- The current v3 Android implementation is maintained on [`develop/v3`](https://github.com/rakhan17/Meizzo/tree/develop/v3), with [PR #9](https://github.com/rakhan17/Meizzo/pull/9) still under review.
- The corrected beta.2 candidate has not been published.
- Both Client and Controller compile, but Android 16 launch smoke testing exposed a native Client `RenderThread` SIGSEGV and an earlier Controller launch failure. The unstable Backdrop lens shader has been removed as a compatibility fix candidate; the revised smoke-test rerun is pending.
- The signaling regression contracts and Node tests pass on the latest CI run; this does not replace device/runtime acceptance.
- The next release must be built from a commit reachable from `develop/v3`, signed with the established release key, and pass launch smoke tests for both apps before assets are published.

See [BETA-CHANGELOG.md](../BETA-CHANGELOG.md) for the correction notice and exact release gates.

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

See [implementation status](implementation-status.md), [architecture](architecture.md), [testing](testing.md), and [security and privacy](security-and-privacy.md) for the detailed acceptance criteria.
