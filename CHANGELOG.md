# MEIZZO Changelog

This is the user-facing changelog for published beta and stable releases. New beta features are recorded here when a release is published, not merely when a branch or CI build exists.

- **Beta changelog and correction notice:** [BETA-CHANGELOG.md](BETA-CHANGELOG.md)
- **Last published beta candidate:** [v3.0.0-beta.1](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1), superseded due to a source-branch mismatch
- **Stable channel:** [v2.5.0](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0)
- **Detailed release history:** [UPDATES.md](UPDATES.md) · [Documentation update log](docs/updates.md)
- **All builds:** [GitHub Releases](https://github.com/rakhan17/Meizzo/releases)

---

## v3.0.0-beta.1 · Superseded candidate

<sub>12 November 2026 · Beta prerelease · Superseded due to source mismatch</sub>

- The tag was created from `main`, not `develop/v3`.
- Its signed Client and Controller APKs do not contain the current v3 Android implementation.
- Do not treat this release as the current v3 beta or as evidence that the current v3 app has passed launch testing.
- A corrected beta release will be recorded here only after both signed APKs pass Android 16 launch smoke tests.

See [BETA-CHANGELOG.md](BETA-CHANGELOG.md) for the correction notice, current development status, and release gates.

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
