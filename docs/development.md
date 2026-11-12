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

Android builds use the Gradle wrapper, JDK 17, Android SDK Platform 37 and Build Tools 37.0.0 for the Backdrop 2.0.1 integration (the release signer also installs Build Tools 35.0.0 for APK inspection):

```bash
./gradlew :apps:client:assembleDebug
./gradlew :apps:controller:assembleDebug
```

The Android plugin/dependency versions are intentionally not replaced to work
around missing local connectivity. This environment can reach Maven Central but
cannot resolve `dl.google.com`, and it has no Android SDK. The remote CI workflow installs the Android 35 and 37 SDK/build-tool packages required by the app and release validation.
Run `gh run download <run-id> --repo rakhan17/Meizzo` to download verified
Client and Controller APK artifacts from a successful run.

Debug and release builds default to the existing MEIZZO WSS endpoint:
`wss://meizzo-signaling.rakhanatanox.workers.dev`. For a local signaling
harness, override the debug endpoint with
`-Pmeizzo.debugSignalingUrl=ws://<host>:8080` using a host reachable from the
test device or emulator.

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
excludes message bodies, and a WSS URL passed to both Android modules through
`-Pmeizzo.releaseSignalingUrl=...`.

Screen and camera prototypes use WebRTC with the configured ICE servers.
The existing Worker can provide short-lived Cloudflare Realtime TURN
credentials when its TURN secrets are configured. Without a working TURN
relay, direct media connections may fail behind restrictive NATs, carrier
grade NAT, or firewalls. TURN is a connectivity enhancement, not a prerequisite
for pairing or local-network smoke tests.

## Releases

Create a `release/v3.x.y-beta.N` branch from a commit already merged into `develop/v3` to run the signed Android beta release workflow. The workflow publishes a prerelease and its APK/manifest/checksum assets. Tag pushes continue to run the Android release workflow and the iOS Simulator artifact publisher. Android release CI requires the
stable keystore and endpoint secrets documented in
[release notes](release-notes.md); it no longer generates a throwaway signing
key, because that would make future in-app updates impossible.
