# Upstream references and third-party licensing

MEIZZO v3 keeps its existing Apache-2.0 project license. The Android remote-access implementation is written against Android platform APIs and the WebRTC Android SDK; it does not copy or link RustDesk source files.

## RustDesk implementation references

RustDesk is used as an engineering reference for the Android host architecture:

- [RustDesk repository](https://github.com/rustdesk/rustdesk) — AGPL-3.0.
- [Android MainService.kt](https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainService.kt) — foreground service lifecycle, MediaProjection capture, encoder/capture sizing, and session cleanup.
- [Android InputService.kt](https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/InputService.kt) — AccessibilityService gesture injection and global Android actions.
- [Android DocumentStorage.kt](https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/DocumentStorage.kt) and [DocumentPicker.kt](https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/DocumentPicker.kt) — Android Storage Access Framework patterns.
- [RustDesk Android documentation](https://rustdesk.com/docs/en/client/android/) — required screen-capture and input permissions, including the user's explicit system consent.

RustDesk's mobile client is AGPL-3.0. If RustDesk source code is copied or adapted into MEIZZO, the resulting distribution and source-offer obligations must be reviewed before merging. Do not paste AGPL implementation files into the Apache-2.0 app while retaining an incompatible licensing claim. Reimplementing the same Android platform workflow independently is not the same as copying its implementation.

## WebRTC Android SDK

MEIZZO uses:

- Maven coordinate: `io.github.webrtc-sdk:android:144.7559.15`
- Upstream: [webrtc-sdk/android](https://github.com/webrtc-sdk/android)
- License: BSD 3-Clause, as published in Maven Central metadata.

The WebRTC SDK is a separate third-party dependency. Its license and notices must be included in release packaging.

## Android platform permissions

- Screen streaming: Android MediaProjection system consent and a foreground service declared as `mediaProjection`.
- Camera streaming: Android runtime CAMERA permission.
- Remote input and lock: user-enabled Android AccessibilityService.
- File transfer: Android Storage Access Framework, with a destination selected by the receiving user.

These permissions are never silently bypassed by MEIZZO.
