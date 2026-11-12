# MEIZZO Android v3 Beta Changelog

This page tracks the v3 development candidate separately from the unchanged stable Android v2.5.0 release.

- **Stable:** [v2.5.0](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0), unchanged.
- **Development source:** [develop/v3](https://github.com/rakhan17/Meizzo/tree/develop/v3).
- **Published candidate:** [v3.0.0-beta.1](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1), superseded because its tag was created from the legacy main source rather than the current v3 development source.
- **Corrected beta:** not published yet. The debug APKs are for manual testing only.

## Latest development validation · 12 November 2026

<sub>Current source: develop/v3 · CI run: [Android](https://github.com/rakhan17/Meizzo/actions/runs/43162464047) · [iOS](https://github.com/rakhan17/Meizzo/actions/runs/43162464097)</sub>

- **Android Client and Controller:** both debug builds and Android 16 emulator launch smoke tests passed.
- **Signaling and regression checks:** release-version derivation, Android command/media/glass-backdrop contracts, Node checks/tests, and pinned RustDesk source/license verification passed.
- **iOS Client and Controller:** both Simulator builds and smoke tests passed. These are simulator validations, not physical-iPhone release packages.
- **Crash fix:** separated the recorded backdrop background into a sibling layer so glass surfaces no longer sample the same layer that records them. This removes the recursive RenderNode feedback loop behind the Android 16 libhwui.so SIGSEGV.
- **Branding:** the MEIZZO logo is now included in the Android Client and Controller headers.
- **Scope:** CI success verifies builds and launch smoke tests. Pairing, approval, and real remote-control behavior still require manual two-device acceptance testing. No corrected signed v3 beta or stable v3 release has been published.

See [BETA-CHANGELOG.md](BETA-CHANGELOG.md) and [UPDATES.md](UPDATES.md) for the detailed change history.

## Changes included in the current development source

### Android Client and Controller
- Fixed the Android 16 native RenderThread crash by separating the recorded backdrop background from the glass surfaces that sample it.
- Added a source-contract regression test to protect the backdrop layering rule.
- Added the MEIZZO logo to the Client and Controller in-app headers.
- Preserved the separate Client and Controller roles and the current v3 UI structure.

### Validation still required
- Manually test both apps together on two Android devices/emulators.
- Verify six-digit pairing, Client approval, session establishment, and actual remote-control interactions.
- Verify reconnect and relaunch behavior after the end-to-end session test.

Do not treat CI smoke tests or debug APKs as a signed public beta release. The corrected beta must be built from develop/v3, signed with the established release key, and published only after the release gates pass.
