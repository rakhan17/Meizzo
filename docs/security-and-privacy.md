# Security and privacy

## Verified controls

- Short-lived, one-time six-digit pairing offers.
- Explicit Client approval.
- Role- and Client-scoped authentication tokens.
- Per-Client command and telemetry routing.
- Android Keystore and iOS Keychain local storage.
- TLS/WSS requirement for release Android configuration.
- No credentials in structured backend logs.

## Deployment responsibilities

The default backend is an in-memory beta service. Production operators must
provide TLS termination, persistent state with a retention policy, rate
limits, secret rotation, access controls, monitoring, backups as appropriate,
and log redaction. Do not expose the development `ws://` endpoint publicly.

## Platform boundaries

Android runtime permissions and iOS public API restrictions are not bypassed.
The current implementation does not provide unrestricted touch control,
arbitrary file browsing, silent screen capture, location sharing, or
production background monitoring. Unsupported fields are unavailable rather
than fabricated.
