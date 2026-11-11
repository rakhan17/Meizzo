# Getting started

## What you need

- Two Android devices or Android environments.
- Install **MEIZZO Client** on the device receiving assistance.
- Install **MEIZZO Controller** on the operator device.
- A network connection to the configured MEIZZO WSS signaling service.
- Android may require notification/background permissions for the connection
  service to operate as intended.

The current public Android beta is
[`v0.1.0-beta.5`](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.5).
Download the Client and Controller APKs separately from the release page.

## First pairing

1. Install and open **MEIZZO Client** on the device being assisted.
2. Keep the Client open and note the temporary six-digit pairing code.
3. Install and open **MEIZZO Controller** on the operator device.
4. Choose the add/pair device flow and enter the six-digit code.
5. Return to Client and review the incoming request.
6. Approve only if you recognize and trust the Controller.
7. After successful pairing, the Client should appear in the Controller's
   device list.

The code is temporary and single-use. Pairing requires explicit approval on
the Client.

## Start an authorized session

1. Select the paired Client in Controller.
2. Request a session type supported by the current app build.
3. Review the incoming session request on Client.
4. Approve or reject it.
5. End the session from Controller when finished.

An approved session currently represents authorization state. It does not
start screen sharing, camera streaming, file transfer, location sharing, or
general remote input. Supported interaction commands remain limited to
implemented command handlers.

## Background connection

Android Client and Controller use foreground connection services and
reconnect logic. Keep the apps allowed to run in the background if you expect
them to maintain connectivity. Some manufacturers add battery optimization,
autostart, or background restrictions. These controls can still stop the
service. Android force-stop and device shutdown also interrupt communication.

## Local development

The repository includes a Node.js signaling harness:

```bash
cd services/signaling
npm ci
npm run check
npm test
PORT=8080 npm start
```

The local Node service uses in-memory state and is intended for development
and testing. It is not the same as the hosted Cloudflare Worker deployment.
For local Android builds, configure the debug signaling URL for the network
path reachable from the emulator/device.

## iOS

iOS targets are under development. Simulator builds require macOS, Xcode, and
XcodeGen. They are not installable on a physical iPhone without the appropriate
Apple signing and provisioning setup. See the [iOS guide](ios-guide.md).

## Next steps

- [User guide](user-guide.md)
- [Pairing protocol](pairing.md)
- [Device monitoring](device-monitoring.md)
- [Troubleshooting](troubleshooting.md)
- [Implementation status](implementation-status.md)
