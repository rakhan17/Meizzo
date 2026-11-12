# Android developer guide

Android modules are `apps/client` and `apps/controller`, with package IDs
`com.meizzo.client` and `com.meizzo.controller`. Both use Kotlin, Gradle, JDK 17, compile SDK 37 / target SDK 35, OkHttp WebSocket, and Android Keystore-backed credential storage.

## Endpoint configuration

Debug builds default to `ws://10.0.2.2:8080`. That address is not guaranteed
to work in BlueStacks Air; override it with the Mac LAN address when
necessary:

```bash
./gradlew -Pmeizzo.debugSignalingUrl=ws://192.168.1.20:8080 \
  :apps:client:assembleDebug :apps:controller:assembleDebug
```

The debug network policy is isolated to debug resources. Release resources
deny cleartext traffic. Release builds must be built with a real TLS endpoint:

```bash
./gradlew -Pmeizzo.releaseSignalingUrl=wss://signal.example \
  :apps:client:assembleRelease
```

The release workflow refuses to publish unless the endpoint and stable
keystore secrets are configured.

## Beta release

The current v3 beta is `v3.0.0-beta.1`. Install separate signed Client and Controller packages from the [GitHub prerelease](https://github.com/rakhan17/Meizzo/releases/tag/v3.0.0-beta.1). Stable `v2.5.0` remains a separate channel.

## Updates

The in-app updater checks GitHub Releases over HTTPS and follows the installed
build's channel: beta builds receive prereleases, while stable builds receive
stable releases. A release must include `MEIZZO-UPDATE.json` (schema version 1)
with separate Client and Controller entries.

The release manifest records each APK's package ID, asset name and size,
version name and code, SHA-256 digest, and signing-certificate SHA-256 digest.
The release workflow derives Android version codes from semantic version tags
so successive prereleases can be installed as upgrades instead of being
rejected as the same version.

Before installation, the updater:

1. Checks the manifest tag, channel, package ID, version, and release asset.
2. Downloads to a temporary `.part` file and verifies the expected size.
3. Verifies the APK SHA-256 digest.
4. Inspects the APK package ID and version metadata.
5. Requires the APK signing certificate to match both the installed app and
   the release manifest.
6. Renames the verified download into its final cache location and hands it to
   Android Package Installer through FileProvider.

Android may require the user to allow installation from MEIZZO. The final
installer confirmation always remains under the user's control. The updater
does not silently install, request root, accept downgrades, or bypass a
certificate mismatch.

Legacy releases without `MEIZZO-UPDATE.json` are not eligible for the new
manifest-based updater. Do not publish a new beta until the release workflow,
signing compatibility, and an actual upgrade from an existing install have
been verified.
