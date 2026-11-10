# Changelog

This changelog records repository releases that are verifiable from Git tags
and GitHub release artifacts. Unreleased work is not presented as a release.

## v0.1.0-beta.3

- Published signed Android Client and Controller APK artifacts.
- Published iOS Simulator ZIP artifacts for Client and Controller.
- Added authoritative multi-device presence synchronization and reconnect
  handling.
- Added Android and iOS beta UI redesign and telemetry surfaces.
- Preserved separate package and bundle identifiers.

## v0.1.0-beta.2

- Published official MEIZZO branding integration.
- Published Android APK and iOS Simulator artifacts.

## v0.1.0-beta.1

- Initial tagged beta release.

## Versioning

MEIZZO uses semantic-version-shaped tags with prerelease identifiers such as
`v0.1.0-beta.3`. Android `versionCode` must increase for installable updates;
`versionName` should match the release tag. Releases must preserve the stable
Android signing key and must not publish unsigned or incorrectly signed APKs.
