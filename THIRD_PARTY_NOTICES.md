# Third-party notices

## RustDesk

- Project: https://github.com/rustdesk/rustdesk
- Pinned commit: `95cca3f661f635b3cc85c04935fe8d96d8f432ef`
- Vendored source path: `third_party/rustdesk` (Git submodule)
- License: GNU Affero General Public License, version 3 only (AGPL-3.0-only)
- License text: [LICENSE-AGPL-3.0-only](LICENSE-AGPL-3.0-only)

The RustDesk source is included as an upstream submodule for engineering and
integration. Until an engine integration milestone is completed, this does
not mean RustDesk's runtime is linked into the Android MEIZZO APKs.

RustDesk names, logos, and other trademarks remain the property of their
respective owners. MEIZZO branding must not imply endorsement by RustDesk.

## WebRTC Android SDK

- Maven coordinate: `io.github.webrtc-sdk:android:144.7559.15`
- Upstream: https://github.com/webrtc-sdk/android
- License: BSD 3-Clause (verify against the exact resolved artifact before
  every release).

## Other dependencies

The above list is not a complete software bill of materials. Release builds
must generate and publish a complete dependency inventory, including
transitive Maven, Gradle, Rust, Flutter, and native dependencies, with the
license identifier and source URL for each component.
