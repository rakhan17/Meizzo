# Changelog

The detailed chronological project ledger is maintained in [UPDATES.md](UPDATES.md) and [docs/updates.md](docs/updates.md). It distinguishes published releases from CI-only development candidates.

## v3.0.0-beta.1 · development candidate

- Integrates AndroidLiquidGlass / Backdrop 2.0.1 into the shared Android Compose UI module.
- Adds Controller Link / Devices / Settings tabs and automatic navigation to Devices after successful pairing.
- Adds a focused one-screen Client pairing experience, Liquid Glass controls/dialogs, theme switching, and updated beta documentation.
- Existing v3 Android remote-assistance prototypes are documented with consent, platform, and testing boundaries.
- Android debug CI builds passed; signed public prerelease publication remains pending.

## v2.5.0 · stable

- Preserves the published stable Android Client and Controller APKs.
- Improves foreground Client presence, heartbeat-based offline detection, Controller Auto-Alerts, emergency messages, and opt-in last-known location.
- Stable assets and signing identity remain unchanged by v3 beta development.

## Historical beta releases

See [the full beta history](docs/updates.md#historical-beta-releases) for v0.1.0-beta.1 through v0.1.0-beta.5, including feature summaries and direct release links.

## Versioning

- Stable: `vX.Y.Z`. The current stable baseline is `v2.5.0`.
- v3 prerelease: `v3.0.0-beta.N`.
- Android release `versionCode` must increase monotonically. `versionName` must match the release tag.
- Public APKs must use the established long-lived signing identity. Never bypass the updater's signing-certificate verification.
