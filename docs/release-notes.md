# Release notes

See [CHANGELOG.md](https://github.com/rakhan17/Meizzo/blob/main/CHANGELOG.md)
for the verified tag history.

## Release process

1. Update app `versionName` and increment Android `versionCode`.
2. Run backend tests and both Android builds.
3. Run iOS Simulator builds and launch smoke tests on macOS.
4. Confirm the release endpoint uses WSS and the stable Android signing key.
5. Push a semver tag such as `v0.1.0-beta.4`.
6. Review generated APK package IDs, signatures, checksums, and iOS
   Simulator artifact types before publishing.

The Android release workflow requires:
`MEIZZO_ANDROID_KEYSTORE_BASE64`, `MEIZZO_ANDROID_KEYSTORE_PASSWORD`,
`MEIZZO_ANDROID_KEY_ALIAS`, `MEIZZO_ANDROID_KEY_PASSWORD`, and
`MEIZZO_SIGNALING_URL`. The iOS device workflow has separate Apple secrets;
it is manual and does not publish an unsigned IPA.

The published beta.3 APKs were signed by the historical release workflow's
temporary key. Until the owner configures the matching long-lived keystore (or
publishes a deliberate migration path), the updater correctly rejects future
APKs whose certificate differs; it must not bypass that safety check.

No new release is created for documentation or updater changes in this task.
