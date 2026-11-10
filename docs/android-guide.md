# Android developer guide

Android modules are `apps/client` and `apps/controller`, with package IDs
`com.meizzo.client` and `com.meizzo.controller`. Both use Kotlin, Gradle,
JDK 17, compile/target SDK 35, OkHttp WebSocket, and Android Keystore-backed
credential storage.

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

## Updates

`UpdateManager.kt` queries GitHub Releases over HTTPS, selects the app-specific
asset, verifies `SHA256SUMS.txt`, validates package ID and installed signing
certificate, and delegates to Android Package Installer through FileProvider.
It does not silently install, request root, accept downgrades, or bypass a
certificate mismatch.
