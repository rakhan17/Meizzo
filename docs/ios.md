# iOS beta (`v0.1.0-beta.3`)

MEIZZO has two separate native SwiftUI applications:

- `ios/Client` — bundle identifier `com.meizzo.client.ios`, display name **MEIZZO Client**.
- `ios/Controller` — bundle identifier `com.meizzo.controller.ios`, display name **MEIZZO Controller**.

Each app has its own XcodeGen project definition, entry point, asset catalog, scheme, and build artifact. Shared protocol and Keychain code lives in `ios/Shared/MeizzoCore.swift`. Both applications use the official logo from `assets/branding/meizzo-logo.svg` for their AppIcon and in-app branding.

## Build on macOS

Install Xcode and XcodeGen, then generate and build either project:

```bash
brew install xcodegen
cd ios/Client
xcodegen generate
xcodebuild -project MEIZZOClient.xcodeproj -scheme MEIZZOClient \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
```

Use `ios/Controller` and `MEIZZOController` for the Controller app. The `iOS` GitHub Actions workflow performs both builds independently on the current `macos-15` hosted runner and uploads unsigned Simulator ZIPs.

## Physical iPhone installation

The repository does not contain signing certificates, provisioning profiles, or App Store Connect credentials. Simulator artifacts are therefore not installable on a physical iPhone. To install on hardware, open the generated project in Xcode, select an Apple Developer team, register the iPhone, enable automatic signing, and run the selected app. For distribution to testers, use TestFlight after configuring App Store Connect signing. Do not treat an unsigned Simulator ZIP as an IPA.

## Current beta boundary

Implemented: SwiftUI entry points, separate identities, editable WebSocket endpoint, six-digit pairing, explicit Client approval, Controller multi-device list, online/offline status, and Keychain storage for pairing tokens.

Not implemented: camera or ReplayKit sessions, WebRTC media, location, file exchange, remote touch control, background monitoring, push notifications, and production reconnect persistence. These remain intentionally limited by the current iOS beta and platform permissions.
