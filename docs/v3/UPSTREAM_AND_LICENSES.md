# Upstream references and third-party licensing

MEIZZO v3 preserves the existing Apache-2.0 license for project-owned code
where the relevant copyright holders have granted those rights. The repository
also includes `LICENSE-AGPL-3.0-only`, copied verbatim from the RustDesk
upstream license text, to prepare for a separately tracked AGPL component.

## Dual-licensing policy

Dual licensing is possible for code whose copyright holders control the
necessary rights. For MEIZZO-authored code, the intended model is:

- Apache-2.0 remains available for original MEIZZO components that can be
  distributed independently.
- AGPL-3.0-only is an additional license option for MEIZZO-authored code that
  is intentionally incorporated into the AGPL-covered remote-access product.
- Existing third-party code keeps its own license. Adding a second license
  file does not relicense RustDesk, AndroidX, WebRTC, or other dependencies.
- Contributions from outside the project may be dual-licensed only when the
  contributor's grant or explicit consent permits it. Do not assume that a
  GitHub commit alone transfers copyright.

When RustDesk implementation code is integrated into the same Android
application or a combined executable, the distributed combined work must be
handled as an AGPL-covered work unless a different written permission is
obtained from the relevant copyright holders. Do not advertise the resulting
APK as Apache-only.

## RustDesk upstream

- Client source: https://github.com/rustdesk/rustdesk
- OSS server source: https://github.com/rustdesk/rustdesk-server
- Upstream license: AGPL-3.0 (the upstream repository's `LICENCE` file).
- Android service reference:
  https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainService.kt
- Android input reference:
  https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/InputService.kt
- Android storage references:
  https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/DocumentStorage.kt
  and
  https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/DocumentPicker.kt

The current native Kotlin MEIZZO Client is not yet a RustDesk fork and does
not yet contain copied RustDesk source. The existing implementation uses
Android platform APIs and the WebRTC Android SDK. The RustDesk source is
currently an upstream engineering reference. The actual engine integration
must be done as a separately reviewed implementation milestone, pinned to a
specific upstream commit, with source provenance and notices recorded.

## Integration constraints and architecture

RustDesk is a Flutter + Rust application with native Android integration and
its own peer-to-peer protocol and server components. MEIZZO currently has
separate native Kotlin Client and Controller applications and a Cloudflare
Worker/Durable Object signaling control plane. These are not drop-in compatible.

The integration must therefore:

1. Pin an exact RustDesk upstream commit and record all included submodules.
2. Preserve RustDesk copyright and license headers in copied/modified files.
3. Keep MEIZZO branding and UI assets separate from RustDesk trademarks.
4. Build and test the Rust engine and Android native bridge before replacing
   any existing MEIZZO streaming path.
5. Keep the MEIZZO Client/Controller pairing, approval, and device identity
   model, and explicitly map those permissions to every remote operation.
6. Maintain a complete corresponding-source offer for every distributed
   AGPL-covered app and any modified AGPL server component.
7. Publish third-party notices and build instructions alongside releases.

A Cloudflare Worker is suitable for the current WSS control plane and Durable
Object coordination. It is not a drop-in host for RustDesk's native rendezvous
and relay servers, which require server processes and network capabilities
outside the Worker runtime. If the RustDesk server protocol is adopted, its
hbbs/hbbr services need a separately supported host; the existing Cloudflare
signaling service can remain for MEIZZO-native pairing/control only where the
two protocols are deliberately bridged.

## Other dependencies

- WebRTC Android SDK:
  `io.github.webrtc-sdk:android:144.7559.15`
- Upstream: https://github.com/webrtc-sdk/android
- License: BSD 3-Clause according to the published package metadata.

Each release must include a dependency/license inventory generated from the
actual resolved dependency graph. Do not infer the license of a transitive
dependency from the top-level SDK license.

## Platform permissions and consent

- Screen streaming: Android MediaProjection system consent and a foreground
  service declared as `mediaProjection`.
- Camera streaming: Android runtime CAMERA permission.
- Remote input and lock: user-enabled Android AccessibilityService.
- File transfer: Android Storage Access Framework, with the destination
  selected by the receiving user.
- Every remote operation remains gated by an explicit, scoped Client approval.
