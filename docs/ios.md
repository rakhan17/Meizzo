# iOS beta (`v0.1.0-beta.3`)

MEIZZO has two separate native SwiftUI applications:

- `ios/Client` — bundle identifier `com.meizzo.client.ios`, display name **MEIZZO Client**.
- `ios/Controller` — bundle identifier `com.meizzo.controller.ios`, display name **MEIZZO Controller**.

Each app has its own XcodeGen project definition, entry point, asset catalog, scheme, and build artifact. Shared protocol and Keychain code lives in `ios/Shared/MeizzoCore.swift`. Both applications use the official logo from `assets/branding/meizzo-logo.svg` for their AppIcon and in-app branding.

## Build Simulator apps

Install Xcode and XcodeGen, then generate and build either project:

```bash
brew install xcodegen
cd ios/Client
xcodegen generate
xcodebuild -project MEIZZOClient.xcodeproj -scheme MEIZZOClient \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
```

Use `ios/Controller` and `MEIZZOController` for the Controller app. The
`iOS` GitHub Actions workflow performs both builds independently on the
current `macos-15` hosted runner, installs each app into an available iPhone
Simulator, launches it, and uploads unsigned Simulator ZIPs. The smoke test
proves installation and launch only; it does not prove a live pairing session.

The backend protocol is covered by
`services/signaling/test/pairing-smoke.test.js` and
`services/signaling/test/multi-device.test.js`, including pairing approval,
multi-device isolation, heartbeat telemetry, offline transitions, and
reconnection. No XCUITest target exists yet.

## Physical-device IPA workflow

The repository includes a separate manual workflow:
[`.github/workflows/ios-device.yml`](../.github/workflows/ios-device.yml).
It uses `iphoneos`, archives Client and Controller independently, exports real
IPAs, and validates the bundle identifier, code signature, and embedded
provisioning profile. It never creates an unsigned IPA or renames a Simulator
ZIP.

Run it from GitHub Actions after configuring these repository secrets:

| Secret | Purpose |
|---|---|
| `APPLE_TEAM_ID` | Apple Developer Team ID |
| `APPLE_SIGNING_IDENTITY` | Exact certificate identity, such as `Apple Development` or `Apple Distribution` |
| `APPLE_CERTIFICATE_BASE64` | Base64-encoded `.p12` signing certificate |
| `APPLE_CERTIFICATE_PASSWORD` | Password for the `.p12` file |
| `APPLE_PROFILE_CLIENT_BASE64` | Base64-encoded profile for `com.meizzo.client.ios` |
| `APPLE_PROFILE_CONTROLLER_BASE64` | Base64-encoded profile for `com.meizzo.controller.ios` |
| `MEIZZO_KEYCHAIN_PASSWORD` | Temporary CI keychain password |

Create the certificate and profiles in the Apple Developer portal or Xcode,
export the certificate as a password-protected `.p12`, and encode files
locally without committing them:

```bash
base64 -i signing.p12 | pbcopy
base64 -i MEIZZOClient.mobileprovision | pbcopy
```

Use a **Development** profile to install on registered development devices,
**Ad Hoc** for registered test devices without Xcode, or **App Store** for
TestFlight/App Store distribution. The selected profile must match the
workflow input and the exact bundle identifier. TestFlight requires App Store
Connect configuration in addition to the signing certificate and profile.

## Physical iPhone installation

The repository does not contain signing certificates, provisioning profiles,
or App Store Connect credentials. Until the manual device workflow is run with
valid secrets, no physical-device IPA is available. Simulator artifacts are
not installable on a physical iPhone. To install on hardware, use Xcode with
automatic signing or run the manual workflow with a matching Development or
Ad Hoc profile. For distribution to testers, use TestFlight with App Store
Connect signing. Do not treat an unsigned Simulator ZIP as an IPA.

## Current beta boundary

Implemented: SwiftUI entry points, separate identities, editable WebSocket
endpoint, six-digit pairing, explicit Client approval, Controller multi-device
list, authoritative online/offline status, heartbeat telemetry, reconnect
handling, and Keychain storage for pairing tokens.

Not implemented: camera or ReplayKit sessions, WebRTC media, location, file exchange, remote touch control, background monitoring, push notifications, and production reconnect persistence. These remain intentionally limited by the current iOS beta and platform permissions.
