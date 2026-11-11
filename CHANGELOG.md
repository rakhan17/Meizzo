# Changelog

This changelog records published releases and verifiable repository changes.
Unreleased work is not presented as shipped functionality.

## v0.1.0-beta.5

- Published signed Android Client and Controller APKs.
- Added release updater manifest and SHA-256 checksum file.
- Added Android foreground connection services for Client and Controller.
- Added reconnect handling and boot receiver integration.
- Improved authenticated reconnect and multi-device presence restoration.
- Added authorization-only session lifecycle with explicit Client approval,
  rejection, and termination.
- Kept interaction command routing behind an approved interaction session.
- Updated hosted signaling to persist state through Cloudflare Durable Objects.
- iOS remains a development/Simulator track and is not a public device release.

## v0.1.0-beta.4

- Published Android Client and Controller APKs.
- Published updater metadata and SHA-256 checksums.
- Established the current Android release-signing pipeline.

## v0.1.0-beta.3

- Published Android Client and Controller APK artifacts.
- Published iOS Simulator ZIP artifacts for Client and Controller.
- Added authoritative multi-device presence synchronization and reconnect
  handling.
- Preserved separate package and bundle identifiers.

## v0.1.0-beta.2

- Published official MEIZZO branding integration.
- Published Android APK and iOS Simulator artifacts.

## v0.1.0-beta.1

- Initial tagged beta release.

## Versioning and release integrity

MEIZZO uses semantic-version-shaped tags with prerelease identifiers such as
`v0.1.0-beta.5`. Android `versionCode` must increase for installable
updates; `versionName` should match the release tag. Releases must preserve
the project's release-signing identity. The release tag should point to the
exact source revision used to build its assets.
