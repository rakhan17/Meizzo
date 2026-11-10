# Implementation status

This status reflects source code and tests in the repository, not planned work.

## Verified implemented

- Separate Android application IDs: `com.meizzo.client` and
  `com.meizzo.controller`.
- Client-generated six-digit pairing offers with ten-minute expiration,
  one-time consumption, explicit approval, and backend attempt limiting.
- Authenticated post-pairing tokens held in app-local preferences.
- One Controller associated with multiple independent Clients.
- Per-device status routing, heartbeat handling, disconnect/offline state,
  authoritative presence broadcasts with stale-connection protection, telemetry,
  and authenticated reconnect.
- Controller re-authentication across all previously paired devices.
- Controller snapshot requests routed only to the selected Client.
- Client battery percentage, charging state, storage totals, uptime, network
  transport, Android/model metadata, flashlight capability, and alarm command.
- CI workflows for Node validation and independent Android debug builds.

## Verified by automated tests

`services/signaling/npm test` covers successful pairing, one-time code reuse,
token authentication, two-device Controller management, command isolation,
disconnect status, and reconnect status.

## Not yet implemented

- WebRTC camera and MediaProjection screen streaming.
- Front/rear camera selection and adaptive media quality.
- Location sharing, scoped file browsing/upload/download/delete.
- Accessibility-based remote interaction.
- Physical BlueStacks execution in this environment; use
  `scripts/bluestacks-test.sh` with three authorized ADB serials.

Pairing tokens are now encrypted with app-specific Android Keystore AES-GCM
keys; non-secret device IDs and display metadata remain in preferences.

Flashlight control requires the Android camera permission because Android
protects torch access through the camera permission group. The implementation
reports unavailable hardware or denied permission rather than pretending the
command succeeded.

## Build status

The Gradle wrapper and CI configuration use JDK 17 and Android SDK 35. Local
builds remain unavailable in this container because `dl.google.com` does not
resolve and no Android SDK is installed. GitHub Actions run `42577957223`
successfully built both modules independently, verified their application IDs
with `aapt`, and uploaded named APK artifacts:

- `MEIZZO-Client.apk`
- `MEIZZO-Controller.apk`
