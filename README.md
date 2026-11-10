<div align="center">

<img src="assets/branding/meizzo-banner.svg" alt="MEIZZO — CONTROL YOUR CONNECTED WORLD." width="100%">

# MEIZZO

**CONTROL YOUR CONNECTED WORLD.**

Permission-based device assistance for Android and iOS, built around explicit
consent, isolated sessions, and real-time device status.

[![Android CI](https://github.com/rakhan17/Meizzo/actions/workflows/ci.yml/badge.svg)](https://github.com/rakhan17/Meizzo/actions/workflows/ci.yml)
[![iOS Simulator CI](https://github.com/rakhan17/Meizzo/actions/workflows/ios.yml/badge.svg)](https://github.com/rakhan17/Meizzo/actions/workflows/ios.yml)
[![Latest beta](https://img.shields.io/github/v/release/rakhan17/Meizzo?include_prereleases&label=latest%20beta)](https://github.com/rakhan17/Meizzo/releases)

[Documentation](https://github.com/rakhan17/Meizzo/tree/main/docs) ·
[Releases](https://github.com/rakhan17/Meizzo/releases) ·
[Issues](https://github.com/rakhan17/Meizzo/issues)

</div>

> **Beta status:** Android pairing, authorization, presence, telemetry, and
> selected commands are implemented. iOS Simulator builds are validated.
> Media streaming, unrestricted remote control, production TLS deployment,
> persistence, and signed iOS distribution remain outside the verified beta
> boundary.

## Product

MEIZZO is an ecosystem of two independent applications:

- **MEIZZO Client** runs on the device receiving assistance. It displays a
  temporary six-digit code and explicitly approves pairing requests.
- **MEIZZO Controller** runs on the operator device. It pairs with and monitors
  one or more authorized Clients independently.

The signaling service works across different networks when exposed through a
reachable endpoint. Authorization is consent-based; a Controller cannot use a
Client until the Client approves the request.

## Downloads

### Android

The latest published Android beta is **v0.1.0-beta.3**. Client and Controller
are separate packages and must not be interchanged.

<table>
<tr><th>Application</th><th>Download</th><th>Package</th><th>Minimum</th></tr>
<tr><td>MEIZZO Client</td><td><a href="https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-Client-v0.1.0-beta.3.apk">Download APK</a></td><td><code>com.meizzo.client</code></td><td>Android 8.0+</td></tr>
<tr><td>MEIZZO Controller</td><td><a href="https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-Controller-v0.1.0-beta.3.apk">Download APK</a></td><td><code>com.meizzo.controller</code></td><td>Android 8.0+</td></tr>
</table>

[Latest Release](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.3) ·
[All Releases](https://github.com/rakhan17/Meizzo/releases) ·
[SHA-256 checksums](https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/SHA256SUMS.txt) ·
[Installation guide](docs/getting-started.md)

### iOS

The published iOS artifacts are **Simulator ZIP files**, not IPAs. They
contain unsigned `.app` bundles for iOS Simulator and cannot be installed on a
physical iPhone or iPad. A signed device IPA is shown as a download only after
the manual Apple-signing workflow has produced and validated it.

- [MEIZZO Client Simulator ZIP](https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-client-iOS-simulator.zip)
- [MEIZZO Controller Simulator ZIP](https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-controller-iOS-simulator.zip)

See [iOS distribution](docs/ios-guide.md) for `.app`, `.zip`, `.ipa`,
provisioning, TestFlight, and physical-device requirements.

## Feature overview

**Android:** six-digit pairing, explicit approval, multi-device management,
authoritative presence, heartbeat telemetry, battery and charging state,
network type, storage, RAM where exposed, device metadata, flashlight and
alarm commands where supported, Android Keystore credentials, and isolated
command routing.

**iOS beta:** native SwiftUI Client and Controller targets, pairing and
approval, Keychain-backed tokens, multi-device presence, reconnect state,
heartbeat telemetry exposed by public APIs, and verified Simulator
install/launch smoke tests. iOS does not claim Android-equivalent remote
control, screen capture, file access, or background monitoring.

## Screenshots

No production screenshots are committed yet. Do not treat mockups as runtime
evidence. The capture workflow and naming convention are documented in
[docs/assets/screenshots/README.md](docs/assets/screenshots/README.md).

## Architecture

```mermaid
flowchart LR
  AC[Android Client] -->|WebSocket / WSS| B[Signaling backend]
  AO[Android Controller] -->|WebSocket / WSS| B
  IC[iOS Client] -->|WebSocket / WSS| B
  IO[iOS Controller] -->|WebSocket / WSS| B
  B --> P[Pairing + explicit approval]
  B --> A[Token authentication]
  B --> R[Presence + heartbeat]
  B --> T[Telemetry]
  B --> C[Isolated command routing]
```

```mermaid
sequenceDiagram
  participant C as Client
  participant S as Signaling
  participant O as Controller
  C->>S: register six-digit offer
  O->>S: request pairing code
  S->>C: pairing request
  C->>S: explicit approval
  S-->>C: client token
  S-->>O: controller token
  C->>S: authenticated heartbeat + telemetry
  O->>S: authenticated device list/command
  S-->>O: authoritative presence and result
```

## Repository

| Path | Responsibility |
|---|---|
| `apps/client` | Android Client, telemetry, consent UI, commands, updater |
| `apps/controller` | Android Controller, multi-device dashboard, updater |
| `ios/Client` | SwiftUI iOS Client target and Simulator project definition |
| `ios/Controller` | SwiftUI iOS Controller target and Simulator project definition |
| `ios/Shared` | iOS WebSocket, reconnect, heartbeat, and Keychain code |
| `services/signaling` | Node.js WebSocket pairing, auth, presence, telemetry, routing |
| `scripts` | Manual BlueStacks/ADB harnesses |
| `docs` | User, developer, platform, security, and release documentation |
| `.github/workflows` | Android, iOS Simulator, iOS device, release, and docs CI |

## Documentation

- [Quick start](docs/getting-started.md)
- [User manual](docs/user-guide.md)
- [Client guide](docs/client-guide.md)
- [Controller guide](docs/controller-guide.md)
- [Pairing](docs/pairing.md)
- [Device monitoring](docs/device-monitoring.md)
- [Development setup](docs/development.md)
- [Architecture](docs/architecture.md)
- [API reference](docs/api-reference.md)
- [Testing](docs/testing.md)
- [Android guide](docs/android-guide.md)
- [iOS guide](docs/ios-guide.md)
- [Backend deployment](docs/backend-deployment.md)
- [Security and privacy](docs/security-and-privacy.md)
- [Contributing](CONTRIBUTING.md)
- [Security reporting](SECURITY.md)
- [Support](SUPPORT.md)

## Security, privacy, and limitations

Pairing requires a short-lived code and explicit Client approval. Tokens are
stored in Android Keystore or iOS Keychain. Device sessions and commands are
isolated by Client identity. Logs must not contain credentials, pairing codes,
or personal data.

The current backend stores active pairing and authorization state in memory.
Production deployments require TLS/WSS, a stable deployment, operational
limits, and a persistent state strategy. iOS system restrictions prevent
unrestricted touch control, arbitrary file access, and silent screen capture.
See [security and privacy](docs/security-and-privacy.md) and
[implementation status](docs/implementation-status.md).

## Development

```bash
cd services/signaling
npm ci
npm run check
npm test
PORT=8080 npm start
```

For Android debug builds, use JDK 17, Android SDK 35, and:

```bash
./gradlew :apps:client:assembleDebug
./gradlew :apps:controller:assembleDebug
```

Debug builds default to `ws://10.0.2.2:8080` for an Android emulator or
BlueStacks instance. Release builds require
`-Pmeizzo.releaseSignalingUrl=wss://your-host.example` and do not permit
cleartext traffic. See [Android development](docs/android-guide.md).

## License status

No repository license has been finalized. Until the owner approves a license,
the source should not be described as open-source software. Apache License 2.0
is documented as the recommended option in [docs/license.md](docs/license.md).

<div align="center">

**MEIZZO** · Android and iOS beta ·
[Documentation](docs/index.md) · [Releases](https://github.com/rakhan17/Meizzo/releases) ·
[Issues](https://github.com/rakhan17/Meizzo/issues)

</div>
