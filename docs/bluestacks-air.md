# BlueStacks Air on macOS

The repository does not have access to a user's BlueStacks Air instance, so
this guide is a manual runtime checklist rather than a claim of completed
device testing.

## Important endpoint rule

`10.0.2.2` is an emulator convention, not a guaranteed BlueStacks Air host
gateway. If it is unreachable, use the Mac's LAN address instead. The backend
must listen on `0.0.0.0`, the Mac firewall must allow the selected port, and
both Android instances must be able to reach that address.

Run the backend on the Mac:

```bash
cd services/signaling
npm ci
PORT=8080 npm start
```

In another terminal, verify the host service:

```bash
curl --fail http://127.0.0.1:8080/healthz
```

Find the Mac's LAN address with `ipconfig getifaddr en0` (or the active
interface), then build both debug APKs with the same address:

```bash
./gradlew \
  -Pmeizzo.debugSignalingUrl=ws://192.168.1.20:8080 \
  :apps:client:assembleDebug :apps:controller:assembleDebug
```

Replace `192.168.1.20` with the address reported by macOS. Debug builds
permit cleartext only because they are explicitly intended for local
development. Release builds must use a real `wss://` endpoint and must not
use this local HTTP configuration.

## Two-instance checklist

1. Install the freshly built `client-debug.apk` in BlueStacks instance A.
2. Install the freshly built `controller-debug.apk` in instance B.
3. Confirm both applications display a backend-connected state.
4. Confirm the Client displays a six-digit code.
5. Enter that code in Controller and approve the request on Client.
6. Confirm the device becomes online and telemetry is populated.
7. Stop the Client and confirm only that device becomes offline.
8. Start a second Client and repeat pairing to verify device isolation.

Interpret errors as follows:

| Message | Meaning |
|---|---|
| Cleartext blocked | The installed APK is a release/older build or has the wrong network policy. |
| Backend host not found | The endpoint hostname or LAN address is not resolvable from BlueStacks. |
| Backend unreachable | The host, port, firewall, or server process is unavailable. |
| TLS validation failed | The WSS certificate or hostname is not trusted; do not disable validation. |

The published beta.3 APK predates the current debug network-policy fix. It
should not be used to validate this checklist; build the current debug APK or
wait for a properly signed release.
