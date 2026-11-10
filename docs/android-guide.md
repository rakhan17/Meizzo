# Android developer guide

Android modules are `apps/client` and `apps/controller`, with package IDs
`com.meizzo.client` and `com.meizzo.controller`. Both use Kotlin, Gradle,
JDK 17, compile/target SDK 35, OkHttp WebSocket, and Android Keystore-backed
credential storage.

## Endpoint configuration

Debug builds default to `ws://10.0.2.2:8080`; the network security config
allowlists only emulator loopback addresses for debug use. Override with:

```bash
./gradlew -Pmeizzo.debugSignalingUrl=ws://192.168.1.20:8080 \
  :apps:client:assembleDebug
```

Release builds default to an invalid placeholder and must be built with a
real TLS endpoint:

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
