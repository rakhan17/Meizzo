# Getting started

## What you need

- A Client device and a separate Controller device.
- Android 8.0 or newer for the published Android APKs.
- A reachable MEIZZO signaling endpoint. The local development backend listens
  on port `8080`.
- For iOS, a Mac with Xcode for Simulator builds; a physical device requires
  Apple signing and provisioning.

## Fastest Android trial

1. Start the signaling service:
   `cd services/signaling && npm ci && PORT=8080 npm start`.
2. Install the separate Client and Controller APKs from the latest release.
3. If using Android emulators or BlueStacks for local development, install a
   debug build; it defaults to `ws://10.0.2.2:8080`.
4. Open Client and keep its six-digit code visible.
5. Open Controller, enter the code, and submit the request.
6. Approve the request on Client. The Controller stores its token and adds the
   Client to the device list.

Physical Android devices need a reachable LAN WebSocket endpoint during
development or a deployed `wss://` endpoint. Do not use `10.0.2.2` on a
physical phone.

## First checks

The Controller connection indicator describes Controller-to-backend state.
Each device card separately reports Client presence. A successful build or
installed app does not prove that the configured endpoint is reachable.

Continue with [pairing](pairing.md) and [troubleshooting](troubleshooting.md).
