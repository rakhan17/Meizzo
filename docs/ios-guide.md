# iOS guide

MEIZZO has two native SwiftUI targets:

- Client: `com.meizzo.client.ios`, XcodeGen directory `ios/Client`.
- Controller: `com.meizzo.controller.ios`, XcodeGen directory `ios/Controller`.

Shared WebSocket, reconnect, heartbeat, and Keychain code is in
`ios/Shared/MeizzoCore.swift`.

## Simulator

On macOS:

```bash
brew install xcodegen
cd ios/Client
xcodegen generate
xcodebuild -project MEIZZOClient.xcodeproj -scheme MEIZZOClient \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
```

Use `ios/Controller` and `MEIZZOController` for Controller. The CI workflow
also launches each resulting app with `ios/scripts/simulator-smoke.sh`.

`.app` is a target-specific application bundle. `.zip` is only an artifact
container. `.ipa` is a signed device distribution package; a Simulator ZIP
must never be renamed to `.ipa`.

## Physical devices and distribution

The manual `ios-device.yml` workflow requires Apple Team ID, a password-
protected certificate, matching Client/Controller provisioning profiles, and
GitHub Secrets. Development profiles are for registered development devices,
Ad Hoc profiles are for registered devices, and App Store/TestFlight exports
are distributed through Apple's services. A signed IPA is not automatically
installable on every iPhone.

iOS does not permit the Android updater model. Direct in-app installation of
arbitrary IPAs is not claimed; users should use TestFlight, the App Store, or
the appropriate managed/development distribution.
