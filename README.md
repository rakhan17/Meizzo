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

> **Beta status:** Android is the active testing platform for this release.
> iOS is still under development and is not part of the current downloadable
> build. Media streaming, unrestricted remote control, production TLS
> deployment, persistence, and signed iOS distribution remain outside the
> verified beta boundary.

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

Get the latest MEIZZO builds here. Android Client and Controller are separate
apps. Choose the one that matches the role of each device.

### Android

<p align="center">
  <img src="https://img.shields.io/badge/LATEST-BETA%20%7C%20v0.1.0--beta.3-CCFF00?style=for-the-badge&labelColor=111111" alt="Latest beta v0.1.0-beta.3">
</p>

<table>
  <tr>
    <td align="center" width="50%">
      <strong>MEIZZO Client</strong><br>
      <sub>For the device receiving assistance</sub><br><br>
      <a href="https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-Client-v0.1.0-beta.3.apk">
        <img src="https://img.shields.io/badge/↓%20DOWNLOAD-CLIENT%20APK-3DDC84?style=for-the-badge&labelColor=111111" alt="Download MEIZZO Client APK">
      </a>
    </td>
    <td align="center" width="50%">
      <strong>MEIZZO Controller</strong><br>
      <sub>For the device managing other devices</sub><br><br>
      <a href="https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-Controller-v0.1.0-beta.3.apk">
        <img src="https://img.shields.io/badge/↓%20DOWNLOAD-CONTROLLER%20APK-60A5FA?style=for-the-badge&labelColor=111111" alt="Download MEIZZO Controller APK">
      </a>
    </td>
  </tr>
</table>

<p align="center">
  <sub>Beta software may contain bugs. Install Client on the device being assisted and Controller on the operator's device.</sub>
</p>

<table>
  <tr>
    <td>
      <strong>Latest Stable</strong><br>
      <sub>The first stable release has not been published yet.</sub>
    </td>
    <td align="right">
      <img src="https://img.shields.io/badge/STABLE-NOT%20RELEASED-F59E0B?style=for-the-badge&labelColor=111111" alt="Stable not released">
    </td>
  </tr>
</table>

<p align="center">
  <a href="https://github.com/rakhan17/Meizzo/releases">
    <img src="https://img.shields.io/badge/EXPLORE-ALL%20VERSIONS-8B5CF6?style=for-the-badge&labelColor=111111" alt="Browse all MEIZZO versions">
  </a>
</p>

### iOS

<p align="center">
  <img src="https://img.shields.io/badge/iOS-UNDER%20DEVELOPMENT-A78BFA?style=for-the-badge&labelColor=111111" alt="iOS under development">
  <br>
  <sub>Latest available development build · v0.1.0-beta.3</sub>
</p>

<table>
  <tr>
    <td align="center" width="50%">
      <strong>MEIZZO Client for iOS</strong><br>
      <sub>iOS Simulator package</sub><br><br>
      <a href="https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-client-iOS-simulator.zip">
        <img src="https://img.shields.io/badge/↓%20DOWNLOAD-CLIENT%20ZIP-A78BFA?style=for-the-badge&labelColor=111111" alt="Download iOS Client ZIP">
      </a>
    </td>
    <td align="center" width="50%">
      <strong>MEIZZO Controller for iOS</strong><br>
      <sub>iOS Simulator package</sub><br><br>
      <a href="https://github.com/rakhan17/Meizzo/releases/download/v0.1.0-beta.3/MEIZZO-controller-iOS-simulator.zip">
        <img src="https://img.shields.io/badge/↓%20DOWNLOAD-CONTROLLER%20ZIP-A78BFA?style=for-the-badge&labelColor=111111" alt="Download iOS Controller ZIP">
      </a>
    </td>
  </tr>
</table>

<p align="center">
  <sub>These are unsigned Simulator ZIPs for use with Xcode on a Mac. They cannot be installed directly on a physical iPhone.</sub>
</p>

[iOS development guide](docs/ios-guide.md)

## Feature overview

**Android:** six-digit pairing, explicit approval, multi-device management,
authoritative presence, heartbeat telemetry, battery and charging state,
network type, storage, RAM where exposed, device metadata, flashlight and
alarm commands where supported, Android Keystore credentials, and isolated
command routing.

**iOS (under development):** native SwiftUI Client and Controller targets are
in the repository, but iOS is not included in this Android-focused beta
milestone. Feature parity, distribution, and runtime validation remain
pending.

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
