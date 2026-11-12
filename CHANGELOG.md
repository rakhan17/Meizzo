# MEIZZO Changelog

This is the user-facing changelog for published beta and stable releases. New beta features are recorded here when a release is published, not merely when a branch or CI build exists.

- **Beta changelog and correction notice:** [BETA-CHANGELOG.md](BETA-CHANGELOG.md)
- **Last published beta candidate:** [v3.0.0-beta.1](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1), superseded due to a source-branch mismatch
- **Stable channel:** [v2.5.0](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0)
- **Detailed release history:** [UPDATES.md](UPDATES.md) · [Documentation update log](docs/updates.md)
- **All builds:** [GitHub Releases](https://github.com/rakhan17/Meizzo/releases)

---

## v3.0.0-beta.1 · Android Liquid Glass and remote-assistance beta

> **Superseded candidate:** this tag was built from `main`, not `develop/v3`, and therefore does not contain the current v3 Android application source. See [the dedicated beta changelog](BETA-CHANGELOG.md) for the correction and next release validation gates.

<sub>12 November 2026 · Beta prerelease</sub>

### New in this beta

- Integrates AndroidLiquidGlass / Backdrop **2.0.1** into the shared Android Compose UI module.
- Rebuilds the Android Controller navigation around three tabs: **Link**, **Devices**, and **Settings**.
- Automatically opens Devices after a pairing request is approved.
- Gives Android Client a focused single-page six-digit pairing-code experience.
- Adds shared Liquid Glass surfaces, buttons, bottom tabs, toggles, sliders, scroll effects, and app-owned dialogs.
- Adds light/dark appearance settings and matching Android system-bar icon contrast.
- Documents the Android remote-assistance prototype set: approved screen/camera sessions, optional two-way microphone audio, remote interaction/navigation, manual clipboard, remote file browsing, and bidirectional WebRTC file transfer.

### Important boundaries

- Sensitive pairing and session capabilities still require explicit approval on the Client.
- Android system permission prompts, document pickers, and Package Installer screens remain native Android UI.
- Remote media and file transfer remain prototypes requiring physical-device and cross-network acceptance.
- TURN relay availability depends on the existing Cloudflare Worker configuration; STUN-only paths may fail behind restrictive NATs.
- RustDesk upstream source is pinned and license-checked, but the RustDesk engine/server is not integrated into the MEIZZO runtime.
- iOS remains a separate development/Simulator track and does not have Android feature parity.

### Downloads

- [MEIZZO Client beta APK](https://github.com/rakhan17/Meizzo/releases/download/v3.0.0-beta.1/MEIZZO-Client-v3.0.0-beta.1.apk)
- [MEIZZO Controller beta APK](https://github.com/rakhan17/Meizzo/releases/download/v3.0.0-beta.1/MEIZZO-Controller-v3.0.0-beta.1.apk)
- [Release page, update manifest, and SHA-256 checksums](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1)

---

## v2.5.0 · Stable

<sub>Published 11 November 2026 · Stable</sub>

- Improved Client foreground connection lifecycle, heartbeat-based presence, and reconnect handling.
- Added Controller telemetry Auto-Alerts and offline-alert deduplication.
- Added consented emergency messages and opt-in last-known location.
- Preserved separate Client and Controller APKs and the established signing identity.

Downloads: [Client APK](https://github.com/rakhan17/Meizzo/releases/download/v2.5.0/MEIZZO-Client-v2.5.0.apk) · [Controller APK](https://github.com/rakhan17/Meizzo/releases/download/v2.5.0/MEIZZO-Controller-v2.5.0.apk) · [Release page](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0)

---

## Historical beta releases

See [UPDATES.md](UPDATES.md#historical-beta-releases) for v0.1.0-beta.1 through v0.1.0-beta.5 and their original release links.

## Release-channel policy

- `vX.Y.Z` is a stable release.
- `vX.Y.Z-beta.N` is a prerelease and must never replace the latest stable release.
- Every public Android release must include separately signed Client and Controller APKs, `MEIZZO-UPDATE.json`, and `SHA256SUMS.txt`.
- A successful debug CI build is not a published release.
- New beta features are added to this changelog only after the GitHub prerelease and its assets are verified.
