# Release notes

The full chronological release history, including feature-level beta notes and build evidence, is maintained in [the MEIZZO update log](updates.md).

## Current channels

| Channel | Version | Status |
|---|---|---|
| Android Stable | `v2.5.0` | Published. Existing APKs and signing identity remain unchanged. |
| Android v3 Beta | `v3.0.0-beta.1` | Current development candidate. CI debug builds passed; signed public release is pending. |
| iOS | Development | Simulator CI only; physical-device distribution is not available. |

## Signed Android release gates

1. Use the correct release branch and a valid v3 prerelease tag.
2. Verify release-version derivation and incremented Android version code.
3. Build Client and Controller release variants against the production WSS endpoint.
4. Sign both APKs with the established long-lived Android keystore.
5. Verify package IDs, version metadata, signing certificates, file sizes, and SHA-256 hashes.
6. Generate `MEIZZO-UPDATE.json` and `SHA256SUMS.txt`.
7. Publish the GitHub prerelease with both APKs and metadata.
8. Verify the published assets and updater compatibility before updating direct README download links.

The workflow requires `MEIZZO_ANDROID_KEYSTORE_BASE64`, `MEIZZO_ANDROID_KEYSTORE_PASSWORD`, `MEIZZO_ANDROID_KEY_ALIAS`, and `MEIZZO_ANDROID_KEY_PASSWORD` as GitHub secrets. Do not paste these values into chat, commits, or logs.

## Important release distinction

CI debug APK artifacts are useful for installation and smoke testing, but they are not signed public-release packages. A green debug build does not mean that a signed GitHub Release has been published. Stable `v2.5.0` must not be overwritten or relabeled as a v3 beta.
