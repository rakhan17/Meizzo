# MEIZZO Changelog

This is the user-facing release and development changelog. Published releases are kept separate from CI-validated development builds.

- **Current development:** [develop/v3](https://github.com/rakhan17/Meizzo/tree/develop/v3).
- **Latest validation details:** [BETA-CHANGELOG.md](BETA-CHANGELOG.md) · [Full update ledger](UPDATES.md).
- **Stable channel:** [v2.5.0](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0), unchanged.
- **Superseded beta candidate:** [v3.0.0-beta.1](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1). Its tag points to legacy main, not the current v3 development source.
- **All published releases:** [GitHub Releases](https://github.com/rakhan17/Meizzo/releases).

---

## Latest development update · 12 November 2026

### Fixed
- Fixed the Android 16 native startup crash caused by a recursive RenderNode feedback loop: the background is recorded in a dedicated sibling layer, while glass surfaces that sample the backdrop render outside that layer.
- Added a regression-contract test to guard the backdrop layering rule.
- Added the MEIZZO logo to the Android Client and Controller in-app headers.

### Validation
- Android Client and Controller debug builds passed.
- Android 16 emulator launch smoke tests passed for both Android apps.
- Signaling tests, Node checks/tests, Android command/media/glass-backdrop regression contracts, and pinned RustDesk source/license checks passed.
- iOS Client and Controller Simulator builds and smoke tests passed.
- Evidence: [Android CI run 43162464047](https://github.com/rakhan17/Meizzo/actions/runs/43162464047) · [iOS CI run 43162464097](https://github.com/rakhan17/Meizzo/actions/runs/43162464097).

### Still pending before a corrected beta
- Manual two-device acceptance of six-digit pairing, Client approval, session establishment, and actual remote-control behavior.
- Build and validate the corrected signed Client and Controller APKs from develop/v3 before publishing a new beta tag.
- These CI results are engineering validation only. They do not make the superseded beta.1 package current, and they do not change Stable v2.5.0.

---

## v3.0.0-beta.1 · Superseded candidate

<sub>12 November 2026 · Published prerelease · Superseded due to source mismatch</sub>

- The tag was created from legacy main rather than develop/v3.
- Its signed Client and Controller APKs do not contain the current v3 Android implementation.
- Do not use beta.1 as the reference build for the current v3 interface.

See [BETA-CHANGELOG.md](BETA-CHANGELOG.md) for the full correction notice and validation record.

---

## v2.5.0 · Stable

<sub>Published 11 November 2026 · Stable</sub>

- Improved Client foreground connection lifecycle, heartbeat-based presence, and reconnect handling.
- Added Controller telemetry Auto-Alerts and offline-alert deduplication.
- Added consented emergency messages and opt-in last-known location.
- Preserved separate Client and Controller APKs and the established signing identity.

Downloads: [Client APK](https://github.com/rakhan17/Meizzo/releases/download/v2.5.0/MEIZZO-Client-v2.5.0.apk) · [Controller APK](https://github.com/rakhan17/Meizzo/releases/download/v2.5.0/MEIZZO-Controller-v2.5.0.apk) · [Release page](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0)

---

## Release-channel policy
- Stable tags use vX.Y.Z.
- Beta tags use vX.Y.Z-beta.N and must never replace the latest stable release.
- Every public Android release must include separately signed Client and Controller APKs, MEIZZO-UPDATE.json, and SHA256SUMS.txt.
- A successful debug CI build is not a published release.
- Corrected beta downloads are published only after signed APK validation and manual acceptance gates.
