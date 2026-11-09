# Development and deployment

## Local development

The signaling service requires Node.js 22 or newer:

```bash
cd services/signaling
npm ci
npm run check
npm test
PORT=8080 npm start
```

Android builds use the Gradle wrapper, JDK 17, Android SDK 35, and build tools
35.0.0:

```bash
./gradlew :apps:client:assembleDebug
./gradlew :apps:controller:assembleDebug
```

The emulator endpoint is `ws://10.0.2.2:8080`. Physical devices need the
reachable LAN address during development, or a deployed WSS endpoint.

## Three-device test

Build the debug APKs, ensure three BlueStacks/emulator instances are visible
and authorized with `adb devices`, then run:

```bash
./scripts/bluestacks-test.sh \
  apps/client/build/outputs/apk/debug/client-debug.apk \
  apps/controller/build/outputs/apk/debug/controller-debug.apk
```

The script uses the actual ADB serials returned by `adb devices`; it does not
assume BlueStacks ports. Pair the two displayed Client codes in Controller,
verify both dashboard entries, disconnect one emulator, and confirm the other
remains online. System permission dialogs and hardware limitations still
require manual verification.

See [implementation-status.md](implementation-status.md) for the verified
feature boundary and remaining product work.

## Deployment

Run the Node service behind a TLS reverse proxy or managed WebSocket platform.
Set `PORT` to the internal listener port and expose `/healthz` for monitoring.
The service intentionally stores pairing and authorization state only in memory;
restarting it invalidates active sessions and pairing offers. A production
deployment must use a stable process, resource limits, access logging that
excludes message bodies, and a WSS URL in both Android modules.

TURN credentials and WebRTC configuration are not required by the current
pairing milestone. They must be added before camera or screen media sessions
are enabled.

## Releases

Push a semver tag such as `v0.1.0` to run `.github/workflows/release.yml`.
Release signing is not configured in source. Add protected signing secrets and a
Gradle signing configuration before distributing production APKs; never commit a
keystore or password.
