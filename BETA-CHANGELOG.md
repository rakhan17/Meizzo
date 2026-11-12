# MEIZZO Android v3 Beta Changelog

This page tracks the Android v3 beta independently from the stable v2.5.0 channel.

- **Stable:** [v2.5.0](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0), unchanged.
- **Published beta candidate:** [v3.0.0-beta.1](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1).
- **Development source:** [develop/v3](https://github.com/rakhan17/Meizzo/tree/develop/v3).

> **Release correction:** the current `v3.0.0-beta.1` tag was created from `main`, not `develop/v3`. Its APKs therefore do not contain the current v3 Liquid Glass application source. Treat beta.1 as a superseded candidate. A corrected beta release must pass signed-APK launch smoke tests before it becomes the download target. Do not interpret a successful Gradle compile as proof that an APK launches successfully.

## v3.0.0-beta.1

**Published:** 12 November 2026  
**Status:** Superseded, source mismatch

- Published separate signed Client and Controller APK assets with an update manifest and SHA-256 checksums.
- The tag resolves to the legacy `main` application source, so the published packages do not represent the current v3 interface.
- The beta.1 package should not be used as the reference build for the v3 Liquid Glass experience.

### Latest validation findings · 12 November 2026

- The Android 16 native signal-11 crash in `libhwui.so` was traced to a recursive `RenderNode::prepareTreeImpl` / `SkiaDisplayList::prepareListAndChildren` backdrop feedback loop. The fix is now in the current development source.
- Root cause: the outer `Box` was marked as the `LayerBackdrop` source while its descendants also sampled that same backdrop. This formed a render feedback loop. The Backdrop maintainer documents this exact pattern as a cause of RenderThread `SIGSEGV` ([official tutorial](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet)).
- Fix: the gradient/background is now recorded in a dedicated sibling `Box`; glass surfaces render as siblings outside the recorded layer. Blur and vibrancy remain enabled. Lens effects were removed separately as a conservative compatibility choice, not as the root-cause fix.
- Both Client and Controller passed Android 16 emulator launch smoke tests in [CI run 43162464047](https://github.com/rakhan17/Meizzo/actions/runs/43162464047).
- The same run passed signaling, Node, glass-backdrop regression-contract, and pinned RustDesk source/license checks.
- Both iOS Client and Controller Simulator builds and smoke tests passed in [CI run 43162464097](https://github.com/rakhan17/Meizzo/actions/runs/43162464097).
- The Android Client and Controller headers now display the MEIZZO logo.
- Manual two-device acceptance testing of pairing, session approval, and remote control is still required. These CI results do not constitute a corrected signed beta release.

## Next corrected v3 beta

**Status:** In development on `develop/v3`, not yet published.

### Client
- Single-page pairing experience with the six-digit code and connection status.
- Add the **Check for updates** action directly to the pairing page.
- Keep the secure updater checks: release-channel match, package ID, version, APK size, SHA-256, and signing-certificate verification.
- Installation remains an explicit Android Package Installer confirmation.

### Controller
- Three tabs: **Link**, **Devices**, and **Settings**.
- Keep **Check for updates** inside the third tab, **Settings**.
- Keep the secure updater checks and explicit Android installation confirmation.

### Shared Android experience
- AndroidLiquidGlass / Backdrop 2.0.1 presentation layer.
- Shared glass surfaces, dialogs, controls, and theme handling.
- Separate Client and Controller application IDs and credentials.

### Release validation gates
- Build both release variants from a commit reachable from `develop/v3`.
- Sign both APKs with the established long-lived release keystore.
- Install and launch both signed APKs on an Android 16 emulator.
- Verify the app process remains alive after launch before publishing assets.
- Publish the two APKs, `MEIZZO-UPDATE.json`, and `SHA256SUMS.txt` together.
- Keep the beta channel separate from Stable `v2.5.0`.

The corrected beta section will be updated with its exact tag, download assets, and test evidence only after the release workflow passes.
