# Release notes

See [CHANGELOG.md](https://github.com/rakhan17/Meizzo/blob/main/CHANGELOG.md)
for the verified release history and
[GitHub Releases](https://github.com/rakhan17/Meizzo/releases) for published
assets.

## Latest Android beta

`v0.1.0-beta.5` is the latest published Android prerelease. It includes
separate signed Client and Controller APKs, `MEIZZO-UPDATE.json`, and
`SHA256SUMS.txt`.

The release workflow completed successfully:
[run 42948592178](https://github.com/rakhan17/Meizzo/actions/runs/42948592178).

## Release process

1. Update app `versionName` and increment Android `versionCode`.
2. Run signaling tests and both Android builds.
3. Run iOS Simulator build and smoke tests where relevant.
4. Confirm the release endpoint uses WSS and the established Android signing
   key.
5. Run the release workflow with a valid semver prerelease/stable tag.
6. Review APK package IDs, signatures, version metadata, checksums, updater
   manifest, and release provenance before publishing.

The Android release workflow requires:
`MEIZZO_ANDROID_KEYSTORE_BASE64`, `MEIZZO_ANDROID_KEYSTORE_PASSWORD`,
`MEIZZO_ANDROID_KEY_ALIAS`, `MEIZZO_ANDROID_KEY_PASSWORD`, and
`MEIZZO_SIGNALING_URL`. Never commit these values. The iOS device workflow
uses separate Apple signing and provisioning secrets.

## Signing and installation

Android updates must be signed by the same certificate as the installed
application. The updater checks the app-specific asset, SHA-256, package ID,
and signing certificate before handing installation to Android Package
Installer. It must not bypass certificate mismatch checks.

The release workflow should create a tag pointing to the exact source commit
used to build the APK assets. Verify the tag target and workflow source SHA
when reviewing a release.

## iOS

iOS Simulator artifacts are engineering outputs, not physical-device
installers. A physical-device IPA requires valid Apple signing and matching
provisioning. No public iOS IPA is currently published.
